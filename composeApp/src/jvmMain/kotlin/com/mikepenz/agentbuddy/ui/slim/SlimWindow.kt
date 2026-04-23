package com.mikepenz.agentbuddy.ui.slim

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
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
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.foundation.focusable
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import com.mikepenz.agentbuddy.ui.approvals.ToolContentSummary
import com.mikepenz.agentbuddy.ui.icons.LucideFolder
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mikepenz.agentbuddy.model.ApprovalRequest
import com.mikepenz.agentbuddy.model.HookInput
import com.mikepenz.agentbuddy.model.Question
import com.mikepenz.agentbuddy.model.QuestionOption
import com.mikepenz.agentbuddy.model.Source
import com.mikepenz.agentbuddy.model.SpecialToolParser
import com.mikepenz.agentbuddy.model.ThemeMode
import com.mikepenz.agentbuddy.model.ToolType
import com.mikepenz.agentbuddy.model.UserQuestionData
import com.mikepenz.agentbuddy.ui.approvals.AskUserQuestionCard
import com.mikepenz.agentbuddy.ui.approvals.PlanCard
import com.mikepenz.agentbuddy.ui.components.HorizontalHairline
import com.mikepenz.agentbuddy.ui.components.LocalPreviewHoverOverride
import com.mikepenz.agentbuddy.ui.components.SlimAllowButton
import com.mikepenz.agentbuddy.ui.components.SlimDenyButton
import com.mikepenz.agentbuddy.ui.components.SlimTertiaryLink
import com.mikepenz.agentbuddy.ui.components.SourceTag
import com.mikepenz.agentbuddy.ui.components.TagSize
import com.mikepenz.agentbuddy.ui.components.ToolTag
import com.mikepenz.agentbuddy.ui.icons.LucideChevronRight
import com.mikepenz.agentbuddy.ui.icons.LucideExpand
import com.mikepenz.agentbuddy.ui.icons.LucideShieldCheck
import com.mikepenz.agentbuddy.ui.theme.AccentEmerald
import com.mikepenz.agentbuddy.ui.theme.AgentBuddyColors
import com.mikepenz.agentbuddy.ui.theme.DangerRed
import com.mikepenz.agentbuddy.ui.theme.PreviewScaffold
import com.mikepenz.agentbuddy.ui.theme.WarnYellow
import com.mikepenz.agentbuddy.ui.theme.riskColor
import kotlinx.datetime.Clock
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject

data class SlimItem(
    val id: String,
    val source: Source,
    val tool: String,
    val toolType: ToolType = ToolType.DEFAULT,
    val summary: String,
    val time: String,
    val risk: Int? = null,
    val riskAssessment: String? = null,
    val riskVia: String? = null,
    /**
     * Full request, required for tool-specific slim renderers (ASK_USER_QUESTION
     * needs the question list, PLAN needs the plan body). When `null` the hero
     * falls back to the generic summary+allow/deny layout — used by previews
     * that construct `SlimItem`s directly.
     */
    val request: ApprovalRequest? = null,
)

enum class SlimAction { Deny, Allow, AllowSession, AskAnotherAgent }

