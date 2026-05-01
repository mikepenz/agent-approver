package com.mikepenz.agentbelay.ui.approvals

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.PlainTooltip
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TooltipBox
import androidx.compose.material3.TooltipAnchorPosition
import androidx.compose.material3.TooltipDefaults
import androidx.compose.material3.rememberTooltipState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mikepenz.agentbelay.model.ApprovalRequest
import com.mikepenz.agentbelay.model.HookInput
import com.mikepenz.agentbelay.model.RiskAnalysis
import com.mikepenz.agentbelay.model.Source
import com.mikepenz.agentbelay.model.SpecialToolParser
import com.mikepenz.agentbelay.model.ToolType
import com.mikepenz.agentbelay.risk.RiskAutoActionOrchestrator.Companion.AUTO_DENY_COUNTDOWN
import com.mikepenz.agentbelay.ui.detail.PopOutSpec
import com.mikepenz.agentbelay.ui.theme.PreviewScaffold
import com.mikepenz.agentbelay.ui.theme.RiskCritical
import com.mikepenz.agentbelay.ui.theme.RiskSafe
import com.mikepenz.agentbelay.ui.theme.riskColor
import com.mikepenz.agentbelay.ui.theme.riskLabel
import com.mikepenz.agentbelay.ui.theme.sourceColor
import com.mikepenz.agentbelay.ui.theme.sourceLabel
import com.mikepenz.agentbelay.ui.theme.toolColor
import kotlinx.coroutines.delay
import kotlin.time.Clock
import kotlin.time.Instant
import kotlinx.serialization.json.JsonElement

enum class RiskStatus { IDLE, ANALYZING, COMPLETED, ERROR }

