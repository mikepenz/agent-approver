package com.mikepenz.agentbuddy.ui.approvals

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import co.touchlab.kermit.Logger
import com.mikepenz.agentbuddy.di.AppScope
import com.mikepenz.agentbuddy.model.AppSettings
import com.mikepenz.agentbuddy.model.ApprovalRequest
import com.mikepenz.agentbuddy.model.Decision
import com.mikepenz.agentbuddy.model.RiskAnalysis
import com.mikepenz.agentbuddy.model.RiskAnalysisBackend
import com.mikepenz.agentbuddy.model.ToolType
import com.mikepenz.agentbuddy.risk.ActiveRiskAnalyzerHolder
import com.mikepenz.agentbuddy.risk.RiskAutoActionOrchestrator
import com.mikepenz.agentbuddy.state.AppStateManager
import dev.zacsweers.metro.ContributesIntoMap
import dev.zacsweers.metro.Inject
import dev.zacsweers.metrox.viewmodel.ViewModelKey
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.serialization.json.JsonElement
import kotlin.time.ComparableTimeMark

/**
 * ViewModel backing the Approvals tab.
 *
 * Owns:
 *  - The per-approval risk-analysis status / error / auto-deny set previously
 *    held as Compose `mutableStateMapOf` in `App.kt`.
 *  - The user-interaction time marks used to gate auto-actions.
 *  - The side effect that observes new pending approvals, kicks off risk
 *    analysis, and triggers auto-approve / auto-deny via [orchestrator].
 *
 * The risk analyzer is read lazily from [analyzerHolder] each time analysis is
 * requested so that backend switches in `Main.kt` take effect immediately.
 */
