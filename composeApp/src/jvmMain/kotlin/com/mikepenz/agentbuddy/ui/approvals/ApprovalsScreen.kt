package com.mikepenz.agentbuddy.ui.approvals

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mikepenz.agentbuddy.model.ApprovalRequest
import com.mikepenz.agentbuddy.model.PlanReviewData
import com.mikepenz.agentbuddy.model.Source
import com.mikepenz.agentbuddy.model.ToolType
import com.mikepenz.agentbuddy.model.UserQuestionData
import kotlinx.serialization.json.JsonElement
import com.mikepenz.agentbuddy.ui.components.ColoredIconTile
import com.mikepenz.agentbuddy.ui.components.DecisionStatus
import com.mikepenz.agentbuddy.ui.components.HorizontalHairline
import com.mikepenz.agentbuddy.ui.components.MetadataField
import com.mikepenz.agentbuddy.ui.components.RiskPill
import com.mikepenz.agentbuddy.ui.components.SectionLabel
import com.mikepenz.agentbuddy.ui.components.SourceTag
import com.mikepenz.agentbuddy.ui.components.StatusPill
import com.mikepenz.agentbuddy.ui.components.TagSize
import com.mikepenz.agentbuddy.ui.components.ToolTag
import com.mikepenz.agentbuddy.ui.icons.LucideCheck
import com.mikepenz.agentbuddy.ui.icons.LucideChevronRight
import com.mikepenz.agentbuddy.ui.icons.LucideClock
import com.mikepenz.agentbuddy.ui.icons.LucideCopy
import com.mikepenz.agentbuddy.ui.icons.LucideFolder
import com.mikepenz.agentbuddy.ui.icons.LucideShieldCheck
import com.mikepenz.agentbuddy.ui.icons.LucideX
import com.mikepenz.agentbuddy.ui.theme.AccentEmerald
import com.mikepenz.agentbuddy.ui.theme.AgentBuddyColors
import com.mikepenz.agentbuddy.ui.theme.DangerRed
import com.mikepenz.agentbuddy.ui.theme.PreviewScaffold
import com.mikepenz.agentbuddy.ui.theme.WarnYellow
import com.mikepenz.agentbuddy.ui.theme.riskColor

/** Lightweight projection of an approval for the design-first screen. */
data class ApprovalQueueItem(
    val id: String,
    val tool: String,
    val toolType: ToolType = ToolType.DEFAULT,
    val source: Source,
    val summary: String,
    /** `null` while no risk result is available (pending, analyzing, errored, or disabled). */
    val risk: Int?,
    val via: String,
    val timestamp: String,
    val elapsedSeconds: Int,
    val ttlSeconds: Int,
    val session: String,
    val prompt: String,
    val workingDir: String,
    val riskAssessment: String,
    val riskAnalysisEnabled: Boolean = true,
    val riskAnalyzing: Boolean = false,
    val riskError: String? = null,
    val questionData: UserQuestionData? = null,
    val planData: PlanReviewData? = null,
    val request: ApprovalRequest? = null,
)

