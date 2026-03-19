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
import kotlinx.serialization.json.JsonElement
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
    onAlwaysAllow: (requestId: String) -> Unit,
    onApproveWithInput: (requestId: String, updatedInput: Map<String, JsonElement>) -> Unit,
    onDismiss: (requestId: String) -> Unit,
    autoDenyRequests: Set<String>,
    onCancelAutoDeny: (requestId: String) -> Unit,
    onPopOut: ((title: String, content: String) -> Unit)? = null,
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
                    onAlwaysAllow = { onAlwaysAllow(request.id) },
                    onApproveWithInput = { updatedInput -> onApproveWithInput(request.id, updatedInput) },
                    onDismiss = { onDismiss(request.id) },
                    autoDenyActive = request.id in autoDenyRequests,
                    onCancelAutoDeny = { onCancelAutoDeny(request.id) },
                    onPopOut = onPopOut,
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
    toolInput: Map<String, JsonElement> = mapOf("command" to JsonPrimitive("ls -la")),
) = ApprovalRequest(
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
            onAlwaysAllow = {},
            onApproveWithInput = { _, _ -> },
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
            riskResults = mapOf("preview-1" to RiskAnalysis(risk = 2, label = "Low", message = "Safe read command")),
            riskStatuses = mapOf("preview-1" to RiskStatus.COMPLETED),
            riskErrors = emptyMap(),
            settings = AppSettings(),
            onApprove = { _, _ -> },
            onDeny = { _, _ -> },
            onAlwaysAllow = {},
            onApproveWithInput = { _, _ -> },
            onDismiss = {},
            autoDenyRequests = emptySet(),
            onCancelAutoDeny = {},
        )
    }
}
