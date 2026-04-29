package com.mikepenz.agentbelay.ui.history

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mikepenz.agentbelay.di.AppEnvironment
import com.mikepenz.agentbelay.di.AppScope
import com.mikepenz.agentbelay.model.ApprovalResult
import com.mikepenz.agentbelay.state.AppStateManager
import dev.zacsweers.metro.ContributesIntoMap
import dev.zacsweers.metro.Inject
import dev.zacsweers.metrox.viewmodel.ViewModelKey
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.datetime.Clock
import java.util.UUID

/**
 * ViewModel for the History tab. Owns the persisted history list, the filter
 * state (scope / source / query) and exposes a single ready-to-render
 * [HistoryUiState]. Projection from [ApprovalResult] to [HistoryEntry] and
 * filtering happen on [viewModelScope] so the composable never re-derives on
 * recomposition — required to keep the screen responsive when history grows
 * into the thousands of entries.
 */
@Inject
@ViewModelKey
@ContributesIntoMap(AppScope::class)
class HistoryViewModel(
    private val stateManager: AppStateManager,
    env: AppEnvironment,
) : ViewModel() {

    val devMode: Boolean = env.devMode

    /**
     * Raw persisted history list. Retained for callers that operate on the
     * underlying `ApprovalResult` (e.g. `replay`) and for existing tests.
     */
    val history: StateFlow<List<ApprovalResult>> = stateManager.state
        .map { it.history }
        .stateIn(viewModelScope, SharingStarted.Eagerly, stateManager.state.value.history)

    private val _scope = MutableStateFlow(HistoryScope.All)
    private val _sourceFilter = MutableStateFlow(HistorySourceFilter.All)
    private val _query = MutableStateFlow("")

    fun setScope(scope: HistoryScope) { _scope.value = scope }
    fun setSourceFilter(filter: HistorySourceFilter) { _sourceFilter.value = filter }
    fun setQuery(query: String) { _query.value = query }

    /**
     * Cached projection. Re-maps only when the source history list itself
     * changes — not on filter/query keystrokes.
     */
    private val projected: StateFlow<List<HistoryEntry>> = history
        .map { list ->
            val now = Clock.System.now()
            list.map { it.toHistoryEntry(now) }
        }
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    val uiState: StateFlow<HistoryUiState> = combine(
        projected,
        _scope,
        _sourceFilter,
        _query,
    ) { entries, scope, sourceFilter, query ->
        val filtered = entries.filter { e ->
            val scopeMatch = when (scope) {
                HistoryScope.All -> true
                HistoryScope.Approvals -> e.status in approvalStatuses
                HistoryScope.Protections -> e.status in protectionStatuses
            }
            scopeMatch && e.matchesSource(sourceFilter) && e.matchesQuery(query)
        }
        val counts = HistoryCounts(
            all = entries.size,
            approvals = entries.count { it.status in approvalStatuses },
            protections = entries.count { it.status in protectionStatuses },
        )
        HistoryUiState(
            entries = filtered,
            total = entries.size,
            counts = counts,
            scope = scope,
            sourceFilter = sourceFilter,
            query = query,
        )
    }.stateIn(viewModelScope, SharingStarted.Eagerly, HistoryUiState.Empty)

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

    /** Looks up a history row by request id and replays it. Returns whether the id was found. */
    fun replayById(id: String): Boolean {
        val result = stateManager.state.value.history.firstOrNull { it.request.id == id } ?: return false
        replay(result)
        return true
    }
}