@Composable
fun ApprovalsScreen(
    items: List<ApprovalQueueItem>,
    modifier: Modifier = Modifier,
    onApprove: (id: String) -> Unit = {},
    onAlwaysAllow: (id: String) -> Unit = {},
    onDeny: (id: String) -> Unit = {},
    onApproveWithInput: (id: String, updatedInput: Map<String, JsonElement>) -> Unit = { _, _ -> },
    onDenyWithFeedback: (id: String, feedback: String) -> Unit = { _, _ -> },
    onDismiss: (id: String) -> Unit = {},
    initialMediumDetailId: String? = null,
) {
    if (items.isEmpty()) {
        ApprovalsEmptyState(modifier = modifier)
        return
    }

    var selectedId by remember(items.first().id) { mutableStateOf(items.first().id) }
    val selected = items.firstOrNull { it.id == selectedId } ?: items.first()
    var mediumDetailId by remember { mutableStateOf(initialMediumDetailId) }

    BoxWithConstraints(modifier = modifier.fillMaxSize()) {
        if (maxWidth > 860.dp) {
            // Wide: 400dp queue panel + 1dp divider + full detail pane
            Row(modifier = Modifier.fillMaxSize()) {
                Column(
                    modifier = Modifier
                        .width(400.dp)
                        .fillMaxHeight()
                        .background(AgentBuddyColors.background),
                ) {
                    QueueHeader(count = items.size)
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 0.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        items(items.size, key = { items[it].id }) { idx ->
                            val item = items[idx]
                            QueueRow(
                                item = item,
                                active = item.id == selectedId,
                                onClick = { selectedId = item.id },
                            )
                        }
                        item { Spacer(Modifier.height(10.dp)) }
                    }
                }
                Box(modifier = Modifier.width(1.dp).fillMaxHeight().background(AgentBuddyColors.line1))
                ApprovalDetail(
                    item = selected,
                    modifier = Modifier.fillMaxSize(),
                    onApprove = onApprove,
                    onAlwaysAllow = onAlwaysAllow,
                    onDeny = onDeny,
                    onApproveWithInput = onApproveWithInput,
                    onDenyWithFeedback = onDenyWithFeedback,
                    onDismiss = onDismiss,
                )
            }
        } else {
            // Medium: full-width flat list; tap opens detail full-screen with back bar
            val mediumSelected = items.firstOrNull { it.id == mediumDetailId }
            Column(modifier = Modifier.fillMaxSize().background(AgentBuddyColors.background)) {
                if (mediumSelected != null) {
                    MediumDetailView(
                        item = mediumSelected,
                        onBack = { mediumDetailId = null },
                        modifier = Modifier.fillMaxSize(),
                        onApprove = onApprove,
                        onAlwaysAllow = onAlwaysAllow,
                        onDeny = onDeny,
                        onApproveWithInput = onApproveWithInput,
                        onDenyWithFeedback = onDenyWithFeedback,
                        onDismiss = onDismiss,
                    )
                } else {
                    QueueHeader(count = items.size)
                    LazyColumn(modifier = Modifier.fillMaxSize()) {
                        items(items.size, key = { items[it].id }) { idx ->
                            val item = items[idx]
                            MediumQueueRow(
                                item = item,
                                onClick = { mediumDetailId = item.id },
                                onApprove = onApprove,
                                onAlwaysAllow = onAlwaysAllow,
                                onDeny = onDeny,
                            )
                            if (idx < items.lastIndex) {
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(1.dp)
                                        .background(AgentBuddyColors.line1),
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

// ── Empty ────────────────────────────────────────────────────────────────────

@Composable
private fun ApprovalsEmptyState(modifier: Modifier = Modifier) {
    Box(modifier = modifier.fillMaxSize().background(AgentBuddyColors.background), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Box(
                modifier = Modifier
                    .size(64.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .background(AgentBuddyColors.accentEmeraldTint)
                    .border(1.dp, AccentEmerald.copy(alpha = 0.30f), RoundedCornerShape(16.dp)),
                contentAlignment = Alignment.Center,
            ) {
                androidx.compose.material3.Icon(
                    imageVector = LucideShieldCheck,
                    contentDescription = null,
                    tint = AccentEmerald,
                    modifier = Modifier.size(28.dp),
                )
            }
            Spacer(Modifier.height(22.dp))
            Text(
                text = "All clear",
                color = AgentBuddyColors.inkPrimary,
                fontSize = 17.sp,
                fontWeight = FontWeight.SemiBold,
                letterSpacing = (-0.3).sp,
            )
            Spacer(Modifier.height(6.dp))
            Text(
                text = "No pending approvals. Your agents are running within the policy bounds you've configured.",
                color = AgentBuddyColors.inkSecondary,
                fontSize = 13.sp,
                lineHeight = 20.sp,
                modifier = Modifier.width(380.dp),
                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
            )
        }
    }
}

// ── Queue ────────────────────────────────────────────────────────────────────

@Composable
private fun QueueHeader(count: Int) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 18.dp, top = 16.dp, end = 18.dp, bottom = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(
            text = "Queue",
            color = AgentBuddyColors.inkPrimary,
            fontSize = 13.sp,
            fontWeight = FontWeight.SemiBold,
        )
        Box(
            modifier = Modifier
                .clip(RoundedCornerShape(4.dp))
                .background(AgentBuddyColors.surface)
                .border(1.dp, AgentBuddyColors.line1, RoundedCornerShape(4.dp))
                .padding(horizontal = 6.dp, vertical = 1.dp),
        ) {
            Text(
                text = count.toString(),
                color = AgentBuddyColors.inkMuted,
                fontSize = 11.sp,
                fontFamily = FontFamily.Monospace,
            )
        }
        Spacer(Modifier.weight(1f))
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(5.dp)) {
            Box(
                modifier = Modifier
                    .size(6.dp)
                    .clip(CircleShape)
                    .background(AccentEmerald),
            )
            Text(
                text = "Streaming",
                color = AgentBuddyColors.inkTertiary,
                fontSize = 11.sp,
            )
        }
    }
}

@Composable
private fun QueueRow(
    item: ApprovalQueueItem,
    active: Boolean,
    onClick: () -> Unit,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isHovered by interactionSource.collectIsHoveredAsState()

    val bgColor = when {
        active -> AgentBuddyColors.surface
        isHovered -> AgentBuddyColors.surface
        else -> Color.Transparent
    }
    val borderColor = if (active) AgentBuddyColors.line2 else Color.Transparent

    val pct = item.elapsedSeconds.toFloat() / item.ttlSeconds.coerceAtLeast(1)
    val urgent = pct < 0.25f

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(bgColor)
            .border(1.dp, borderColor, RoundedCornerShape(8.dp))
            .hoverable(interactionSource)
            .clickable(interactionSource = interactionSource, indication = null) { onClick() },
    ) {
        if (active) {
            Box(
                modifier = Modifier
                    .align(Alignment.CenterStart)
                    .padding(vertical = 10.dp)
                    .width(2.dp)
                    .fillMaxHeight()
                    .background(AccentEmerald, RoundedCornerShape(1.dp)),
            )
        }
        Column(modifier = Modifier.padding(12.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                modifier = Modifier.fillMaxWidth(),
            ) {
                ToolTag(toolName = item.tool, toolType = item.toolType, size = TagSize.SMALL)
                SourceTag(source = item.source)
                Spacer(Modifier.weight(1f))
                Text(
                    text = item.timestamp,
                    color = AgentBuddyColors.inkMuted,
                    fontSize = 10.sp,
                    fontFamily = FontFamily.Monospace,
                )
            }
            Spacer(Modifier.height(8.dp))
            val isSpecialQueue = item.toolType == ToolType.ASK_USER_QUESTION ||
                item.toolType == ToolType.PLAN
            Text(
                text = item.summary,
                color = AgentBuddyColors.inkPrimary,
                fontSize = 12.sp,
                lineHeight = 18.sp,
                fontFamily = FontFamily.Monospace,
                letterSpacing = (-0.1).sp,
                maxLines = if (isSpecialQueue) 4 else 2,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.height(10.dp))
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxWidth(),
            ) {
                RiskPill(
                    level = item.risk,
                    via = item.via,
                    analyzing = item.riskAnalyzing,
                    error = item.riskError != null,
                    enabled = item.riskAnalysisEnabled,
                )
                Spacer(Modifier.weight(1f))
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    Icon(
                        imageVector = LucideClock,
                        contentDescription = null,
                        tint = if (urgent) DangerRed else AgentBuddyColors.inkTertiary,
                        modifier = Modifier.size(11.dp),
                    )
                    Text(
                        text = "${item.elapsedSeconds}s",
                        color = if (urgent) DangerRed else AgentBuddyColors.inkTertiary,
                        fontSize = 10.sp,
                        fontFamily = FontFamily.Monospace,
                    )
                }
            }
            Spacer(Modifier.height(10.dp))
            // Progress bar
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(2.dp)
                    .clip(RoundedCornerShape(1.dp))
                    .background(AgentBuddyColors.surface2),
            ) {
                val barColor = when {
                    urgent -> DangerRed
                    pct < 0.5f -> WarnYellow
                    else -> AccentEmerald
                }
                Box(
                    modifier = Modifier
                        .fillMaxHeight()
                        .fillMaxWidth(pct.coerceIn(0f, 1f))
                        .background(barColor),
                )
            }
        }
    }
}

