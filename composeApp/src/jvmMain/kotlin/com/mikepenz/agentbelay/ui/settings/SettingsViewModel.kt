package com.mikepenz.agentbelay.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mikepenz.agentbelay.capability.CapabilityEngine
import com.mikepenz.agentbelay.capability.CapabilityModule
import com.mikepenz.agentbelay.capability.HookEvent
import com.mikepenz.agentbelay.di.AppScope
import com.mikepenz.agentbelay.hook.CopilotBridge
import com.mikepenz.agentbelay.hook.HookRegistry
import com.mikepenz.agentbelay.hook.RegistrationEvents
import com.mikepenz.agentbelay.model.AppSettings
import com.mikepenz.agentbelay.model.CapabilitySettings
import com.mikepenz.agentbelay.model.ProtectionSettings
import com.mikepenz.agentbelay.app.GlobalHotkeyManager
import com.mikepenz.agentbelay.app.RiskAnalyzerLifecycle
import com.mikepenz.agentbelay.protection.ProtectionEngine
import com.mikepenz.agentbelay.protection.ProtectionModule
import com.mikepenz.agentbelay.risk.CopilotInitState
import com.mikepenz.agentbelay.risk.CopilotStateHolder
import com.mikepenz.agentbelay.risk.OllamaInitState
import com.mikepenz.agentbelay.risk.OllamaMetrics
import com.mikepenz.agentbelay.risk.OllamaStateHolder
import com.mikepenz.agentbelay.state.AppStateManager
import com.mikepenz.agentbelay.update.UpdateManager
import com.mikepenz.agentbelay.update.UpdateUiState
import dev.zacsweers.metro.ContributesIntoMap
import dev.zacsweers.metro.Inject
import dev.zacsweers.metrox.viewmodel.ViewModelKey
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * ViewModel backing the Settings tab and its four sub-tabs.
 *
 * Combines [AppStateManager.state] with two pieces of UI-only state — whether
 * the Claude Code hook and whether the Copilot user-scoped hook are currently
 * registered — into a single [SettingsUiState]. Both flags are re-polled
 * whenever `serverPort` changes since the registered hook bakes the port into
 * its bridge scripts.
 *
 * All [HookRegistry] / [CopilotBridge] calls hit the user's home directory so
 * they are dispatched to [ioDispatcher] (defaults to [Dispatchers.IO]) to keep
 * the main thread free at startup and on settings changes.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@Inject