@Composable
fun SlimWindow(
    items: List<SlimItem>,
    onResolve: (id: String, action: SlimAction) -> Unit,
    onExpand: () -> Unit,
    modifier: Modifier = Modifier,
    onApproveWithInput: (id: String, Map<String, JsonElement>) -> Unit = { _, _ -> },
) {
    Column(
        modifier = modifier
            .width(340.dp)
            .height(680.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(AgentBuddyColors.background)
            .border(1.dp, AgentBuddyColors.line2, RoundedCornerShape(14.dp)),
    ) {
        SlimTitleBar(count = items.size, onExpand = onExpand)
        SlimContent(
            items = items,
            onResolve = onResolve,
            onExpand = onExpand,
            onApproveWithInput = onApproveWithInput,
            modifier = Modifier.weight(1f),
        )
    }
}

/**
 * Chromeless slim approvals content. All items are expanded by default and
 * can be individually collapsed. Each item has a "View details" link that
 * opens a full-coverage overlay with rich tool content and risk assessment.
 * Press ESC to close the overlay.
 */
@Composable
fun SlimContent(
    items: List<SlimItem>,
    onResolve: (id: String, action: SlimAction) -> Unit,
    onExpand: () -> Unit,
    modifier: Modifier = Modifier,
    onApproveWithInput: (id: String, Map<String, JsonElement>) -> Unit = { _, _ -> },
) {
    // All items start expanded; new items auto-expand when added.
    var expandedIds by remember { mutableStateOf(items.map { it.id }.toSet()) }
    LaunchedEffect(items.map { it.id }) {
        val incoming = items.map { it.id }.toSet() - expandedIds
        if (incoming.isNotEmpty()) expandedIds = expandedIds + incoming
    }
    var detailItem by remember { mutableStateOf<SlimItem?>(null) }

    Box(modifier = modifier.fillMaxSize().background(AgentBuddyColors.background)) {
        Column(modifier = Modifier.fillMaxSize()) {
            if (items.isEmpty()) {
                SlimEmptyState(modifier = Modifier.weight(1f).fillMaxWidth())
            } else {
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .verticalScroll(rememberScrollState()),
                ) {
                    items.forEachIndexed { idx, item ->
                        if (idx > 0) {
                            Box(Modifier.fillMaxWidth().height(1.dp).background(AgentBuddyColors.line2))
                        }
                        SlimItemCard(
                            item = item,
                            isExpanded = item.id in expandedIds,
                            onToggle = {
                                expandedIds = if (item.id in expandedIds)
                                    expandedIds - item.id else expandedIds + item.id
                            },
                            onResolve = onResolve,
                            onApproveWithInput = onApproveWithInput,
                            onViewDetail = { detailItem = item },
                        )
                    }
                }
            }
            SlimFooter(onExpand = onExpand)
        }

        detailItem?.let { item ->
            SlimDetailOverlay(
                item = item,
                onClose = { detailItem = null },
                onResolve = { action ->
                    onResolve(item.id, action)
                    detailItem = null
                },
                onApproveWithInput = { updated ->
                    onApproveWithInput(item.id, updated)
                    detailItem = null
                },
            )
        }
    }
}