// ── Medium layout ────────────────────────────────────────────────────────────

@Composable
private fun MediumQueueRow(
    item: ApprovalQueueItem,
    onClick: () -> Unit,
    onApprove: (id: String) -> Unit = {},
    onAlwaysAllow: (id: String) -> Unit = {},
    onDeny: (id: String) -> Unit = {},
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isHovered by interactionSource.collectIsHoveredAsState()

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(if (isHovered) AgentBuddyColors.surface else Color.Transparent)
            .hoverable(interactionSource)
            .clickable(interactionSource = interactionSource, indication = null) { onClick() }
            .padding(horizontal = 18.dp, vertical = 14.dp),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            modifier = Modifier.fillMaxWidth(),
        ) {
            ToolTag(toolName = item.tool, toolType = item.toolType, size = TagSize.SMALL)
            SourceTag(source = item.source)
            Spacer(Modifier.weight(1f))
            Text(
                text = item.timestamp,
                color = AgentBuddyColors.inkMuted,
                fontSize = 10.5.sp,
                fontFamily = FontFamily.Monospace,
                maxLines = 1,
            )
        }
        Spacer(Modifier.height(8.dp))
        val isSpecialRow = item.toolType == ToolType.ASK_USER_QUESTION ||
            item.toolType == ToolType.PLAN
        Text(
            text = item.summary,
            color = AgentBuddyColors.inkPrimary,
            fontSize = 12.5.sp,
            lineHeight = 18.sp,
            fontFamily = FontFamily.Monospace,
            letterSpacing = (-0.1).sp,
            maxLines = if (isSpecialRow) 4 else 1,
            overflow = TextOverflow.Ellipsis,
        )
        Spacer(Modifier.height(10.dp))
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.fillMaxWidth(),
        ) {
            RiskPill(
                level = item.risk,
                via = item.via,
                analyzing = item.riskAnalyzing,
                error = item.riskError != null,
                enabled = item.riskAnalysisEnabled,
            )
            Spacer(Modifier.weight(1f))
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Icon(
                    imageVector = LucideClock,
                    contentDescription = null,
                    tint = AgentBuddyColors.inkTertiary,
                    modifier = Modifier.size(11.dp),
                )
                Text(
                    text = "${item.elapsedSeconds}s",
                    color = AgentBuddyColors.inkTertiary,
                    fontSize = 10.5.sp,
                    fontFamily = FontFamily.Monospace,
                    maxLines = 1,
                )
            }
        }
        if (item.toolType != ToolType.ASK_USER_QUESTION && item.toolType != ToolType.PLAN) {
            Spacer(Modifier.height(10.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                MediumActionButton(
                    text = "Deny",
                    icon = LucideX,
                    primary = false,
                    modifier = Modifier.weight(1f),
                    onClick = { onDeny(item.id) },
                )
                MediumActionButton(
                    text = "Always allow",
                    icon = LucideCheck,
                    primary = false,
                    modifier = Modifier.weight(1f),
                    onClick = { onAlwaysAllow(item.id) },
                )
                MediumActionButton(
                    text = "Allow",
                    icon = LucideCheck,
                    primary = true,
                    modifier = Modifier.weight(1f),
                    onClick = { onApprove(item.id) },
                )
            }
        }
    }
}

