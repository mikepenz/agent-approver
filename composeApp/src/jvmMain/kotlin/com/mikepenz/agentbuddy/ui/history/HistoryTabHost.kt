package com.mikepenz.agentbuddy.ui.history

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import dev.zacsweers.metrox.viewmodel.metroViewModel

/**
 * Resolves the [HistoryViewModel] from Metro and forwards its state into
 * the stateless [HistoryTab]. Replay (dev mode only) clones the request and
 * jumps the user back to the Approvals tab.
 */
@Composable
fun HistoryTabHost(onJumpToApprovals: () -> Unit) {
    val viewModel: HistoryViewModel = metroViewModel()
    val history by viewModel.history.collectAsState()

    HistoryTab(
        history = history,
        onReplay = if (viewModel.devMode) { result ->
            viewModel.replay(result)
            onJumpToApprovals()
        } else null,
    )
}
