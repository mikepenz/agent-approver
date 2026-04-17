package com.mikepenz.agentbuddy.ui.approvals

import androidx.compose.foundation.background
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
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mikepenz.agentbuddy.model.AllowedPrompt
import com.mikepenz.agentbuddy.model.ApprovalRequest
import com.mikepenz.agentbuddy.model.HookInput
import com.mikepenz.agentbuddy.model.PlanReviewData
import com.mikepenz.agentbuddy.model.Source
import com.mikepenz.agentbuddy.model.ToolType
import com.mikepenz.agentbuddy.ui.theme.AgentBuddyTheme
import kotlinx.datetime.Clock

@Composable
fun PlanCard(
    request: ApprovalRequest,
    planData: PlanReviewData,
    onApprove: (String?) -> Unit,
    onDeny: (String) -> Unit,
    onPopOut: ((title: String, content: String) -> Unit)? = null,
) {
    var feedback by remember { mutableStateOf("") }
    var planExpanded by remember { mutableStateOf(false) }

    val trimmedPlan = planData.plan.trim()
    val lineCount = trimmedPlan.count { it == '\n' } + 1
    val canExpand = lineCount > 3

    Column(modifier = Modifier.fillMaxWidth()) {
        // No timeout label + pop-out
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("No timeout", fontSize = 10.sp, color = Color.Gray)
            if (onPopOut != null) {
                IconButton(
                    onClick = { onPopOut("Plan Review", trimmedPlan) },
                    modifier = Modifier.size(20.dp),
                ) {
                    Text("↗", fontSize = 14.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }

        if (request.hookInput.cwd.isNotBlank()) {
            Text(
                text = request.hookInput.cwd,
                fontSize = 10.sp,
                color = Color.Gray,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }

        // Allowed prompts section
        if (planData.allowedPrompts.isNotEmpty()) {
            Spacer(Modifier.height(8.dp))
            Text(
                text = "Allowed Actions (${planData.allowedPrompts.size})",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(4.dp))
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(6.dp))
                    .padding(8.dp),
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                planData.allowedPrompts.forEach { prompt ->
                    Text(
                        text = "[${prompt.tool}] ${prompt.prompt}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }

        Spacer(Modifier.height(8.dp))

        // Plan content (collapsed/expanded)
        if (planExpanded && canExpand) {
            Surface(
                onClick = { planExpanded = false },
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 300.dp),
                color = Color(0xFF1E1E1E),
                shape = MaterialTheme.shapes.small,
            ) {
                Box(
                    modifier = Modifier
                        .padding(8.dp)
                        .verticalScroll(rememberScrollState()),
                ) {
                    Text(
                        text = trimmedPlan,
                        style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                        color = Color(0xFFCCCCCC),
                    )
                }
            }
        } else {
            Surface(
                onClick = { planExpanded = true },
                enabled = canExpand,
                modifier = Modifier.fillMaxWidth(),
                color = Color(0xFF1E1E1E),
                shape = MaterialTheme.shapes.small,
            ) {
                Box(modifier = Modifier.padding(8.dp)) {
                    Text(
                        text = trimmedPlan,
                        style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                        color = Color(0xFFCCCCCC),
                        maxLines = 3,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }

        Spacer(Modifier.height(8.dp))

        // Feedback input
        OutlinedTextField(
            value = feedback,
            onValueChange = { feedback = it },
            modifier = Modifier.fillMaxWidth(),
            placeholder = { Text("Refine plan with instructions...", fontSize = 12.sp) },
            minLines = 2,
            maxLines = 4,
            textStyle = LocalTextStyle.current.copy(fontSize = 12.sp),
        )

        Spacer(Modifier.height(8.dp))

        // Button logic: if feedback present, show "Refine Plan"; otherwise "Reject" + "Approve Plan"
        val hasMessage = feedback.isNotBlank()

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            if (hasMessage) {
                Spacer(Modifier.weight(1f))
                OutlinedButton(
                    onClick = { onDeny(feedback) },
                    colors = ButtonDefaults.outlinedButtonColors(
                        contentColor = MaterialTheme.colorScheme.tertiary,
                    ),
                ) {
                    Text("Refine Plan")
                }
            } else {
                OutlinedButton(
                    onClick = { onDeny("") },
                    modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error),
                ) {
                    Text("Reject")
                }
                Button(
                    onClick = { onApprove(null) },
                    modifier = Modifier.weight(1f),
                ) {
                    Text("Approve Plan")
                }
            }
        }
    }
}

@Preview
@Composable
private fun PreviewPlanCard() {
    AgentBuddyTheme {
        Surface(color = MaterialTheme.colorScheme.background) {
            Box(modifier = Modifier.width(350.dp).padding(12.dp)) {
                PlanCard(
                    request = ApprovalRequest(
                        id = "preview-plan",
                        source = Source.CLAUDE_CODE,
                        toolType = ToolType.PLAN,
                        hookInput = HookInput(
                            sessionId = "sess-abc123",
                            toolName = "ExitPlanMode",
                            toolInput = mapOf(
                                "plan" to kotlinx.serialization.json.JsonPrimitive(
                                    "## Implementation Plan\n\n1. Add data models\n2. Set up database\n3. Implement API\n4. Add auth\n5. Write tests"
                                ),
                            ),
                            cwd = "/home/user/project",
                        ),
                        timestamp = Clock.System.now(),
                        rawRequestJson = "{}",
                    ),
                    planData = PlanReviewData(
                        plan = "## Implementation Plan\n\n1. Add data models\n2. Set up database\n3. Implement API\n4. Add auth\n5. Write tests",
                        allowedPrompts = listOf(
                            AllowedPrompt(tool = "Edit", prompt = "Modify source files"),
                            AllowedPrompt(tool = "Bash", prompt = "Run tests"),
                        ),
                    ),
                    onApprove = {},
                    onDeny = {},
                )
            }
        }
    }
}
