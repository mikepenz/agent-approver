package com.mikepenz.agentbelay.risk

import com.mikepenz.agentbelay.model.Decision
import com.mikepenz.agentbelay.model.RiskAnalysis
import com.mikepenz.agentbelay.state.AppStateManager
import dev.zacsweers.metro.Inject
import kotlinx.coroutines.delay
import kotlin.time.ComparableTimeMark
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds
import kotlin.time.ExperimentalTime
import kotlin.time.TimeSource

/**
 * Pure (non-Compose) state machine for risk-based auto-actions.
 *
 * Implements the same behavior previously inlined in `App.kt`:
 * - [waitUntilUserQuiet] suspends until at least [USER_QUIET_PERIOD] has elapsed
 *   since the user's last interaction with a given approval.
 * - [runAutoDenyWithRetry] runs the [AUTO_DENY_COUNTDOWN] for risk-5 requests,
 *   re-arming after a quiet period whenever the user interacts mid-countdown.
 *
 * The orchestrator is decoupled from the rest of the app via lambdas, so it can
 * be unit-tested in isolation under `kotlinx-coroutines-test`. A [TimeSource] is
 * injected so tests can drive elapsed time alongside the test scheduler.
 */
@OptIn(ExperimentalTime::class)
@Inject
class RiskAutoActionOrchestrator(
    private val stateManager: AppStateManager,
    private val timeSource: TimeSource.WithComparableMarks = TimeSource.Monotonic,
) {
    fun markNow(): ComparableTimeMark = timeSource.markNow()

    /**
     * Auto-approve a risk-1 request once the user has been quiet for [USER_QUIET_PERIOD].
     * Mirrors the original 500ms initial nudge before checking quiet state. Honours
     * [com.mikepenz.agentbelay.model.AppSettings.autoApproveDelaySeconds] so the
     * user can require a visible review window before the request disappears.
     */
    suspend fun runAutoApprove(
        approvalId: String,
        analysis: RiskAnalysis,
        timestamps: () -> Map<String, ComparableTimeMark>,
    ) {
        delay(500)
        val configuredDelay = stateManager.state.value.settings.autoApproveDelaySeconds.coerceAtLeast(0)
        if (configuredDelay > 0) delay(configuredDelay.seconds)
        // Verify the request is still present after the configurable delay; the
        // user may have resolved it manually during the window.
        if (stateManager.state.value.pendingApprovals.none { it.id == approvalId }) return
        waitUntilUserQuiet(approvalId, timestamps)
        if (stateManager.state.value.pendingApprovals.any { it.id == approvalId }) {
            stateManager.resolve(
                requestId = approvalId,
                decision = Decision.AUTO_APPROVED,
                feedback = "Auto-approved: risk level 1",
                riskAnalysis = analysis,
                rawResponseJson = null,
            )
        }
    }

    /**
     * Auto-deny a risk-5 request after a 15-second visible countdown. The countdown
     * is re-armed whenever the user interacts with the card during it, and aborted
     * if the user manually cancels via the overlay or resolves the request.
     */
    suspend fun runAutoDenyWithRetry(
        approvalId: String,
        analysis: RiskAnalysis,
        timestamps: () -> Map<String, ComparableTimeMark>,
        startCountdown: () -> Unit,
        cancelCountdown: () -> Unit,
        isCountdownActive: () -> Boolean,
    ) {
        while (stateManager.state.value.pendingApprovals.any { it.id == approvalId }) {
            waitUntilUserQuiet(approvalId, timestamps)
            if (stateManager.state.value.pendingApprovals.none { it.id == approvalId }) return

            startCountdown()
            val countdownStartedAt = timeSource.markNow()
            // Read the configured countdown each iteration so a settings change
            // during a re-armed retry takes effect immediately.
            val countdown = stateManager.state.value.settings.autoDenyCountdownSeconds
                .coerceAtLeast(1).seconds
            var interrupted = false
            while (countdownStartedAt.elapsedNow() < countdown) {
                delay(200)
                // Request was resolved manually (or otherwise removed) — stop the countdown.
                if (stateManager.state.value.pendingApprovals.none { it.id == approvalId }) {
                    cancelCountdown()
                    return
                }
                // User cancelled via the overlay button
                if (!isCountdownActive()) return
                // User interacted with the card during the countdown — abort and wait for quiet
                val last = timestamps()[approvalId]
                if (last != null && last >= countdownStartedAt) {
                    cancelCountdown()
                    interrupted = true
                    break
                }
            }
            if (interrupted) continue

            if (isCountdownActive()) {
                cancelCountdown()
                if (stateManager.state.value.pendingApprovals.any { it.id == approvalId }) {
                    stateManager.resolve(
                        requestId = approvalId,
                        decision = Decision.AUTO_DENIED,
                        feedback = "Auto-denied: risk level 5",
                        riskAnalysis = analysis,
                        rawResponseJson = null,
                    )
                }
                return
            }
        }
    }

    private suspend fun waitUntilUserQuiet(
        approvalId: String,
        timestamps: () -> Map<String, ComparableTimeMark>,
    ) {
        while (true) {
            val last = timestamps()[approvalId] ?: return
            val remaining = USER_QUIET_PERIOD - last.elapsedNow()
            if (remaining <= Duration.ZERO) return
            delay(remaining)
        }
    }

    companion object {
        val USER_QUIET_PERIOD: Duration = 10.seconds
        val AUTO_DENY_COUNTDOWN: Duration = 15.seconds
    }
}
