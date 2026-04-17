package com.mikepenz.agentbuddy.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mikepenz.agentbuddy.di.AppEnvironment
import com.mikepenz.agentbuddy.di.AppScope
import com.mikepenz.agentbuddy.state.AppStateManager
import dev.zacsweers.metro.ContributesIntoMap
import dev.zacsweers.metro.Inject
import dev.zacsweers.metrox.viewmodel.ViewModelKey
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

/**
 * Slim ViewModel for [App] itself: owns the selected-tab state and exposes a
 * derived [TabState] (badge counts, away-mode flag, dev-mode flag) so the
 * `TabRow` doesn't need to read [AppStateManager] directly.
 */
@Inject
@ViewModelKey
@ContributesIntoMap(AppScope::class)
class AppViewModel(
    stateManager: AppStateManager,
    env: AppEnvironment,
) : ViewModel() {

    private val _selectedTab = MutableStateFlow(0)
    val selectedTab: StateFlow<Int> = _selectedTab.asStateFlow()

    val tabState: StateFlow<TabState> = stateManager.state
        .map { state ->
            TabState(
                pendingCount = state.pendingApprovals.size,
                protectionLogCount = state.preToolUseLog.size,
                awayMode = state.settings.awayMode,
                devMode = env.devMode,
            )
        }
        .stateIn(
            viewModelScope,
            SharingStarted.Eagerly,
            TabState(
                pendingCount = stateManager.state.value.pendingApprovals.size,
                protectionLogCount = stateManager.state.value.preToolUseLog.size,
                awayMode = stateManager.state.value.settings.awayMode,
                devMode = env.devMode,
            ),
        )

    fun selectTab(index: Int) {
        _selectedTab.value = index
    }
}

/**
 * Snapshot of all the bits the top-level [App] composable cares about.
 * Computed by [AppViewModel] off [AppStateManager.state].
 */
data class TabState(
    val pendingCount: Int,
    val protectionLogCount: Int,
    val awayMode: Boolean,
    val devMode: Boolean,
)
