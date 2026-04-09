package com.mikepenz.agentapprover.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mikepenz.agentapprover.di.AppScope
import com.mikepenz.agentapprover.hook.CopilotBridge
import com.mikepenz.agentapprover.hook.HookRegistrar
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
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChangedBy
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * ViewModel backing the Settings tab and its four sub-tabs.
 *
 * Combines [AppStateManager.state] with two pieces of UI-only state — whether
 * the Claude Code hook is currently registered, and whether the Copilot bridge
 * script is installed — into a single [SettingsUiState]. The hook-registered
 * flag is re-polled whenever `serverPort` changes; the Copilot install flag is
 * re-checked after install/uninstall actions.
 */
@Inject
@ViewModelKey
@ContributesIntoMap(AppScope::class)
class SettingsViewModel(
    private val stateManager: AppStateManager,
    private val copilotBridge: CopilotBridge,
    private val copilotStateHolder: CopilotStateHolder,
    protectionEngine: ProtectionEngine,
) : ViewModel() {

    // HookRegistrar is a Kotlin object — referenced directly rather than
    // injected so Metro doesn't need to know about it. Made overridable as a
    // private property so tests could swap it via reflection if needed.
    private val hookRegistrar = HookRegistrar

    val protectionModules: List<ProtectionModule> = protectionEngine.modules

    private val isHookRegistered = MutableStateFlow(
        hookRegistrar.isRegistered(stateManager.state.value.settings.serverPort),
    )
    private val isCopilotInstalled = MutableStateFlow(copilotBridge.isInstalled())

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
            isHookRegistered = isHookRegistered.value,
            isCopilotInstalled = isCopilotInstalled.value,
            copilotModels = copilotStateHolder.models.value,
            copilotInitState = copilotStateHolder.initState.value,
        ),
    )

    init {
        // Re-poll hook registration when the configured port changes — covers the
        // case where the user edits the port in settings while the hook is also
        // wired up so the UI reflects the new state.
        viewModelScope.launch {
            stateManager.state
                .map { it.settings.serverPort }
                .distinctUntilChangedBy { it }
                .collect { port ->
                    isHookRegistered.value = hookRegistrar.isRegistered(port)
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
        val port = stateManager.state.value.settings.serverPort
        hookRegistrar.register(port)
        isHookRegistered.value = hookRegistrar.isRegistered(port)
    }

    fun unregisterHook() {
        val port = stateManager.state.value.settings.serverPort
        hookRegistrar.unregister(port)
        isHookRegistered.value = hookRegistrar.isRegistered(port)
    }

    fun installCopilot() {
        copilotBridge.install()
        isCopilotInstalled.value = copilotBridge.isInstalled()
    }

    fun uninstallCopilot() {
        copilotBridge.uninstall()
        isCopilotInstalled.value = copilotBridge.isInstalled()
    }

    fun registerCopilotHook(projectPath: String) {
        copilotBridge.registerHook(projectPath)
    }

    fun unregisterCopilotHook(projectPath: String) {
        copilotBridge.unregisterHook(projectPath)
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
