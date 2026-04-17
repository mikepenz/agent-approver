package com.mikepenz.agentbuddy.di

import com.mikepenz.agentbuddy.app.ApprovalServerRunner
import com.mikepenz.agentbuddy.app.RiskAnalyzerLifecycle
import com.mikepenz.agentbuddy.app.TrayManager
import com.mikepenz.agentbuddy.protection.ProtectionEngine
import com.mikepenz.agentbuddy.risk.ActiveRiskAnalyzerHolder
import com.mikepenz.agentbuddy.risk.ClaudeCliRiskAnalyzer
import com.mikepenz.agentbuddy.risk.CopilotStateHolder
import com.mikepenz.agentbuddy.risk.OllamaStateHolder
import com.mikepenz.agentbuddy.state.AppStateManager
import com.mikepenz.agentbuddy.storage.DatabaseStorage
import com.mikepenz.agentbuddy.storage.SettingsStorage
import dev.zacsweers.metro.DependencyGraph
import dev.zacsweers.metro.Provides
import dev.zacsweers.metrox.viewmodel.ViewModelGraph

/**
 * The application-wide Metro dependency graph.
 *
 * Holds all app-scoped singletons (storage, state, protection engine, hook
 * registrar, risk analyzer). Bindings are contributed by [AppProviders] via
 * `@ContributesTo(AppScope::class)`.
 *
 * Construct with [createGraphFactory] in `Main.kt`:
 * ```
 * val graph = createGraphFactory<AppGraph.Factory>().create(environment)
 * ```
 */
@DependencyGraph(AppScope::class)
interface AppGraph : ViewModelGraph {
    val environment: AppEnvironment
    val settingsStorage: SettingsStorage
    val databaseStorage: DatabaseStorage
    val stateManager: AppStateManager
    val protectionEngine: ProtectionEngine
    val claudeAnalyzer: ClaudeCliRiskAnalyzer
    val activeRiskAnalyzerHolder: ActiveRiskAnalyzerHolder
    val copilotStateHolder: CopilotStateHolder
    val ollamaStateHolder: OllamaStateHolder
    val trayManager: TrayManager
    val riskAnalyzerLifecycle: RiskAnalyzerLifecycle
    val approvalServerRunner: ApprovalServerRunner

    @DependencyGraph.Factory
    fun interface Factory {
        fun create(@Provides environment: AppEnvironment): AppGraph
    }
}
