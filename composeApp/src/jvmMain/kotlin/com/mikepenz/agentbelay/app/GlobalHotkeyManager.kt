package com.mikepenz.agentbelay.app

import co.touchlab.kermit.Logger
import com.mikepenz.agentbelay.di.AppEnvironment
import com.mikepenz.agentbelay.di.AppScope
import com.mikepenz.agentbelay.model.Decision
import com.mikepenz.agentbelay.model.GlobalHotkey
import com.mikepenz.agentbelay.model.HotkeyModifier
import com.mikepenz.agentbelay.state.AppStateManager
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import io.github.kdroidfilter.nucleus.globalhotkey.GlobalHotKeyManager
import io.github.kdroidfilter.nucleus.globalhotkey.HotKeyListener
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

/**
 * App-scoped wrapper around Nucleus' [GlobalHotKeyManager] that keeps the OS-
 * level shortcuts in sync with [com.mikepenz.agentbelay.model.AppSettings].
 *
 * Two shortcuts are supported: approve-oldest and deny-oldest. When either
 * fires we resolve the **oldest** entry in [AppStateManager.state]'s
 * `pendingApprovals` list (which is prepended on insert, so the oldest is at
 * the tail). If no requests are pending the keystroke is a no-op.
 *
 * **Threading**: every Nucleus call (`initialize`, `register`, `unregister`,
 * `shutdown`) is dispatched onto the AWT EDT. macOS' Carbon
 * `RegisterEventHotKey` and Linux' X11 grab path both expect to be invoked
 * from the main UI thread. Without this wrapping the registration silently
 * succeeds (returning a handle) on macOS but the listener never fires.
 */
@SingleIn(AppScope::class)
@Inject
class GlobalHotkeyManager(
    private val stateManager: AppStateManager,
    private val environment: AppEnvironment,
) {
    private val log = Logger.withTag("GlobalHotkeyManager")

    private var collectorJob: Job? = null
    private var initialized = false

    private var approveHandle: Long = 0L
    private var denyHandle: Long = 0L
    private var approveBound: GlobalHotkey? = null
    private var denyBound: GlobalHotkey? = null

    /**
     * UI-visible registration errors per role. Null = no error (either no
     * binding configured or registration succeeded). Non-null = the OS
     * rejected the combination — usually because it's already in use system
     * wide. The settings UI surfaces this under the relevant capture field.
     */
    private val _approveError = MutableStateFlow<String?>(null)
    val approveError: StateFlow<String?> = _approveError.asStateFlow()

    private val _denyError = MutableStateFlow<String?>(null)
    val denyError: StateFlow<String?> = _denyError.asStateFlow()

    fun start() {
        if (collectorJob != null) return
        if (!initialized) {
            initialized = onEdt<Boolean> { GlobalHotKeyManager.initialize() }
            log.i {
                "Nucleus initialize: result=$initialized, available=${GlobalHotKeyManager.isAvailable}, " +
                    "lastError=${GlobalHotKeyManager.lastError}"
            }
            if (!initialized) {
                log.w {
                    "Global hotkey manager unavailable on this platform: " +
                        (GlobalHotKeyManager.lastError ?: "unknown")
                }
            }
        }
        collectorJob = environment.appScope.launch {
            stateManager.state
                .map { it.settings.approveOldestHotkey to it.settings.denyOldestHotkey }
                .distinctUntilChanged()
                .collect { (approve, deny) ->
                    syncBinding(role = HotkeyRole.APPROVE, target = approve)
                    syncBinding(role = HotkeyRole.DENY, target = deny)
                }
        }
    }

    fun shutdown() {
        collectorJob?.cancel()
        collectorJob = null
        if (approveHandle != 0L) {
            onEdt { GlobalHotKeyManager.unregister(approveHandle) }
            approveHandle = 0L
            approveBound = null
        }
        if (denyHandle != 0L) {
            onEdt { GlobalHotKeyManager.unregister(denyHandle) }
            denyHandle = 0L
            denyBound = null
        }
        if (initialized) {
            runCatching { onEdtUnit { GlobalHotKeyManager.shutdown() } }
            initialized = false
        }
    }

    private fun syncBinding(role: HotkeyRole, target: GlobalHotkey?) {
        val (boundRef, handleRef) = when (role) {
            HotkeyRole.APPROVE -> approveBound to approveHandle
            HotkeyRole.DENY -> denyBound to denyHandle
        }
        if (boundRef == target) return  // already up to date
        // Tear down the old registration if any.
        if (handleRef != 0L) {
            onEdtUnit { GlobalHotKeyManager.unregister(handleRef) }
            setHandle(role, 0L, null)
        }
        setError(role, null)
        if (target == null) return
        if (!initialized) {
            log.w { "Cannot register $role hotkey ($target) — Nucleus not initialized" }
            setError(role, "Global hotkey support is unavailable on this platform")
            return
        }
        // Nucleus' native bridges (Carbon on macOS, X11 on Linux, WM_HOTKEY
        // on Windows) already translate AWT VK_* codes to the appropriate
        // platform key codes and expect the portable modifier bitmask
        // (1=ALT, 2=CONTROL, 4=SHIFT, 8=META). We pass through unchanged.
        //
        // **API order matters**: GlobalHotKeyManager.register(keyCode, modifiers, listener).
        // The first int is the AWT VK_* code, the second is the portable
        // modifier bitmask. Calling these in the wrong order makes Nucleus
        // try to translate the modifier bits as a key code and reject the
        // call as "Unsupported key code" — which has bitten us before.
        val portableMods = target.modifiers.fold(0) { acc, m -> acc or m.portableFlag() }
        val handle = runCatching {
            onEdt<Long> {
                GlobalHotKeyManager.register(
                    target.keyCode,
                    portableMods,
                    HotKeyListener { mods, key ->
                        log.d { "$role hotkey listener fired (mods=$mods, key=$key)" }
                        onHotkeyFired(role)
                    },
                )
            }
        }.getOrElse {
            log.e(it) { "Failed to register $role hotkey: $target (key=${target.keyCode}, mods=$portableMods)" }
            setError(role, "Failed to register: ${it.message ?: it::class.simpleName}")
            return
        }
        // Nucleus returns 0L for "manager not ready" / unsupported-platform
        // fall-through and -1L when its native bridge reports an error
        // (e.g. macOS' RegisterEventHotKey returned non-noErr because the
        // shortcut conflicts with a system-reserved combo). Treat anything
        // <= 0 as a failed registration and surface Nucleus' lastError.
        if (handle <= 0L) {
            val nucleusErr = GlobalHotKeyManager.lastError ?: "registration rejected by OS"
            log.w {
                "Nucleus refused to register $role hotkey $target " +
                    "(key=${target.keyCode}, mods=$portableMods, handle=$handle): $nucleusErr"
            }
            setError(role, friendlyError(nucleusErr))
            return
        }
        setHandle(role, handle, target)
        log.i {
            "Registered $role hotkey: $target (key=${target.keyCode}, mods=$portableMods, handle=$handle)"
        }
    }

    private fun setError(role: HotkeyRole, message: String?) {
        when (role) {
            HotkeyRole.APPROVE -> _approveError.value = message
            HotkeyRole.DENY -> _denyError.value = message
        }
    }

    /**
     * Maps Nucleus' raw error strings to short messages friendly enough to
     * render in a small UI hint. Falls back to the raw text when no mapping
     * matches so the user sees *something* rather than an opaque label.
     */
    private fun friendlyError(raw: String): String = when {
        raw.contains("RegisterEventHotKey", ignoreCase = true) ||
            raw.contains("already", ignoreCase = true) ->
            "Already in use by another app"
        raw.contains("Unsupported key code", ignoreCase = true) ->
            "This key isn't supported as a global shortcut"
        raw.contains("Not initialized", ignoreCase = true) ->
            "Global hotkey support is unavailable"
        else -> raw
    }

    private fun setHandle(role: HotkeyRole, handle: Long, bound: GlobalHotkey?) {
        when (role) {
            HotkeyRole.APPROVE -> {
                approveHandle = handle
                approveBound = bound
            }
            HotkeyRole.DENY -> {
                denyHandle = handle
                denyBound = bound
            }
        }
    }

    private fun onHotkeyFired(role: HotkeyRole) {
        // Pending list is newest-first (see AppStateManager.addPending) — the
        // last entry is the oldest unresolved request, which is what the user
        // most likely intends to action with a "next item" hotkey.
        val oldest = stateManager.state.value.pendingApprovals.lastOrNull()
        if (oldest == null) {
            log.d { "Hotkey $role fired but no pending approvals" }
            return
        }
        val (decision, feedback) = when (role) {
            HotkeyRole.APPROVE -> Decision.APPROVED to "Approved via global hotkey"
            HotkeyRole.DENY -> Decision.DENIED to "Denied via global hotkey"
        }
        stateManager.resolve(
            requestId = oldest.id,
            decision = decision,
            feedback = feedback,
            riskAnalysis = null,
            rawResponseJson = null,
        )
        log.i { "Hotkey $role resolved oldest pending ${oldest.id}" }
    }

    private enum class HotkeyRole { APPROVE, DENY }
}

