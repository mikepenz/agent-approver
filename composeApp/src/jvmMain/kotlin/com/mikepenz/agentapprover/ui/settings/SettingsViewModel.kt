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
import com.mikepenz.agentapprover.state.AppStateManager
import dev.zacsweers.metro.ContributesIntoMap
import dev.zacsweers.metro.Inject
import dev.zacsweers.metrox.viewmodel.ViewModelKey
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
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
 * the Claude Code hook is currently registered, and whether the Copilot bridge
 * script is installed — into a single [SettingsUiState]. The hook-registered
 * flag is re-polled whenever `serverPort` changes; the Copilot install flag is
 * re-checked after install/uninstall actions.
 *
 * All [HookRegistry] calls hit the user's `~/.claude/settings.json` so they
 * are dispatched to [ioDispatcher] (defaults to [Dispatchers.IO]) to keep the
 * main thread free at startup and on settings changes.
 */
@Inject
@ViewModelKey
@ContributesIntoMap(AppScope::class)
class SettingsViewModel(
    private val stateManager: AppStateManager,
    private val copilotBridge: CopilotBridge,
    private val copilotStateHolder: CopilotStateHolder,
    protectionEngine: ProtectionEngine,
    private val hookRegistry: HookRegistry,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
) : ViewModel() {

    val protectionModules: List<ProtectionModule> = protectionEngine.modules

    // Initialised to a safe default; the real value is read from disk by the
    // init block on [ioDispatcher] and re-polled whenever the server port
    // changes. The Copilot install flag is similarly populated off the main
    // thread because it shells out to a script directory check.
    private val isHookRegistered = MutableStateFlow(false)
    private val isCopilotInstalled = MutableStateFlow(false)

    val uiState: StateFlow<SettingsUiState> = combine(
        stateManager.state,
        isHookRegistered,
        isCopilotInstalled,
        copilotStateHolder.models,
        copilotStateHolder.initState,
    ) { state, hookRegistered, copilotInstalled, copilotModels, copilotInitState ->
        SettingsUiState(
            settings = state.settings,
            historyCount = state.history.size,
            isHookRegistered = hookRegistered,
            isCopilotInstalled = copilotInstalled,
            copilotModels = copilotModels,
            copilotInitState = copilotInitState,
        )
    }.stateIn(
        viewModelScope,
        SharingStarted.Eagerly,
        SettingsUiState(
            settings = stateManager.state.value.settings,
            historyCount = stateManager.state.value.history.size,
            isHookRegistered = false,
            isCopilotInstalled = false,
            copilotModels = copilotStateHolder.models.value,
            copilotInitState = copilotStateHolder.initState.value,
        ),
    )

    init {
        // Initial poll on IO. Without this the VM blocks the main thread at
        // startup parsing ~/.claude/settings.json and the Copilot script dir.
        viewModelScope.launch(ioDispatcher) {
            isCopilotInstalled.value = copilotBridge.isInstalled()
            isHookRegistered.value = hookRegistry.isRegistered(
                stateManager.state.value.settings.serverPort,
            )
        }

        // Re-poll hook registration when the configured port changes — covers
        // the case where the user edits the port in settings while the hook is
        // also wired up so the UI reflects the new state.
        viewModelScope.launch {
            stateManager.state
                .map { it.settings.serverPort }
                .distinctUntilChanged()
                .collect { port ->
                    val registered = withContext(ioDispatcher) { hookRegistry.isRegistered(port) }
                    isHookRegistered.value = registered
                }
        }
    }

    fun updateSettings(settings: AppSettings) {
        stateManager.updateSettings(settings)
    }

    fun updateProtectionSettings(protectionSettings: ProtectionSettings) {
        val current = stateManager.state.value.settings
        stateManager.updateSettings(current.copy(protectionSettings = protectionSettings))
    }

    fun registerHook() {
        viewModelScope.launch(ioDispatcher) {
            val port = stateManager.state.value.settings.serverPort
            hookRegistry.register(port)
            isHookRegistered.value = hookRegistry.isRegistered(port)
        }
    }

    fun unregisterHook() {
        viewModelScope.launch(ioDispatcher) {
            val port = stateManager.state.value.settings.serverPort
            hookRegistry.unregister(port)
            isHookRegistered.value = hookRegistry.isRegistered(port)
        }
    }

    fun installCopilot() {
        viewModelScope.launch(ioDispatcher) {
            copilotBridge.install()
            isCopilotInstalled.value = copilotBridge.isInstalled()
        }
    }

    fun uninstallCopilot() {
        viewModelScope.launch(ioDispatcher) {
            copilotBridge.uninstall()
            isCopilotInstalled.value = copilotBridge.isInstalled()
        }
    }

    fun registerCopilotHook(projectPath: String) {
        viewModelScope.launch(ioDispatcher) { copilotBridge.registerHook(projectPath) }
    }

    fun unregisterCopilotHook(projectPath: String) {
        viewModelScope.launch(ioDispatcher) { copilotBridge.unregisterHook(projectPath) }
    }

    fun isCopilotHookRegistered(projectPath: String): Boolean =
        copilotBridge.isHookRegistered(projectPath)

    fun clearHistory() {
        stateManager.clearHistory()
    }
}

/**
 * Snapshot of all settings-tab inputs. Computed by [SettingsViewModel] from
 * [AppStateManager.state] plus the hook-registered and Copilot-installed flags.
 */
data class SettingsUiState(
    val settings: AppSettings,
    val historyCount: Int,
    val isHookRegistered: Boolean,
    val isCopilotInstalled: Boolean,
    val copilotModels: List<Pair<String, String>>,
    val copilotInitState: CopilotInitState,
)