@Composable
private fun MediumActionButton(
    text: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    primary: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    val bg = if (primary) AccentEmerald else Color.Transparent
    val fg = if (primary) Color(0xFF0C2D1D) else AgentBuddyColors.inkSecondary
    val border = if (primary) Color.Transparent else AgentBuddyColors.line2
    Row(
        modifier = modifier
            .height(30.dp)
            .clip(RoundedCornerShape(6.dp))
            .background(bg)
            .border(1.dp, border, RoundedCornerShape(6.dp))
            .clickable { onClick() }
            .padding(horizontal = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center,
    ) {
        Icon(imageVector = icon, contentDescription = null, tint = fg, modifier = Modifier.size(12.dp))
        Spacer(Modifier.width(5.dp))
        Text(
            text = text,
            color = fg,
            fontSize = 12.sp,
            fontWeight = if (primary) FontWeight.SemiBold else FontWeight.Medium,
            maxLines = 1,
        )
    }
}

@Composable
private fun MediumDetailView(
    item: ApprovalQueueItem,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    onApprove: (id: String) -> Unit = {},
    onAlwaysAllow: (id: String) -> Unit = {},
    onDeny: (id: String) -> Unit = {},
    onApproveWithInput: (id: String, updatedInput: Map<String, JsonElement>) -> Unit = { _, _ -> },
    onDenyWithFeedback: (id: String, feedback: String) -> Unit = { _, _ -> },
    onDismiss: (id: String) -> Unit = {},
) {
    Column(modifier = modifier) {
        // Back bar
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(40.dp)
                .clickable { onBack() }
                .padding(horizontal = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Icon(
                imageVector = LucideChevronRight,
                contentDescription = "Back",
                tint = AgentBuddyColors.inkTertiary,
                modifier = Modifier.size(14.dp).rotate(180f),
            )
            Text(
                text = "Back",
                color = AgentBuddyColors.inkTertiary,
                fontSize = 12.sp,
            )
            Spacer(Modifier.weight(1f))
        }
        HorizontalHairline()
        ApprovalDetail(
            item = item,
            modifier = Modifier.weight(1f),
            onApprove = onApprove,
            onAlwaysAllow = onAlwaysAllow,
            onDeny = onDeny,
            onApproveWithInput = onApproveWithInput,
            onDenyWithFeedback = onDenyWithFeedback,
            onDismiss = onDismiss,
        )
    }
}

// ── Detail ──────────────────────────────────────────────────────────────────

@Composable
private fun ApprovalDetail(
    item: ApprovalQueueItem,
    modifier: Modifier = Modifier,
    onApprove: (id: String) -> Unit = {},
    onAlwaysAllow: (id: String) -> Unit = {},
    onDeny: (id: String) -> Unit = {},
    onApproveWithInput: (id: String, updatedInput: Map<String, JsonElement>) -> Unit = { _, _ -> },
    onDenyWithFeedback: (id: String, feedback: String) -> Unit = { _, _ -> },
    onDismiss: (id: String) -> Unit = {},
) {
    val askRequest = item.request?.takeIf {
        item.toolType == ToolType.ASK_USER_QUESTION && item.questionData != null
    }
    val planRequest = item.request?.takeIf {
        item.toolType == ToolType.PLAN && item.planData != null
    }
    val isSpecial = askRequest != null || planRequest != null
    val askState = if (askRequest != null) rememberAskUserQuestionState() else null
    val planState = if (planRequest != null) rememberPlanReviewFormState() else null
    Column(modifier = modifier.background(AgentBuddyColors.background)) {
        // Header
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 32.dp, end = 32.dp, top = 22.dp, bottom = 18.dp),
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxWidth(),
            ) {
                ToolTag(toolName = item.tool, toolType = item.toolType)
                SourceTag(source = item.source)
                RiskPill(
                    level = item.risk,
                    via = item.via,
                    analyzing = item.riskAnalyzing,
                    error = item.riskError != null,
                    enabled = item.riskAnalysisEnabled,
                )
                StatusPill(status = DecisionStatus.PENDING)
                Spacer(Modifier.weight(1f))
                Text(
                    text = "session · ${item.session}",
                    color = AgentBuddyColors.inkMuted,
                    fontSize = 10.sp,
                    fontFamily = FontFamily.Monospace,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Spacer(Modifier.height(14.dp))
            Text(
                text = "Approval requested",
                color = AgentBuddyColors.inkPrimary,
                fontSize = 20.sp,
                fontWeight = FontWeight.SemiBold,
                letterSpacing = (-0.4).sp,
            )
            Spacer(Modifier.height(6.dp))
            Text(
                text = item.prompt,
                color = AgentBuddyColors.inkSecondary,
                fontSize = 13.sp,
                lineHeight = 20.sp,
            )
        }
        HorizontalHairline()

        // Body (scrollable)
        Column(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(start = 32.dp, end = 32.dp, top = 22.dp, bottom = 28.dp),
        ) {
            if (isSpecial) {
                if (askRequest != null && item.questionData != null) {
                    AskUserQuestionForm(
                        request = askRequest,
                        questionData = item.questionData,
                        state = askState!!,
                    )
                } else if (planRequest != null && item.planData != null) {
                    PlanReviewForm(
                        request = planRequest,
                        planData = item.planData,
                        state = planState!!,
                    )
                }

                Spacer(Modifier.height(22.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                    MetaRow(
                        label = "Working directory",
                        value = item.workingDir,
                        icon = LucideFolder,
                        modifier = Modifier.weight(1f),
                    )
                    MetaRow(
                        label = "Timeout",
                        value = "${item.ttlSeconds} seconds",
                        icon = LucideClock,
                        modifier = Modifier.weight(1f),
                    )
                }
                Spacer(Modifier.height(22.dp))
                RiskAssessmentBlock(item = item)
                return@Column
            }
            // Command
            DetailBlock(
                label = "Command",
                action = {
                    ActionButton(
                        text = "Copy",
                        variant = ButtonVariant.Ghost,
                        leadingIcon = LucideCopy,
                    )
                },
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(8.dp))
                        .background(AgentBuddyColors.surface)
                        .border(1.dp, AgentBuddyColors.line1, RoundedCornerShape(8.dp))
                        .padding(horizontal = 16.dp, vertical = 14.dp),
                ) {
                    Text(
                        text = "$ ${item.summary}",
                        color = AgentBuddyColors.inkPrimary,
                        fontSize = 12.sp,
                        lineHeight = 20.sp,
                        fontFamily = FontFamily.Monospace,
                        letterSpacing = (-0.1).sp,
                    )
                }
            }

            Spacer(Modifier.height(14.dp))

            // Meta grid (2 columns)
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                MetaRow(
                    label = "Working directory",
                    value = item.workingDir,
                    icon = LucideFolder,
                    modifier = Modifier.weight(1f),
                )
                MetaRow(
                    label = "Timeout",
                    value = "${item.ttlSeconds} seconds",
                    icon = LucideClock,
                    modifier = Modifier.weight(1f),
                )
            }

            Spacer(Modifier.height(22.dp))

            RiskAssessmentBlock(item = item)

            Spacer(Modifier.height(22.dp))

            // Recent similar requests
            DetailBlock(label = "Recent similar requests") {
                RecentSimilarList(tool = item.tool)
            }
        }

        // Pinned footer actions
        Column(modifier = Modifier.fillMaxWidth()) {
            HorizontalHairline()
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(AgentBuddyColors.chrome)
                    .padding(horizontal = 32.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                when {
                    askRequest != null && item.questionData != null && askState != null -> {
                        AskUserQuestionActionBar(
                            request = askRequest,
                            questionData = item.questionData,
                            state = askState,
                            onApproveWithInput = { updated -> onApproveWithInput(item.id, updated) },
                            onDismiss = { onDismiss(item.id) },
                        )
                    }
                    planRequest != null && planState != null -> {
                        PlanReviewActionBar(
                            state = planState,
                            onApprove = { onApprove(item.id) },
                            onDeny = { feedback -> onDenyWithFeedback(item.id, feedback) },
                        )
                    }
                    else -> {
                        ActionButton(text = "Deny", variant = ButtonVariant.Outline, leadingIcon = LucideX, onClick = { onDeny(item.id) })
                        Spacer(Modifier.weight(1f))
                        ActionButton(text = "Always allow", variant = ButtonVariant.Secondary, onClick = { onAlwaysAllow(item.id) })
                        ActionButton(text = "Allow", variant = ButtonVariant.Primary, leadingIcon = LucideCheck, onClick = { onApprove(item.id) })
                    }
                }
            }
        }
    }
}

