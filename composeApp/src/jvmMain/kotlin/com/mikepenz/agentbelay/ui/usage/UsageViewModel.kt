package com.mikepenz.agentbelay.ui.usage

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mikepenz.agentbelay.di.AppScope
import com.mikepenz.agentbelay.model.Source
import com.mikepenz.agentbelay.state.AppStateManager
import com.mikepenz.agentbelay.storage.DatabaseStorage
import com.mikepenz.agentbelay.storage.UsageHarnessTotals
import com.mikepenz.agentbelay.ui.components.sourceAccentColor
import com.mikepenz.agentbelay.ui.components.sourceDisplayName
import com.mikepenz.agentbelay.usage.UsageIngestService
import dev.zacsweers.metro.ContributesIntoMap
import dev.zacsweers.metro.Inject
import dev.zacsweers.metrox.viewmodel.ViewModelKey
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.time.Clock
import kotlin.time.Duration
import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.hours

private fun UsageRange.offset(): Duration? = when (this) {
    UsageRange.Last24h -> 24.hours
    UsageRange.Last7d -> 7.days
    UsageRange.Last30d -> 30.days
    UsageRange.AllTime -> null
}

data class UsageUiState(
    val range: UsageRange = UsageRange.Last7d,
    val data: UsageScreenData = UsageScreenData(emptyList(), 0.0, 0, 0, 0, 0, 0),
    val selectedSource: Source? = null,
    /**
     * True until the ingest service finishes its first pass — drives the
     * "Scanning harness sessions…" state. Even after it goes false, an
     * in-flight refresh can still flip it true momentarily.
     */
    val loading: Boolean = true,
    /**
     * True only while a manual refresh kicked off via [UsageViewModel.refreshNow]
     * is in flight. Drives the spinning state on the refresh button. Distinct
     * from [loading] (which gates the full-screen "scanning…" state) so the
     * page keeps showing existing data while the spinner overlays the icon.
     */
    val refreshing: Boolean = false,
)

