package com.mikepenz.agentbelay.di

import com.mikepenz.agentbelay.app.ApprovalServerRunner
import com.mikepenz.agentbelay.app.GlobalHotkeyManager
import com.mikepenz.agentbelay.app.RiskAnalyzerLifecycle
import com.mikepenz.agentbelay.app.TrayManager
import com.mikepenz.agentbelay.app.TrayNotificationsManager
import com.mikepenz.agentbelay.capability.CapabilityEngine
import com.mikepenz.agentbelay.protection.ProtectionEngine
import com.mikepenz.agentbelay.risk.ActiveRiskAnalyzerHolder
import com.mikepenz.agentbelay.risk.ClaudeCliRiskAnalyzer
import com.mikepenz.agentbelay.risk.CopilotStateHolder
import com.mikepenz.agentbelay.risk.OllamaStateHolder
import com.mikepenz.agentbelay.state.AppStateManager
import com.mikepenz.agentbelay.storage.DatabaseStorage
import com.mikepenz.agentbelay.storage.SettingsStorage
import com.mikepenz.agentbelay.update.UpdateManager
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
    val trayNotificationsManager: TrayNotificationsManager
    val capabilityEngine: CapabilityEngine
    val riskAnalyzerLifecycle: RiskAnalyzerLifecycle
    val globalHotkeyManager: GlobalHotkeyManager
    val approvalServerRunner: ApprovalServerRunner
    val updateManager: UpdateManager

    @DependencyGraph.Factory
    fun interface Factory {
        fun create(@Provides environment: AppEnvironment): AppGraph
    }
}
