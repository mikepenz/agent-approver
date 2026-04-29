package com.mikepenz.agentbelay.ui.approvals

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import com.mikepenz.agentbelay.model.AppSettings
import com.mikepenz.agentbelay.model.ApprovalRequest
import com.mikepenz.agentbelay.model.RiskAnalysis
import com.mikepenz.agentbelay.model.RiskAnalysisBackend
import com.mikepenz.agentbelay.model.SpecialToolParser
import com.mikepenz.agentbelay.model.ToolType
import kotlinx.coroutines.delay
import kotlinx.datetime.Clock
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import kotlinx.serialization.json.JsonElement

@Composable
fun ApprovalsTab(
    pendingApprovals: List<ApprovalRequest>,
    riskResults: Map<String, RiskAnalysis>,
    riskStatuses: Map<String, RiskStatus>,
    riskErrors: Map<String, String>,
    settings: AppSettings,
    onApprove: (requestId: String, feedback: String?) -> Unit,
    onDeny: (requestId: String, feedback: String) -> Unit,
    onAlwaysAllow: (requestId: String) -> Unit,
    onApproveWithInput: (requestId: String, updatedInput: Map<String, JsonElement>) -> Unit,
    onDismiss: (requestId: String) -> Unit,
    autoDenyRequests: Set<String>,
    onCancelAutoDeny: (requestId: String) -> Unit,
    onUserInteraction: (requestId: String) -> Unit = {},
    onPopOut: ((title: String, content: String) -> Unit)? = null,
    onSettingsChange: (AppSettings) -> Unit = {},
) {
    var now by remember { mutableStateOf(Clock.System.now()) }
    LaunchedEffect(Unit) {
        while (true) {
            delay(1000)
            now = Clock.System.now()
        }
    }

    val via = when (settings.riskAnalysisBackend) {
        RiskAnalysisBackend.CLAUDE -> "claude"
        RiskAnalysisBackend.COPILOT -> "copilot"
        RiskAnalysisBackend.OLLAMA -> "ollama"
    }

    val items = pendingApprovals.map { request ->
        val risk = riskResults[request.id]
        val status = riskStatuses[request.id]
        val elapsedSeconds = (now - request.timestamp).inWholeSeconds.toInt().coerceAtLeast(0)
        val localTime = request.timestamp.toLocalDateTime(TimeZone.currentSystemDefault())
        val timeStr = "%02d:%02d:%02d".format(localTime.hour, localTime.minute, localTime.second)
        val analysisError = if (status == RiskStatus.ERROR) {
            riskErrors[request.id] ?: "Risk analysis failed."
        } else null
        val questionData = if (request.toolType == ToolType.ASK_USER_QUESTION) {
            SpecialToolParser.parseUserQuestion(request.hookInput.toolInput)
        } else null
        val planData = if (request.toolType == ToolType.PLAN) {
            SpecialToolParser.parsePlanReview(request.hookInput.toolInput)
        } else null
        ApprovalQueueItem(
            id = request.id,
            tool = request.hookInput.toolName,
            toolType = request.toolType,
            source = request.source,
            summary = toolSummaryText(request.hookInput.toolName, request.hookInput.toolInput),
            risk = risk?.risk,
            via = via,
            timestamp = timeStr,
            elapsedSeconds = elapsedSeconds,
            ttlSeconds = settings.defaultTimeoutSeconds,
            session = request.hookInput.sessionId.take(7),
            prompt = "Approval requested for ${request.hookInput.toolName} in ${request.hookInput.cwd}.",
            workingDir = request.hookInput.cwd,
            riskAssessment = risk?.message?.takeIf { it.isNotBlank() }
                ?: when {
                    !settings.riskAnalysisEnabled -> "Risk analysis is disabled."
                    status == RiskStatus.ANALYZING -> "Risk analysis in progress…"
                    status == RiskStatus.ERROR -> analysisError ?: "Risk analysis failed."
                    else -> "Risk analysis pending…"
                },
            riskAnalysisEnabled = settings.riskAnalysisEnabled,
            riskAnalyzing = settings.riskAnalysisEnabled && risk == null &&
                (status == null || status == RiskStatus.ANALYZING || status == RiskStatus.IDLE),
            riskError = analysisError,
            questionData = questionData,
            planData = planData,
            request = request,
        )
    }

    ApprovalsScreen(
        items = items,
        onApprove = { id -> onApprove(id, null) },
        onAlwaysAllow = { id -> onAlwaysAllow(id) },
        onDeny = { id -> onDeny(id, "") },
        onApproveWithInput = { id, updatedInput -> onApproveWithInput(id, updatedInput) },
        onDenyWithFeedback = { id, feedback -> onDeny(id, feedback) },
        onDismiss = { id -> onDismiss(id) },
    )
}