@Composable
private fun SlimItemCard(
    item: SlimItem,
    isExpanded: Boolean,
    onToggle: () -> Unit,
    onResolve: (String, SlimAction) -> Unit,
    onApproveWithInput: (String, Map<String, JsonElement>) -> Unit,
    onViewDetail: () -> Unit,
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        // Header row — always visible, click to collapse/expand
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { onToggle() }
                .padding(start = 14.dp, end = 14.dp, top = 12.dp, bottom = if (isExpanded) 0.dp else 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            if (isExpanded) {
                SourceTag(source = item.source)
                Text("·", color = AgentBuddyColors.inkSubtle, fontSize = 10.sp)
            } else {
                val dotColor = riskColor(item.risk ?: 1).copy(alpha = 0.7f)
                Box(Modifier.size(7.dp).clip(CircleShape).background(dotColor))
                ToolTag(toolName = item.tool, toolType = item.toolType, size = TagSize.SMALL)
                Text(
                    text = item.summary,
                    color = AgentBuddyColors.inkSecondary,
                    fontSize = 11.sp,
                    fontFamily = FontFamily.Monospace,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
            }
            Spacer(Modifier.weight(1f))
            item.risk?.let { RiskPill(risk = it) }
            Icon(
                imageVector = LucideChevronRight,
                contentDescription = if (isExpanded) "Collapse" else "Expand",
                tint = AgentBuddyColors.inkMuted,
                modifier = Modifier.size(10.dp).rotate(if (isExpanded) 90f else 0f),
            )
        }

        AnimatedVisibility(visible = isExpanded) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 14.dp, end = 14.dp, top = 10.dp, bottom = 14.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                SlimHeroBody(item = item, onResolve = onResolve, onApproveWithInput = onApproveWithInput)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    SlimTertiaryLink(text = "View details", onClick = onViewDetail)
                    if (item.toolType == ToolType.DEFAULT) {
                        Text(
                            text = "·",
                            color = AgentBuddyColors.inkSubtle,
                            fontSize = 10.sp,
                            modifier = Modifier.padding(horizontal = 4.dp),
                        )
                        SlimTertiaryLink(
                            text = "Always allow",
                            onClick = { onResolve(item.id, SlimAction.AllowSession) },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun SlimDetailOverlay(
    item: SlimItem,
    onClose: () -> Unit,
    onResolve: (SlimAction) -> Unit,
    onApproveWithInput: (Map<String, JsonElement>) -> Unit,
) {
    val focusRequester = remember { FocusRequester() }
    LaunchedEffect(Unit) { focusRequester.requestFocus() }
    val req = item.request

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(AgentBuddyColors.background)
            .focusRequester(focusRequester)
            .focusable()
            .onKeyEvent { event ->
                if (event.key == Key.Escape && event.type == KeyEventType.KeyDown) {
                    onClose(); true
                } else false
            },
    ) {
        // Back bar
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(40.dp)
                .clickable { onClose() }
                .padding(horizontal = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Icon(LucideChevronRight, contentDescription = "Back", tint = AgentBuddyColors.inkTertiary, modifier = Modifier.size(12.dp).rotate(180f))
            Text("Back", color = AgentBuddyColors.inkTertiary, fontSize = 11.5.sp)
            Spacer(Modifier.weight(1f))
            Text("ESC", color = AgentBuddyColors.inkMuted, fontSize = 9.sp, fontFamily = FontFamily.Monospace)
        }
        HorizontalHairline()

        // Scrollable detail body
        Column(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 14.dp, vertical = 14.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            // Tag header
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                modifier = Modifier.fillMaxWidth(),
            ) {
                ToolTag(toolName = item.tool, toolType = item.toolType, size = TagSize.SMALL)
                SourceTag(source = item.source)
                item.risk?.let { RiskPill(risk = it) }
                Spacer(Modifier.weight(1f))
                req?.hookInput?.sessionId?.takeIf { it.isNotBlank() }?.let { sid ->
                    Text(
                        text = "session · ${sid.take(7)}",
                        color = AgentBuddyColors.inkMuted,
                        fontSize = 9.sp,
                        fontFamily = FontFamily.Monospace,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }

            // Rich tool content
            if (req != null) {
                ToolContentSummary(
                    toolName = req.hookInput.toolName,
                    toolInput = req.hookInput.toolInput,
                    cwd = req.hookInput.cwd,
                )
            } else {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(6.dp))
                        .background(AgentBuddyColors.surface)
                        .border(1.dp, AgentBuddyColors.line1, RoundedCornerShape(6.dp))
                        .padding(10.dp),
                ) {
                    Text(item.summary, color = AgentBuddyColors.inkPrimary, fontSize = 11.5.sp, fontFamily = FontFamily.Monospace)
                }
            }

            // Working directory
            req?.hookInput?.cwd?.takeIf { it.isNotBlank() }?.let { cwd ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(6.dp))
                        .background(AgentBuddyColors.surface)
                        .border(1.dp, AgentBuddyColors.line1, RoundedCornerShape(6.dp))
                        .padding(horizontal = 10.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Icon(LucideFolder, contentDescription = null, tint = AgentBuddyColors.inkMuted, modifier = Modifier.size(10.dp))
                    Text(cwd, color = AgentBuddyColors.inkSecondary, fontSize = 11.sp, fontFamily = FontFamily.Monospace, maxLines = 2, overflow = TextOverflow.Ellipsis)
                }
            }

            // Risk assessment
            item.risk?.let { risk ->
                val c = riskColor(risk)
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(6.dp))
                        .background(AgentBuddyColors.surface)
                        .border(1.dp, AgentBuddyColors.line1, RoundedCornerShape(6.dp))
                        .padding(10.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    Box(
                        modifier = Modifier.size(28.dp).clip(RoundedCornerShape(6.dp)).background(c.copy(alpha = 0.12f)),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text("$risk", color = c, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, fontFamily = FontFamily.Monospace)
                    }
                    Column(modifier = Modifier.weight(1f)) {
                        val via = item.riskVia?.uppercase() ?: "AI"
                        Text("LEVEL $risk · GRADED BY $via", color = AgentBuddyColors.inkMuted, fontSize = 9.sp, fontWeight = FontWeight.SemiBold, letterSpacing = 0.4.sp)
                        item.riskAssessment?.takeIf { it.isNotBlank() }?.let { assessment ->
                            Spacer(Modifier.height(4.dp))
                            Text(assessment, color = AgentBuddyColors.inkPrimary, fontSize = 11.sp, lineHeight = 16.sp)
                        }
                    }
                }
            }
        }

        // Footer actions
        HorizontalHairline()
        Column(
            modifier = Modifier.fillMaxWidth().background(AgentBuddyColors.surface).padding(horizontal = 14.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                SlimDenyButton(modifier = Modifier.weight(1f), onClick = { onResolve(SlimAction.Deny) })
                SlimAllowButton(modifier = Modifier.weight(1f), onClick = { onResolve(SlimAction.Allow) })
            }
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center) {
                SlimTertiaryLink(text = "Always allow", onClick = { onResolve(SlimAction.AllowSession) })
            }
        }
    }
}

