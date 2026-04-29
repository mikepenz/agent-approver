package com.mikepenz.agentbelay.app

import com.mikepenz.agentbelay.di.AppEnvironment
import com.mikepenz.agentbelay.model.RiskAnalysisBackend
import com.mikepenz.agentbelay.risk.ActiveRiskAnalyzerHolder
import com.mikepenz.agentbelay.risk.ClaudeCliRiskAnalyzer
import com.mikepenz.agentbelay.risk.CopilotStateHolder
import com.mikepenz.agentbelay.risk.OllamaStateHolder
import com.mikepenz.agentbelay.risk.RiskMessageBuilder
import com.mikepenz.agentbelay.state.AppStateManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

/**
 * Tests for [RiskAnalyzerLifecycle]. Focused on the Claude side because the
 * Copilot side spawns a child process via [com.mikepenz.agentbelay.risk.CopilotRiskAnalyzer]
 * which isn't available in unit tests; that path is exercised by manual smoke
 * during phase rollouts.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class RiskAnalyzerLifecycleTest {

    private fun env(scope: CoroutineScope) = AppEnvironment(
        dataDir = "/tmp/test",
        devMode = false,
        appScope = scope,
    )

    @Test
    fun `start seeds the active analyzer with the claude analyzer`() = runTest {
        val state = AppStateManager()
        val claude = ClaudeCliRiskAnalyzer()
        val holder = ActiveRiskAnalyzerHolder()
        val copilotState = CopilotStateHolder()
        val lifecycle = RiskAnalyzerLifecycle(state, claude, holder, copilotState, OllamaStateHolder(), env(this))

        lifecycle.start()
        runCurrent()

        assertEquals(claude, holder.analyzer.value)
        lifecycle.shutdown()
    }

    @Test
    fun `claude model and system prompt are propagated from settings`() = runTest {
        val state = AppStateManager()
        val claude = ClaudeCliRiskAnalyzer()
        val lifecycle = RiskAnalyzerLifecycle(
            state,
            claude,
            ActiveRiskAnalyzerHolder(),
            CopilotStateHolder(),
            OllamaStateHolder(),
            env(this),
        )

        lifecycle.start()
        runCurrent()

        // Default settings → default prompt
        val customPrompt = "You are a security auditor."
        state.updateSettings(
            state.state.value.settings.copy(
                riskAnalysisModel = "sonnet",
                riskAnalysisCustomPrompt = customPrompt,
            ),
        )
        runCurrent()

        assertEquals("sonnet", claude.model)
        assertEquals(customPrompt, claude.systemPrompt)
        lifecycle.shutdown()
    }

    @Test
    fun `blank custom prompt falls back to the default system prompt`() = runTest {
        val state = AppStateManager()
        val claude = ClaudeCliRiskAnalyzer()
        val lifecycle = RiskAnalyzerLifecycle(
            state,
            claude,
            ActiveRiskAnalyzerHolder(),
            CopilotStateHolder(),
            OllamaStateHolder(),
            env(this),
        )

        lifecycle.start()
        runCurrent()

        // Update something else (the model) so the collector emits with a blank prompt.
        state.updateSettings(state.state.value.settings.copy(riskAnalysisModel = "opus"))
        runCurrent()

        assertEquals(RiskMessageBuilder.DEFAULT_SYSTEM_PROMPT, claude.systemPrompt)
        lifecycle.shutdown()
    }

    @Test
    fun `selecting the CLAUDE backend keeps claude as the active analyzer`() = runTest {
        val state = AppStateManager()
        val claude = ClaudeCliRiskAnalyzer()
        val holder = ActiveRiskAnalyzerHolder()
        val lifecycle = RiskAnalyzerLifecycle(
            state,
            claude,
            holder,
            CopilotStateHolder(),
            OllamaStateHolder(),
            env(this),
        )

        lifecycle.start()
        runCurrent()

        // Touch settings while staying on CLAUDE — active analyzer must remain claude.
        state.updateSettings(state.state.value.settings.copy(riskAnalysisModel = "sonnet"))
        runCurrent()

        val active = assertNotNull(holder.analyzer.value)
        assertEquals(claude, active)
        lifecycle.shutdown()
    }

    @Test
    fun `shutdown is idempotent and clears no copilot when none was started`() = runTest {
        val lifecycle = RiskAnalyzerLifecycle(
            AppStateManager(),
            ClaudeCliRiskAnalyzer(),
            ActiveRiskAnalyzerHolder(),
            CopilotStateHolder(),
            OllamaStateHolder(),
            env(this),
        )
        // Calling shutdown without starting must not throw and must be idempotent.
        lifecycle.shutdown()
        lifecycle.shutdown()
    }
}
