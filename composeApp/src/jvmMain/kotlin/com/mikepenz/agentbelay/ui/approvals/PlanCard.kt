package com.mikepenz.agentbelay.ui.approvals

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
import androidx.compose.material3.Icon
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
import com.mikepenz.agentbelay.model.AllowedPrompt
import com.mikepenz.agentbelay.model.ApprovalRequest
import com.mikepenz.agentbelay.model.HookInput
import com.mikepenz.agentbelay.model.PlanReviewData
import com.mikepenz.agentbelay.model.Source
import com.mikepenz.agentbelay.model.ToolType
import com.mikepenz.agentbelay.ui.components.SlimAllowButton
import com.mikepenz.agentbelay.ui.components.SlimDenyButton
import com.mikepenz.agentbelay.ui.detail.PopOutSpec
import com.mikepenz.agentbelay.ui.icons.FeatherExternalLink
import com.mikepenz.agentbelay.ui.theme.PreviewScaffold
import kotlin.time.Clock

class PlanReviewFormState internal constructor(
    internal val feedbackState: androidx.compose.runtime.MutableState<String>,
    internal val expandedState: androidx.compose.runtime.MutableState<Boolean>,
) {
    var feedback: String
        get() = feedbackState.value
        set(value) { feedbackState.value = value }
    var expanded: Boolean
        get() = expandedState.value
        set(value) { expandedState.value = value }
}

@Composable
fun rememberPlanReviewFormState(initiallyExpanded: Boolean = false): PlanReviewFormState {
    val feedback = remember { mutableStateOf("") }
    val expanded = remember { mutableStateOf(initiallyExpanded) }
    return remember(feedback, expanded) { PlanReviewFormState(feedback, expanded) }
}

@Composable
fun PlanReviewForm(
    request: ApprovalRequest,
    planData: PlanReviewData,
    state: PlanReviewFormState,
    onPopOut: ((PopOutSpec) -> Unit)? = null,
    onApprove: ((String?) -> Unit)? = null,
    onDeny: ((String) -> Unit)? = null,
    modifier: Modifier = Modifier,
) {
    val trimmedPlan = planData.plan.trim()
    val lineCount = trimmedPlan.count { it == '\n' } + 1
    val canExpand = lineCount > 3
    Column(modifier = modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("No timeout", fontSize = 10.sp, color = Color.Gray)
            if (onPopOut != null) {
                IconButton(
                    onClick = {
                        onPopOut(
                            PopOutSpec(
                                title = "Plan Review",
                                content = trimmedPlan,
                                approveAction = onApprove?.let { { it(null) } },
                                denyAction = onDeny?.let { { it("") } },
                                refineAction = onDeny?.let { deny -> { feedback: String -> deny(feedback) } },
                            )
                        )
                    },
                    modifier = Modifier.size(24.dp),
                ) {
                    Icon(
                        imageVector = FeatherExternalLink,
                        contentDescription = "Open plan in new window",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(14.dp),
                    )
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
        if (state.expanded && canExpand) {
            Surface(
                onClick = { state.expanded = false },
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
                onClick = { state.expanded = true },
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
        OutlinedTextField(
            value = state.feedback,
            onValueChange = { state.feedback = it },
            modifier = Modifier.fillMaxWidth(),
            placeholder = { Text("Refine plan with instructions...", fontSize = 12.sp) },
            minLines = 2,
            maxLines = 4,
            textStyle = LocalTextStyle.current.copy(fontSize = 12.sp),
        )
    }
}

@Composable
fun PlanReviewActionBar(
    state: PlanReviewFormState,
    onApprove: (String?) -> Unit,
    onDeny: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val hasMessage = state.feedback.isNotBlank()
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        if (hasMessage) {
            Spacer(Modifier.weight(1f))
            SlimDenyButton(
                label = "Refine Plan",
                icon = null,
                onClick = { onDeny(state.feedback) },
            )
        } else {
            SlimDenyButton(
                modifier = Modifier.weight(1f),
                label = "Reject",
                onClick = { onDeny("") },
            )
            SlimAllowButton(
                modifier = Modifier.weight(1f),
                label = "Approve Plan",
                onClick = { onApprove(null) },
            )
        }
    }
}

@Composable
fun PlanCard(
    request: ApprovalRequest,
    planData: PlanReviewData,
    onApprove: (String?) -> Unit,
    onDeny: (String) -> Unit,
    onPopOut: ((PopOutSpec) -> Unit)? = null,
) {
    val state = rememberPlanReviewFormState()
    Column(modifier = Modifier.fillMaxWidth()) {
        PlanReviewForm(request, planData, state, onPopOut, onApprove, onDeny)
        Spacer(Modifier.height(8.dp))
        PlanReviewActionBar(state, onApprove, onDeny)
    }
}


@Preview
@Composable
private fun PreviewPlanCard() {
    PreviewScaffold {
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
                onPopOut = {},
            )
        }
    }
}

@Preview
@Composable
private fun PreviewPlanCardSlimButtons() {
    // Slim-styled buttons for use in the chromeless slim window.
    PreviewScaffold {
        Box(modifier = Modifier.width(350.dp).padding(12.dp)) {
            PlanCard(
                request = ApprovalRequest(
                    id = "preview-plan-slim",
                    source = Source.CLAUDE_CODE,
                    toolType = ToolType.PLAN,
                    hookInput = HookInput(
                        sessionId = "sess-slim",
                        toolName = "ExitPlanMode",
                        toolInput = emptyMap(),
                        cwd = "/home/user/project",
                    ),
                    timestamp = Clock.System.now(),
                    rawRequestJson = "{}",
                ),
                planData = PlanReviewData(
                    plan = "## Implementation Plan\n\n1. Add data models\n2. Set up database\n3. Implement API",
                    allowedPrompts = listOf(
                        AllowedPrompt(tool = "Edit", prompt = "Modify source files"),
                    ),
                ),
                onApprove = {},
                onDeny = {},
                onPopOut = {},
            )
        }
    }
}

@Preview
@Composable
private fun PreviewPlanCardLongPlan() {
    PreviewScaffold {
        Box(modifier = Modifier.width(350.dp).padding(12.dp)) {
            PlanCard(
                request = ApprovalRequest(
                    id = "preview-plan-long",
                    source = Source.CLAUDE_CODE,
                    toolType = ToolType.PLAN,
                    hookInput = HookInput(
                        sessionId = "sess-abc123",
                        toolName = "ExitPlanMode",
                        toolInput = emptyMap(),
                        cwd = "/home/user/project",
                    ),
                    timestamp = Clock.System.now(),
                    rawRequestJson = "{}",
                ),
                planData = PlanReviewData(
                    plan = "## Migration Plan\n\n1. Audit existing code\n2. Freeze dependencies\n3. Run baseline tests\n4. Apply migrations\n5. Update documentation\n6. Re-run integration suite\n7. Ship release notes",
                    allowedPrompts = emptyList(),
                ),
                onApprove = {},
                onDeny = {},
                onPopOut = {},
            )
        }
    }
}