@Composable
private fun SlimTitleBar(count: Int, onExpand: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(40.dp)
            .background(AgentBuddyColors.chrome)
            .border(0.dp, Color.Transparent)
            .padding(horizontal = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        TrafficLights()
        Spacer(Modifier.width(4.dp))
        Row(
            modifier = Modifier.weight(1f),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(7.dp),
            ) {
                Box(
                    modifier = Modifier
                        .size(16.dp)
                        .clip(RoundedCornerShape(4.dp))
                        .background(AccentEmerald),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = LucideShieldCheck,
                        contentDescription = null,
                        tint = AgentBuddyColors.accentEmeraldInk,
                        modifier = Modifier.size(9.dp),
                    )
                }
                Text(
                    text = "Agent Buddy",
                    color = AgentBuddyColors.inkSecondary,
                    fontSize = 11.5.sp,
                    fontWeight = FontWeight.SemiBold,
                    letterSpacing = (-0.1).sp,
                )
                if (count > 0) {
                    Box(
                        modifier = Modifier
                            .heightIn(min = 14.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(AccentEmerald)
                            .padding(horizontal = 6.dp, vertical = 1.dp),
                    ) {
                        Text(
                            text = count.toString(),
                            color = AgentBuddyColors.accentEmeraldInk,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.SemiBold,
                        )
                    }
                }
            }
        }
        ChromeIconButton(onClick = onExpand) {
            Icon(
                imageVector = LucideExpand,
                contentDescription = "Expand",
                tint = AgentBuddyColors.inkTertiary,
                modifier = Modifier.size(12.dp),
            )
        }
    }
    HorizontalHairline()
}

@Composable
private fun TrafficLights() {
    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        TrafficDot(Color(0xFFFF5F57))
        TrafficDot(Color(0xFFFEBC2E))
        TrafficDot(Color(0xFF28C840))
    }
}

@Composable
private fun TrafficDot(color: Color) {
    Box(Modifier.size(10.dp).clip(CircleShape).background(color))
}

@Composable
private fun ChromeIconButton(onClick: () -> Unit, content: @Composable () -> Unit) {
    val interaction = remember { MutableInteractionSource() }
    val live by interaction.collectIsHoveredAsState()
    val hovered = LocalPreviewHoverOverride.current ?: live
    Box(
        modifier = Modifier
            .size(28.dp)
            .clip(RoundedCornerShape(5.dp))
            .background(if (hovered) AgentBuddyColors.surface2 else Color.Transparent)
            .hoverable(interaction)
            .clickable(interactionSource = interaction, indication = null) { onClick() },
        contentAlignment = Alignment.Center,
    ) { content() }
}

