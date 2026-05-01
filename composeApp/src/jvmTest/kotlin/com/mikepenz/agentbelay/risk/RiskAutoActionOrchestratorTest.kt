package com.mikepenz.agentbelay.risk

import com.mikepenz.agentbelay.model.AppSettings
import com.mikepenz.agentbelay.model.ApprovalRequest
import com.mikepenz.agentbelay.model.Decision
import com.mikepenz.agentbelay.model.HookInput
import com.mikepenz.agentbelay.model.RiskAnalysis
import com.mikepenz.agentbelay.model.Source
import com.mikepenz.agentbelay.model.ToolType
import com.mikepenz.agentbelay.state.AppStateManager
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.time.Clock
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.time.ComparableTimeMark
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds
import kotlin.time.ExperimentalTime

/**
 * Unit tests for [RiskAutoActionOrchestrator]. The orchestrator owns the
 * auto-approve / auto-deny state machine that used to live inside `App.kt`'s
 * `LaunchedEffect`. These tests run under `runTest` virtual time, with the
 * orchestrator's [TimeSource][kotlin.time.TimeSource] swapped to the test
 * scheduler's so elapsed-time checks advance in lockstep with `delay()`.
 */
@OptIn(ExperimentalCoroutinesApi::class, ExperimentalTime::class)
class RiskAutoActionOrchestratorTest {

    private val analysis = RiskAnalysis(risk = 5, label = "Critical", message = "rm -rf /")
    private val approvalId = "req-1"

    private fun newRequest() = ApprovalRequest(
        id = approvalId,
        source = Source.CLAUDE_CODE,
        toolType = ToolType.DEFAULT,
        hookInput = HookInput(sessionId = "s", toolName = "Bash"),
        timestamp = Clock.System.now(),
        rawRequestJson = "{}",
    )

    @Test
    fun `auto-approve fires after the user-quiet period when no interaction`() = runTest {
        val state = AppStateManager()
        state.addPending(newRequest())
        val orchestrator = RiskAutoActionOrchestrator(state, testScheduler.timeSource)

        val timestamps = mutableMapOf<String, ComparableTimeMark>()
        val job = launch { orchestrator.runAutoApprove(approvalId, analysis) { timestamps.toMap() } }

        // Initial 500ms nudge — still pending.
        advanceTimeBy(400.milliseconds); runCurrent()
        assertEquals(1, state.state.value.pendingApprovals.size)

        // Quiet period elapses with no recorded interaction → resolves immediately after the nudge.
        advanceTimeBy(200.milliseconds); runCurrent()
        job.join()
        assertEquals(0, state.state.value.pendingApprovals.size)
        val result = state.state.value.history.first()
        assertEquals(Decision.AUTO_APPROVED, result.decision)
    }

    @Test
    fun `auto-approve waits out a user interaction before firing`() = runTest {
        val state = AppStateManager()
        state.addPending(newRequest())
        val orchestrator = RiskAutoActionOrchestrator(state, testScheduler.timeSource)

        val timestamps = mutableMapOf<String, ComparableTimeMark>()
        timestamps[approvalId] = testScheduler.timeSource.markNow()  // user just interacted
        val job = launch { orchestrator.runAutoApprove(analysis = analysis, approvalId = approvalId) { timestamps.toMap() } }

        // Less than the quiet period — must still be pending.
        advanceTimeBy(8.seconds); runCurrent()
        assertEquals(1, state.state.value.pendingApprovals.size)

        // Past the 10s quiet period (plus the initial 500ms nudge) → fires.
        advanceTimeBy(3.seconds); runCurrent()
        job.join()
        assertEquals(Decision.AUTO_APPROVED, state.state.value.history.first().decision)
    }

