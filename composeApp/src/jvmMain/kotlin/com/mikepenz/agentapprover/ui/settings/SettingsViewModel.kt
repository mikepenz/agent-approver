package com.mikepenz.agentapprover.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mikepenz.agentapprover.di.AppScope
import com.mikepenz.agentapprover.hook.CopilotBridge
import com.mikepenz.agentapprover.hook.HookRegistry
import com.mikepenz.agentapprover.model.AppSettings
import com.mikepenz.agentapprover.model.ProtectionSettings
import com.mikepenz.agentapprover.protection.ProtectionEngine
import com.mikepenz.agentapprover.protection.ProtectionModule
import com.mikepenz.agentapprover.risk.CopilotInitState
import com.mikepenz.agentapprover.risk.CopilotStateHolder
import com.mikepenz.agentapprover.risk.OllamaInitState
import com.mikepenz.agentapprover.risk.OllamaStateHolder
import com.mikepenz.agentapprover.state.AppStateManager
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
    protectionEngine: ProtectionEngine,
    private val hookRegistry: HookRegistry,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
) : ViewModel() {

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

    /** Pre-combined Ollama lifecycle state, same reason. */
    private val ollamaState: kotlinx.coroutines.flow.Flow<Pair<List<String>, OllamaInitState>> =
        combine(ollamaStateHolder.models, ollamaStateHolder.initState) { models, state -> models to state }

    val uiState: StateFlow<SettingsUiState> = combine(
        stateManager.state,
        isHookRegistered,
        isCopilotRegistered,
        copilotState,
        ollamaState,
    ) { state, hookRegistered, copilotRegistered, copilot, ollama ->
        SettingsUiState(
            settings = state.settings,
            historyCount = state.history.size,
            isHookRegistered = hookRegistered,
            isCopilotRegistered = copilotRegistered,
            copilotModels = copilot.first,
            copilotInitState = copilot.second,
            ollamaModels = ollama.first,
            ollamaInitState = ollama.second,
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

    fun registerHook() {
        viewModelScope.launch(writeDispatcher) {
            val port = stateManager.state.value.settings.serverPort
            hookRegistry.register(port)
            isHookRegistered.value = hookRegistry.isRegistered(port)
        }
    }

    fun unregisterHook() {
        viewModelScope.launch(writeDispatcher) {
            val port = stateManager.state.value.settings.serverPort
            hookRegistry.unregister(port)
            isHookRegistered.value = hookRegistry.isRegistered(port)
        }
    }

    fun registerCopilot() {
        viewModelScope.launch(writeDispatcher) {
            val port = stateManager.state.value.settings.serverPort
            copilotBridge.register(port)
            isCopilotRegistered.value = copilotBridge.isRegistered(port)
        }
    }

    fun unregisterCopilot() {
        viewModelScope.launch(writeDispatcher) {
            val port = stateManager.state.value.settings.serverPort
            copilotBridge.unregister(port)
            isCopilotRegistered.value = copilotBridge.isRegistered(port)
        }
    }

    fun clearHistory() {
        viewModelScope.launch(writeDispatcher) {
            stateManager.clearHistory()
        }
    }
}

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
)
