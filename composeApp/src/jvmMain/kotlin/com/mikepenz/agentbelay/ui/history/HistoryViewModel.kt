package com.mikepenz.agentbelay.ui.history

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mikepenz.agentbelay.di.AppEnvironment
import com.mikepenz.agentbelay.di.AppScope
import com.mikepenz.agentbelay.model.ApprovalResult
import com.mikepenz.agentbelay.model.Source
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
import kotlin.time.Clock
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
    private val _query = MutableStateFlow("")
    private val _sort = MutableStateFlow(HistorySort.Recent)
    private val _harnessFilter = MutableStateFlow<Set<Source>?>(null)

    fun setScope(scope: HistoryScope) { _scope.value = scope }
    fun setQuery(query: String) { _query.value = query }
    fun setSort(sort: HistorySort) { _sort.value = sort }
    fun setHarnessFilter(filter: Set<Source>?) {
        // Empty set behaves like null so the user can't accidentally hide
        // every row by deselecting everything.
        _harnessFilter.value = filter?.takeIf { it.isNotEmpty() }
    }

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
        listOf(projected, _scope, _query, _sort, _harnessFilter),
    ) { array ->
        @Suppress("UNCHECKED_CAST")
        val entries = array[0] as List<HistoryEntry>
        val scope = array[1] as HistoryScope
        val query = array[2] as String
        val sort = array[3] as HistorySort
        @Suppress("UNCHECKED_CAST")
        val harnessFilter = array[4] as Set<Source>?

        val filtered = entries.filter { e ->
            val scopeMatch = when (scope) {
                HistoryScope.All -> true
                HistoryScope.Approvals -> e.status in approvalStatuses
                HistoryScope.Protections -> e.status in protectionStatuses
            }
            scopeMatch &&
                e.matchesHarnessFilter(harnessFilter) &&
                e.matchesQuery(query)
        }
        // `projected` is already most-recent-first (history is loaded
        // ORDER BY decided_at DESC), so Recent is a no-op pass-through.
        val sorted = when (sort) {
            HistorySort.Recent -> filtered
            HistorySort.Tool -> filtered.sortedBy { it.tool.lowercase() }
            HistorySort.Source -> filtered.sortedBy { it.source.ordinal }
        }
        val counts = HistoryCounts(
            all = entries.size,
            approvals = entries.count { it.status in approvalStatuses },
            protections = entries.count { it.status in protectionStatuses },
        )
        HistoryUiState(
            entries = sorted,
            total = entries.size,
            counts = counts,
            scope = scope,
            query = query,
            sort = sort,
            harnessFilter = harnessFilter,
            availableHarnesses = entries.map { it.source }.distinct().sortedBy { it.ordinal },
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