@ViewModelKey
@ContributesIntoMap(AppScope::class)
class SettingsViewModel(
    private val stateManager: AppStateManager,
    private val copilotBridge: CopilotBridge,
    private val copilotStateHolder: CopilotStateHolder,
    private val ollamaStateHolder: OllamaStateHolder,
    private val riskAnalyzerLifecycle: RiskAnalyzerLifecycle,
    private val globalHotkeyManager: GlobalHotkeyManager,
    protectionEngine: ProtectionEngine,
    capabilityEngine: CapabilityEngine,
    private val hookRegistry: HookRegistry,
    private val registrationEvents: RegistrationEvents,
    private val updateManager: UpdateManager,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
) : ViewModel() {

    val updateState: StateFlow<UpdateUiState> = updateManager.state
    val isUpdateSupported: Boolean get() = updateManager.isSupported

    fun checkForUpdates() = updateManager.check()
    fun downloadUpdate() = updateManager.downloadAvailable()
    fun installUpdate() = updateManager.installCurrent()
    fun resetUpdateState() = updateManager.reset()

    val capabilityModules: List<CapabilityModule> = capabilityEngine.modules

    /**
     * Single-slot dispatcher derived from [ioDispatcher]. All write actions
     * (settings updates, hook register/unregister, copilot register/unregister,
     * clearHistory) launch on this so concurrent calls drain in FIFO order
     * — preserving the user's intent when they click two settings rapidly.
     * Reads use the unrestricted [ioDispatcher] so they can run in parallel.
     */
    private val writeDispatcher = ioDispatcher.limitedParallelism(1)

    val protectionModules: List<ProtectionModule> = protectionEngine.modules

    // Initialised to a safe default; the real value is read from disk by the
    // port-flow collector below and re-polled whenever the server port changes.
    private val isHookRegistered = MutableStateFlow(false)
    private val isCopilotRegistered = MutableStateFlow(false)

    /** Pre-combined Copilot lifecycle state to keep the main `combine` arity reasonable. */
    private val copilotState: kotlinx.coroutines.flow.Flow<Pair<List<Pair<String, String>>, CopilotInitState>> =
        combine(copilotStateHolder.models, copilotStateHolder.initState) { models, state -> models to state }

    /** Pre-combined Ollama lifecycle state, same reason. Bundles models, init state, error, metrics, version. */
    private val ollamaState: kotlinx.coroutines.flow.Flow<OllamaSnapshot> = combine(
        ollamaStateHolder.models,
        ollamaStateHolder.initState,
        ollamaStateHolder.lastError,
        ollamaStateHolder.lastMetrics,
        ollamaStateHolder.version,
    ) { models, state, error, metrics, version ->
        OllamaSnapshot(models, state, error, metrics, version)
    }

    /** Pre-combined hotkey error state, exposed to the settings UI. */
    private val hotkeyErrors: kotlinx.coroutines.flow.Flow<Pair<String?, String?>> =
        combine(globalHotkeyManager.approveError, globalHotkeyManager.denyError) { a, d -> a to d }

    val uiState: StateFlow<SettingsUiState> = combine(
        combine(
            stateManager.state,
            isHookRegistered,
            isCopilotRegistered,
            copilotState,
            ollamaState,
        ) { state, hookRegistered, copilotRegistered, copilot, ollama ->
            UiBase(state, hookRegistered, copilotRegistered, copilot, ollama)
        },
        hotkeyErrors,
    ) { base, hotkeys ->
        SettingsUiState(
            settings = base.state.settings,
            historyCount = base.state.history.size,
            isHookRegistered = base.hookRegistered,
            isCopilotRegistered = base.copilotRegistered,
            copilotModels = base.copilot.first,
            copilotInitState = base.copilot.second,
            ollamaModels = base.ollama.models,
            ollamaInitState = base.ollama.initState,
            ollamaLastError = base.ollama.lastError,
            ollamaLastMetrics = base.ollama.lastMetrics,
            ollamaVersion = base.ollama.version,
            approveHotkeyError = hotkeys.first,
            denyHotkeyError = hotkeys.second,
        )
    }.stateIn(
        viewModelScope,
        SharingStarted.Eagerly,
        SettingsUiState(
            settings = stateManager.state.value.settings,
            historyCount = stateManager.state.value.history.size,
            isHookRegistered = false,
            isCopilotRegistered = false,
            copilotModels = copilotStateHolder.models.value,
            copilotInitState = copilotStateHolder.initState.value,
            ollamaModels = ollamaStateHolder.models.value,
            ollamaInitState = ollamaStateHolder.initState.value,
            ollamaLastError = ollamaStateHolder.lastError.value,
            ollamaLastMetrics = ollamaStateHolder.lastMetrics.value,
            ollamaVersion = ollamaStateHolder.version.value,
            approveHotkeyError = globalHotkeyManager.approveError.value,
            denyHotkeyError = globalHotkeyManager.denyError.value,
        ),
    )

    init {
        // Poll both registrations whenever the configured port changes. The
        // first emission of `serverPort` covers app startup; subsequent
        // emissions cover the case where the user edits the port. Always run
        // on [ioDispatcher] because both backends parse files under ~.
        viewModelScope.launch {
            stateManager.state
                .map { it.settings.serverPort }
                .distinctUntilChanged()
                .collect { port ->
                    val (claude, copilot) = withContext(ioDispatcher) {
                        hookRegistry.isRegistered(port) to copilotBridge.isRegistered(port)
                    }
                    isHookRegistered.value = claude
                    isCopilotRegistered.value = copilot
                }
        }

        // Reconcile capability hooks whenever `serverPort` changes, in
        // addition to the startup emission. Capability hooks have no
        // manual re-register button in the UI (unlike the main approval
        // hooks), so without this they would orphan their old-port entries
        // silently when the user changes `serverPort`. Runs on the
        // serialized `writeDispatcher` so it drains FIFO with
        // `updateCapabilitySettings` and `register*Hook()` calls.
        viewModelScope.launch(writeDispatcher) {
            stateManager.state
                .map { it.settings.serverPort }
                .distinctUntilChanged()
                .collect { port ->
                    val settings = stateManager.state.value.settings
                    reconcileCapabilityHooks(port, settings.capabilitySettings, settings.copilotFailClosed)
                }
        }

        // Re-bake the Copilot bridge scripts whenever the fail-closed flag
        // changes, but only for whichever of the two hook surfaces is
        // currently installed. `drop(1)` skips the initial emission so
        // startup doesn't trigger an unsolicited install.
        //
        // Two branches because capability hooks can be installed
        // independently of the main approval hooks (see
        // `reconcileCapabilityHooks`). The `else if` is safe — and required
        // to avoid double-writing — because `copilotBridge.register()`
        // already refreshes the capability script in place when it exists
        // on disk, so when the main hook is registered a single call
        // re-bakes all three scripts.
        viewModelScope.launch(writeDispatcher) {
            stateManager.state
                .map { it.settings.copilotFailClosed }
                .distinctUntilChanged()
                .drop(1)
                .collect { failClosed ->
                    val port = stateManager.state.value.settings.serverPort
                    if (copilotBridge.isRegistered(port)) {
                        copilotBridge.register(port, failClosed)
                        isCopilotRegistered.value = copilotBridge.isRegistered(port)
                    } else if (copilotBridge.isCapabilityHookRegistered(port)) {
                        copilotBridge.registerCapabilityHook(port, failClosed)
                    }
                }
        }
    }

    fun updateSettings(settings: AppSettings) {
        viewModelScope.launch(writeDispatcher) {
            stateManager.updateSettings(settings)
        }
    }

    fun updateProtectionSettings(protectionSettings: ProtectionSettings) {
        viewModelScope.launch(writeDispatcher) {
            val current = stateManager.state.value.settings
            stateManager.updateSettings(current.copy(protectionSettings = protectionSettings))
        }
    }

    /**
     * Updates capability settings and, as a side effect, reconciles each
     * agent's capability hook registration — Claude Code's `UserPromptSubmit`
     * entry and Copilot CLI's `sessionStart` entry. The capability hook is
     * installed iff at least one capability is enabled.
     *
     * Reconciliation is unconditional: it updates both agents' capability
     * hook entries based purely on capability state, independent of whether
     * the main approval hooks are registered.
     */
    fun updateCapabilitySettings(capabilitySettings: CapabilitySettings) {
        viewModelScope.launch(writeDispatcher) {
            val current = stateManager.state.value.settings
            stateManager.updateSettings(current.copy(capabilitySettings = capabilitySettings))
            reconcileCapabilityHooks(current.serverPort, capabilitySettings, current.copilotFailClosed)
        }
    }

    /**
     * Writes or removes the capability hook entries for both agents based on
     * whether any capability module is enabled. Unconditional — no gating on
     * whether the main approval hooks are present, because capability and
     * approval hooks are independent features and the user may want one
     * without the other.
     */
    private fun reconcileCapabilityHooks(port: Int, capSettings: CapabilitySettings, copilotFailClosed: Boolean) {
        val enabledModules = capabilityModules.filter { capSettings.modules[it.id]?.enabled == true }
        val requiredEvents = enabledModules.flatMap { it.requiredHookEvents }.toSet()

        // UserPromptSubmit hook — only if an enabled module requires it.
        if (HookEvent.USER_PROMPT_SUBMIT in requiredEvents) {
            hookRegistry.registerCapabilityHook(port)
        } else {
            hookRegistry.unregisterCapabilityHook(port)
        }

        // SessionStart hook — only if an enabled module requires it.
        if (HookEvent.SESSION_START in requiredEvents) {
            hookRegistry.registerSessionStartHook(port)
        } else {
            hookRegistry.unregisterSessionStartHook(port)
        }

        // Copilot: sessionStart covers both events, register if anything is enabled.
        if (requiredEvents.isNotEmpty()) {
            copilotBridge.registerCapabilityHook(port, copilotFailClosed)
        } else {
            hookRegistry.unregisterCapabilityHook(port)
            copilotBridge.unregisterCapabilityHook(port)
        }
    }

    fun registerHook() {
        viewModelScope.launch(writeDispatcher) {
            val port = stateManager.state.value.settings.serverPort
            hookRegistry.register(port)
            isHookRegistered.value = hookRegistry.isRegistered(port)
            registrationEvents.emit()
        }
    }

    fun unregisterHook() {
        viewModelScope.launch(writeDispatcher) {
            val port = stateManager.state.value.settings.serverPort
            hookRegistry.unregister(port)
            isHookRegistered.value = hookRegistry.isRegistered(port)
            registrationEvents.emit()
        }
    }

    fun registerCopilot() {
        viewModelScope.launch(writeDispatcher) {
            val settings = stateManager.state.value.settings
            copilotBridge.register(settings.serverPort, settings.copilotFailClosed)
            isCopilotRegistered.value = copilotBridge.isRegistered(settings.serverPort)
            registrationEvents.emit()
        }
    }

    fun unregisterCopilot() {
        viewModelScope.launch(writeDispatcher) {
            val port = stateManager.state.value.settings.serverPort
            copilotBridge.unregister(port)
            isCopilotRegistered.value = copilotBridge.isRegistered(port)
            registrationEvents.emit()
        }
    }

    fun clearHistory() {
        viewModelScope.launch(writeDispatcher) {
            stateManager.clearHistory()
        }
    }

    /** Re-probes `/api/tags` and refreshes the model dropdown. */
    fun refreshOllamaModels() {
        viewModelScope.launch(ioDispatcher) {
            riskAnalyzerLifecycle.refreshOllamaModels()
        }
    }
}

