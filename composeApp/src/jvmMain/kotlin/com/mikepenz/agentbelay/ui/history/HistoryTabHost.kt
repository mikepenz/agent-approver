package com.mikepenz.agentbelay.ui.history

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import com.mikepenz.agentbelay.model.ApprovalRequest
import com.mikepenz.agentbelay.model.ApprovalResult
import com.mikepenz.agentbelay.model.Decision
import com.mikepenz.agentbelay.model.ToolType
import com.mikepenz.agentbelay.ui.components.DecisionStatus
import dev.zacsweers.metrox.viewmodel.metroViewModel
import kotlin.time.Instant
import com.mikepenz.agentbelay.util.asStringOrNull
import kotlin.time.Duration
import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds

/**
 * Resolves the [HistoryViewModel] from Metro and forwards its pre-projected,
 * pre-filtered [HistoryUiState] to [HistoryScreen]. All projection, filtering,
 * and counting happens in the VM — this composable is a thin pass-through.
 */
@Composable
fun HistoryTabHost(onJumpToApprovals: () -> Unit) {
    val viewModel: HistoryViewModel = metroViewModel()
    val ui by viewModel.uiState.collectAsState()
    val onReplay: ((String) -> Unit)? = if (viewModel.devMode) {
        { id -> if (viewModel.replayById(id)) onJumpToApprovals() }
    } else null
    HistoryScreen(
        ui = ui,
        onScopeChange = viewModel::setScope,
        onQueryChange = viewModel::setQuery,
        onSortChange = viewModel::setSort,
        onHarnessFilterChange = viewModel::setHarnessFilter,
        onReplay = onReplay,
    )
}

/**
 * Convenience for previews: project a list of [ApprovalResult] (the persisted
 * shape) into the design [HistoryEntry] shape, evaluated against [now].
 */
fun List<ApprovalResult>.toHistoryEntries(now: Instant): List<HistoryEntry> =
    map { it.toHistoryEntry(now) }

internal fun ApprovalResult.toHistoryEntry(now: Instant): HistoryEntry {
    val req = request
    val viaName = riskAnalysis?.source?.takeIf { it.isNotBlank() }
    val cwd = req.hookInput.cwd.takeIf { it.isNotBlank() }
    val timeToDecision = formatDecisionDuration(decidedAt - req.timestamp)
    val feedbackText = feedback?.takeIf { it.isNotBlank() }
        ?: when (decision) {
            Decision.RESOLVED_EXTERNALLY ->
                "Resolved externally (decided in harness or harness exited)"
            else -> null
        }
    val assessmentText = riskAnalysis?.let {
        // risk = 0 / label = "error" is the synthetic entry recorded by
        // ApprovalsViewModel when the analyzer itself failed; show it as a
        // failure reason rather than as "Level 0 — …" which reads as a real
        // verdict.
        if (it.risk !in 1..5) "Analysis failed — ${it.message}"
        else "Level ${it.risk} — ${it.message}"
    } ?: protectionDetail
    val promptText = req.hookInput.toolInput["prompt"].asStringOrNull()
        ?: req.hookInput.toolInput["question"].asStringOrNull()

    return HistoryEntry(
        id = req.id,
        tool = req.hookInput.toolName,
        source = req.source,
        summary = summaryText(req),
        status = decision.toStatus(),
        risk = riskAnalysis?.risk,
        via = viaName,
        time = relativeTimestamp(decidedAt, now),
        tag = protectionModule?.let(::prettyProtectionModule),
        workingDir = cwd,
        timeToDecision = timeToDecision,
        feedback = feedbackText,
        assessment = assessmentText,
        prompt = promptText,
        toolType = req.toolType,
        toolInput = req.hookInput.toolInput,
        rawRequestJson = req.rawRequestJson.takeIf { it.isNotBlank() },
        rawResponseJson = rawResponseJson?.takeIf { it.isNotBlank() },
        rawValidationResponseJson = riskAnalysis?.rawResponse?.takeIf { it.isNotBlank() },
        // Sum hit counts across all redaction modules so the pill shows the
        // total number of spans replaced, not just the rule count.
        redactionCount = redactionHits.sumOf { it.count },
    )
}

private fun Decision.toStatus(): DecisionStatus = when (this) {
    Decision.APPROVED, Decision.ALWAYS_ALLOWED -> DecisionStatus.APPROVED
    Decision.AUTO_APPROVED -> DecisionStatus.AUTO_APPROVED
    Decision.DENIED, Decision.CANCELLED_BY_CLIENT -> DecisionStatus.DENIED
    Decision.AUTO_DENIED -> DecisionStatus.AUTO_DENIED
    Decision.RESOLVED_EXTERNALLY -> DecisionStatus.RESOLVED_EXT
    Decision.PROTECTION_BLOCKED -> DecisionStatus.PROTECTION_BLOCKED
    Decision.PROTECTION_LOGGED, Decision.PROTECTION_OVERRIDDEN -> DecisionStatus.PROTECTION_LOGGED
    Decision.TIMEOUT -> DecisionStatus.TIMEOUT
}

private fun summaryText(request: ApprovalRequest): String {
    val name = request.hookInput.toolName
    val input = request.hookInput.toolInput
    return when {
        name.equals("Bash", true) -> input["command"].asStringOrNull() ?: name
        name.equals("Edit", true) || name.equals("Write", true) || name.equals("Read", true) ->
            input["file_path"].asStringOrNull() ?: name
        name.equals("WebFetch", true) -> input["url"].asStringOrNull() ?: name
        name.equals("WebSearch", true) -> input["query"].asStringOrNull() ?: name
        name.equals("Grep", true) || name.equals("Glob", true) ->
            input["pattern"].asStringOrNull() ?: name
        request.toolType == ToolType.ASK_USER_QUESTION ->
            input["question"].asStringOrNull()?.take(120) ?: "Question"
        request.toolType == ToolType.PLAN -> "Plan"
        else -> name
    }
}

private fun prettyProtectionModule(module: String): String =
    module.split('_', ' ').filter { it.isNotEmpty() }
        .joinToString(" ") { it.lowercase() }

private fun formatDecisionDuration(duration: Duration): String? {
    if (duration.isNegative() || duration < 100.milliseconds) return null
    return when {
        duration < 1.minutes -> "${duration.inWholeSeconds}s"
        duration < 1.hours -> "${duration.inWholeMinutes}m"
        duration < 1.days -> "${duration.inWholeHours}h"
        else -> "${duration.inWholeDays}d"
    }
}

private fun relativeTimestamp(instant: Instant, now: Instant): String {
    val elapsed = now - instant
    return when {
        elapsed < 5.seconds -> "just now"
        elapsed < 1.minutes -> "${elapsed.inWholeSeconds}s ago"
        elapsed < 1.hours -> "${elapsed.inWholeMinutes}m ago"
        elapsed < 1.days -> "${elapsed.inWholeHours}h ago"
        else -> "${elapsed.inWholeDays}d ago"
    }
}