@Composable
private fun RiskAssessmentBlock(item: ApprovalQueueItem) {
    DetailBlock(label = "Risk assessment") {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(8.dp))
                .background(AgentBuddyColors.surface)
                .border(1.dp, AgentBuddyColors.line1, RoundedCornerShape(8.dp))
                .padding(16.dp),
            horizontalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            val risk = item.risk
            if (risk != null) {
                val c = riskColor(risk)
                ColoredIconTile(tint = c, bgAlpha = 0.12f, borderAlpha = 0f) {
                    Text(
                        text = "$risk",
                        color = c,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.SemiBold,
                        fontFamily = FontFamily.Monospace,
                    )
                }
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "LEVEL $risk · GRADED BY ${item.via.uppercase()}",
                        color = AgentBuddyColors.inkMuted,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.SemiBold,
                        letterSpacing = 0.4.sp,
                    )
                    Spacer(Modifier.height(5.dp))
                    Text(
                        text = item.riskAssessment,
                        color = AgentBuddyColors.inkPrimary,
                        fontSize = 13.sp,
                        lineHeight = 20.sp,
                    )
                }
            } else {
                ColoredIconTile(
                    tint = AgentBuddyColors.inkTertiary,
                    bgAlpha = 0.12f,
                    borderAlpha = 0f,
                ) {
                    Icon(
                        imageVector = LucideClock,
                        contentDescription = null,
                        tint = AgentBuddyColors.inkTertiary,
                        modifier = Modifier.size(14.dp),
                    )
                }
                Column(modifier = Modifier.weight(1f)) {
                    val header = when {
                        !item.riskAnalysisEnabled -> "RISK ANALYSIS DISABLED"
                        item.riskError != null -> "ANALYSIS FAILED · ${item.via.uppercase()}"
                        item.riskAnalyzing -> "ANALYZING · ${item.via.uppercase()}"
                        else -> "PENDING · ${item.via.uppercase()}"
                    }
                    Text(
                        text = header,
                        color = AgentBuddyColors.inkMuted,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.SemiBold,
                        letterSpacing = 0.4.sp,
                    )
                    Spacer(Modifier.height(5.dp))
                    Text(
                        text = item.riskAssessment,
                        color = AgentBuddyColors.inkPrimary,
                        fontSize = 13.sp,
                        lineHeight = 20.sp,
                    )
                }
            }
        }
    }
}

@Composable
private fun DetailBlock(
    label: String,
    action: (@Composable () -> Unit)? = null,
    content: @Composable () -> Unit,
) {
    Column {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            SectionLabel(text = label)
            action?.invoke()
        }
        Spacer(Modifier.height(8.dp))
        content()
    }
}

@Composable
private fun MetaRow(
    label: String,
    value: String,
    modifier: Modifier = Modifier,
    icon: androidx.compose.ui.graphics.vector.ImageVector? = null,
) {
    MetadataField(
        label = label,
        icon = icon,
        labelGap = 6.dp,
        modifier = modifier
            .clip(RoundedCornerShape(8.dp))
            .background(AgentBuddyColors.surface)
            .border(1.dp, AgentBuddyColors.line1, RoundedCornerShape(8.dp))
            .padding(horizontal = 14.dp, vertical = 12.dp),
    ) {
        Text(
            text = value,
            color = AgentBuddyColors.inkPrimary,
            fontSize = 12.sp,
            fontFamily = FontFamily.Monospace,
            letterSpacing = (-0.1).sp,
        )
    }
}