@Composable
private fun SlimEmptyState(modifier: Modifier = Modifier) {
    Column(
        modifier = modifier.padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Box(
            modifier = Modifier
                .size(44.dp)
                .clip(RoundedCornerShape(11.dp))
                .background(AgentBuddyColors.surface)
                .border(1.dp, AgentBuddyColors.line1, RoundedCornerShape(11.dp)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = LucideShieldCheck,
                contentDescription = null,
                tint = AccentEmerald,
                modifier = Modifier.size(20.dp),
            )
        }
        Spacer(Modifier.height(14.dp))
        Text(
            text = "All clear",
            color = AgentBuddyColors.inkPrimary,
            fontSize = 13.sp,
            fontWeight = FontWeight.SemiBold,
        )
        Spacer(Modifier.height(4.dp))
        Text(
            text = "No pending approvals.",
            color = AgentBuddyColors.inkTertiary,
            fontSize = 11.5.sp,
        )
    }
}

@Composable
private fun SlimHeroBody(
    item: SlimItem,
    onResolve: (String, SlimAction) -> Unit,
    onApproveWithInput: (String, Map<String, JsonElement>) -> Unit,
) {
    // Dispatch on tool type so that AskUserQuestion / Plan get their proper
    // interactive renderers instead of the generic "command + allow/deny" hero,
    // which cannot express the data these tools carry (multi-select options,
    // long plan bodies, etc.).
    val req = item.request
    val questionData = remember(req) {
        if (req?.toolType == ToolType.ASK_USER_QUESTION)
            SpecialToolParser.parseUserQuestion(req.hookInput.toolInput)
        else null
    }
    val planData = remember(req) {
        if (req?.toolType == ToolType.PLAN)
            SpecialToolParser.parsePlanReview(req.hookInput.toolInput)
        else null
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 14.dp, end = 14.dp, top = 2.dp, bottom = 14.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        when {
            req != null && questionData != null ->
                SlimAskUserQuestion(
                    item = item,
                    request = req,
                    questionData = questionData,
                    onApproveWithInput = onApproveWithInput,
                    onResolve = onResolve,
                )

            req != null && planData != null ->
                SlimPlanReview(
                    item = item,
                    request = req,
                    planData = planData,
                    onResolve = onResolve,
                )

            else -> SlimDefaultHero(item = item, onResolve = onResolve)
        }
    }
}

@Composable
private fun SlimDefaultHero(
    item: SlimItem,
    onResolve: (String, SlimAction) -> Unit,
) {
    // Tool + command
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            ToolTag(toolName = item.tool, toolType = item.toolType, size = TagSize.SMALL)
            Text(
                text = "wants to run",
                color = AgentBuddyColors.inkTertiary,
                fontSize = 11.5.sp,
            )
        }
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(6.dp))
                .background(AgentBuddyColors.background)
                .border(1.dp, AgentBuddyColors.line1, RoundedCornerShape(6.dp))
                .padding(horizontal = 10.dp, vertical = 8.dp),
        ) {
            Text(
                text = item.summary,
                color = AgentBuddyColors.inkPrimary,
                fontSize = 11.5.sp,
                fontFamily = FontFamily.Monospace,
                letterSpacing = (-0.1).sp,
                lineHeight = 17.sp,
                maxLines = 3,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }

    // Primary actions
    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        SlimDenyButton(
            modifier = Modifier.weight(1f),
            onClick = { onResolve(item.id, SlimAction.Deny) },
        )
        SlimAllowButton(
            modifier = Modifier.weight(1f),
            onClick = { onResolve(item.id, SlimAction.Allow) },
        )
    }
}

