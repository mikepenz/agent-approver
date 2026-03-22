package com.mikepenz.agentapprover.ui.history

import androidx.compose.desktop.ui.tooling.preview.Preview
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mikepenz.agentapprover.model.*
import com.mikepenz.agentapprover.ui.theme.AgentApproverTheme
import kotlinx.datetime.Clock
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.minutes

@Composable
fun HistoryTab(history: List<ApprovalResult>, onReplay: ((ApprovalResult) -> Unit)? = null) {
    var filterText by remember { mutableStateOf("") }
    var expandedId by remember { mutableStateOf<String?>(null) }

    val filtered = remember(history, filterText) {
        if (filterText.isBlank()) {
            history
        } else {
            val query = filterText.trim().lowercase()
            history.filter { result ->
                result.request.hookInput.toolName.lowercase().contains(query) ||
                    result.request.hookInput.sessionId.lowercase().contains(query) ||
                    result.decision.name.lowercase().contains(query) ||
                    (result.riskAnalysis?.risk?.toString() == query)
            }
        }
    }

    Column(modifier = Modifier.fillMaxSize().padding(horizontal = 8.dp)) {
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
    AgentApproverTheme {
        HistoryTab(history = emptyList())
    }
}

@Preview
@Composable
private fun PreviewPopulated() {
    AgentApproverTheme {
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
