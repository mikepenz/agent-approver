package com.mikepenz.agentbelay.ui.statistics

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import dev.zacsweers.metrox.viewmodel.metroViewModel

/**
 * Resolves [StatisticsViewModel] from Metro and feeds the new [StatisticsScreen].
 * The stateless screen accepts a pre-built [StatsScreenData]; we map from the
 * raw [StatsUiState] here.
 */
@Composable
fun StatisticsTabHost() {
    val viewModel: StatisticsViewModel = metroViewModel()
    val state by viewModel.uiState.collectAsState()
    val data = remember(state) {
        state.summary.toScreenData(state.window, state.decidedInWindow, state.previousSummary)
    }
    StatisticsScreen(
        data = data,
        range = state.window.toStatsRange(),
        onRangeChange = { viewModel.setWindow(it.toTimeWindow()) },
    )
}

private fun TimeWindow.toStatsRange(): StatsRange = when (this) {
    TimeWindow.Last7Days -> StatsRange.Last7d
    TimeWindow.Last30Days -> StatsRange.Last30d
    TimeWindow.AllTime -> StatsRange.AllTime
}

private fun StatsRange.toTimeWindow(): TimeWindow = when (this) {
    StatsRange.Last7d -> TimeWindow.Last7Days
    StatsRange.Last30d -> TimeWindow.Last30Days
    StatsRange.AllTime -> TimeWindow.AllTime
}
