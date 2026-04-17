package com.mikepenz.agentbuddy.risk

import com.mikepenz.agentbuddy.di.AppScope
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * App-scoped holder for the currently active [RiskAnalyzer].
 *
 * Bridges `Main.kt`'s Claude/Copilot lifecycle (which still owns analyzer
 * switching during Phase 2) to consumers in the DI graph (notably
 * [com.mikepenz.agentbuddy.ui.approvals.ApprovalsViewModel]). The VM reads
 * `analyzer.value` lazily when a new approval arrives, so backend switches
 * take effect immediately for subsequent analyses.
 *
 * The current analyzer is initialised lazily by `Main.kt` via [set] before any
 * analysis would be requested.
 */
@SingleIn(AppScope::class)
@Inject
class ActiveRiskAnalyzerHolder {
    private val _analyzer = MutableStateFlow<RiskAnalyzer?>(null)
    val analyzer: StateFlow<RiskAnalyzer?> = _analyzer.asStateFlow()

    fun set(analyzer: RiskAnalyzer) {
        _analyzer.value = analyzer
    }
}
