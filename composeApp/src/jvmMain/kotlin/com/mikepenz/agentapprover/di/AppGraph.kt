package com.mikepenz.agentapprover.di

import com.mikepenz.agentapprover.protection.ProtectionEngine
import com.mikepenz.agentapprover.risk.ActiveRiskAnalyzerHolder
import com.mikepenz.agentapprover.risk.ClaudeCliRiskAnalyzer
import com.mikepenz.agentapprover.risk.CopilotStateHolder
import com.mikepenz.agentapprover.state.AppStateManager
import com.mikepenz.agentapprover.storage.DatabaseStorage
import com.mikepenz.agentapprover.storage.SettingsStorage
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

    @DependencyGraph.Factory
    fun interface Factory {
        fun create(@Provides environment: AppEnvironment): AppGraph
    }
}