@Composable
private fun RecentSimilarList(tool: String) {
    val rows = remember {
        listOf(
            Triple("2h ago", DecisionStatus.APPROVED, "agent-buddy"),
            Triple("yesterday", DecisionStatus.AUTO_APPROVED, "agent-buddy"),
            Triple("3d ago", DecisionStatus.APPROVED, "agent-buddy"),
        )
    }
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(AgentBuddyColors.surface)
            .border(1.dp, AgentBuddyColors.line1, RoundedCornerShape(8.dp)),
    ) {
        rows.forEachIndexed { idx, row ->
            if (idx > 0) {
                HorizontalHairline()
            }
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 14.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Icon(
                    imageVector = LucideClock,
                    contentDescription = null,
                    tint = AgentBuddyColors.inkMuted,
                    modifier = Modifier.size(12.dp),
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = "Similar $tool call in ",
                        color = AgentBuddyColors.inkSecondary,
                        fontSize = 12.sp,
                    )
                    Text(
                        text = row.third,
                        color = AgentBuddyColors.inkPrimary,
                        fontSize = 12.sp,
                        fontFamily = FontFamily.Monospace,
                    )
                }
                Spacer(Modifier.weight(1f))
                StatusPill(status = row.second, size = TagSize.SMALL)
                Text(
                    text = row.first,
                    color = AgentBuddyColors.inkMuted,
                    fontSize = 11.sp,
                    modifier = Modifier.width(80.dp),
                    textAlign = androidx.compose.ui.text.style.TextAlign.End,
                )
            }
        }
    }
}

private enum class ButtonVariant { Primary, Secondary, Outline, Ghost }

@Composable
private fun ActionButton(
    text: String,
    variant: ButtonVariant,
    leadingIcon: androidx.compose.ui.graphics.vector.ImageVector? = null,
    onClick: () -> Unit = {},
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isHovered by interactionSource.collectIsHoveredAsState()
    val (bg, fg, border) = when (variant) {
        ButtonVariant.Primary -> Triple(AccentEmerald, Color(0xFF0C2D1D), Color.Transparent)
        ButtonVariant.Secondary -> Triple(AgentBuddyColors.surface2, AgentBuddyColors.inkPrimary, AgentBuddyColors.line2)
        ButtonVariant.Outline -> Triple(Color.Transparent, AgentBuddyColors.inkSecondary, AgentBuddyColors.line2)
        ButtonVariant.Ghost -> Triple(Color.Transparent, AgentBuddyColors.inkTertiary, Color.Transparent)
    }
    val hoverBg = when (variant) {
        ButtonVariant.Primary -> AccentEmerald.copy(alpha = 0.9f)
        ButtonVariant.Secondary -> AgentBuddyColors.surface
        ButtonVariant.Outline -> AgentBuddyColors.surface
        ButtonVariant.Ghost -> AgentBuddyColors.surface
    }
    val isSmall = variant == ButtonVariant.Ghost
    val height = if (isSmall) 26.dp else 30.dp
    val hPad = if (isSmall) 10.dp else 12.dp
    val fontSize = if (isSmall) 12.sp else 12.sp
    val iconSize = if (isSmall) 12.dp else 14.dp
    Row(
        modifier = Modifier
            .height(height)
            .clip(RoundedCornerShape(6.dp))
            .background(if (isHovered) hoverBg else bg)
            .border(1.dp, border, RoundedCornerShape(6.dp))
            .hoverable(interactionSource)
            .clickable(interactionSource = interactionSource, indication = null) { onClick() }
            .padding(horizontal = hPad),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(if (isSmall) 6.dp else 7.dp),
    ) {
        if (leadingIcon != null) {
            Icon(
                imageVector = leadingIcon,
                contentDescription = null,
                tint = fg,
                modifier = Modifier.size(iconSize),
            )
        }
        Text(
            text = text,
            color = fg,
            fontSize = fontSize,
            fontWeight = if (variant == ButtonVariant.Primary) FontWeight.SemiBold else FontWeight.Medium,
            letterSpacing = (-0.05).sp,
            maxLines = 1,
        )
    }
}

// ── Previews ───────────────────────────────────────────────────────────────

private fun sample(idx: Int = 0) = listOf(
    ApprovalQueueItem(
        id = "1",
        tool = "Bash",
        source = Source.CLAUDE_CODE,
        summary = "rm -rf node_modules && pnpm install --frozen-lockfile",
        risk = 3,
        via = "claude",
        timestamp = "14:02:11",
        elapsedSeconds = 18,
        ttlSeconds = 60,
        session = "a84e7bf",
        prompt = "Claude Code is asking to run a shell command in /agent-buddy. Review carefully before approving.",
        workingDir = "/Users/mike/dev/agent-buddy",
        riskAssessment = "Command performs a destructive filesystem delete in the project directory. Similar commands have been approved 3x in this project over the past week.",
    ),
    ApprovalQueueItem(
        id = "2",
        tool = "WebFetch",
        source = Source.CLAUDE_CODE,
        summary = "GET https://api.anthropic.com/v1/messages — outbound network call",
        risk = 2,
        via = "claude",
        timestamp = "14:01:44",
        elapsedSeconds = 45,
        ttlSeconds = 60,
        session = "a84e7bf",
        prompt = "",
        workingDir = "/Users/mike/dev/agent-buddy",
        riskAssessment = "Read-only network request.",
    ),
    ApprovalQueueItem(
        id = "3",
        tool = "Edit",
        source = Source.COPILOT,
        summary = "Write src/server/ApprovalServer.kt (+124 / −12)",
        risk = 1,
        via = "copilot",
        timestamp = "14:01:02",
        elapsedSeconds = 55,
        ttlSeconds = 60,
        session = "b9a02cd",
        prompt = "",
        workingDir = "/Users/mike/dev/agent-buddy",
        riskAssessment = "Edits match existing style; no secrets touched.",
    ),
)[idx.coerceIn(0, 2)]

