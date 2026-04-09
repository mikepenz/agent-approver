package com.mikepenz.agentapprover.app

import co.touchlab.kermit.Logger
import com.mikepenz.agentapprover.di.AppEnvironment
import com.mikepenz.agentapprover.di.AppScope
import com.mikepenz.agentapprover.model.AppSettings
import com.mikepenz.agentapprover.model.RiskAnalysisBackend
import com.mikepenz.agentapprover.risk.ActiveRiskAnalyzerHolder
import com.mikepenz.agentapprover.risk.ClaudeCliRiskAnalyzer
import com.mikepenz.agentapprover.risk.CopilotInitState
import com.mikepenz.agentapprover.risk.CopilotRiskAnalyzer
import com.mikepenz.agentapprover.risk.CopilotStateHolder
import com.mikepenz.agentapprover.risk.RiskMessageBuilder
import com.mikepenz.agentapprover.state.AppStateManager
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

/**
 * App-scoped manager that keeps the active risk analyzer in sync with the
 * user's settings. Used to live as a `LaunchedEffect` inside `Main.kt`'s
 * `application { }` block — extracted in Phase 4 so the lifecycle (a) starts
 * with the application rather than the first window mount, (b) is testable
 * without standing up Compose, and (c) can manage Copilot's process lifecycle
 * without leaking it into the UI tree.
 *
 * The class owns the [CopilotRiskAnalyzer] instance directly (not via DI)
 * because it needs lifecycle control: lazy creation when the Copilot backend
 * is first selected, shutdown when the user switches back to Claude or when
 * the app exits. [shutdown] should be called from the application onDispose.
 */
@SingleIn(AppScope::class)
@Inject
class RiskAnalyzerLifecycle(
    private val stateManager: AppStateManager,
    private val claudeAnalyzer: ClaudeCliRiskAnalyzer,
    private val activeAnalyzerHolder: ActiveRiskAnalyzerHolder,
    private val copilotStateHolder: CopilotStateHolder,
    private val environment: AppEnvironment,
) {
    private val log = Logger.withTag("RiskAnalyzerLifecycle")

    private var copilotAnalyzer: CopilotRiskAnalyzer? = null
    private var collectorJob: Job? = null

    /**
     * Start observing settings changes. Call once at app startup. The collector
     * runs on [AppEnvironment.appScope] so it survives the main window being
     * hidden or closed — matching the headless tray behaviour established in
     * Phase 2. Idempotent: a second [start] call is a no-op.
     */
    fun start() {
        if (collectorJob != null) return

        // Seed with the current Claude analyzer so consumers (notably
        // ApprovalsViewModel) have something to read before the first emission.
        activeAnalyzerHolder.set(claudeAnalyzer)

        collectorJob = environment.appScope.launch {
            stateManager.state
                .map { it.settings }
                .distinctUntilChanged { old, new ->
                    old.riskAnalysisBackend == new.riskAnalysisBackend &&
                        old.riskAnalysisModel == new.riskAnalysisModel &&
                        old.riskAnalysisCopilotModel == new.riskAnalysisCopilotModel &&
                        old.riskAnalysisCustomPrompt == new.riskAnalysisCustomPrompt &&
                        old.riskAnalysisCopilotCliPath == new.riskAnalysisCopilotCliPath
                }
                .collect { settings -> applySettings(settings) }
        }
    }

    private suspend fun applySettings(settings: AppSettings) {
        val effectivePrompt = settings.riskAnalysisCustomPrompt.ifBlank {
            RiskMessageBuilder.DEFAULT_SYSTEM_PROMPT
        }

        // Always keep the Claude analyzer up-to-date so a backend switch is
        // free of latency.
        claudeAnalyzer.model = settings.riskAnalysisModel
        claudeAnalyzer.systemPrompt = effectivePrompt

        when (settings.riskAnalysisBackend) {
            RiskAnalysisBackend.COPILOT -> activateCopilot(settings, effectivePrompt)
            RiskAnalysisBackend.CLAUDE -> activateClaude()
        }
    }

    private suspend fun activateCopilot(settings: AppSettings, effectivePrompt: String) {
        var analyzer = copilotAnalyzer
        if (analyzer == null) {
            copilotStateHolder.setInitState(CopilotInitState.LOADING)
            analyzer = CopilotRiskAnalyzer(
                model = settings.riskAnalysisCopilotModel,
                customSystemPrompt = settings.riskAnalysisCustomPrompt,
            )
            analyzer.cliPath = settings.riskAnalysisCopilotCliPath
            try {
                analyzer.start()
                copilotAnalyzer = analyzer
                analyzer.listModels().onSuccess { models ->
                    copilotStateHolder.setModels(models)
                }
                copilotStateHolder.setInitState(CopilotInitState.READY)
            } catch (e: Exception) {
                log.e(e) { "Failed to start Copilot client" }
                copilotStateHolder.setInitState(CopilotInitState.ERROR)
                activeAnalyzerHolder.set(claudeAnalyzer)
                return
            }
        }
        analyzer.model = settings.riskAnalysisCopilotModel
        analyzer.systemPrompt = effectivePrompt
        analyzer.cliPath = settings.riskAnalysisCopilotCliPath
        activeAnalyzerHolder.set(analyzer)
    }

    private fun activateClaude() {
        copilotAnalyzer?.shutdown()
        copilotAnalyzer = null
        copilotStateHolder.setInitState(CopilotInitState.IDLE)
        activeAnalyzerHolder.set(claudeAnalyzer)
    }

    /**
     * Cancel the settings observer and tear down the Copilot client (if
     * running) so its child process exits cleanly. Idempotent.
     */
    fun shutdown() {
        collectorJob?.cancel()
        collectorJob = null
        copilotAnalyzer?.shutdown()
        copilotAnalyzer = null
    }
}
