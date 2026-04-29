package com.mikepenz.agentbelay.ui.statistics

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mikepenz.agentbelay.di.AppScope
import com.mikepenz.agentbelay.model.ApprovalResult
import com.mikepenz.agentbelay.state.AppStateManager
import com.mikepenz.agentbelay.storage.DatabaseStorage
import com.mikepenz.agentbelay.storage.StatsSummary
import dev.zacsweers.metro.ContributesIntoMap
import dev.zacsweers.metro.Inject
import dev.zacsweers.metrox.viewmodel.ViewModelKey
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import kotlin.time.Duration
import kotlin.time.Duration.Companion.days

/**
 * The 7d / 30d / All-time selector for the Statistics tab. The offset is
 * subtracted from `Clock.System.now()` to derive the SQL `since` bound; a
 * `null` offset means "all-time" and skips the date filter entirely.
 */
enum class TimeWindow(val sinceOffset: Duration?) {
    Last7Days(7.days),
    Last30Days(30.days),
    AllTime(null),
}

data class StatsUiState(
    val window: TimeWindow = TimeWindow.Last7Days,
    val summary: StatsSummary = StatsSummary.EMPTY,
    val previousSummary: StatsSummary? = null,
    val decidedInWindow: List<ApprovalResult> = emptyList(),
    val loading: Boolean = false,
)

@Inject
@ViewModelKey
@ContributesIntoMap(AppScope::class)
class StatisticsViewModel(
    private val databaseStorage: DatabaseStorage,
    private val stateManager: AppStateManager,
) : ViewModel() {

    private val _uiState = MutableStateFlow(StatsUiState())
    val uiState: StateFlow<StatsUiState> = _uiState.asStateFlow()

    private var refreshJob: Job? = null

    init {
        refresh()
        // Re-query when the in-memory history changes (a new decision landed).
        // Tracks the latest decided_at + first id rather than `history.size`,
        // because once history reaches `maxHistoryEntries` the size stops growing
        // even though new decisions keep replacing older ones — using `size`
        // would silently freeze auto-refresh after the cap is hit.
        // Debounced so a burst of resolutions doesn't trigger a query each time.
        @OptIn(kotlinx.coroutines.FlowPreview::class)
        stateManager.state
            .map { state -> state.history.firstOrNull()?.let { it.request.id to it.decidedAt } }
            .distinctUntilChanged()
            .debounce(500)
            .onEach { refresh() }
            .launchIn(viewModelScope)
    }

    fun setWindow(window: TimeWindow) {
        if (_uiState.value.window == window) return
        _uiState.value = _uiState.value.copy(window = window)
        refresh()
    }

    private fun refresh() {
        refreshJob?.cancel()
        refreshJob = viewModelScope.launch {
            _uiState.value = _uiState.value.copy(loading = true)
            try {
                val window = _uiState.value.window
                val now = Clock.System.now()
                val since: Instant? = window.sinceOffset?.let { now - it }
                val summary = withContext(Dispatchers.IO) { databaseStorage.queryStats(since) }
                // Query the mirror period immediately before the current window for delta comparison.
                // AllTime has no meaningful prior period → null.
                val previousSummary: StatsSummary? = window.sinceOffset?.let { offset ->
                    val prevUntil = now - offset
                    val prevSince = prevUntil - offset
                    withContext(Dispatchers.IO) { databaseStorage.queryStats(prevSince, prevUntil) }
                }
                val history = stateManager.state.value.history
                val decidedInWindow = if (since == null) history else history.filter { it.decidedAt >= since }
                _uiState.value = _uiState.value.copy(
                    summary = summary,
                    previousSummary = previousSummary,
                    decidedInWindow = decidedInWindow,
                )
            } catch (e: Exception) {
                // Don't let SQLite errors (busy/IO) propagate up and tear down the
                // ViewModel scope. Stale summary stays visible until the next refresh.
                co.touchlab.kermit.Logger.withTag("StatisticsViewModel")
                    .w(e) { "queryStats failed: ${e.message}" }
            } finally {
                _uiState.value = _uiState.value.copy(loading = false)
            }
        }
    }
}
