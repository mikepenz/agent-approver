package com.mikepenz.agentapprover.ui.history

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.desktop.ui.tooling.preview.Preview
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mikepenz.agentapprover.model.*
import com.mikepenz.agentapprover.ui.approvals.ToolBadge
import com.mikepenz.agentapprover.ui.icons.LucideCopy
import com.mikepenz.agentapprover.ui.theme.*
import kotlinx.serialization.json.Json
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonPrimitive
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds

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
    Decision.CANCELLED_BY_CLIENT -> Color(0xFF9E9E9E)
}

private fun decisionLabel(decision: Decision): String = when (decision) {
    Decision.APPROVED -> "Approved"
    Decision.DENIED -> "Denied"
    Decision.TIMEOUT -> "Timeout"
    Decision.AUTO_APPROVED -> "Auto-Approved"
    Decision.AUTO_DENIED -> "Auto-Denied"
    Decision.CANCELLED_BY_CLIENT -> "Cancelled"
}

private fun summaryText(request: ApprovalRequest): String = when {
    request.hookInput.toolName.equals("Bash", ignoreCase = true) ->
        request.hookInput.toolInput["command"]?.jsonPrimitive?.content ?: request.hookInput.toolName

    request.hookInput.toolName.equals("Edit", ignoreCase = true) ||
        request.hookInput.toolName.equals("Write", ignoreCase = true) ->
        request.hookInput.toolInput["file_path"]?.jsonPrimitive?.content ?: request.hookInput.toolName

    request.toolType == ToolType.ASK_USER_QUESTION ->
        request.hookInput.toolInput["question"]?.jsonPrimitive?.content?.take(80) ?: "Question"

    request.toolType == ToolType.PLAN -> "Plan"
    else -> request.hookInput.toolName
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

@Composable
fun HistoryRow(
    result: ApprovalResult,
    isExpanded: Boolean,
    onToggleExpand: () -> Unit,
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        onClick = onToggleExpand,
        shape = RoundedCornerShape(8.dp),
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = if (isExpanded) 2.dp else 0.dp,
    ) {
        Column(modifier = Modifier.padding(8.dp)) {
            // Top line: tool badge + summary
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth(),
            ) {
                ToolBadge(toolName = result.request.hookInput.toolName, toolType = result.request.toolType)
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
                            text = "Risk ${result.riskAnalysis.risk}",
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

            // Expanded detail
            AnimatedVisibility(visible = isExpanded) {
                val clipboardManager = androidx.compose.ui.platform.LocalClipboardManager.current
                val fullText = buildString {
                    if (result.riskAnalysis != null) {
                        appendLine("Risk Assessment: Level ${result.riskAnalysis.risk} (${riskLabel(result.riskAnalysis.risk)}) — ${result.riskAnalysis.message}")
                    }
                    if (result.request.hookInput.cwd.isNotBlank()) {
                        appendLine("Working Directory: ${result.request.hookInput.cwd}")
                    }
                    if (result.feedback != null) {
                        appendLine("Feedback: ${result.feedback}")
                    }
                    appendLine("Request:\n${prettyPrintJson(result.request.rawRequestJson)}")
                    if (result.rawResponseJson != null) {
                        appendLine("Response:\n${prettyPrintJson(result.rawResponseJson)}")
                    }
                }

                Column(
                    modifier = Modifier
                        .padding(top = 8.dp)
                        .fillMaxWidth()
                        .background(Color(0xFF0D0D0D), RoundedCornerShape(6.dp))
                        .padding(8.dp),
                ) {
                    // Copy button row
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.End,
                    ) {
                        IconButton(
                            onClick = { clipboardManager.setText(androidx.compose.ui.text.AnnotatedString(fullText)) },
                            modifier = Modifier.size(24.dp),
                        ) {
                            Icon(
                                imageVector = LucideCopy,
                                contentDescription = "Copy to clipboard",
                                modifier = Modifier.size(14.dp),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }

                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = 280.dp)
                            .verticalScroll(rememberScrollState()),
                    ) {
                    SelectionContainer {
                        Column {
                            if (result.riskAnalysis != null) {
                                Text(
                                    text = "Risk Assessment:",
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
                            if (result.feedback != null) {
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
