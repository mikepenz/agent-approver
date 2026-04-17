package com.mikepenz.agentbuddy.ui.protectionlog

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import dev.zacsweers.metrox.viewmodel.metroViewModel

/**
 * Resolves the [ProtectionLogViewModel] from Metro and forwards its state into
 * the stateless [ProtectionLogTab]. Dev-mode-only.
 */
@Composable
fun ProtectionLogTabHost() {
    val viewModel: ProtectionLogViewModel = metroViewModel()
    val events by viewModel.events.collectAsState()

    ProtectionLogTab(
        events = events,
        protectionEngine = viewModel.protectionEngine,
        onClear = viewModel::clear,
    )
}