@Composable
private fun SlimAskUserQuestion(
    item: SlimItem,
    request: com.mikepenz.agentbuddy.model.ApprovalRequest,
    questionData: UserQuestionData,
    onApproveWithInput: (String, Map<String, JsonElement>) -> Unit,
    onResolve: (String, SlimAction) -> Unit,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        ToolTag(toolName = item.tool, toolType = item.toolType, size = TagSize.SMALL)
        Text(
            text = "is asking a question",
            color = AgentBuddyColors.inkTertiary,
            fontSize = 11.5.sp,
        )
    }
    // Reuse the full-view AskUserQuestionCard — it's already designed for a
    // ~350dp column and handles the option/radio/checkbox/custom-answer UX
    // plus the Submit/Dismiss wiring. Rendered inside the slim Column so it
    // inherits our AgentBuddyTheme.
    AskUserQuestionCard(
        request = request,
        questionData = questionData,
        onApproveWithInput = { updated -> onApproveWithInput(item.id, updated) },
        onDismiss = { onResolve(item.id, SlimAction.AskAnotherAgent) },

    )
}

@Composable
private fun SlimPlanReview(
    item: SlimItem,
    request: com.mikepenz.agentbuddy.model.ApprovalRequest,
    planData: com.mikepenz.agentbuddy.model.PlanReviewData,
    onResolve: (String, SlimAction) -> Unit,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        ToolTag(toolName = item.tool, toolType = item.toolType, size = TagSize.SMALL)
        Text(
            text = "is proposing a plan",
            color = AgentBuddyColors.inkTertiary,
            fontSize = 11.5.sp,
        )
    }
    PlanCard(
        request = request,
        planData = planData,
        onApprove = { onResolve(item.id, SlimAction.Allow) },
        onDeny = { onResolve(item.id, SlimAction.Deny) },

    )
}

@Composable
private fun RiskPill(risk: Int) {
    val color = when {
        risk >= 4 -> DangerRed
        risk >= 3 -> WarnYellow
        else -> AccentEmerald
    }
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(4.dp))
            .background(color.copy(alpha = 0.12f))
            .border(1.dp, color.copy(alpha = 0.25f), RoundedCornerShape(4.dp))
            .padding(horizontal = 7.dp, vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(5.dp),
    ) {
        Box(Modifier.size(5.dp).clip(CircleShape).background(color))
        Text(
            text = "RISK $risk",
            color = color,
            fontSize = 10.sp,
            fontWeight = FontWeight.SemiBold,
            letterSpacing = 0.3.sp,
        )
    }
}

@Composable
private fun SlimFooter(onExpand: () -> Unit) {
    HorizontalHairline()
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(AgentBuddyColors.surface)
            .padding(horizontal = 14.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(5.dp),
        ) {
            Box(Modifier.size(6.dp).clip(CircleShape).background(AccentEmerald))
            Text(
                text = ":19532 · 2 agents",
                color = AgentBuddyColors.inkMuted,
                fontSize = 10.5.sp,
                fontFamily = FontFamily.Monospace,
            )
        }
        Spacer(Modifier.weight(1f))
        FooterExpandLink(onClick = onExpand)
    }
}

@Composable
private fun FooterExpandLink(onClick: () -> Unit) {
    val interaction = remember { MutableInteractionSource() }
    val live by interaction.collectIsHoveredAsState()
    val hovered = LocalPreviewHoverOverride.current ?: live
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(4.dp))
            .hoverable(interaction)
            .clickable(interactionSource = interaction, indication = null) { onClick() }
            .padding(horizontal = 6.dp, vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(5.dp),
    ) {
        Text(
            text = "Open full view",
            color = if (hovered) AgentBuddyColors.inkPrimary else AgentBuddyColors.inkTertiary,
            fontSize = 10.5.sp,
        )
        Icon(
            imageVector = LucideChevronRight,
            contentDescription = null,
            tint = if (hovered) AgentBuddyColors.inkPrimary else AgentBuddyColors.inkTertiary,
            modifier = Modifier.size(10.dp),
        )
    }
}

// ── Previews ─────────────────────────────────────────────────────────────────