@Composable
fun ApprovalCard(
    request: ApprovalRequest,
    riskResult: RiskAnalysis?,
    riskStatus: RiskStatus,
    riskError: String?,
    timeoutSeconds: Int,
    onApprove: (String?) -> Unit,
    onDeny: (String) -> Unit,
    onAlwaysAllow: () -> Unit,
    onApproveWithInput: (Map<String, JsonElement>) -> Unit,
    onDismiss: () -> Unit,
    autoDenyActive: Boolean,
    onCancelAutoDeny: () -> Unit,
    autoDenyCountdownSeconds: Int = 15,
    onUserInteraction: () -> Unit = {},
    awayMode: Boolean = false,
    prominentAlwaysAllow: Boolean = false,
    now: Instant = Clock.System.now(),
    onPopOut: ((PopOutSpec) -> Unit)? = null,
) {
    val borderColor by animateColorAsState(
        targetValue = when {
            riskResult != null -> riskColor(riskResult.risk)
            else -> Color(0xFF333333)
        },
        animationSpec = tween(500),
    )
    val shadowElevation by animateDpAsState(
        targetValue = if (riskResult != null) 4.dp else 0.dp,
        animationSpec = tween(500),
    )

    // Auto-approve flash
    var flashGreen by remember { mutableStateOf(false) }
    LaunchedEffect(riskResult) {
        if (riskResult?.risk == 1) {
            flashGreen = true
            delay(500)
            flashGreen = false
        }
    }

    val effectiveBorder = if (flashGreen) RiskSafe else borderColor

    // Parse special tool data
    val questionData = remember(request) {
        if (request.toolType == ToolType.ASK_USER_QUESTION) {
            SpecialToolParser.parseUserQuestion(request.hookInput.toolInput)
        } else null
    }
    val planData = remember(request) {
        if (request.toolType == ToolType.PLAN) {
            SpecialToolParser.parsePlanReview(request.hookInput.toolInput)
        } else null
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        border = BorderStroke(1.dp, effectiveBorder),
        elevation = CardDefaults.cardElevation(defaultElevation = shadowElevation),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
    ) {
        // Track remaining seconds for both progress bar and text display
        var remainingSeconds by remember { mutableIntStateOf(timeoutSeconds) }

        Box {
            Column {
                // Full-width progress bar at top of card (only for DEFAULT, hidden in away mode)
                if (request.toolType == ToolType.DEFAULT && !awayMode) {
                    TimerProgressBar(
                        timeoutSeconds = timeoutSeconds,
                        remainingSeconds = remainingSeconds,
                        onRemainingSecondsChanged = { remainingSeconds = it },
                        onTimeout = { onDeny("Request timed out") },
                    )
                }

                Column(modifier = Modifier.padding(12.dp)) {
                    // Tool badge + Source badge + Risk badge + Timer row
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            ToolBadge(toolName = request.hookInput.toolName, toolType = request.toolType)
                            SourceBadge(source = request.source)
                        }
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            RiskBadge(riskResult = riskResult, riskStatus = riskStatus, riskError = riskError)
                            if (request.toolType == ToolType.DEFAULT) {
                                if (awayMode) {
                                    ElapsedTimeBadge(request.timestamp, now)
                                } else {
                                    val fraction = remainingSeconds.toFloat() / timeoutSeconds
                                    val color = if (fraction < 0.2f) Color(0xFFF44336) else MaterialTheme.colorScheme.onSurfaceVariant
                                    Text(
                                        text = "${remainingSeconds}s",
                                        fontSize = 10.sp,
                                        color = color,
                                        fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace,
                                    )
                                }
                            }
                        }
                    }

                    Spacer(Modifier.height(8.dp))

                    // Delegate to specific card content
                    when {
                        request.toolType == ToolType.ASK_USER_QUESTION && questionData != null ->
                            AskUserQuestionCard(
                                request = request,
                                questionData = questionData,
                                onApproveWithInput = onApproveWithInput,
                                onDismiss = onDismiss,
                            )

                        request.toolType == ToolType.PLAN && planData != null ->
                            PlanCard(
                                request = request,
                                planData = planData,
                                onApprove = onApprove,
                                onDeny = onDeny,
                                onPopOut = onPopOut,
                            )

                        else -> {
                            val popOut = remember(request) {
                                toolPopOutContent(request.hookInput.toolName, request.hookInput.toolInput)
                            }
                            DefaultCard(
                                request = request,
                                onApprove = onApprove,
                                onDeny = onDeny,
                                onAlwaysAllow = onAlwaysAllow,
                                onUserInteraction = onUserInteraction,
                                prominentAlwaysAllow = prominentAlwaysAllow,
                                onPopOut = onPopOut,
                                popOutContent = popOut,
                            ) {
                                ToolContentSummary(
                                    toolName = request.hookInput.toolName,
                                    toolInput = request.hookInput.toolInput,
                                    cwd = request.hookInput.cwd,
                                )
                            }
                        }
                    }
                }
            }

            // Auto-deny overlay
            if (autoDenyActive) {
                AutoDenyOverlay(
                    onCancel = onCancelAutoDeny,
                    countdownMillis = autoDenyCountdownSeconds.coerceAtLeast(1) * 1_000,
                )
            }
        }
    }
}

@Composable
fun TimerProgressBar(
    timeoutSeconds: Int,
    remainingSeconds: Int,
    onRemainingSecondsChanged: (Int) -> Unit,
    onTimeout: () -> Unit,
) {
    var progress by remember { mutableStateOf(1f) }
    val animatedProgress by animateFloatAsState(
        targetValue = progress,
        animationSpec = tween(durationMillis = timeoutSeconds * 1000, easing = LinearEasing),
    )

    LaunchedEffect(timeoutSeconds) {
        progress = 0f
        var remaining = remainingSeconds
        while (remaining > 0) {
            delay(1000)
            remaining--
            onRemainingSecondsChanged(remaining)
        }
        onTimeout()
    }

    val color = if (animatedProgress < 0.2f) Color(0xFFF44336) else MaterialTheme.colorScheme.primary
    LinearProgressIndicator(
        progress = { animatedProgress },
        modifier = Modifier.fillMaxWidth().height(3.dp),
        color = color,
        trackColor = Color.Transparent,
    )
}

