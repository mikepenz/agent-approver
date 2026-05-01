package com.mikepenz.agentbelay.ui.usage

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import dev.zacsweers.metrox.viewmodel.metroViewModel

/**
 * Resolves [UsageViewModel] from Metro and feeds the stateless [UsageScreen].
 */
@Composable
fun UsageTabHost() {
    val viewModel: UsageViewModel = metroViewModel()
    val state by viewModel.uiState.collectAsState()
    UsageScreen(
        data = state.data,
        selectedSource = state.selectedSource,
        onSelectSource = viewModel::selectSource,
        range = state.range,
        onRangeChange = viewModel::setRange,
        loading = state.loading,
    )
}
