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
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
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

    /**
     * Cached map of project path → "is the Copilot hook registered for this
     * project". Populated lazily by [queryCopilotHookRegistered] so the UI can
     * call a synchronous lookup without doing disk I/O during composition.
     * The cache is invalidated and refreshed by [registerCopilotHook] /
     * [unregisterCopilotHook] so the UI reflects the new state immediately.
     */
    private val _copilotHookRegistrations = MutableStateFlow<Map<String, Boolean>>(emptyMap())
    val copilotHookRegistrations: StateFlow<Map<String, Boolean>> = _copilotHookRegistrations.asStateFlow()

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
        // Initial poll on IO for the Copilot script-dir check. Without this
        // the VM blocks the main thread at startup. The hook-registered check
        // is owned by the port-flow collector below — its first emission of
        // the StateFlow's current value covers the startup case, so checking
        // here would duplicate the disk read.
        viewModelScope.launch(ioDispatcher) {
            isCopilotInstalled.value = copilotBridge.isInstalled()
        }

        // Poll hook registration whenever the configured port changes. The
        // first emission of `serverPort` covers app startup; subsequent
        // emissions cover the case where the user edits the port. Always run
        // on [ioDispatcher] because the registry parses ~/.claude/settings.json.
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
        // AppStateManager.updateSettings persists to disk synchronously, so
        // dispatch onto IO to avoid blocking the UI thread on every change.
        viewModelScope.launch(ioDispatcher) {
            stateManager.updateSettings(settings)
        }
    }

    fun updateProtectionSettings(protectionSettings: ProtectionSettings) {
        viewModelScope.launch(ioDispatcher) {
            val current = stateManager.state.value.settings
            stateManager.updateSettings(current.copy(protectionSettings = protectionSettings))
        }
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
        viewModelScope.launch(ioDispatcher) {
            copilotBridge.registerHook(projectPath)
            // Refresh the cache from disk so the UI reflects the new state.
            val now = copilotBridge.isHookRegistered(projectPath)
            _copilotHookRegistrations.update { it + (projectPath to now) }
        }
    }

    fun unregisterCopilotHook(projectPath: String) {
        viewModelScope.launch(ioDispatcher) {
            copilotBridge.unregisterHook(projectPath)
            val now = copilotBridge.isHookRegistered(projectPath)
            _copilotHookRegistrations.update { it + (projectPath to now) }
        }
    }

    /**
     * Trigger an asynchronous refresh of the cached registration status for
     * [projectPath]. The result lands in [copilotHookRegistrations]. Safe to
     * call from inside Compose composition — it just enqueues a coroutine and
     * returns. Subsequent calls for the same path while one is in flight are
     * idempotent (the result simply overwrites the cache).
     */
    fun queryCopilotHookRegistered(projectPath: String) {
        if (projectPath.isBlank()) return
        viewModelScope.launch(ioDispatcher) {
            val registered = copilotBridge.isHookRegistered(projectPath)
            _copilotHookRegistrations.update { it + (projectPath to registered) }
        }
    }

    fun clearHistory() {
        // AppStateManager.clearHistory does a synchronous DB delete; dispatch
        // to IO so the UI doesn't stall.
        viewModelScope.launch(ioDispatcher) {
            stateManager.clearHistory()
        }
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