@Composable
fun ElapsedTimeBadge(timestamp: Instant, now: Instant) {
    val elapsedSeconds = (now - timestamp).inWholeSeconds.toInt().coerceAtLeast(0)

    val text = when {
        elapsedSeconds < 60 -> "${elapsedSeconds}s"
        elapsedSeconds < 3600 -> "${elapsedSeconds / 60}m ${elapsedSeconds % 60}s"
        else -> "${elapsedSeconds / 3600}h ${(elapsedSeconds % 3600) / 60}m"
    }

    Text(
        text = text,
        fontSize = 10.sp,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace,
    )
}

@Composable
fun ToolBadge(toolName: String, toolType: ToolType) {
    val color = toolColor(toolName, toolType)
    Surface(
        shape = RoundedCornerShape(4.dp),
        color = color.copy(alpha = 0.2f),
        border = BorderStroke(1.dp, color.copy(alpha = 0.5f)),
    ) {
        Text(
            text = toolName,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
            color = color,
            fontSize = 11.sp,
            style = MaterialTheme.typography.labelSmall,
        )
    }
}

@Composable
fun SourceBadge(source: Source) {
    Surface(
        shape = RoundedCornerShape(4.dp),
        color = sourceColor(source).copy(alpha = 0.15f),
    ) {
        Text(
            text = sourceLabel(source),
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
            style = MaterialTheme.typography.labelSmall,
            color = sourceColor(source),
            fontSize = 10.sp,
        )
    }
}

@Composable
@OptIn(ExperimentalMaterial3Api::class)
fun RiskBadge(riskResult: RiskAnalysis?, riskStatus: RiskStatus, riskError: String?) {
    when {
        riskStatus == RiskStatus.ANALYZING -> {
            Row(verticalAlignment = Alignment.CenterVertically) {
                CircularProgressIndicator(
                    modifier = Modifier.size(12.dp),
                    strokeWidth = 1.5.dp,
                    color = MaterialTheme.colorScheme.primary,
                )
                Spacer(Modifier.width(4.dp))
                Text("Analyzing...", fontSize = 10.sp, color = Color.Gray)
            }
        }

        riskStatus == RiskStatus.ERROR -> {
            Surface(
                shape = RoundedCornerShape(4.dp),
                color = RiskCritical.copy(alpha = 0.2f),
            ) {
                Text(
                    text = riskError ?: "Error",
                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                    color = RiskCritical,
                    fontSize = 10.sp,
                )
            }
        }

        riskResult != null -> {
            val color = riskColor(riskResult.risk)
            val label = riskResult.label.ifBlank { riskLabel(riskResult.risk) }
            val tooltipText = buildString {
                append("$label")
                if (riskResult.message.isNotBlank()) append(" — ${riskResult.message}")
            }
            val tooltipState = rememberTooltipState(isPersistent = true)
            TooltipBox(
                positionProvider = TooltipDefaults.rememberTooltipPositionProvider(positioning = TooltipAnchorPosition.Above),
                tooltip = {
                    PlainTooltip {
                        Text(tooltipText)
                    }
                },
                state = tooltipState,
            ) {
                Surface(
                    shape = RoundedCornerShape(4.dp),
                    color = color.copy(alpha = 0.2f),
                ) {
                    Text(
                        text = "Risk ${riskResult.risk}",
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                        color = color,
                        fontSize = 10.sp,
                    )
                }
            }
        }
    }
}

@Composable
private fun AutoDenyOverlay(
    onCancel: () -> Unit,
    countdownMillis: Int = AUTO_DENY_COUNTDOWN.inWholeMilliseconds.toInt(),
) {
    var progress by remember { mutableStateOf(1f) }
    val animatedProgress by animateFloatAsState(
        targetValue = progress,
        animationSpec = tween(durationMillis = countdownMillis, easing = LinearEasing),
    )

    LaunchedEffect(Unit) {
        progress = 0f
    }

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = Color.Black.copy(alpha = 0.7f),
    ) {
        Column(
            modifier = Modifier.fillMaxSize().padding(16.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text("Auto-denying critical risk...", color = RiskCritical, fontSize = 14.sp)
            Spacer(Modifier.height(8.dp))
            LinearProgressIndicator(
                progress = { animatedProgress },
                modifier = Modifier.fillMaxWidth(),
                color = RiskCritical,
            )
            Spacer(Modifier.height(8.dp))
            OutlinedButton(onClick = onCancel) {
                Text("Cancel")
            }
        }
    }
}

