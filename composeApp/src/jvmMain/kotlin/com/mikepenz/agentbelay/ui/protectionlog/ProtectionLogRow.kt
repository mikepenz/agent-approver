package com.mikepenz.agentbelay.ui.protectionlog

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mikepenz.agentbelay.model.ProtectionMode
import com.mikepenz.agentbelay.model.ToolType
import com.mikepenz.agentbelay.protection.ProtectionEngine
import com.mikepenz.agentbelay.protection.ProtectionEvaluation
import com.mikepenz.agentbelay.protection.RuleEvalResult
import com.mikepenz.agentbelay.state.PreToolUseEvent
import com.mikepenz.agentbelay.state.ProtectionLogConclusion
import com.mikepenz.agentbelay.ui.approvals.ToolBadge
import kotlinx.datetime.Clock
import com.mikepenz.agentbelay.util.asStringOrNull
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.minutes

private fun conclusionColor(conclusion: ProtectionLogConclusion): Color = when (conclusion) {
    ProtectionLogConclusion.PASS -> Color(0xFF9E9E9E)
    ProtectionLogConclusion.AUTO_BLOCKED -> Color(0xFFF44336)
    ProtectionLogConclusion.ASK -> Color(0xFFFF9800)
    ProtectionLogConclusion.LOGGED -> Color(0xFF2196F3)
}

private fun conclusionLabel(conclusion: ProtectionLogConclusion): String = when (conclusion) {
    ProtectionLogConclusion.PASS -> "Pass"
    ProtectionLogConclusion.AUTO_BLOCKED -> "Auto-blocked"
    ProtectionLogConclusion.ASK -> "Ask"
    ProtectionLogConclusion.LOGGED -> "Logged"
}

private fun modeColor(mode: ProtectionMode): Color = when (mode) {
    ProtectionMode.AUTO_BLOCK -> Color(0xFFF44336)
    ProtectionMode.ASK_AUTO_BLOCK -> Color(0xFFFF9800)
    ProtectionMode.ASK -> Color(0xFFFFC107)
    ProtectionMode.LOG_ONLY -> Color(0xFF2196F3)
    ProtectionMode.DISABLED -> Color(0xFF9E9E9E)
}

private fun summaryText(event: PreToolUseEvent): String {
    val input = event.request.hookInput
    return when {
        input.toolName.equals("Bash", ignoreCase = true) ->
            input.toolInput["command"].asStringOrNull() ?: input.toolName
        input.toolName.equals("Edit", ignoreCase = true) ||
                input.toolName.equals("Write", ignoreCase = true) ||
                input.toolName.equals("Read", ignoreCase = true) ->
            input.toolInput["file_path"].asStringOrNull() ?: input.toolName
        input.toolName.equals("WebFetch", ignoreCase = true) ->
            input.toolInput["url"].asStringOrNull() ?: input.toolName
        input.toolName.equals("WebSearch", ignoreCase = true) ->
            input.toolInput["query"].asStringOrNull() ?: input.toolName
        input.toolName.equals("Grep", ignoreCase = true) ||
                input.toolName.equals("Glob", ignoreCase = true) ->
            input.toolInput["pattern"].asStringOrNull() ?: input.toolName
        event.request.toolType == ToolType.ASK_USER_QUESTION ->
            input.toolInput["question"].asStringOrNull()?.take(80) ?: "Question"
        event.request.toolType == ToolType.PLAN -> "Plan"
        else -> input.toolName
    }
}

private fun relativeTimestamp(event: PreToolUseEvent): String {
    val elapsed = Clock.System.now() - event.timestamp
    return when {
        elapsed < 1.minutes -> "just now"
        elapsed < 1.hours -> "${elapsed.inWholeMinutes}m ago"
        elapsed < 24.hours -> "${elapsed.inWholeHours}h ago"
        else -> "${elapsed.inWholeDays}d ago"
    }
}

