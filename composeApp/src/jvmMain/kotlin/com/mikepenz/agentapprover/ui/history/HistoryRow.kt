package com.mikepenz.agentapprover.ui.history

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mikepenz.agentapprover.model.ApprovalRequest
import com.mikepenz.agentapprover.model.ApprovalResult
import com.mikepenz.agentapprover.model.Decision
import com.mikepenz.agentapprover.model.HookInput
import com.mikepenz.agentapprover.model.RiskAnalysis
import com.mikepenz.agentapprover.model.Source
import com.mikepenz.agentapprover.model.ToolType
import com.mikepenz.agentapprover.ui.approvals.SourceBadge
import com.mikepenz.agentapprover.ui.approvals.ToolBadge
import com.mikepenz.agentapprover.ui.approvals.ToolContentSummary
import com.mikepenz.agentapprover.ui.icons.LucideCopy
import com.mikepenz.agentapprover.ui.theme.AgentApproverTheme
import com.mikepenz.agentapprover.ui.theme.riskColor
import com.mikepenz.agentapprover.ui.theme.riskLabel
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonPrimitive
import kotlin.time.Duration
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.minutes

private val prettyJson = Json { prettyPrint = true }

private fun prettyPrintJson(raw: String): String {
    return try {
        val element = prettyJson.parseToJsonElement(raw)
        prettyJson.encodeToString(kotlinx.serialization.json.JsonElement.serializer(), element)
    } catch (_: Exception) {
        raw
    }
}

fun decisionColor(decision: Decision): Color = when (decision) {
    Decision.APPROVED -> Color(0xFF4CAF50)
    Decision.DENIED -> Color(0xFFF44336)
    Decision.TIMEOUT -> Color(0xFFFF9800)
    Decision.AUTO_APPROVED -> Color(0xFF2196F3)
    Decision.AUTO_DENIED -> Color(0xFFB71C1C)
    Decision.ALWAYS_ALLOWED -> Color(0xFF00ACC1)
    Decision.CANCELLED_BY_CLIENT -> Color(0xFF9E9E9E)
    Decision.RESOLVED_EXTERNALLY -> Color(0xFF78909C)
    Decision.PROTECTION_BLOCKED -> Color(0xFFF44336)
    Decision.PROTECTION_LOGGED -> Color(0xFF2196F3)
    Decision.PROTECTION_OVERRIDDEN -> Color(0xFFFF9800)
}

private fun decisionLabel(decision: Decision): String = when (decision) {
    Decision.APPROVED -> "Approved"
    Decision.DENIED -> "Denied"
    Decision.TIMEOUT -> "Timeout"
    Decision.AUTO_APPROVED -> "Auto-Approved"
    Decision.AUTO_DENIED -> "Auto-Denied"
    Decision.ALWAYS_ALLOWED -> "Always Allowed"
    Decision.CANCELLED_BY_CLIENT -> "Cancelled"
    Decision.RESOLVED_EXTERNALLY -> "Resolved Externally"
    Decision.PROTECTION_BLOCKED -> "Protection Blocked"
    Decision.PROTECTION_LOGGED -> "Protection Logged"
    Decision.PROTECTION_OVERRIDDEN -> "Protection Overridden"
}

private fun summaryText(request: ApprovalRequest): String = when {
    request.hookInput.toolName.equals("Bash", ignoreCase = true) ->
        request.hookInput.toolInput["command"]?.jsonPrimitive?.content ?: request.hookInput.toolName

    request.hookInput.toolName.equals("Edit", ignoreCase = true) ||
            request.hookInput.toolName.equals("Write", ignoreCase = true) ||
            request.hookInput.toolName.equals("Read", ignoreCase = true) ->
        request.hookInput.toolInput["file_path"]?.jsonPrimitive?.content ?: request.hookInput.toolName

    request.hookInput.toolName.equals("WebFetch", ignoreCase = true) ->
        request.hookInput.toolInput["url"]?.jsonPrimitive?.content ?: request.hookInput.toolName

    request.hookInput.toolName.equals("Grep", ignoreCase = true) ||
            request.hookInput.toolName.equals("Glob", ignoreCase = true) ->
        request.hookInput.toolInput["pattern"]?.jsonPrimitive?.content ?: request.hookInput.toolName

    request.toolType == ToolType.ASK_USER_QUESTION ->
        request.hookInput.toolInput["question"]?.jsonPrimitive?.content?.take(80) ?: "Question"

    request.toolType == ToolType.PLAN -> "Plan"
    else -> request.hookInput.toolName
}