// -- Previews --

private fun sampleRequest(
    toolName: String = "Bash",
    toolType: ToolType = ToolType.DEFAULT,
    toolInput: Map<String, JsonElement> = mapOf("command" to kotlinx.serialization.json.JsonPrimitive("ls -la")),
) = ApprovalRequest(
    id = "preview-1",
    source = Source.CLAUDE_CODE,
    toolType = toolType,
    hookInput = HookInput(
        sessionId = "session-abc",
        toolName = toolName,
        toolInput = toolInput,
        cwd = "/home/user/project",
    ),
    timestamp = Clock.System.now(),
    rawRequestJson = "{}",
)

@Preview(widthDp = 404, heightDp = 320)
@Composable
private fun PreviewAnalyzing() {
    PreviewScaffold {
        androidx.compose.foundation.layout.Box(
            modifier = Modifier.fillMaxSize().padding(20.dp),
            contentAlignment = Alignment.TopCenter,
        ) {
          androidx.compose.foundation.layout.Box(modifier = Modifier.width(380.dp)) {
            ApprovalCard(
                request = sampleRequest(),
                riskResult = null,
                riskStatus = RiskStatus.ANALYZING,
                riskError = null,
                timeoutSeconds = 120,
                onApprove = {}, onDeny = {}, onAlwaysAllow = {}, onApproveWithInput = {}, onDismiss = {},
                autoDenyActive = false, onCancelAutoDeny = {},
            )
          }
        }
    }
}

@Preview(widthDp = 404, heightDp = 360)
@Composable
private fun PreviewRisk1() {
    PreviewScaffold {
        androidx.compose.foundation.layout.Box(
            modifier = Modifier.fillMaxSize().padding(20.dp),
            contentAlignment = Alignment.TopCenter,
        ) {
          androidx.compose.foundation.layout.Box(modifier = Modifier.width(380.dp)) {
            ApprovalCard(
                request = sampleRequest(),
                riskResult = RiskAnalysis(risk = 1, label = "Safe", message = "Read-only command"),
                riskStatus = RiskStatus.COMPLETED,
                riskError = null,
                timeoutSeconds = 120,
                onApprove = {}, onDeny = {}, onAlwaysAllow = {}, onApproveWithInput = {}, onDismiss = {},
                autoDenyActive = false, onCancelAutoDeny = {},
            )
          }
        }
    }
}

@Preview(widthDp = 404, heightDp = 420)
@Composable
private fun PreviewRisk3() {
    PreviewScaffold {
        androidx.compose.foundation.layout.Box(
            modifier = Modifier.fillMaxSize().padding(20.dp),
            contentAlignment = Alignment.TopCenter,
        ) {
          androidx.compose.foundation.layout.Box(modifier = Modifier.width(380.dp)) {
            ApprovalCard(
                request = sampleRequest(
                    toolName = "Edit", toolInput = mapOf(
                        "file_path" to kotlinx.serialization.json.JsonPrimitive("/src/main.kt"),
                        "old_string" to kotlinx.serialization.json.JsonPrimitive("foo"),
                        "new_string" to kotlinx.serialization.json.JsonPrimitive("bar"),
                    )
                ),
                riskResult = RiskAnalysis(risk = 3, label = "Moderate", message = "Modifies source file"),
                riskStatus = RiskStatus.COMPLETED,
                riskError = null,
                timeoutSeconds = 120,
                onApprove = {}, onDeny = {}, onAlwaysAllow = {}, onApproveWithInput = {}, onDismiss = {},
                autoDenyActive = false, onCancelAutoDeny = {},
            )
          }
        }
    }
}

