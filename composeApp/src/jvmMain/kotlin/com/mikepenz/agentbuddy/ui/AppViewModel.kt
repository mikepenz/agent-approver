package com.mikepenz.agentbuddy.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mikepenz.agentbuddy.VERSION
import com.mikepenz.agentbuddy.di.AppEnvironment
import com.mikepenz.agentbuddy.di.AppScope
import com.mikepenz.agentbuddy.hook.CopilotBridge
import com.mikepenz.agentbuddy.hook.HookRegistry
import com.mikepenz.agentbuddy.hook.RegistrationEvents
import com.mikepenz.agentbuddy.state.AppNotice
import com.mikepenz.agentbuddy.state.AppStateManager
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
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Slim ViewModel for [App] itself: owns the selected-tab state and exposes a
 * derived [TabState] (badge counts, away-mode flag, dev-mode flag, sidebar
 * footer info) so the shell doesn't need to read [AppStateManager] or the
 * hook registries directly.
 */
@Inject
@ViewModelKey
@ContributesIntoMap(AppScope::class)
class AppViewModel(
    private val stateManager: AppStateManager,
    env: AppEnvironment,
    private val hookRegistry: HookRegistry,
    private val copilotBridge: CopilotBridge,
    private val registrationEvents: RegistrationEvents,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
) : ViewModel() {

    private val _selectedTab = MutableStateFlow(0)
    val selectedTab: StateFlow<Int> = _selectedTab.asStateFlow()

    /** Re-polled whenever the configured `serverPort` changes. */
    private val claudeRegistered = MutableStateFlow(false)
    private val copilotRegistered = MutableStateFlow(false)

    val tabState: StateFlow<TabState> = combine(
        stateManager.state,
        claudeRegistered,
        copilotRegistered,
    ) { state, claude, copilot ->
        TabState(
            pendingCount = state.pendingApprovals.size,
            protectionLogCount = state.preToolUseLog.size,
            awayMode = state.settings.awayMode,
            devMode = env.devMode,
            appVersion = VERSION,
            serverPort = state.settings.serverPort,
            agentRegistrations = listOf(
                AgentRegistration("Claude Code", claude),
                AgentRegistration("GitHub Copilot", copilot),
            ),
        )
    }.stateIn(
        viewModelScope,
        SharingStarted.Eagerly,
        TabState(
            pendingCount = stateManager.state.value.pendingApprovals.size,
            protectionLogCount = stateManager.state.value.preToolUseLog.size,
            awayMode = stateManager.state.value.settings.awayMode,
            devMode = env.devMode,
            appVersion = VERSION,
            serverPort = stateManager.state.value.settings.serverPort,
            agentRegistrations = emptyList(),
        ),
    )

    init {
        // Re-poll registrations whenever the configured port changes (covers
        // first emission at startup and any later edit). File I/O so we hop
        // off the main dispatcher.
        viewModelScope.launch {
            stateManager.state
                .map { it.settings.serverPort }
                .distinctUntilChanged()
                .collect { port ->
                    val (c, cp) = withContext(ioDispatcher) {
                        hookRegistry.isRegistered(port) to copilotBridge.isRegistered(port)
                    }
                    claudeRegistered.value = c
                    copilotRegistered.value = cp
                }
        }
        // Re-poll when SettingsViewModel registers or unregisters a hook so
        // the sidebar indicators stay in sync without waiting for a port change.
        viewModelScope.launch {
            registrationEvents.changes.collect {
                val port = stateManager.state.value.settings.serverPort
                val (c, cp) = withContext(ioDispatcher) {
                    hookRegistry.isRegistered(port) to copilotBridge.isRegistered(port)
                }
                claudeRegistered.value = c
                copilotRegistered.value = cp
            }
        }
    }

    fun selectTab(index: Int) {
        _selectedTab.value = index
    }

    val notices: StateFlow<List<AppNotice>> = stateManager.state
        .map { it.notices }
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    fun dismissNotice(id: String) = stateManager.dismissNotice(id)
}

/**
 * Snapshot of all the bits the top-level [App] composable and the sidebar
 * footer care about. Computed by [AppViewModel].
 */
data class TabState(
    val pendingCount: Int,
    val protectionLogCount: Int,
    val awayMode: Boolean,
    val devMode: Boolean,
    val appVersion: String,
    val serverPort: Int,
    val agentRegistrations: List<AgentRegistration>,
)

data class AgentRegistration(val name: String, val registered: Boolean)