private fun sampleRequest(
    id: String,
    toolType: ToolType,
    toolName: String,
    toolInput: Map<String, JsonElement>,
    source: Source = Source.CLAUDE_CODE,
): ApprovalRequest = ApprovalRequest(
    id = id,
    source = source,
    toolType = toolType,
    hookInput = HookInput(
        sessionId = "slim-preview",
        toolName = toolName,
        toolInput = toolInput,
        cwd = "/home/user/project",
    ),
    timestamp = Clock.System.now(),
    rawRequestJson = "{}",
)

private val sampleItems = listOf(
    SlimItem(
        id = "1",
        source = Source.CLAUDE_CODE,
        tool = "Bash",
        summary = "rm -rf node_modules && pnpm install --frozen-lockfile",
        time = "2s",
        risk = 3,
        riskAssessment = "Destructive filesystem operation in project directory. Approved 3× recently.",
        riskVia = "claude",
    ),
    SlimItem(
        id = "2",
        source = Source.CLAUDE_CODE,
        tool = "WebFetch",
        summary = "https://api.github.com/repos/mikepenz/agent-buddy/releases/latest",
        time = "14s",
        risk = 1,
        riskAssessment = "Read-only network request to public GitHub API.",
        riskVia = "claude",
    ),
    SlimItem(
        id = "3",
        source = Source.COPILOT,
        tool = "Write",
        summary = "composeApp/src/commonMain/.../ApprovalsViewModel.kt",
        time = "28s",
        risk = 2,
        riskAssessment = "Edits existing file, no secrets touched.",
        riskVia = "copilot",
    ),
)

@Preview(widthDp = 380, heightDp = 900)
@Composable
private fun PreviewSlimWindow() {
    PreviewScaffold {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color(0xFF0B0B0D))
                .padding(20.dp),
            contentAlignment = Alignment.Center,
        ) {
            SlimWindow(
                items = sampleItems,
                onResolve = { _, _ -> },
                onExpand = {},
            )
        }
    }
}

@Preview(widthDp = 380, heightDp = 500)
@Composable
private fun PreviewSlimWindowSingle() {
    PreviewScaffold {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color(0xFF0B0B0D))
                .padding(20.dp),
            contentAlignment = Alignment.Center,
        ) {
            SlimWindow(
                items = listOf(sampleItems.first()),
                onResolve = { _, _ -> },
                onExpand = {},
            )
        }
    }
}

@Preview(widthDp = 380, heightDp = 620)
@Composable
private fun PreviewSlimWindowEmpty() {
    PreviewScaffold {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color(0xFF0B0B0D))
                .padding(20.dp),
            contentAlignment = Alignment.Center,
        ) {
            SlimWindow(
                items = emptyList(),
                onResolve = { _, _ -> },
                onExpand = {},
            )
        }
    }
}

private val askQuestionRequest = sampleRequest(
    id = "ask-1",
    toolType = ToolType.ASK_USER_QUESTION,
    toolName = "AskUserQuestion",
    toolInput = mapOf(
        "questions" to buildJsonArray {
            add(
                buildJsonObject {
                    put("header", JsonPrimitive("Database Choice"))
                    put("question", JsonPrimitive("Which database should we use?"))
                    put("multiSelect", JsonPrimitive(false))
                    put(
                        "options",
                        buildJsonArray {
                            add(
                                buildJsonObject {
                                    put("label", JsonPrimitive("PostgreSQL"))
                                    put("description", JsonPrimitive("Robust relational DB"))
                                },
                            )
                            add(
                                buildJsonObject {
                                    put("label", JsonPrimitive("SQLite"))
                                    put("description", JsonPrimitive("Lightweight embedded DB"))
                                },
                            )
                        },
                    )
                },
            )
        },
    ),
)

private val planRequest = sampleRequest(
    id = "plan-1",
    toolType = ToolType.PLAN,
    toolName = "ExitPlanMode",
    toolInput = mapOf(
        "plan" to JsonPrimitive(
            "## Implementation Plan\n\n1. Add data models\n2. Set up database\n3. Implement API\n4. Add auth\n5. Write tests",
        ),
    ),
)

