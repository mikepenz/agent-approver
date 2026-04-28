package com.mikepenz.agentbuddy.app

import co.touchlab.kermit.Logger
import com.mikepenz.agentbuddy.di.AppEnvironment
import com.mikepenz.agentbuddy.di.AppScope
import com.mikepenz.agentbuddy.model.Decision
import com.mikepenz.agentbuddy.model.GlobalHotkey
import com.mikepenz.agentbuddy.model.HotkeyModifier
import com.mikepenz.agentbuddy.state.AppStateManager
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import io.github.kdroidfilter.nucleus.globalhotkey.GlobalHotKeyManager
import io.github.kdroidfilter.nucleus.globalhotkey.HotKeyListener
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

/**
 * App-scoped wrapper around Nucleus' [GlobalHotKeyManager] that keeps the OS-
 * level shortcuts in sync with [com.mikepenz.agentbuddy.model.AppSettings].
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
        if (target == null) return
        if (!initialized) {
            log.w { "Cannot register $role hotkey ($target) — Nucleus not initialized" }
            return
        }
        // Nucleus' native bridges (Carbon on macOS, X11 on Linux, WM_HOTKEY
        // on Windows) already translate AWT VK_* codes to the appropriate
        // platform key codes and expect the portable modifier bitmask
        // (1=ALT, 2=CONTROL, 4=SHIFT, 8=META). We pass through unchanged.
        val portableMods = target.modifiers.fold(0) { acc, m -> acc or m.portableFlag() }
        val handle = runCatching {
            onEdt<Long> {
                GlobalHotKeyManager.register(
                    portableMods,
                    target.keyCode,
                    HotKeyListener { mods, key ->
                        log.d { "$role hotkey listener fired (mods=$mods, key=$key)" }
                        onHotkeyFired(role)
                    },
                )
            }
        }.getOrElse {
            log.e(it) { "Failed to register $role hotkey: $target (mods=$portableMods, key=${target.keyCode})" }
            return
        }
        // Nucleus returns 0L for "manager not ready" / unsupported-platform
        // fall-through and -1L when its native bridge reports an error
        // (e.g. macOS' RegisterEventHotKey returned non-noErr because the
        // shortcut conflicts with a system-reserved combo, or AWT key code
        // isn't in the bridge's translation table). Treat anything <= 0 as
        // a failed registration and surface Nucleus' lastError so the user
        // can tell why.
        if (handle <= 0L) {
            log.w {
                "Nucleus refused to register $role hotkey $target " +
                    "(mods=$portableMods, key=${target.keyCode}, handle=$handle): " +
                    "${GlobalHotKeyManager.lastError}"
            }
            return
        }
        setHandle(role, handle, target)
        log.i {
            "Registered $role hotkey: $target (mods=$portableMods, key=${target.keyCode}, handle=$handle)"
        }
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