/**
 * Compact human-readable form of how long a request was pending before being
 * resolved. Returns null when the duration isn't meaningful — protection rules
 * resolve in microseconds and would just clutter the row.
 */
private fun formatDecisionDuration(duration: Duration): String? {
    if (duration < 500.milliseconds) return null
    return when {
        duration < 1.minutes -> "${duration.inWholeSeconds}s"
        duration < 1.hours -> "${duration.inWholeMinutes}m"
        duration < 24.hours -> "${duration.inWholeHours}h"
        else -> "${duration.inWholeDays}d"
    }
}

private fun relativeTimestamp(instant: Instant): String {
    val elapsed = Clock.System.now() - instant
    return when {
        elapsed < 1.minutes -> "just now"
        elapsed < 1.hours -> "${elapsed.inWholeMinutes}m ago"
        elapsed < 24.hours -> "${elapsed.inWholeHours}h ago"
        else -> "${elapsed.inWholeDays}d ago"
    }
}

@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
fun HistoryRow(
    result: ApprovalResult,
    isExpanded: Boolean,
    onToggleExpand: () -> Unit,
    onReplay: ((ApprovalResult) -> Unit)? = null,
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(8.dp),
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = if (isExpanded) 2.dp else 0.dp,
    ) {
        Column {
            // Clickable header area — full width, rounded top corners
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(8.dp))
                    .combinedClickable(
                        onClick = onToggleExpand,
                        onLongClick = if (onReplay != null) {
                            { onReplay(result) }
                        } else null,
                    )
                    .padding(8.dp),
            ) {
            // Top line: tool badge + source badge + summary
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth(),
            ) {
                ToolBadge(toolName = result.request.hookInput.toolName, toolType = result.request.toolType)
                Spacer(Modifier.width(4.dp))
                SourceBadge(source = result.request.source)
                result.protectionModule?.let { module ->
                    Spacer(Modifier.width(4.dp))
                    Surface(
                        shape = MaterialTheme.shapes.small,
                        color = Color(0xFFF44336).copy(alpha = 0.15f),
                    ) {
                        Text(
                            text = module.replace('_', ' '),
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 1.dp),
                            style = MaterialTheme.typography.labelSmall,
                            color = Color(0xFFF44336),
                            fontSize = 9.sp,
                        )
                    }
                }
                Spacer(Modifier.width(6.dp))
                Text(
                    text = summaryText(result.request),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.8f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
            }

            // Agent info (no cwd in compact view)
            val agentType = result.request.hookInput.agentType
            if (agentType != null) {
                Text(
                    text = "Agent: $agentType",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }

            Spacer(Modifier.height(4.dp))

            // Bottom line: decision badge, risk badge, timestamp
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                val dColor = decisionColor(result.decision)
                Surface(
                    shape = RoundedCornerShape(4.dp),
                    color = dColor.copy(alpha = 0.2f),
                ) {
                    Text(
                        text = decisionLabel(result.decision),
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                        color = dColor,
                        fontSize = 10.sp,
                        style = MaterialTheme.typography.labelSmall,
                    )
                }

                if (result.riskAnalysis != null) {
                    val rColor = riskColor(result.riskAnalysis.risk)
                    Surface(
                        shape = RoundedCornerShape(4.dp),
                        color = rColor.copy(alpha = 0.2f),
                    ) {
                        Text(
                            text = buildString {
                                append("Risk ${result.riskAnalysis.risk}")
                                if (result.riskAnalysis.source.isNotEmpty()) {
                                    append(" via ${result.riskAnalysis.source}")
                                }
                            },
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                            color = rColor,
                            fontSize = 10.sp,
                            style = MaterialTheme.typography.labelSmall,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }

                Spacer(Modifier.weight(1f))

                Text(
                    text = relativeTimestamp(result.decidedAt),
                    fontSize = 10.sp,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
                )
            }
            } // end clickable header

            // Expanded detail
            AnimatedVisibility(visible = isExpanded) {
                val clipboardManager = androidx.compose.ui.platform.LocalClipboardManager.current
                val fullText = buildString {
                    if (result.riskAnalysis != null) {
                        val sourceText = if (result.riskAnalysis.source.isNotEmpty()) " (via ${result.riskAnalysis.source})" else ""
                        appendLine("Risk Assessment$sourceText: Level ${result.riskAnalysis.risk} (${riskLabel(result.riskAnalysis.risk)}) — ${result.riskAnalysis.message}")
                    }
                    if (result.request.hookInput.cwd.isNotBlank()) {
                        appendLine("Working Directory: ${result.request.hookInput.cwd}")
                    }
                    if (!result.feedback.isNullOrBlank()) {
                        appendLine("Feedback: ${result.feedback}")
                    }
                    appendLine("Request:\n${prettyPrintJson(result.request.rawRequestJson)}")
                    if (result.rawResponseJson != null) {
                        appendLine("Response:\n${prettyPrintJson(result.rawResponseJson)}")
                    }
                }

                Box(
                    modifier = Modifier
                        .padding(start = 8.dp, end = 8.dp, bottom = 8.dp)
                        .fillMaxWidth()
                        .background(Color(0xFF0D0D0D), RoundedCornerShape(6.dp))
                        .padding(8.dp)
                        .heightIn(max = 300.dp),
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .verticalScroll(rememberScrollState()),
                    ) {
                        SelectionContainer {
                            Column {
                                if (result.riskAnalysis != null) {
                                    Text(
                                        text = buildString {
                                            append("Risk Assessment")
                                            if (result.riskAnalysis.source.isNotEmpty()) {
                                                append(" (via ${result.riskAnalysis.source})")
                                            }
                                            append(":")
                                        },
                                        fontSize = 10.sp,
                                        color = Color.Gray,
                                        style = MaterialTheme.typography.labelSmall,
                                    )
                                    Text(
                                        text = "Level ${result.riskAnalysis.risk} (${riskLabel(result.riskAnalysis.risk)}) — ${result.riskAnalysis.message}",
                                        fontFamily = FontFamily.Monospace,
                                        fontSize = 10.sp,
                                        color = riskColor(result.riskAnalysis.risk),
                                    )
                                    Spacer(Modifier.height(8.dp))
                                }
                                if (result.request.hookInput.cwd.isNotBlank()) {
                                    Text(
                                        text = "Working Directory:",
                                        fontSize = 10.sp,
                                        color = Color.Gray,
                                        style = MaterialTheme.typography.labelSmall,
                                    )
                                    Text(
                                        text = result.request.hookInput.cwd,
                                        fontFamily = FontFamily.Monospace,
                                        fontSize = 10.sp,
                                        color = Color(0xFFCCCCCC),
                                    )
                                    Spacer(Modifier.height(8.dp))
                                }
                                val decisionDuration = formatDecisionDuration(
                                    result.decidedAt - result.request.timestamp,
                                )
                                if (decisionDuration != null) {
                                    Text(
                                        text = "Time to decision:",
                                        fontSize = 10.sp,
                                        color = Color.Gray,
                                        style = MaterialTheme.typography.labelSmall,
                                    )
                                    Text(
                                        text = "took $decisionDuration",
                                        fontFamily = FontFamily.Monospace,
                                        fontSize = 10.sp,
                                        color = Color(0xFFCCCCCC),
                                    )
                                    Spacer(Modifier.height(8.dp))
                                }
                                if (!result.feedback.isNullOrBlank()) {
                                    Text(
                                        text = "Feedback:",
                                        fontSize = 10.sp,
                                        color = Color.Gray,
                                        style = MaterialTheme.typography.labelSmall,
                                    )
                                    Text(
                                        text = result.feedback,
                                        fontFamily = FontFamily.Monospace,
                                        fontSize = 10.sp,
                                        color = Color(0xFFCCCCCC),
                                    )
                                    Spacer(Modifier.height(8.dp))
                                }
                                result.protectionDetail?.let { detail ->
                                    Text(
                                        text = "Protection: $detail",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                    Spacer(Modifier.height(8.dp))
                                }
                                Spacer(Modifier.height(8.dp))
                                ToolContentSummary(
                                    toolName = result.request.hookInput.toolName,
                                    toolInput = result.request.hookInput.toolInput,
                                    cwd = result.request.hookInput.cwd,
                                )
                                Spacer(Modifier.height(8.dp))
                                Text(
                                    text = "Request:",
                                    fontSize = 10.sp,
                                    color = Color.Gray,
                                    style = MaterialTheme.typography.labelSmall,
                                )
                                Text(
                                    text = prettyPrintJson(result.request.rawRequestJson),
                                    fontFamily = FontFamily.Monospace,
                                    fontSize = 10.sp,
                                    color = Color(0xFFCCCCCC),
                                )
                                if (result.rawResponseJson != null) {
                                    Spacer(Modifier.height(8.dp))
                                    Text(
                                        text = "Response:",
                                        fontSize = 10.sp,
                                        color = Color.Gray,
                                        style = MaterialTheme.typography.labelSmall,
                                    )
                                    Text(
                                        text = prettyPrintJson(result.rawResponseJson),
                                        fontFamily = FontFamily.Monospace,
                                        fontSize = 10.sp,
                                        color = Color(0xFFCCCCCC),
                                    )
                                }
                            }
                        }
                    }

                    // Overlapping copy button top-right
                    Surface(
                        onClick = { clipboardManager.setText(androidx.compose.ui.text.AnnotatedString(fullText)) },
                        modifier = Modifier.align(Alignment.TopEnd),
                        shape = RoundedCornerShape(4.dp),
                        color = Color(0xFF0D0D0D).copy(alpha = 0.85f),
                    ) {
                        Icon(
                            imageVector = LucideCopy,
                            contentDescription = "Copy to clipboard",
                            modifier = Modifier.padding(4.dp).size(14.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }
    }
}

// -- Previews --

private fun sampleResult(
    id: String = "h1",
    toolName: String = "Bash",
    toolType: ToolType = ToolType.DEFAULT,
    toolInput: JsonObject = JsonObject(mapOf("command" to JsonPrimitive("ls -la"))),
    decision: Decision = Decision.APPROVED,
    risk: Int? = 2,
) = ApprovalResult(
    request = ApprovalRequest(
        id = id,
        source = Source.CLAUDE_CODE,
        toolType = toolType,
        hookInput = HookInput(
            sessionId = "session-abc",
            toolName = toolName,
            toolInput = toolInput,
            cwd = "/home/user/project",
        ),
        timestamp = Clock.System.now(),
        rawRequestJson = """{"tool":"$toolName","input":$toolInput}""",
    ),
    decision = decision,
    riskAnalysis = risk?.let { RiskAnalysis(risk = it, label = "", message = "Sample risk") },
    rawResponseJson = """{"result":"ok"}""",
    decidedAt = Clock.System.now(),
)

@Preview
@Composable
private fun PreviewApprovedRow() {
    AgentApproverTheme {
        HistoryRow(
            result = sampleResult(),
            isExpanded = false,
            onToggleExpand = {},
        )
    }
}

@Preview
@Composable
private fun PreviewDeniedRow() {
    AgentApproverTheme {
        HistoryRow(
            result = sampleResult(decision = Decision.DENIED, risk = 4),
            isExpanded = false,
            onToggleExpand = {},
        )
    }
}

@Preview
@Composable
private fun PreviewExpandedRow() {
    AgentApproverTheme {
        HistoryRow(
            result = sampleResult(),
            isExpanded = true,
            onToggleExpand = {},
        )
    }
}
