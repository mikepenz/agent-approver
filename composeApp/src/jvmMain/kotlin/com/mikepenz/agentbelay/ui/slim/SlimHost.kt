package com.mikepenz.agentbelay.ui.slim

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import com.mikepenz.agentbelay.model.ApprovalRequest
import com.mikepenz.agentbelay.model.ToolType
import com.mikepenz.agentbelay.ui.approvals.ApprovalsViewModel
import dev.zacsweers.metrox.viewmodel.metroViewModel
import kotlin.time.Clock
import kotlin.time.Instant

/**
 * Host that binds a live [ApprovalsViewModel] to the chromeless [SlimContent].
 * Rendered inside the main app window when it has been resized below the
 * full-UI threshold — provides the same approve/deny actions as the full
 * Approvals screen but compacted into the 340-wide slim layout.
 */
@Composable
fun SlimHost(
    onExpand: () -> Unit,
    onPopOut: ((com.mikepenz.agentbelay.ui.detail.PopOutSpec) -> Unit)? = null,
    modifier: Modifier = Modifier,
) {
    val viewModel: ApprovalsViewModel = metroViewModel()
    val pending by viewModel.pendingApprovals.collectAsState()
    val riskResults by viewModel.riskResults.collectAsState()

    val slimItems = pending.map { approval ->
        val riskResult = riskResults[approval.id]
        SlimItem(
            id = approval.id,
            source = approval.source,
            tool = approval.hookInput.toolName,
            toolType = approval.toolType,
            summary = approval.summarize(),
            time = approval.timestamp.humanize(),
            risk = riskResult?.risk,
            riskAssessment = riskResult?.message,
            riskVia = riskResult?.source,
            request = approval,
        )
    }

    SlimContent(
        items = slimItems,
        onResolve = { id, action ->
            when (action) {
                SlimAction.Allow -> viewModel.onApprove(id, feedback = null)
                SlimAction.AllowSession -> viewModel.onAlwaysAllow(id)
                SlimAction.Deny -> viewModel.onDeny(id, feedback = "")
                SlimAction.AskAnotherAgent -> viewModel.onDismiss(id)
            }
        },
        onExpand = onExpand,
        onApproveWithInput = { id, updated -> viewModel.onApproveWithInput(id, updated) },
        onPopOut = onPopOut,
        modifier = modifier,
    )
}

private fun ApprovalRequest.summarize(): String {
    val input = hookInput.toolInput
    // Tool-type specific summaries — the generic key scan below cannot surface
    // anything useful for structured inputs like AskUserQuestion's `questions`
    // array or Plan's `plan` body, which caused them to collapse to the tool
    // name in the slim queue.
    when (toolType) {
        ToolType.ASK_USER_QUESTION ->
            com.mikepenz.agentbelay.model.SpecialToolParser.parseUserQuestion(input)
                ?.questions?.firstOrNull()
                ?.let { q -> q.question.ifBlank { q.header } }
                ?.takeIf { it.isNotBlank() }
                ?.let { return it }

        ToolType.PLAN ->
            com.mikepenz.agentbelay.model.SpecialToolParser.parsePlanReview(input)
                ?.plan?.lineSequence()
                ?.map { it.trim().trimStart('#', ' ') }
                ?.firstOrNull { it.isNotBlank() }
                ?.let { return it }

        ToolType.DEFAULT -> Unit
    }
    val candidateKeys = listOf("command", "file_path", "path", "url", "pattern", "prompt")
    for (key in candidateKeys) {
        val value = input[key]?.toString()?.trim('"')?.takeIf { it.isNotBlank() }
        if (value != null) return value
    }
    return hookInput.toolName
}

private fun Instant.humanize(): String {
    val diffSeconds = (Clock.System.now() - this).inWholeSeconds
    return when {
        diffSeconds < 60 -> "just now"
        diffSeconds < 3600 -> "${diffSeconds / 60}m ago"
        diffSeconds < 86_400 -> "${diffSeconds / 3600}h ago"
        else -> "${diffSeconds / 86_400}d ago"
    }
}