@Preview(widthDp = 404, heightDp = 420)
@Composable
private fun PreviewRisk5() {
    PreviewScaffold {
        androidx.compose.foundation.layout.Box(
            modifier = Modifier.fillMaxSize().padding(20.dp),
            contentAlignment = Alignment.TopCenter,
        ) {
          androidx.compose.foundation.layout.Box(modifier = Modifier.width(380.dp)) {
            ApprovalCard(
                request = sampleRequest(
                    toolName = "Bash", toolInput = mapOf(
                        "command" to kotlinx.serialization.json.JsonPrimitive("rm -rf /"),
                    )
                ),
                riskResult = RiskAnalysis(risk = 5, label = "Critical", message = "Destructive system command"),
                riskStatus = RiskStatus.COMPLETED,
                riskError = null,
                timeoutSeconds = 120,
                onApprove = {}, onDeny = {}, onAlwaysAllow = {}, onApproveWithInput = {}, onDismiss = {},
                autoDenyActive = true, onCancelAutoDeny = {},
            )
          }
        }
    }
}

@Preview(widthDp = 404, heightDp = 420)
@Composable
private fun PreviewRisk2Copilot() {
    PreviewScaffold {
        androidx.compose.foundation.layout.Box(
            modifier = Modifier.fillMaxSize().padding(20.dp),
            contentAlignment = Alignment.TopCenter,
        ) {
          androidx.compose.foundation.layout.Box(modifier = Modifier.width(380.dp)) {
            ApprovalCard(
                request = sampleRequest(
                    toolName = "Write",
                    toolInput = mapOf(
                        "file_path" to kotlinx.serialization.json.JsonPrimitive("/src/config.json"),
                        "content" to kotlinx.serialization.json.JsonPrimitive("{\n  \"debug\": true\n}"),
                    ),
                ).copy(source = Source.COPILOT),
                riskResult = RiskAnalysis(risk = 2, label = "Low", message = "Writing config file"),
                riskStatus = RiskStatus.COMPLETED,
                riskError = null,
                timeoutSeconds = 120,
                onApprove = {}, onDeny = {}, onAlwaysAllow = {}, onApproveWithInput = {}, onDismiss = {},
                autoDenyActive = false, onCancelAutoDeny = {},
            )
          }
        }
    }
}

@Preview(widthDp = 404, heightDp = 360)
@Composable
private fun PreviewRiskError() {
    PreviewScaffold {
        androidx.compose.foundation.layout.Box(
            modifier = Modifier.fillMaxSize().padding(20.dp),
            contentAlignment = Alignment.TopCenter,
        ) {
          androidx.compose.foundation.layout.Box(modifier = Modifier.width(380.dp)) {
            ApprovalCard(
                request = sampleRequest(),
                riskResult = null,
                riskStatus = RiskStatus.ERROR,
                riskError = "timeout",
                timeoutSeconds = 120,
                onApprove = {}, onDeny = {}, onAlwaysAllow = {}, onApproveWithInput = {}, onDismiss = {},
                autoDenyActive = false, onCancelAutoDeny = {},
            )
          }
        }
    }
}

@Preview(widthDp = 404, heightDp = 420)
@Composable
private fun PreviewRisk3Light() {
    PreviewScaffold(themeMode = com.mikepenz.agentbelay.model.ThemeMode.LIGHT) {
        androidx.compose.foundation.layout.Box(
            modifier = Modifier.fillMaxSize().padding(20.dp),
            contentAlignment = Alignment.TopCenter,
        ) {
            androidx.compose.foundation.layout.Box(modifier = Modifier.width(380.dp)) {
                ApprovalCard(
                    request = sampleRequest(
                        toolName = "Edit", toolInput = mapOf(
                            "file_path" to kotlinx.serialization.json.JsonPrimitive("/src/main.kt"),
                            "old_string" to kotlinx.serialization.json.JsonPrimitive("foo"),
                            "new_string" to kotlinx.serialization.json.JsonPrimitive("bar"),
                        )
                    ),
                    riskResult = RiskAnalysis(risk = 3, label = "Moderate", message = "Modifies source file"),
                    riskStatus = RiskStatus.COMPLETED,
                    riskError = null,
                    timeoutSeconds = 120,
                    onApprove = {}, onDeny = {}, onAlwaysAllow = {}, onApproveWithInput = {}, onDismiss = {},
                    autoDenyActive = false, onCancelAutoDeny = {},
                )
            }
        }
    }
}