private fun sampleList() = listOf(sample(0), sample(1), sample(2))

private fun sampleAskUserQuestion(): ApprovalQueueItem {
    val input = mapOf(
        "questions" to kotlinx.serialization.json.JsonArray(
            listOf(
                kotlinx.serialization.json.JsonObject(
                    mapOf(
                        "question" to kotlinx.serialization.json.JsonPrimitive("Which database should the project use?"),
                        "header" to kotlinx.serialization.json.JsonPrimitive("Database"),
                        "multiSelect" to kotlinx.serialization.json.JsonPrimitive(false),
                        "options" to kotlinx.serialization.json.JsonArray(
                            listOf(
                                kotlinx.serialization.json.JsonObject(
                                    mapOf(
                                        "label" to kotlinx.serialization.json.JsonPrimitive("PostgreSQL"),
                                        "description" to kotlinx.serialization.json.JsonPrimitive("Robust relational DB."),
                                    ),
                                ),
                                kotlinx.serialization.json.JsonObject(
                                    mapOf(
                                        "label" to kotlinx.serialization.json.JsonPrimitive("SQLite"),
                                        "description" to kotlinx.serialization.json.JsonPrimitive("Lightweight embedded DB."),
                                    ),
                                ),
                            ),
                        ),
                    ),
                ),
                kotlinx.serialization.json.JsonObject(
                    mapOf(
                        "question" to kotlinx.serialization.json.JsonPrimitive("Which features should we enable?"),
                        "header" to kotlinx.serialization.json.JsonPrimitive("Features"),
                        "multiSelect" to kotlinx.serialization.json.JsonPrimitive(true),
                        "options" to kotlinx.serialization.json.JsonArray(
                            listOf(
                                kotlinx.serialization.json.JsonObject(
                                    mapOf(
                                        "label" to kotlinx.serialization.json.JsonPrimitive("Auth"),
                                        "description" to kotlinx.serialization.json.JsonPrimitive("User login / signup."),
                                    ),
                                ),
                                kotlinx.serialization.json.JsonObject(
                                    mapOf(
                                        "label" to kotlinx.serialization.json.JsonPrimitive("Billing"),
                                        "description" to kotlinx.serialization.json.JsonPrimitive("Stripe integration."),
                                    ),
                                ),
                            ),
                        ),
                    ),
                ),
            ),
        ),
    )
    val request = ApprovalRequest(
        id = "preview-auq",
        source = Source.CLAUDE_CODE,
        toolType = ToolType.ASK_USER_QUESTION,
        hookInput = com.mikepenz.agentbuddy.model.HookInput(
            sessionId = "sess-auq",
            toolName = "AskUserQuestion",
            toolInput = input,
            cwd = "/Users/mike/dev/agent-buddy",
        ),
        timestamp = kotlinx.datetime.Clock.System.now(),
        rawRequestJson = "{}",
    )
    val qd = com.mikepenz.agentbuddy.model.SpecialToolParser.parseUserQuestion(input)
    return ApprovalQueueItem(
        id = "preview-auq",
        tool = "AskUserQuestion",
        toolType = ToolType.ASK_USER_QUESTION,
        source = Source.CLAUDE_CODE,
        summary = "• Which database should the project use?\n• Which features should we enable?",
        risk = null,
        via = "claude",
        timestamp = "14:02:50",
        elapsedSeconds = 0,
        ttlSeconds = 0,
        session = "sess-auq",
        prompt = "Claude Code needs answers before it can continue.",
        workingDir = "/Users/mike/dev/agent-buddy",
        riskAssessment = "Risk analysis is skipped for AskUserQuestion.",
        riskAnalysisEnabled = false,
        questionData = qd,
        request = request,
    )
}

private fun samplePlan(): ApprovalQueueItem {
    val planText = "## Migration Plan\n\n1. Audit existing code\n2. Freeze dependencies\n3. Run baseline tests\n4. Apply migrations\n5. Update documentation\n6. Re-run integration suite"
    val input = mapOf(
        "plan" to kotlinx.serialization.json.JsonPrimitive(planText),
    )
    val request = ApprovalRequest(
        id = "preview-plan",
        source = Source.CLAUDE_CODE,
        toolType = ToolType.PLAN,
        hookInput = com.mikepenz.agentbuddy.model.HookInput(
            sessionId = "sess-plan",
            toolName = "ExitPlanMode",
            toolInput = input,
            cwd = "/Users/mike/dev/agent-buddy",
        ),
        timestamp = kotlinx.datetime.Clock.System.now(),
        rawRequestJson = "{}",
    )
    val pd = com.mikepenz.agentbuddy.model.SpecialToolParser.parsePlanReview(input)
    return ApprovalQueueItem(
        id = "preview-plan",
        tool = "ExitPlanMode",
        toolType = ToolType.PLAN,
        source = Source.CLAUDE_CODE,
        summary = "## Migration Plan\n1. Audit existing code\n2. Freeze dependencies",
        risk = null,
        via = "claude",
        timestamp = "14:03:02",
        elapsedSeconds = 0,
        ttlSeconds = 0,
        session = "sess-plan",
        prompt = "Claude Code wants to exit plan mode and start executing.",
        workingDir = "/Users/mike/dev/agent-buddy",
        riskAssessment = "Risk analysis is skipped for Plan approvals.",
        riskAnalysisEnabled = false,
        planData = pd,
        request = request,
    )
}