@Inject
@ViewModelKey
@ContributesIntoMap(AppScope::class)
class UsageViewModel(
    private val database: DatabaseStorage,
    private val stateManager: AppStateManager,
    private val ingestService: UsageIngestService,
) : ViewModel() {

    private val _uiState = MutableStateFlow(UsageUiState())
    val uiState: StateFlow<UsageUiState> = _uiState.asStateFlow()

    private var refreshJob: Job? = null

    @Volatile private var firstScanComplete: Boolean = false

    init {
        refresh()
        // Re-pull whenever the ingest loop reports a successful pass; that
        // first emission also clears the initial "scanning…" state.
        ingestService.scans.onEach {
            firstScanComplete = true
            refresh()
        }.launchIn(viewModelScope)
    }

    fun setRange(range: UsageRange) {
        if (_uiState.value.range == range) return
        _uiState.value = _uiState.value.copy(range = range)
        refresh()
    }

    fun selectSource(source: Source) {
        _uiState.value = _uiState.value.copy(selectedSource = source)
    }

    /** Manually triggers a scan pass; wired to the Refresh button in the header. */
    fun refreshNow() {
        if (_uiState.value.refreshing) return
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(refreshing = true)
            try {
                runCatching { ingestService.refreshNow() }
                refresh()
            } finally {
                _uiState.value = _uiState.value.copy(refreshing = false)
            }
        }
    }

    private fun refresh() {
        refreshJob?.cancel()
        refreshJob = viewModelScope.launch {
            // Don't flip loading back to true after the first scan has
            // completed — once we've shown real data we shouldn't fall back
            // to the "scanning…" state on a routine refresh.
            if (!firstScanComplete) {
                _uiState.value = _uiState.value.copy(loading = true)
            }
            try {
                val range = _uiState.value.range
                val now = Clock.System.now()
                val sinceMillis = range.offset()?.let { (now - it).toEpochMilliseconds() }
                val totals = withContext(Dispatchers.IO) {
                    database.queryUsageTotals(sinceMillis = sinceMillis)
                }
                val daily = withContext(Dispatchers.IO) {
                    database.queryUsagePerDay(sinceMillis = sinceMillis)
                }
                val history = stateManager.state.value.history
                val data = mapToScreenData(totals, daily, history, sinceMillis)
                val selected = _uiState.value.selectedSource
                    ?: data.rows.firstOrNull()?.source
                _uiState.value = _uiState.value.copy(data = data, selectedSource = selected)
            } catch (e: Exception) {
                co.touchlab.kermit.Logger.withTag("UsageViewModel")
                    .w(e) { "usage refresh failed: ${e.message}" }
            } finally {
                if (firstScanComplete) {
                    _uiState.value = _uiState.value.copy(loading = false)
                }
            }
        }
    }

    private fun mapToScreenData(
        totals: List<UsageHarnessTotals>,
        daily: Map<Source, List<com.mikepenz.agentbelay.storage.UsageDailyCount>>,
        history: List<com.mikepenz.agentbelay.model.ApprovalResult>,
        sinceMillis: Long?,
    ): UsageScreenData {
        // Latency per source from history (in seconds).
        val latencies: Map<Source, LatencyAgg> = history.asSequence()
            .filter { sinceMillis == null || it.decidedAt.toEpochMilliseconds() >= sinceMillis }
            .groupBy { it.request.source }
            .mapValues { (_, rows) ->
                val samples = rows.map { r ->
                    val seconds = ((r.decidedAt - r.request.timestamp).inWholeMilliseconds.coerceAtLeast(0L)) / 1000.0
                    seconds
                }.sorted()
                LatencyAgg(p50 = percentile(samples, 0.5), p95 = percentile(samples, 0.95))
            }

        // Build a row per harness present in totals.
        val byHarness = totals.associateBy { it.harness }
        // Stable order: respect Source enum declaration order, plus any unknown last.
        val orderedSources = Source.entries.filter { it in byHarness.keys }
        val rows = orderedSources.map { source ->
            val t = byHarness.getValue(source)
            val dailyForSource = daily[source].orEmpty()
            val sparkline = dailyForSource
                .takeLast(12)
                .map { it.requests }
                .ifEmpty { listOf(0) }
            HarnessUsageRow(
                source = source,
                displayName = sourceDisplayName(source),
                model = null,
                accent = sourceAccentColor(source),
                active = dailyForSource.isNotEmpty() &&
                    dailyForSource.last().epochDay >=
                    (System.currentTimeMillis() / 86_400_000L) - 1L,
                sessions = t.sessions,
                requests = t.requests,
                tokensIn = t.inputTokens,
                tokensOut = t.outputTokens,
                tokensCacheRead = t.cacheReadTokens,
                tokensCacheWrite = t.cacheWriteTokens,
                reasoningTokens = t.reasoningTokens,
                cost = t.costUsd,
                medianLatencySeconds = latencies[source]?.p50,
                p95LatencySeconds = latencies[source]?.p95,
                errorRate = 0.0,
                sparkline = sparkline,
            )
        }
        return UsageScreenData(
            rows = rows,
            totalCost = rows.sumOf { it.cost },
            totalTokensIn = rows.sumOf { it.tokensIn },
            totalTokensOut = rows.sumOf { it.tokensOut },
            totalTokensCache = rows.sumOf { it.tokensCacheRead + it.tokensCacheWrite },
            totalRequests = rows.sumOf { it.requests },
            totalSessions = rows.sumOf { it.sessions },
        )
    }

    private data class LatencyAgg(val p50: Double, val p95: Double)

    private fun percentile(sorted: List<Double>, p: Double): Double {
        if (sorted.isEmpty()) return 0.0
        val rank = kotlin.math.ceil(p * sorted.size).toInt().coerceIn(1, sorted.size)
        return sorted[rank - 1]
    }
}
