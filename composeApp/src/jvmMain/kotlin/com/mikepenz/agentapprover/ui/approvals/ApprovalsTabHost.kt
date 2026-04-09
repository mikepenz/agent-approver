package com.mikepenz.agentapprover.ui.approvals

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import dev.zacsweers.metrox.viewmodel.metroViewModel

/**
 * Thin glue between [com.mikepenz.agentapprover.ui.App] and [ApprovalsTab]:
 * resolves the [ApprovalsViewModel] from Metro and forwards its state and
 * actions into the (still-stateless) presentational tab.
 */
@Composable
fun ApprovalsTabHost(
    onPopOut: ((title: String, content: String) -> Unit)? = null,
) {
    val viewModel: ApprovalsViewModel = metroViewModel()
    val ui by viewModel.uiState.collectAsState()
    val pendingApprovals by viewModel.pendingApprovals.collectAsState()
    val settings by viewModel.settings.collectAsState()
    val riskResults by viewModel.riskResults.collectAsState()

    ApprovalsTab(
        pendingApprovals = pendingApprovals,
        riskResults = riskResults,
        riskStatuses = ui.riskStatuses,
        riskErrors = ui.riskErrors,
        settings = settings,
        onApprove = viewModel::onApprove,
        onAlwaysAllow = viewModel::onAlwaysAllow,
        onDeny = viewModel::onDeny,
        onApproveWithInput = viewModel::onApproveWithInput,
        onDismiss = viewModel::onDismiss,
        autoDenyRequests = ui.autoDenyRequests,
        onCancelAutoDeny = viewModel::onCancelAutoDeny,
        onUserInteraction = viewModel::onUserInteraction,
        onPopOut = onPopOut,
        onSettingsChange = viewModel::onSettingsChange,
    )
}
