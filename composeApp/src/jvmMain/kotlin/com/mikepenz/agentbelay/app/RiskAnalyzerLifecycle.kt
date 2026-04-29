package com.mikepenz.agentbelay.app

import co.touchlab.kermit.Logger
import com.mikepenz.agentbelay.di.AppEnvironment
import com.mikepenz.agentbelay.di.AppScope
import com.mikepenz.agentbelay.model.AppSettings
import com.mikepenz.agentbelay.model.RiskAnalysisBackend
import com.mikepenz.agentbelay.risk.ActiveRiskAnalyzerHolder
import com.mikepenz.agentbelay.risk.ClaudeCliRiskAnalyzer
import com.mikepenz.agentbelay.risk.CopilotInitState
import com.mikepenz.agentbelay.risk.CopilotRiskAnalyzer
import com.mikepenz.agentbelay.risk.CopilotStateHolder
import com.mikepenz.agentbelay.risk.OllamaInitState
import com.mikepenz.agentbelay.risk.OllamaRiskAnalyzer
import com.mikepenz.agentbelay.risk.OllamaStateHolder
import com.mikepenz.agentbelay.risk.RiskMessageBuilder
import com.mikepenz.agentbelay.state.AppStateManager
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
    private val ollamaStateHolder: OllamaStateHolder,
    private val environment: AppEnvironment,
) {
    private val log = Logger.withTag("RiskAnalyzerLifecycle")

    private var copilotAnalyzer: CopilotRiskAnalyzer? = null
    private var ollamaAnalyzer: OllamaRiskAnalyzer? = null
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
                        old.riskAnalysisCopilotCliPath == new.riskAnalysisCopilotCliPath &&
                        old.riskAnalysisOllamaUrl == new.riskAnalysisOllamaUrl &&
                        old.riskAnalysisOllamaModel == new.riskAnalysisOllamaModel &&
                        old.riskAnalysisOllamaThinking == new.riskAnalysisOllamaThinking &&
                        old.riskAnalysisOllamaKeepAlive == new.riskAnalysisOllamaKeepAlive &&
                        old.riskAnalysisOllamaTimeoutSeconds == new.riskAnalysisOllamaTimeoutSeconds &&
                        old.riskAnalysisOllamaNumCtx == new.riskAnalysisOllamaNumCtx
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
            RiskAnalysisBackend.OLLAMA -> activateOllama(settings, effectivePrompt)
            RiskAnalysisBackend.CLAUDE -> activateClaude()
        }
    }

    private suspend fun activateOllama(settings: AppSettings, effectivePrompt: String) {
        // Tear down Copilot if it was previously active.
        copilotAnalyzer?.shutdown()
        copilotAnalyzer = null
        copilotStateHolder.setInitState(CopilotInitState.IDLE)

        var analyzer = ollamaAnalyzer
        val urlChanged = analyzer != null && analyzer.baseUrl != settings.riskAnalysisOllamaUrl.trimEnd('/')
        if (urlChanged) {
            // Recreate to pick up the new base URL — start() re-probes /api/tags.
            analyzer.shutdown()
            analyzer = null
            ollamaAnalyzer = null
        }
        if (analyzer == null) {
            ollamaStateHolder.setInitState(OllamaInitState.LOADING)
            ollamaStateHolder.setLastError(null)
            analyzer = OllamaRiskAnalyzer(
                baseUrl = settings.riskAnalysisOllamaUrl,
                model = settings.riskAnalysisOllamaModel,
                customSystemPrompt = settings.riskAnalysisCustomPrompt,
            )
            applyOllamaTuning(analyzer, settings, effectivePrompt)
            wireOllamaListeners(analyzer)
            try {
                val models = analyzer.start()
                ollamaAnalyzer = analyzer
                ollamaStateHolder.setModels(models)
                ollamaStateHolder.setVersion(analyzer.version)
                ollamaStateHolder.setLastError(null)
                ollamaStateHolder.setInitState(OllamaInitState.READY)
                reconcileSelectedOllamaModel(models, analyzer)
            } catch (e: Exception) {
                log.e(e) { "Failed to start Ollama analyzer" }
                ollamaStateHolder.setLastError(analyzer.lastError ?: e.message)
                ollamaStateHolder.setInitState(OllamaInitState.ERROR)
                // Keep the analyzer around so the user can retry once the daemon is up.
                ollamaAnalyzer = analyzer
                activeAnalyzerHolder.set(analyzer)
                return
            }
        } else {
            applyOllamaTuning(analyzer, settings, effectivePrompt)
        }
        activeAnalyzerHolder.set(analyzer)
    }

    private fun applyOllamaTuning(analyzer: OllamaRiskAnalyzer, settings: AppSettings, effectivePrompt: String) {
        analyzer.model = settings.riskAnalysisOllamaModel
        analyzer.systemPrompt = effectivePrompt
        analyzer.thinking = settings.riskAnalysisOllamaThinking
        analyzer.keepAlive = settings.riskAnalysisOllamaKeepAlive
        analyzer.timeoutMs = settings.riskAnalysisOllamaTimeoutSeconds.coerceAtLeast(5).toLong() * 1_000L
        analyzer.numCtx = settings.riskAnalysisOllamaNumCtx
    }

    /**
     * If the currently selected Ollama model is missing from [models], replace
     * it with the first available one. Persisting an unknown model leaves the
     * dropdown empty and analysis fails with "model not found" on every call.
     */
    private fun reconcileSelectedOllamaModel(models: List<String>, analyzer: OllamaRiskAnalyzer) {
        if (models.isEmpty()) return
        val current = stateManager.state.value.settings.riskAnalysisOllamaModel
        if (current in models) return
        val replacement = models.first()
        log.w { "Selected model '$current' not in /api/tags; falling back to '$replacement'" }
        analyzer.model = replacement
        stateManager.updateSettings(
            stateManager.state.value.settings.copy(riskAnalysisOllamaModel = replacement),
        )
    }

    private fun wireOllamaListeners(analyzer: OllamaRiskAnalyzer) {
        analyzer.onMetrics = { ollamaStateHolder.setLastMetrics(it) }
        analyzer.onError = { ollamaStateHolder.setLastError(it) }
    }

    /**
     * User-triggered refresh of `/api/tags`. Returns true on success, false on
     * failure. The state holder is updated with the new model list (or error).
     */
    suspend fun refreshOllamaModels(): Boolean {
        val analyzer = ollamaAnalyzer ?: return false
        ollamaStateHolder.setInitState(OllamaInitState.LOADING)
        return analyzer.listModels().fold(
            onSuccess = { models ->
                ollamaStateHolder.setModels(models)
                ollamaStateHolder.setLastError(null)
                ollamaStateHolder.setInitState(OllamaInitState.READY)
                reconcileSelectedOllamaModel(models, analyzer)
                true
            },
            onFailure = {
                ollamaStateHolder.setLastError(analyzer.lastError ?: it.message)
                ollamaStateHolder.setInitState(OllamaInitState.ERROR)
                false
            },
        )
    }

    private suspend fun activateCopilot(settings: AppSettings, effectivePrompt: String) {
        // Tear down Ollama if it was previously active.
        ollamaAnalyzer?.shutdown()
        ollamaAnalyzer = null
        ollamaStateHolder.setInitState(OllamaInitState.IDLE)

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
        ollamaAnalyzer?.shutdown()
        ollamaAnalyzer = null
        ollamaStateHolder.setInitState(OllamaInitState.IDLE)
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
        ollamaAnalyzer?.shutdown()
        ollamaAnalyzer = null
    }
}
