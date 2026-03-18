package com.mikepenz.agentapprover.ui.approvals

import androidx.compose.desktop.ui.tooling.preview.Preview
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
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

@Composable
fun ApprovalsTab(
    pendingApprovals: List<ApprovalRequest>,
    riskResults: Map<String, RiskAnalysis>,
    riskStatuses: Map<String, RiskStatus>,
    riskErrors: Map<String, String>,
    settings: AppSettings,
    onApprove: (requestId: String, feedback: String?) -> Unit,
    onDeny: (requestId: String, feedback: String) -> Unit,
    onSendResponse: (requestId: String, response: String) -> Unit,
    onDismiss: (requestId: String) -> Unit,
    autoDenyRequests: Set<String>,
    onCancelAutoDeny: (requestId: String) -> Unit,
) {
    if (pendingApprovals.isEmpty()) {
        EmptyApprovalsState()
    } else {
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(horizontal = 8.dp),
            contentPadding = PaddingValues(vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            item {
                Text(
                    text = "${pendingApprovals.size} pending approval${if (pendingApprovals.size != 1) "s" else ""}",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(bottom = 4.dp),
                )
            }

            items(pendingApprovals, key = { it.id }) { request ->
                ApprovalCard(
                    request = request,
                    riskResult = riskResults[request.id],
                    riskStatus = riskStatuses[request.id] ?: RiskStatus.IDLE,
                    riskError = riskErrors[request.id],
                    timeoutSeconds = settings.defaultTimeoutSeconds,
                    onApprove = { feedback -> onApprove(request.id, feedback) },
                    onDeny = { feedback -> onDeny(request.id, feedback) },
                    onSendResponse = { response -> onSendResponse(request.id, response) },
                    onDismiss = { onDismiss(request.id) },
                    autoDenyActive = request.id in autoDenyRequests,
                    onCancelAutoDeny = { onCancelAutoDeny(request.id) },
                )
            }
        }
    }
}

@Composable
private fun EmptyApprovalsState() {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = "No pending approvals",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                textAlign = TextAlign.Center,
                fontSize = 14.sp,
            )
        }
    }
}

// -- Previews --

private fun sampleRequest(
    id: String = "preview-1",
    toolName: String = "Bash",
    toolType: ToolType = ToolType.DEFAULT,
    toolInput: JsonObject = JsonObject(mapOf("command" to JsonPrimitive("ls -la"))),
) = ApprovalRequest(
    id = id,
    source = Source.CLAUDE_CODE,
    toolName = toolName,
    toolType = toolType,
    toolInput = toolInput,
    sessionId = "session-abc",
    cwd = "/home/user/project",
    timestamp = Clock.System.now(),
    rawRequestJson = "{}",
)

@Preview
@Composable
private fun PreviewEmptyState() {
    AgentApproverTheme {
        ApprovalsTab(
            pendingApprovals = emptyList(),
            riskResults = emptyMap(),
            riskStatuses = emptyMap(),
            riskErrors = emptyMap(),
            settings = AppSettings(),
            onApprove = { _, _ -> },
            onDeny = { _, _ -> },
            onSendResponse = { _, _ -> },
            onDismiss = {},
            autoDenyRequests = emptySet(),
            onCancelAutoDeny = {},
        )
    }
}

@Preview
@Composable
private fun PreviewSingleCard() {
    AgentApproverTheme {
        ApprovalsTab(
            pendingApprovals = listOf(sampleRequest()),
            riskResults = mapOf("preview-1" to RiskAnalysis(risk = 2, message = "Safe read command")),
            riskStatuses = mapOf("preview-1" to RiskStatus.COMPLETED),
            riskErrors = emptyMap(),
            settings = AppSettings(),
            onApprove = { _, _ -> },
            onDeny = { _, _ -> },
            onSendResponse = { _, _ -> },
            onDismiss = {},
            autoDenyRequests = emptySet(),
            onCancelAutoDeny = {},
        )
    }
}

@Preview
@Composable
private fun PreviewMultipleCards() {
    AgentApproverTheme {
        ApprovalsTab(
            pendingApprovals = listOf(
                sampleRequest(id = "p1", toolName = "Bash"),
                sampleRequest(id = "p2", toolName = "Edit", toolInput = JsonObject(mapOf(
                    "file_path" to JsonPrimitive("/src/main.kt"),
                    "old_string" to JsonPrimitive("foo"),
                    "new_string" to JsonPrimitive("bar"),
                ))),
                sampleRequest(id = "p3", toolName = "Bash", toolInput = JsonObject(mapOf(
                    "command" to JsonPrimitive("rm -rf /tmp/build"),
                ))),
            ),
            riskResults = mapOf(
                "p1" to RiskAnalysis(risk = 1, message = "Read-only"),
                "p2" to RiskAnalysis(risk = 3, message = "Modifies source"),
                "p3" to RiskAnalysis(risk = 5, message = "Destructive command"),
            ),
            riskStatuses = mapOf(
                "p1" to RiskStatus.COMPLETED,
                "p2" to RiskStatus.COMPLETED,
                "p3" to RiskStatus.COMPLETED,
            ),
            riskErrors = emptyMap(),
            settings = AppSettings(),
            onApprove = { _, _ -> },
            onDeny = { _, _ -> },
            onSendResponse = { _, _ -> },
            onDismiss = {},
            autoDenyRequests = setOf("p3"),
            onCancelAutoDeny = {},
        )
    }
}
