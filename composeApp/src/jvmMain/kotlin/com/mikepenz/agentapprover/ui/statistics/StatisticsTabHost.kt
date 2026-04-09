package com.mikepenz.agentapprover.ui.statistics

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import dev.zacsweers.metrox.viewmodel.metroViewModel

/**
 * Resolves [StatisticsViewModel] from Metro and forwards its UI state into the
 * stateless [StatisticsTab]. Mirrors `HistoryTabHost` for consistency.
 */
@Composable
fun StatisticsTabHost() {
    val viewModel: StatisticsViewModel = metroViewModel()
    val state by viewModel.uiState.collectAsState()
    StatisticsTab(state = state, onWindowChange = viewModel::setWindow)
}
