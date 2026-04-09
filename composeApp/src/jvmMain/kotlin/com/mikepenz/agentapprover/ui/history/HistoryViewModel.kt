package com.mikepenz.agentapprover.ui.history

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mikepenz.agentapprover.di.AppEnvironment
import com.mikepenz.agentapprover.di.AppScope
import com.mikepenz.agentapprover.model.ApprovalResult
import com.mikepenz.agentapprover.state.AppStateManager
import dev.zacsweers.metro.ContributesIntoMap
import dev.zacsweers.metro.Inject
import dev.zacsweers.metrox.viewmodel.ViewModelKey
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.datetime.Clock
import java.util.UUID

/**
 * ViewModel for the History tab. Exposes the persisted history list and the
 * dev-mode-only "replay" action that re-injects a previous request into the
 * pending queue with a fresh id and timestamp.
 */
@Inject
@ViewModelKey
@ContributesIntoMap(AppScope::class)
class HistoryViewModel(
    private val stateManager: AppStateManager,
    env: AppEnvironment,
) : ViewModel() {

    val devMode: Boolean = env.devMode

    val history: StateFlow<List<ApprovalResult>> = stateManager.state
        .map { it.history }
        .stateIn(viewModelScope, SharingStarted.Eagerly, stateManager.state.value.history)

    /**
     * Re-inject a historical request into the pending queue with a new id and
     * timestamp so it appears as a fresh approval. Only used in dev mode.
     */
    fun replay(result: ApprovalResult) {
        val cloned = result.request.copy(
            id = UUID.randomUUID().toString(),
            timestamp = Clock.System.now(),
        )
        stateManager.addPending(cloned)
    }
}