private fun specialSampleList() = listOf(sampleAskUserQuestion(), samplePlan(), sample(0))

@Preview(widthDp = 1088, heightDp = 860)
@Composable
private fun PreviewApprovalsScreen() {
    PreviewScaffold {
        ApprovalsScreen(items = sampleList())
    }
}

@Preview(widthDp = 1088, heightDp = 860)
@Composable
private fun PreviewApprovalsEmpty() {
    PreviewScaffold {
        ApprovalsScreen(items = emptyList())
    }
}

@Preview(widthDp = 400, heightDp = 860)
@Composable
private fun PreviewQueueOnly() {
    PreviewScaffold {
        ApprovalsScreen(items = sampleList())
    }
}

// ── Light theme & state coverage (iter 3) ──────────────────────────────────

@Preview(widthDp = 1088, heightDp = 860)
@Composable
private fun PreviewApprovalsScreenLight() {
    PreviewScaffold(themeMode = com.mikepenz.agentbuddy.model.ThemeMode.LIGHT) {
        ApprovalsScreen(items = sampleList())
    }
}

@Preview(widthDp = 1088, heightDp = 860)
@Composable
private fun PreviewApprovalsEmptyLight() {
    PreviewScaffold(themeMode = com.mikepenz.agentbuddy.model.ThemeMode.LIGHT) {
        ApprovalsScreen(items = emptyList())
    }
}

@Preview(widthDp = 1088, heightDp = 860)
@Composable
private fun PreviewApprovalsLoading() {
    PreviewScaffold {
        com.mikepenz.agentbuddy.ui.components.ScreenLoadingState(label = "Streaming approvals…")
    }
}

@Preview(widthDp = 1088, heightDp = 860)
@Composable
private fun PreviewApprovalsError() {
    PreviewScaffold {
        com.mikepenz.agentbuddy.ui.components.ScreenErrorState(
            title = "Approval stream interrupted",
            message = "The hook server stopped responding. Restart Agent Buddy or check the logs.",
        )
    }
}

@Preview(widthDp = 620, heightDp = 860)
@Composable
private fun PreviewApprovalsMedium() {
    PreviewScaffold {
        ApprovalsScreen(
            items = sampleList(),
            modifier = Modifier.width(620.dp).fillMaxHeight(),
        )
    }
}

@Preview(widthDp = 620, heightDp = 860)
@Composable
private fun PreviewApprovalsMediumLight() {
    PreviewScaffold(themeMode = com.mikepenz.agentbuddy.model.ThemeMode.LIGHT) {
        ApprovalsScreen(
            items = sampleList(),
            modifier = Modifier.width(620.dp).fillMaxHeight(),
        )
    }
}

@Preview(widthDp = 620, heightDp = 860)
@Composable
private fun PreviewApprovalsMediumDetail() {
    PreviewScaffold {
        ApprovalsScreen(
            items = sampleList(),
            modifier = Modifier.width(620.dp).fillMaxHeight(),
            initialMediumDetailId = sampleList().first().id,
        )
    }
}

@Preview(widthDp = 1088, heightDp = 860)
@Composable
private fun PreviewApprovalsSpecialWide() {
    PreviewScaffold {
        ApprovalsScreen(items = specialSampleList())
    }
}

@Preview(widthDp = 620, heightDp = 860)
@Composable
private fun PreviewApprovalsSpecialMediumList() {
    PreviewScaffold {
        ApprovalsScreen(
            items = specialSampleList(),
            modifier = Modifier.width(620.dp).fillMaxHeight(),
        )
    }
}

@Preview(widthDp = 620, heightDp = 860)
@Composable
private fun PreviewApprovalsSpecialMediumAskDetail() {
    PreviewScaffold {
        ApprovalsScreen(
            items = specialSampleList(),
            modifier = Modifier.width(620.dp).fillMaxHeight(),
            initialMediumDetailId = "preview-auq",
        )
    }
}

@Preview(widthDp = 620, heightDp = 860)
@Composable
private fun PreviewApprovalsSpecialMediumPlanDetail() {
    PreviewScaffold {
        ApprovalsScreen(
            items = specialSampleList(),
            modifier = Modifier.width(620.dp).fillMaxHeight(),
            initialMediumDetailId = "preview-plan",
        )
    }
}

@Preview(widthDp = 400, heightDp = 420)
@Composable
private fun PreviewQueueRowSpecial() {
    PreviewScaffold {
        androidx.compose.foundation.layout.Column(
            modifier = Modifier.width(400.dp).padding(10.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            QueueRow(item = sampleAskUserQuestion(), active = false, onClick = {})
            QueueRow(item = samplePlan(), active = true, onClick = {})
        }
    }
}

@Preview(widthDp = 620, heightDp = 420)
@Composable
private fun PreviewMediumQueueRowSpecial() {
    PreviewScaffold {
        androidx.compose.foundation.layout.Column(
            modifier = Modifier.width(620.dp),
        ) {
            MediumQueueRow(item = sampleAskUserQuestion(), onClick = {})
            MediumQueueRow(item = samplePlan(), onClick = {})
        }
    }
}

@Preview(widthDp = 400, heightDp = 420)
@Composable
private fun PreviewQueueRowHover() {
    PreviewScaffold {
        androidx.compose.foundation.layout.Column(
            modifier = Modifier.width(400.dp).padding(10.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            QueueRow(item = sample(0), active = false, onClick = {})
            QueueRow(item = sample(1), active = true, onClick = {})
            QueueRow(item = sample(2), active = false, onClick = {})
        }
    }
}
