package com.mikepenz.agentbelay.ui.approvals

import com.mikepenz.agentbelay.model.AppSettings
import com.mikepenz.agentbelay.model.ApprovalRequest
import com.mikepenz.agentbelay.model.Decision
import com.mikepenz.agentbelay.model.HookInput
import com.mikepenz.agentbelay.model.RiskAnalysis
import com.mikepenz.agentbelay.model.Source
import com.mikepenz.agentbelay.model.ToolType
import com.mikepenz.agentbelay.risk.ActiveRiskAnalyzerHolder
import com.mikepenz.agentbelay.risk.RiskAnalyzer
import com.mikepenz.agentbelay.risk.RiskAutoActionOrchestrator
import com.mikepenz.agentbelay.state.AppStateManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlin.time.Clock
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.seconds
import kotlin.time.ExperimentalTime

/**
 * Integration test for the auto-approve pipeline that goes
 *   AppStateManager.addPending → ApprovalsViewModel.handlePendingChanged →
 *   analyzer.analyze → RiskAutoActionOrchestrator.runAutoApprove →
 *   AppStateManager.resolve(AUTO_APPROVED).
 *
 * Catches regressions in the wiring between [ApprovalsViewModel] and
 * [RiskAutoActionOrchestrator] that the orchestrator unit tests can't see.
 */
@OptIn(ExperimentalCoroutinesApi::class, ExperimentalTime::class)
class ApprovalsViewModelAutoApproveTest {

    @BeforeTest fun setMainDispatcher() {
        Dispatchers.setMain(kotlinx.coroutines.test.UnconfinedTestDispatcher())
    }

    @AfterTest fun resetMainDispatcher() {
        Dispatchers.resetMain()
    }

    private fun newRequest(id: String = "req-1") = ApprovalRequest(
        id = id,
        source = Source.CLAUDE_CODE,
        toolType = ToolType.DEFAULT,
        hookInput = HookInput(sessionId = "s", toolName = "Bash"),
        timestamp = Clock.System.now(),
        rawRequestJson = "{}",
    )

    private class StubAnalyzer(private val analysis: RiskAnalysis) : RiskAnalyzer {
        override suspend fun analyze(hookInput: HookInput): Result<RiskAnalysis> =
            Result.success(analysis)
        // Not exercised by these tests, but the interface requires it.
        override suspend fun analyzeText(systemPrompt: String, userPrompt: String): Result<String> =
            error("not used")
    }

    @Test
    fun `auto-approve fires when autoApproveLevel covers the analyzed risk`() = runTest {
        val state = AppStateManager()
        // Initialize default state and explicitly enable auto-approve up to risk 1.
        state.updateSettings(AppSettings(autoApproveLevel = 1, riskAnalysisEnabled = true))

        val analyzerHolder = ActiveRiskAnalyzerHolder().apply {
            set(StubAnalyzer(RiskAnalysis(risk = 1, label = "Safe", message = "ls")))
        }
        val orchestrator = RiskAutoActionOrchestrator(state, testScheduler.timeSource)

        ApprovalsViewModel(state, analyzerHolder, orchestrator)
        runCurrent()

        state.addPending(newRequest())
        advanceUntilIdle()

        assertEquals(0, state.state.value.pendingApprovals.size, "approval should be auto-resolved")
        val first = state.state.value.history.firstOrNull()
        assertTrue(first != null, "history should have one entry")
        assertEquals(Decision.AUTO_APPROVED, first.decision)
    }

    @Test
    fun `auto-approve does not fire when autoApproveLevel is 0`() = runTest {
        val state = AppStateManager()
        state.updateSettings(AppSettings(autoApproveLevel = 0, riskAnalysisEnabled = true))

        val analyzerHolder = ActiveRiskAnalyzerHolder().apply {
            set(StubAnalyzer(RiskAnalysis(risk = 1, label = "Safe", message = "ls")))
        }
        val orchestrator = RiskAutoActionOrchestrator(state, testScheduler.timeSource)

        ApprovalsViewModel(state, analyzerHolder, orchestrator)
        runCurrent()

        state.addPending(newRequest())
        advanceTimeBy(20.seconds); runCurrent()

        assertEquals(1, state.state.value.pendingApprovals.size, "approval should remain pending")
        assertTrue(state.state.value.history.isEmpty())
    }

    @Test
    fun `autoDecisionsEnabled false suppresses auto-approve even when level allows it`() = runTest {
        val state = AppStateManager()
        state.updateSettings(
            AppSettings(
                autoApproveLevel = 1,
                riskAnalysisEnabled = true,
                autoDecisionsEnabled = false,
            ),
        )

        val analyzerHolder = ActiveRiskAnalyzerHolder().apply {
            set(StubAnalyzer(RiskAnalysis(risk = 1, label = "Safe", message = "ls")))
        }
        val orchestrator = RiskAutoActionOrchestrator(state, testScheduler.timeSource)

        ApprovalsViewModel(state, analyzerHolder, orchestrator)
        runCurrent()

        state.addPending(newRequest())
        advanceTimeBy(20.seconds); runCurrent()

        assertEquals(1, state.state.value.pendingApprovals.size, "approval should remain pending while paused")
        assertTrue(state.state.value.history.isEmpty())
    }

    @Test
    fun `auto-approve respects fresh settings updated after analysis is queued`() = runTest {
        val state = AppStateManager()
        // Start with auto-approve OFF.
        state.updateSettings(AppSettings(autoApproveLevel = 0, riskAnalysisEnabled = true))

        val analyzerHolder = ActiveRiskAnalyzerHolder().apply {
            set(StubAnalyzer(RiskAnalysis(risk = 1, label = "Safe", message = "ls")))
        }
        val orchestrator = RiskAutoActionOrchestrator(state, testScheduler.timeSource)

        ApprovalsViewModel(state, analyzerHolder, orchestrator)
        runCurrent()

        // Toggle auto-approve ON before any approval arrives — simulates the
        // user changing the setting in the Settings tab.
        state.updateSettings(AppSettings(autoApproveLevel = 1, riskAnalysisEnabled = true))
        runCurrent()

        state.addPending(newRequest())
        advanceUntilIdle()

        assertEquals(0, state.state.value.pendingApprovals.size)
        assertEquals(Decision.AUTO_APPROVED, state.state.value.history.first().decision)
    }
}
