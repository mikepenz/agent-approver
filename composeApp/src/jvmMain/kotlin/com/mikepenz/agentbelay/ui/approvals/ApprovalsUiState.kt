package com.mikepenz.agentbelay.ui.approvals

/**
 * Immutable per-approval UI state owned by [ApprovalsViewModel] and consumed
 * by [ApprovalsTab]. Replaces the four `mutableStateMapOf` / `mutableStateSetOf`
 * holders previously kept in `App.kt`.
 */
data class ApprovalsUiState(
    val riskStatuses: Map<String, RiskStatus> = emptyMap(),
    val riskErrors: Map<String, String> = emptyMap(),
    val autoDenyRequests: Set<String> = emptySet(),
)