@Inject
@ViewModelKey
@ContributesIntoMap(AppScope::class)
class ApprovalsViewModel(
    private val stateManager: AppStateManager,
    private val analyzerHolder: ActiveRiskAnalyzerHolder,
    private val orchestrator: RiskAutoActionOrchestrator,
) : ViewModel() {

    private val log = Logger.withTag("ApprovalsViewModel")
    private val _uiState = MutableStateFlow(ApprovalsUiState())
    val uiState: StateFlow<ApprovalsUiState> = _uiState.asStateFlow()

    val pendingApprovals: StateFlow<List<ApprovalRequest>> = stateManager.state
        .map { it.pendingApprovals }
        .stateIn(viewModelScope, SharingStarted.Eagerly, stateManager.state.value.pendingApprovals)

    val settings: StateFlow<AppSettings> = stateManager.state
        .map { it.settings }
        .stateIn(viewModelScope, SharingStarted.Eagerly, stateManager.state.value.settings)

    val riskResults: StateFlow<Map<String, com.mikepenz.agentbuddy.model.RiskAnalysis>> =
        stateManager.state
            .map { it.riskResults }
            .stateIn(viewModelScope, SharingStarted.Eagerly, stateManager.state.value.riskResults)

    /**
     * Per-approval monotonic time marks of the user's last interaction. Read by
     * [orchestrator] to enforce the user-quiet period before auto-actions.
     * Plain mutable map (not a flow) — only the orchestrator's polling reads it.
     */
    private val userInteractionTimestamps = mutableMapOf<String, ComparableTimeMark>()

    /** IDs we've already kicked off analysis for, so re-emissions don't re-trigger. */
    private val knownIds = mutableSetOf<String>()

    init {
        viewModelScope.launch {
            stateManager.state
                .map { it.pendingApprovals }
                .collect { handlePendingChanged(it) }
        }
    }

    private suspend fun handlePendingChanged(pending: List<ApprovalRequest>) {
        for (approval in pending) {
            if (approval.id in knownIds) continue
            knownIds.add(approval.id)
            val currentSettings = stateManager.state.value.settings
            if (!currentSettings.riskAnalysisEnabled) continue

            _uiState.update { it.copy(riskStatuses = it.riskStatuses + (approval.id to RiskStatus.ANALYZING)) }
            viewModelScope.launch { analyzeAndAct(approval) }
        }
        // Clean up state for IDs no longer pending
        val currentIds = pending.map { it.id }.toSet()
        knownIds.removeAll { it !in currentIds }
        userInteractionTimestamps.keys.removeAll { it !in currentIds }
        _uiState.update { ui ->
            ui.copy(
                riskStatuses = ui.riskStatuses.filterKeys { it in currentIds },
                riskErrors = ui.riskErrors.filterKeys { it in currentIds },
                autoDenyRequests = ui.autoDenyRequests.filterTo(mutableSetOf()) { it in currentIds },
            )
        }
    }

    private suspend fun analyzeAndAct(approval: ApprovalRequest) {
        val analyzer = analyzerHolder.analyzer.value
        if (analyzer == null) {
            _uiState.update {
                it.copy(
                    riskStatuses = it.riskStatuses + (approval.id to RiskStatus.ERROR),
                    riskErrors = it.riskErrors + (approval.id to "No analyzer"),
                )
            }
            return
        }

        val result = analyzer.analyze(approval.hookInput)
        result.onSuccess { analysis ->
            _uiState.update { it.copy(riskStatuses = it.riskStatuses + (approval.id to RiskStatus.COMPLETED)) }
            stateManager.updateRiskResult(approval.id, analysis)

            // Auto-actions never apply to Plan or AskUserQuestion
            val skipAutoActions = approval.toolType == ToolType.PLAN ||
                approval.toolType == ToolType.ASK_USER_QUESTION
            if (skipAutoActions) return@onSuccess

            // Read fresh settings right before auto-action decisions so that
            // toggling autoApproveLevel / autoDenyLevel while analysis is
            // in flight takes effect immediately.
            val freshSettings = stateManager.state.value.settings
            // Master kill-switch (tray menu). When off, fall through to manual
            // resolution for both bands without changing the stored levels.
            if (!freshSettings.autoDecisionsEnabled) return@onSuccess
            // Defensive: only the 1..5 band represents a real risk verdict. A
            // sentinel value (e.g. 0) from a failed-analysis path must never
            // trigger auto-actions.
            if (analysis.risk !in 1..5) return@onSuccess
            when {
                freshSettings.autoApproveLevel > 0 && analysis.risk <= freshSettings.autoApproveLevel -> {
                    orchestrator.runAutoApprove(
                        approvalId = approval.id,
                        analysis = analysis,
                        timestamps = { userInteractionTimestamps.toMap() },
                    )
                }
                freshSettings.autoDenyLevel > 0 && analysis.risk >= freshSettings.autoDenyLevel && !freshSettings.awayMode -> {
                    orchestrator.runAutoDenyWithRetry(
                        approvalId = approval.id,
                        analysis = analysis,
                        timestamps = { userInteractionTimestamps.toMap() },
                        startCountdown = {
                            _uiState.update { it.copy(autoDenyRequests = it.autoDenyRequests + approval.id) }
                        },
                        cancelCountdown = {
                            _uiState.update { it.copy(autoDenyRequests = it.autoDenyRequests - approval.id) }
                        },
                        isCountdownActive = { approval.id in _uiState.value.autoDenyRequests },
                    )
                }
            }
        }.onFailure { error ->
            // Surface the actual exception in both the diagnostics log and the
            // pending-approval card. Previously every failure was collapsed to
            // a one-word label ("Error" / "Ollama offline") which left the user
            // with no clue why analysis failed.
            log.e(error) { "Risk analysis failed for ${approval.id} (tool=${approval.hookInput.toolName})" }
            val detail = error.message?.takeIf { it.isNotBlank() }
                ?: error::class.simpleName
                ?: "Unknown error"
            _uiState.update { ui ->
                ui.copy(
                    riskStatuses = ui.riskStatuses + (approval.id to RiskStatus.ERROR),
                    riskErrors = ui.riskErrors + (approval.id to detail),
                )
            }
        }
    }

    /**
     * Builds a synthetic [RiskAnalysis] carrying the captured analyzer error
     * so manual resolution preserves the failure reason in history. `risk = 0`
     * (out of the 1..5 band) marks the entry as "no real analysis" — the
     * auto-decision branches above already require risk in 1..5, so this
     * cannot retrigger them.
     */
    private fun errorRiskAnalysis(requestId: String): RiskAnalysis? {
        val message = _uiState.value.riskErrors[requestId] ?: return null
        val backend = stateManager.state.value.settings.riskAnalysisBackend
        val source = when (backend) {
            RiskAnalysisBackend.CLAUDE -> "claude"
            RiskAnalysisBackend.COPILOT -> "copilot"
            RiskAnalysisBackend.OLLAMA -> "ollama"
        }
        return RiskAnalysis(
            risk = 0,
            label = "error",
            message = message,
            source = source,
        )
    }

    // ----- Approval actions (called by ApprovalsTab) -----

    fun onApprove(requestId: String, feedback: String?) {
        stateManager.resolve(requestId, Decision.APPROVED, feedback, errorRiskAnalysis(requestId), null)
    }

    fun onAlwaysAllow(requestId: String) {
        stateManager.resolve(requestId, Decision.ALWAYS_ALLOWED, "Always allowed", errorRiskAnalysis(requestId), null)
    }

    fun onDeny(requestId: String, feedback: String) {
        stateManager.resolve(requestId, Decision.DENIED, feedback, errorRiskAnalysis(requestId), null)
    }

    fun onApproveWithInput(requestId: String, updatedInput: Map<String, JsonElement>) {
        stateManager.resolve(
            requestId = requestId,
            decision = Decision.APPROVED,
            feedback = "User answered question",
            riskAnalysis = errorRiskAnalysis(requestId),
            rawResponseJson = null,
            updatedInput = updatedInput,
        )
    }

    fun onDismiss(requestId: String) {
        stateManager.resolve(requestId, Decision.DENIED, "Dismissed", errorRiskAnalysis(requestId), null)
    }

    fun onCancelAutoDeny(requestId: String) {
        _uiState.update { it.copy(autoDenyRequests = it.autoDenyRequests - requestId) }
    }

    fun onUserInteraction(requestId: String) {
        userInteractionTimestamps[requestId] = orchestrator.markNow()
    }

    fun onSettingsChange(settings: AppSettings) {
        stateManager.updateSettings(settings)
    }
}