private val askQuestionSlimItem = SlimItem(
    id = askQuestionRequest.id,
    source = askQuestionRequest.source,
    tool = askQuestionRequest.hookInput.toolName,
    toolType = askQuestionRequest.toolType,
    summary = "Which database should we use?",
    time = "3s",
    risk = null,
    request = askQuestionRequest,
)

private val planSlimItem = SlimItem(
    id = planRequest.id,
    source = planRequest.source,
    tool = planRequest.hookInput.toolName,
    toolType = planRequest.toolType,
    summary = "Implementation Plan",
    time = "5s",
    risk = null,
    request = planRequest,
)

@Preview(widthDp = 380, heightDp = 620)
@Composable
private fun PreviewSlimWindowAskUserQuestion() {
    PreviewScaffold {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color(0xFF0B0B0D))
                .padding(20.dp),
            contentAlignment = Alignment.Center,
        ) {
            SlimWindow(
                items = listOf(askQuestionSlimItem),
                onResolve = { _, _ -> },
                onExpand = {},
                onApproveWithInput = { _, _ -> },
            )
        }
    }
}

@Preview(widthDp = 380, heightDp = 720)
@Composable
private fun PreviewSlimWindowPlan() {
    PreviewScaffold {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color(0xFF0B0B0D))
                .padding(20.dp),
            contentAlignment = Alignment.Center,
        ) {
            SlimWindow(
                items = listOf(planSlimItem),
                onResolve = { _, _ -> },
                onExpand = {},
            )
        }
    }
}

@Preview(widthDp = 380, heightDp = 620)
@Composable
private fun PreviewSlimWindowMixedQueue() {
    PreviewScaffold {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color(0xFF0B0B0D))
                .padding(20.dp),
            contentAlignment = Alignment.Center,
        ) {
            SlimWindow(
                items = listOf(askQuestionSlimItem) + sampleItems,
                onResolve = { _, _ -> },
                onExpand = {},
                onApproveWithInput = { _, _ -> },
            )
        }
    }
}

@Preview(widthDp = 380, heightDp = 900)
@Composable
private fun PreviewSlimWindowHover() {
    PreviewScaffold {
        androidx.compose.runtime.CompositionLocalProvider(LocalPreviewHoverOverride provides true) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color(0xFF0B0B0D))
                    .padding(20.dp),
                contentAlignment = Alignment.Center,
            ) {
                SlimWindow(
                    items = sampleItems,
                    onResolve = { _, _ -> },
                    onExpand = {},
                )
            }
        }
    }
}

@Preview(widthDp = 380, heightDp = 700)
@Composable
private fun PreviewSlimWindowDetailOverlay() {
    PreviewScaffold {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color(0xFF0B0B0D))
                .padding(20.dp),
            contentAlignment = Alignment.Center,
        ) {
            Column(
                modifier = Modifier
                    .width(340.dp)
                    .height(680.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .background(AgentBuddyColors.background)
                    .border(1.dp, AgentBuddyColors.line2, RoundedCornerShape(14.dp)),
            ) {
                SlimTitleBar(count = 1, onExpand = {})
                SlimDetailOverlay(
                    item = sampleItems.first().copy(
                        riskAssessment = "Command deletes node_modules. Approved 3x recently in this project.",
                        riskVia = "claude",
                        request = sampleRequest("1", ToolType.DEFAULT, "Bash", mapOf("command" to JsonPrimitive("rm -rf node_modules && pnpm install --frozen-lockfile"))),
                    ),
                    onClose = {},
                    onResolve = {},
                    onApproveWithInput = {},
                )
            }
        }
    }
}

@Preview(widthDp = 380, heightDp = 900)
@Composable
private fun PreviewSlimWindowLight() {
    PreviewScaffold(themeMode = com.mikepenz.agentbuddy.model.ThemeMode.LIGHT) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color(0xFFEDEEF2))
                .padding(20.dp),
            contentAlignment = Alignment.Center,
        ) {
            SlimWindow(
                items = sampleItems,
                onResolve = { _, _ -> },
                onExpand = {},
            )
        }
    }
}