private data class OllamaSnapshot(
    val models: List<String>,
    val initState: OllamaInitState,
    val lastError: String?,
    val lastMetrics: OllamaMetrics?,
    val version: String?,
)

/**
 * Snapshot of all settings-tab inputs. Computed by [SettingsViewModel] from
 * [AppStateManager.state] plus the hook-registered flags for both agents.
 */
data class SettingsUiState(
    val settings: AppSettings,
    val historyCount: Int,
    val isHookRegistered: Boolean,
    val isCopilotRegistered: Boolean,
    val copilotModels: List<Pair<String, String>>,
    val copilotInitState: CopilotInitState,
    val ollamaModels: List<String> = emptyList(),
    val ollamaInitState: OllamaInitState = OllamaInitState.IDLE,
    val ollamaLastError: String? = null,
    val ollamaLastMetrics: OllamaMetrics? = null,
    val ollamaVersion: String? = null,
    val approveHotkeyError: String? = null,
    val denyHotkeyError: String? = null,
)

private data class UiBase(
    val state: com.mikepenz.agentbelay.state.AppState,
    val hookRegistered: Boolean,
    val copilotRegistered: Boolean,
    val copilot: Pair<List<Pair<String, String>>, CopilotInitState>,
    val ollama: OllamaSnapshot,
)