/**
 * Portable modifier bitmask the Nucleus native bridges expect (regardless of
 * OS). Values come from Nucleus' own internal `HotKeyModifier.nativeFlag` —
 * each native bridge translates them to its platform's representation
 * (Carbon `cmdKey/shiftKey/...` on macOS, `MOD_*` on Windows, etc.) before
 * calling the OS API.
 */
private fun HotkeyModifier.portableFlag(): Int = when (this) {
    HotkeyModifier.ALT -> 1
    HotkeyModifier.CONTROL -> 2
    HotkeyModifier.SHIFT -> 4
    HotkeyModifier.META -> 8
}

/**
 * Run [block] on the AWT EDT and return its result. macOS' Carbon hotkey API
 * (and the X11 keyboard-grab path on Linux) require registration to happen
 * on the main / UI thread; calling from a coroutine IO dispatcher silently
 * succeeds but the listener never fires. The Nucleus library does not route
 * onto the EDT itself, so we do it here.
 */
private fun <T> onEdt(block: () -> T): T {
    if (java.awt.EventQueue.isDispatchThread()) return block()
    val ref = arrayOfNulls<Any?>(1)
    val err = arrayOfNulls<Throwable>(1)
    java.awt.EventQueue.invokeAndWait {
        try {
            ref[0] = block()
        } catch (t: Throwable) {
            err[0] = t
        }
    }
    err[0]?.let { throw it }
    @Suppress("UNCHECKED_CAST")
    return ref[0] as T
}

private fun onEdtUnit(block: () -> Unit) {
    if (java.awt.EventQueue.isDispatchThread()) block()
    else java.awt.EventQueue.invokeAndWait(block)
}
