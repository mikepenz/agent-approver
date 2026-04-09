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
import kotlinx.coroutines.ExperimentalCoroutinesApi
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
@OptIn(ExperimentalCoroutinesApi::class)
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

    /**
     * Single-slot dispatcher derived from [ioDispatcher]. All write actions
     * (settings updates, hook register/unregister, copilot install/uninstall,
     * clearHistory) launch on this so concurrent calls drain in FIFO order
     * — preserving the user's intent when they click two settings rapidly.
     * Reads (the cache populator and the init poll) use the unrestricted
     * [ioDispatcher] so they can run in parallel.
     */
    private val writeDispatcher = ioDispatcher.limitedParallelism(1)

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

    /**
     * Set of project paths whose Copilot hook status is currently being read
     * from disk. Used to dedupe [queryCopilotHookRegistered] so the host
     * composable can call it from inside the cache lookup lambda on every
     * recomposition without spamming the IO dispatcher. Synchronised on
     * itself; reads/writes are infrequent and short.
     */
    private val inFlightCopilotQueries = mutableSetOf<String>()

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

    fun installCopilot() {
        viewModelScope.launch(writeDispatcher) {
            copilotBridge.install()
            isCopilotInstalled.value = copilotBridge.isInstalled()
        }
    }

    fun uninstallCopilot() {
        viewModelScope.launch(writeDispatcher) {
            copilotBridge.uninstall()
            isCopilotInstalled.value = copilotBridge.isInstalled()
        }
    }

    fun registerCopilotHook(projectPath: String) {
        viewModelScope.launch(writeDispatcher) {
            copilotBridge.registerHook(projectPath)
            // Refresh the cache from disk so the UI reflects the new state.
            val now = copilotBridge.isHookRegistered(projectPath)
            _copilotHookRegistrations.update { it + (projectPath to now) }
        }
    }

    fun unregisterCopilotHook(projectPath: String) {
        viewModelScope.launch(writeDispatcher) {
            copilotBridge.unregisterHook(projectPath)
            val now = copilotBridge.isHookRegistered(projectPath)
            _copilotHookRegistrations.update { it + (projectPath to now) }
        }
    }

    /**
     * Trigger an asynchronous refresh of the cached registration status for
     * [projectPath]. The result lands in [copilotHookRegistrations]. Safe to
     * call from inside Compose composition — it just enqueues a coroutine and
     * returns immediately. Calls for the same path while one is already in
     * flight are dropped, so per-recomposition spam doesn't trigger duplicate
     * disk reads.
     */
    fun queryCopilotHookRegistered(projectPath: String) {
        if (projectPath.isBlank()) return
        synchronized(inFlightCopilotQueries) {
            if (!inFlightCopilotQueries.add(projectPath)) return
        }
        viewModelScope.launch(ioDispatcher) {
            try {
                val registered = copilotBridge.isHookRegistered(projectPath)
                _copilotHookRegistrations.update { it + (projectPath to registered) }
            } finally {
                synchronized(inFlightCopilotQueries) { inFlightCopilotQueries.remove(projectPath) }
            }
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
