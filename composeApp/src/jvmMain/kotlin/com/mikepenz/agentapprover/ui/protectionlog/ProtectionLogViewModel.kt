package com.mikepenz.agentapprover.ui.protectionlog

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mikepenz.agentapprover.di.AppScope
import com.mikepenz.agentapprover.protection.ProtectionEngine
import com.mikepenz.agentapprover.state.AppStateManager
import com.mikepenz.agentapprover.state.PreToolUseEvent
import dev.zacsweers.metro.ContributesIntoMap
import dev.zacsweers.metro.Inject
import dev.zacsweers.metrox.viewmodel.ViewModelKey
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

/**
 * ViewModel for the dev-mode-only Protection Log tab.
 *
 * Exposes the live `preToolUseLog` (events captured by the protection engine
 * before any tool action is executed) and a clear action. Holds a reference
 * to [ProtectionEngine] so the tab composable can render module/rule details
 * for each hit.
 */
@Inject
@ViewModelKey
@ContributesIntoMap(AppScope::class)
class ProtectionLogViewModel(
    private val stateManager: AppStateManager,
    val protectionEngine: ProtectionEngine,
) : ViewModel() {

    val events: StateFlow<List<PreToolUseEvent>> = stateManager.state
        .map { it.preToolUseLog }
        .stateIn(viewModelScope, SharingStarted.Eagerly, stateManager.state.value.preToolUseLog)

    fun clear() {
        stateManager.clearPreToolUseLog()
    }
}
