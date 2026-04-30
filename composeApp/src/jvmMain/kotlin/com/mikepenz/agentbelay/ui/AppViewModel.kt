package com.mikepenz.agentbelay.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mikepenz.agentbelay.VERSION
import com.mikepenz.agentbelay.di.AppEnvironment
import com.mikepenz.agentbelay.di.AppScope
import com.mikepenz.agentbelay.hook.CopilotBridge
import com.mikepenz.agentbelay.hook.HookRegistry
import com.mikepenz.agentbelay.hook.OpenCodeBridge
import com.mikepenz.agentbelay.hook.RegistrationEvents
import com.mikepenz.agentbelay.state.AppNotice
import com.mikepenz.agentbelay.state.AppStateManager
import com.mikepenz.agentbelay.update.UpdateManager
import com.mikepenz.agentbelay.update.UpdateUiState
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
    private val openCodeBridge: OpenCodeBridge,
    private val registrationEvents: RegistrationEvents,
    private val updateManager: UpdateManager,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
) : ViewModel() {

    /**
     * Surfaces the auto-update result to the in-app banner. Mirrors
     * [UpdateManager.state] so the shell can react without depending on the
     * Settings ViewModel (the banner is rendered above the tab content,
     * which is shared infrastructure).
     */
    val updateState: StateFlow<UpdateUiState> = updateManager.state

    fun downloadUpdate() = updateManager.downloadAvailable()
    fun installUpdate() = updateManager.installCurrent()
    fun dismissUpdate() = updateManager.reset()

    private val _selectedTab = MutableStateFlow(0)
    val selectedTab: StateFlow<Int> = _selectedTab.asStateFlow()

    /** Re-polled whenever the configured `serverPort` changes. */
    private val claudeRegistered = MutableStateFlow(false)
    private val copilotRegistered = MutableStateFlow(false)
    private val openCodeRegistered = MutableStateFlow(false)

    val tabState: StateFlow<TabState> = combine(
        stateManager.state,
        claudeRegistered,
        copilotRegistered,
        openCodeRegistered,
    ) { state, claude, copilot, openCode ->
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
                AgentRegistration("OpenCode", openCode),
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
                    val (c, cp, oc) = withContext(ioDispatcher) {
                        Triple(
                            hookRegistry.isRegistered(port),
                            copilotBridge.isRegistered(port),
                            openCodeBridge.isRegistered(port),
                        )
                    }
                    claudeRegistered.value = c
                    copilotRegistered.value = cp
                    openCodeRegistered.value = oc
                }
        }
        // Re-poll when SettingsViewModel registers or unregisters a hook so
        // the sidebar indicators stay in sync without waiting for a port change.
        viewModelScope.launch {
            registrationEvents.changes.collect {
                val port = stateManager.state.value.settings.serverPort
                val (c, cp, oc) = withContext(ioDispatcher) {
                    Triple(
                        hookRegistry.isRegistered(port),
                        copilotBridge.isRegistered(port),
                        openCodeBridge.isRegistered(port),
                    )
                }
                claudeRegistered.value = c
                copilotRegistered.value = cp
                openCodeRegistered.value = oc
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
