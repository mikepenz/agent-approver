package com.mikepenz.agentbuddy.ui.history

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mikepenz.agentbuddy.model.ApprovalRequest
import com.mikepenz.agentbuddy.model.ApprovalResult
import com.mikepenz.agentbuddy.model.Decision
import com.mikepenz.agentbuddy.model.HookInput
import com.mikepenz.agentbuddy.model.RiskAnalysis
import com.mikepenz.agentbuddy.model.Source
import com.mikepenz.agentbuddy.model.ToolType
import com.mikepenz.agentbuddy.ui.theme.AgentBuddyTheme
import com.mikepenz.agentbuddy.ui.theme.sourceLabel
import kotlinx.datetime.Clock
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlin.time.Duration.Companion.minutes

/**
 * Pure filter used by the History tab. Extracted as a top-level function so it
 * can be unit-tested without standing up Compose.
 *
 * @param sourceFilter null = match all sources.
 */
internal fun filterHistory(
    history: List<ApprovalResult>,
    query: String,
    typeFilter: String,
    sourceFilter: Source?,
): List<ApprovalResult> {
    val q = query.trim().lowercase()
    return history.filter { result ->
        val typeMatch = when (typeFilter) {
            "approvals" -> result.protectionModule == null
            "protections" -> result.protectionModule != null
            else -> true
        }
        val sourceMatch = sourceFilter == null || result.request.source == sourceFilter
        val textMatch = q.isBlank() || result.request.hookInput.toolName.lowercase().contains(q) ||
                result.request.hookInput.sessionId.lowercase().contains(q) ||
                result.decision.name.lowercase().contains(q) ||
                (result.riskAnalysis?.risk?.toString() == q) ||
                (result.protectionModule?.lowercase()?.contains(q) == true) ||
                (result.protectionRule?.lowercase()?.contains(q) == true)
        typeMatch && sourceMatch && textMatch
    }
}

@Composable
fun HistoryTab(history: List<ApprovalResult>, onReplay: ((ApprovalResult) -> Unit)? = null) {
    var filterText by remember { mutableStateOf("") }
    var filter by remember { mutableStateOf("all") }
    var sourceFilter by remember { mutableStateOf<Source?>(null) }
    var expandedId by remember { mutableStateOf<String?>(null) }

    val filterOptions = listOf("all" to "All", "approvals" to "Approvals", "protections" to "Protections")
    val presentSources = remember(history) {
        history.mapTo(mutableSetOf()) { it.request.source }
    }
    val showSourceFilter = presentSources.size > 1
    val sourceOptions: List<Pair<Source?, String>> = remember(presentSources) {
        buildList {
            add(null to "All")
            if (Source.CLAUDE_CODE in presentSources) add(Source.CLAUDE_CODE to sourceLabel(Source.CLAUDE_CODE))
            if (Source.COPILOT in presentSources) add(Source.COPILOT to sourceLabel(Source.COPILOT))
        }
    }

    val effectiveSourceFilter = if (showSourceFilter) sourceFilter else null
    val filtered = remember(history, filterText, filter, effectiveSourceFilter) {
        filterHistory(history, filterText, filter, effectiveSourceFilter)
    }

    Column(modifier = Modifier.fillMaxSize().padding(horizontal = 8.dp)) {
        SingleChoiceSegmentedButtonRow(
            modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
        ) {
            filterOptions.forEachIndexed { index, (key, label) ->
                SegmentedButton(
                    selected = filter == key,
                    onClick = { filter = key },
                    shape = SegmentedButtonDefaults.itemShape(index = index, count = filterOptions.size),
                ) {
                    Text(label, fontSize = 12.sp)
                }
            }
        }

        if (showSourceFilter) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(top = 6.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                sourceOptions.forEach { (source, label) ->
                    FilterChip(
                        selected = sourceFilter == source,
                        onClick = { sourceFilter = source },
                        label = { Text(label, fontSize = 11.sp) },
                    )
                }
            }
        }

        OutlinedTextField(
            value = filterText,
            onValueChange = { filterText = it },
            modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
            placeholder = { Text("Filter by tool, session, risk...", fontSize = 12.sp) },
            singleLine = true,
            textStyle = MaterialTheme.typography.bodySmall,
        )

        if (filtered.isEmpty()) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = if (history.isEmpty()) "No history yet" else "No matches",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                    textAlign = TextAlign.Center,
                    fontSize = 14.sp,
                )
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(vertical = 4.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                items(filtered, key = { it.request.id }) { result ->
                    HistoryRow(
                        result = result,
                        isExpanded = expandedId == result.request.id,
                        onToggleExpand = {
                            expandedId = if (expandedId == result.request.id) null else result.request.id
                        },
                        onReplay = onReplay,
                    )
                }
            }
        }
    }
}

// -- Previews --

private fun sampleResult(
    id: String,
    toolName: String = "Bash",
    toolType: ToolType = ToolType.DEFAULT,
    toolInput: JsonObject = JsonObject(mapOf("command" to JsonPrimitive("ls -la"))),
    decision: Decision = Decision.APPROVED,
    risk: Int? = 2,
    minutesAgo: Int = 5,
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
        timestamp = Clock.System.now() - minutesAgo.minutes,
        rawRequestJson = """{"tool":"$toolName"}""",
    ),
    decision = decision,
    riskAnalysis = risk?.let { RiskAnalysis(risk = it, label = "", message = "Sample") },
    rawResponseJson = """{"ok":true}""",
    decidedAt = Clock.System.now() - minutesAgo.minutes,
)

@Preview
@Composable
private fun PreviewEmpty() {
    AgentBuddyTheme {
        HistoryTab(history = emptyList())
    }
}

@Preview
@Composable
private fun PreviewPopulated() {
    AgentBuddyTheme {
        HistoryTab(
            history = listOf(
                sampleResult(id = "h1", decision = Decision.APPROVED, minutesAgo = 2),
                sampleResult(
                    id = "h2",
                    toolName = "Edit",
                    toolInput = JsonObject(mapOf("file_path" to JsonPrimitive("/src/main.kt"))),
                    decision = Decision.DENIED,
                    risk = 4,
                    minutesAgo = 10,
                ),
                sampleResult(id = "h3", decision = Decision.TIMEOUT, risk = 3, minutesAgo = 30),
                sampleResult(
                    id = "h4",
                    toolName = "AskUserQuestion",
                    toolType = ToolType.ASK_USER_QUESTION,
                    toolInput = JsonObject(mapOf("question" to JsonPrimitive("Should I continue?"))),
                    decision = Decision.AUTO_APPROVED,
                    risk = null,
                    minutesAgo = 120,
                ),
            ),
        )
    }
}