    @Test
    fun `auto-deny fires after the 15s countdown when there's no interaction`() = runTest {
        val state = AppStateManager()
        state.addPending(newRequest())
        val orchestrator = RiskAutoActionOrchestrator(state, testScheduler.timeSource)

        val timestamps = mutableMapOf<String, ComparableTimeMark>()
        var countdownActive = false
        val job = launch {
            orchestrator.runAutoDenyWithRetry(
                approvalId = approvalId,
                analysis = analysis,
                timestamps = { timestamps.toMap() },
                startCountdown = { countdownActive = true },
                cancelCountdown = { countdownActive = false },
                isCountdownActive = { countdownActive },
            )
        }

        // No interactions → quiet period returns immediately, countdown starts.
        runCurrent()
        assertTrue(countdownActive, "countdown should be active")
        assertEquals(1, state.state.value.pendingApprovals.size)

        // Mid-countdown → still pending.
        advanceTimeBy(10.seconds); runCurrent()
        assertEquals(1, state.state.value.pendingApprovals.size)
        assertTrue(countdownActive)

        // Past the 15s countdown → resolved.
        advanceTimeBy(6.seconds); runCurrent()
        job.join()
        assertEquals(0, state.state.value.pendingApprovals.size)
        assertEquals(Decision.AUTO_DENIED, state.state.value.history.first().decision)
    }

    @Test
    fun `user interaction during countdown re-arms after another quiet period`() = runTest {
        val state = AppStateManager()
        state.addPending(newRequest())
        val orchestrator = RiskAutoActionOrchestrator(state, testScheduler.timeSource)

        val timestamps = mutableMapOf<String, ComparableTimeMark>()
        var countdownActive = false
        val job = launch {
            orchestrator.runAutoDenyWithRetry(
                approvalId = approvalId,
                analysis = analysis,
                timestamps = { timestamps.toMap() },
                startCountdown = { countdownActive = true },
                cancelCountdown = { countdownActive = false },
                isCountdownActive = { countdownActive },
            )
        }

        // First countdown begins.
        runCurrent()
        assertTrue(countdownActive)

        // User interacts 5 seconds in.
        advanceTimeBy(5.seconds); runCurrent()
        timestamps[approvalId] = testScheduler.timeSource.markNow()
        // The orchestrator polls every 200ms — give it a tick to notice.
        advanceTimeBy(300.milliseconds); runCurrent()
        // Countdown should be cancelled and the orchestrator now waiting for quiet.
        assertEquals(false, countdownActive)
        assertEquals(1, state.state.value.pendingApprovals.size)

        // Wait out the quiet period — countdown re-arms.
        advanceTimeBy(11.seconds); runCurrent()
        assertTrue(countdownActive, "countdown should re-arm after quiet period")

        // Then run out the new countdown.
        advanceTimeBy(16.seconds); runCurrent()
        job.join()
        assertEquals(Decision.AUTO_DENIED, state.state.value.history.first().decision)
    }

    @Test
    fun `cancelling auto-deny via the overlay aborts without resolving`() = runTest {
        val state = AppStateManager()
        state.addPending(newRequest())
        val orchestrator = RiskAutoActionOrchestrator(state, testScheduler.timeSource)

        val timestamps = mutableMapOf<String, ComparableTimeMark>()
        var countdownActive = false
        val job = launch {
            orchestrator.runAutoDenyWithRetry(
                approvalId = approvalId,
                analysis = analysis,
                timestamps = { timestamps.toMap() },
                startCountdown = { countdownActive = true },
                cancelCountdown = { countdownActive = false },
                isCountdownActive = { countdownActive },
            )
        }
        runCurrent()
        assertTrue(countdownActive)

        // User clicks "cancel auto-deny" → toggle the flag externally.
        advanceTimeBy(5.seconds); runCurrent()
        countdownActive = false
        advanceTimeBy(300.milliseconds); runCurrent()

        job.join()
        // Request still pending — orchestrator returned without resolving.
        assertEquals(1, state.state.value.pendingApprovals.size)
        assertTrue(state.state.value.history.isEmpty())
    }