@Composable
fun ProtectionLogRow(
    event: PreToolUseEvent,
    isExpanded: Boolean,
    onToggleExpand: () -> Unit,
    protectionEngine: ProtectionEngine,
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(8.dp),
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = if (isExpanded) 2.dp else 0.dp,
    ) {
        Column {
            // Compact row
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(8.dp))
                    .clickable(onClick = onToggleExpand)
                    .padding(8.dp),
            ) {
                // Top line: tool badge + summary
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    ToolBadge(toolName = event.request.hookInput.toolName, toolType = event.request.toolType)
                    Spacer(Modifier.width(6.dp))
                    Text(
                        text = summaryText(event),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.8f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f),
                    )
                }

                Spacer(Modifier.height(4.dp))

                // Bottom line: conclusion badge + match count + timestamp
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    val cColor = conclusionColor(event.conclusion)
                    Surface(
                        shape = RoundedCornerShape(4.dp),
                        color = cColor.copy(alpha = 0.2f),
                    ) {
                        Text(
                            text = conclusionLabel(event.conclusion),
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                            color = cColor,
                            fontSize = 10.sp,
                            style = MaterialTheme.typography.labelSmall,
                        )
                    }

                    if (event.hits.isNotEmpty()) {
                        Text(
                            text = "${event.hits.size} rule${if (event.hits.size != 1) "s" else ""} matched",
                            fontSize = 10.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            style = MaterialTheme.typography.labelSmall,
                        )
                    }

                    Spacer(Modifier.weight(1f))

                    Text(
                        text = relativeTimestamp(event),
                        fontSize = 10.sp,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
                    )
                }
            }

            // Expanded detail: full rule breakdown
            AnimatedVisibility(visible = isExpanded) {
                val evaluations = remember(event.request.id) {
                    protectionEngine.evaluateAll(event.request.hookInput)
                }

                Column(
                    modifier = Modifier
                        .padding(start = 8.dp, end = 8.dp, bottom = 8.dp)
                        .fillMaxWidth()
                        .background(Color(0xFF0D0D0D), RoundedCornerShape(6.dp))
                        .padding(8.dp)
                        .heightIn(max = 400.dp)
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    for (eval in evaluations) {
                        ModuleSection(eval)
                    }
                }
            }
        }
    }
}

@Composable
private fun ModuleSection(eval: ProtectionEvaluation) {
    Column(modifier = Modifier.fillMaxWidth()) {
        // Module header
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Text(
                text = eval.moduleName,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurface,
                fontSize = 11.sp,
            )
            val badgeColor = modeColor(eval.mode)
            Surface(
                shape = RoundedCornerShape(4.dp),
                color = badgeColor.copy(alpha = 0.15f),
            ) {
                Text(
                    text = eval.mode.name.replace('_', ' ').lowercase()
                        .replaceFirstChar { it.uppercase() },
                    modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp),
                    color = badgeColor,
                    fontSize = 9.sp,
                    style = MaterialTheme.typography.labelSmall,
                )
            }
        }

        // Skipped state
        if (!eval.enabled) {
            Text(
                text = "Disabled",
                fontSize = 10.sp,
                color = Color(0xFF9E9E9E),
                modifier = Modifier.padding(start = 8.dp),
            )
            return
        }
        if (!eval.applicable) {
            Text(
                text = "N/A (tool not applicable)",
                fontSize = 10.sp,
                color = Color(0xFF9E9E9E),
                modifier = Modifier.padding(start = 8.dp),
            )
            return
        }

        // Rule results
        for (rule in eval.ruleResults) {
            RuleResultRow(rule)
        }
    }
}

@Composable
private fun RuleResultRow(rule: RuleEvalResult) {
    Row(
        verticalAlignment = Alignment.Top,
        modifier = Modifier.padding(start = 8.dp, top = 2.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        // Status indicator
        val indicatorColor = when {
            !rule.enabled -> Color(0xFF9E9E9E)
            rule.matched -> Color(0xFF4CAF50)
            else -> Color(0xFF616161)
        }
        Surface(
            modifier = Modifier.padding(top = 4.dp).size(8.dp),
            shape = CircleShape,
            color = if (rule.matched) indicatorColor else Color.Transparent,
            border = if (!rule.matched) BorderStroke(1.dp, indicatorColor) else null,
        ) {}

        Column {
            Text(
                text = rule.ruleName,
                fontSize = 10.sp,
                color = if (rule.enabled) MaterialTheme.colorScheme.onSurface.copy(alpha = 0.8f)
                else Color(0xFF9E9E9E),
                style = MaterialTheme.typography.bodySmall,
                textDecoration = if (!rule.enabled) TextDecoration.LineThrough else null,
            )
            if (rule.matched && rule.message != null) {
                Text(
                    text = rule.message,
                    fontSize = 9.sp,
                    color = Color(0xFF4CAF50).copy(alpha = 0.8f),
                    fontFamily = FontFamily.Monospace,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            if (!rule.enabled) {
                Text(
                    text = "Disabled",
                    fontSize = 9.sp,
                    color = Color(0xFF9E9E9E),
                )
            }
        }
    }
}