    @Test
    fun `manually resolving the request stops the countdown`() = runTest {
        val state = AppStateManager()
        state.addPending(newRequest())
        val orchestrator = RiskAutoActionOrchestrator(state, testScheduler.timeSource)

        val timestamps = mutableMapOf<String, ComparableTimeMark>()
        var countdownActive = false
        val job = launch {
            orchestrator.runAutoDenyWithRetry(
                approvalId = approvalId,
                analysis = analysis,
                timestamps = { timestamps.toMap() },
                startCountdown = { countdownActive = true },
                cancelCountdown = { countdownActive = false },
                isCountdownActive = { countdownActive },
            )
        }
        runCurrent()
        assertTrue(countdownActive)

        // User manually approves mid-countdown.
        advanceTimeBy(5.seconds); runCurrent()
        state.resolve(approvalId, Decision.APPROVED, feedback = null, riskAnalysis = null, rawResponseJson = null)
        advanceTimeBy(300.milliseconds); runCurrent()

        job.join()
        assertEquals(false, countdownActive)
        // The history entry is the manual one, not auto-denied.
        assertEquals(Decision.APPROVED, state.state.value.history.first().decision)
        // The orchestrator did not double-add an AUTO_DENIED entry.
        assertEquals(1, state.state.value.history.size)
    }

    @Test
    fun `autoApproveDelaySeconds delays the resolution`() = runTest {
        val state = AppStateManager()
        state.updateSettings(AppSettings(autoApproveDelaySeconds = 5))
        state.addPending(newRequest())
        val orchestrator = RiskAutoActionOrchestrator(state, testScheduler.timeSource)

        val timestamps = mutableMapOf<String, ComparableTimeMark>()
        val approveAnalysis = RiskAnalysis(risk = 1, label = "Safe", message = "ls")
        val job = launch { orchestrator.runAutoApprove(approvalId, approveAnalysis) { timestamps.toMap() } }

        // 500ms nudge + part of the configured delay → still pending.
        advanceTimeBy(3.seconds); runCurrent()
        assertEquals(1, state.state.value.pendingApprovals.size)

        // Past the 5-second window plus the nudge → resolves.
        advanceTimeBy(3.seconds); runCurrent()
        job.join()
        assertEquals(Decision.AUTO_APPROVED, state.state.value.history.first().decision)
    }

    @Test
    fun `autoDenyCountdownSeconds overrides the default countdown`() = runTest {
        val state = AppStateManager()
        state.updateSettings(AppSettings(autoDenyCountdownSeconds = 5))
        state.addPending(newRequest())
        val orchestrator = RiskAutoActionOrchestrator(state, testScheduler.timeSource)

        val timestamps = mutableMapOf<String, ComparableTimeMark>()
        var countdownActive = false
        val job = launch {
            orchestrator.runAutoDenyWithRetry(
                approvalId = approvalId,
                analysis = analysis,
                timestamps = { timestamps.toMap() },
                startCountdown = { countdownActive = true },
                cancelCountdown = { countdownActive = false },
                isCountdownActive = { countdownActive },
            )
        }
        runCurrent()
        assertTrue(countdownActive)

        // 4s in — still pending under the 5s configured countdown.
        advanceTimeBy(4.seconds); runCurrent()
        assertEquals(1, state.state.value.pendingApprovals.size)

        // Past the 5s configured countdown → resolves (much faster than the default 15s).
        advanceTimeBy(2.seconds); runCurrent()
        job.join()
        assertEquals(Decision.AUTO_DENIED, state.state.value.history.first().decision)
    }

    @Test
    fun `auto-approve does not fire if request was resolved during quiet wait`() = runTest {
        val state = AppStateManager()
        state.addPending(newRequest())
        val orchestrator = RiskAutoActionOrchestrator(state, testScheduler.timeSource)

        val timestamps = mutableMapOf<String, ComparableTimeMark>()
        timestamps[approvalId] = testScheduler.timeSource.markNow()
        val approveAnalysis = RiskAnalysis(risk = 1, label = "Safe", message = "ls")
        val job = launch { orchestrator.runAutoApprove(approvalId, approveAnalysis) { timestamps.toMap() } }

        // Resolve manually before the quiet period elapses.
        advanceTimeBy(2.seconds); runCurrent()
        state.resolve(approvalId, Decision.DENIED, feedback = "no", riskAnalysis = null, rawResponseJson = null)

        advanceTimeBy(20.seconds); runCurrent()
        job.join()
        // No second history entry from the orchestrator.
        assertEquals(1, state.state.value.history.size)
        assertEquals(Decision.DENIED, state.state.value.history.first().decision)
    }
}
