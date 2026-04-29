package com.mikepenz.agentbelay.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
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
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.semantics.toggleableState
import androidx.compose.ui.state.ToggleableState
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mikepenz.agentbelay.model.Source
import com.mikepenz.agentbelay.model.ToolType
import com.mikepenz.agentbelay.ui.theme.AccentEmerald
import com.mikepenz.agentbelay.ui.theme.DangerRed
import com.mikepenz.agentbelay.ui.theme.AgentBelayColors
import com.mikepenz.agentbelay.ui.theme.InfoBlue
import com.mikepenz.agentbelay.ui.theme.PreviewScaffold
import com.mikepenz.agentbelay.ui.theme.WarnYellow
import com.mikepenz.agentbelay.ui.theme.riskColor
import com.mikepenz.agentbelay.ui.theme.sourceColor
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.material3.Icon
import androidx.compose.ui.graphics.vector.ImageVector
import com.mikepenz.agentbelay.ui.icons.FeatherExternalLink
import com.mikepenz.agentbelay.ui.icons.LucideClock
import com.mikepenz.agentbelay.ui.icons.LucideCopy

// ── ToolTag ─────────────────────────────────────────────────────────────────
// Colored badge with tool name, monospace font

@Composable
fun ToolTag(
    toolName: String,
    toolType: ToolType = ToolType.DEFAULT,
    size: TagSize = TagSize.MEDIUM,
    modifier: Modifier = Modifier,
) {
    val color = com.mikepenz.agentbelay.ui.theme.toolColor(toolName, toolType)
    val height = if (size == TagSize.SMALL) 18.dp else 20.dp
    val fontSize = if (size == TagSize.SMALL) 10.sp else 11.sp
    val hPad = if (size == TagSize.SMALL) 6.dp else 7.dp

    Box(
        modifier = modifier
            .height(height)
            .clip(RoundedCornerShape(4.dp))
            .background(color.copy(alpha = 0.12f))
            .padding(horizontal = hPad),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = toolName,
            color = color,
            fontSize = fontSize,
            lineHeight = fontSize,
            fontWeight = FontWeight.Medium,
            fontFamily = FontFamily.Monospace,
            letterSpacing = (-0.1).sp,
            maxLines = 1,
            style = androidx.compose.ui.text.TextStyle(
                lineHeightStyle = androidx.compose.ui.text.style.LineHeightStyle(
                    alignment = androidx.compose.ui.text.style.LineHeightStyle.Alignment.Center,
                    trim = androidx.compose.ui.text.style.LineHeightStyle.Trim.Both,
                ),
            ),
        )
    }
}

enum class TagSize { SMALL, MEDIUM }

// ── StatusPill ──────────────────────────────────────────────────────────────
// Pill with colored dot + label

enum class DecisionStatus(val label: String) {
    PENDING("Pending"),
    APPROVED("Approved"),
    AUTO_APPROVED("Auto-approved"),
    DENIED("Denied"),
    AUTO_DENIED("Auto-denied"),
    RESOLVED_EXT("Resolved externally"),
    PROTECTION_BLOCKED("Protection · blocked"),
    PROTECTION_LOGGED("Protection · logged"),
    TIMEOUT("Timed out"),
}

@Composable
fun StatusPill(
    status: DecisionStatus,
    size: TagSize = TagSize.MEDIUM,
    modifier: Modifier = Modifier,
    text: String = status.label,
) {
    val color = when (status) {
        DecisionStatus.PENDING -> WarnYellow
        DecisionStatus.APPROVED, DecisionStatus.AUTO_APPROVED -> AccentEmerald
        DecisionStatus.DENIED, DecisionStatus.AUTO_DENIED -> DangerRed
        DecisionStatus.PROTECTION_BLOCKED -> DangerRed
        DecisionStatus.PROTECTION_LOGGED -> InfoBlue
        DecisionStatus.RESOLVED_EXT -> AgentBelayColors.inkTertiary
        DecisionStatus.TIMEOUT -> AgentBelayColors.inkTertiary
    }
    val pulse = status == DecisionStatus.PENDING
    val height = if (size == TagSize.SMALL) 18.dp else 20.dp
    val fontSize = if (size == TagSize.SMALL) 10.sp else 11.sp

    val infiniteTransition = rememberInfiniteTransition(label = "pulse")
    val alpha by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = if (pulse) 0.35f else 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(900),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "pulse-alpha",
    )

    Row(
        modifier = modifier
            .height(height)
            .clip(RoundedCornerShape(height / 2))
            .background(color.copy(alpha = 0.1f))
            .padding(horizontal = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Box(
            modifier = Modifier
                .size(5.dp)
                .clip(CircleShape)
                .background(color.copy(alpha = if (pulse) alpha else 1f)),
        )
        Text(
            text = text,
            color = color,
            fontSize = fontSize,
            lineHeight = fontSize,
            fontWeight = FontWeight.Medium,
            letterSpacing = (-0.05).sp,
            maxLines = 1,
            style = androidx.compose.ui.text.TextStyle(
                lineHeightStyle = androidx.compose.ui.text.style.LineHeightStyle(
                    alignment = androidx.compose.ui.text.style.LineHeightStyle.Alignment.Center,
                    trim = androidx.compose.ui.text.style.LineHeightStyle.Trim.Both,
                ),
            ),
        )
    }
}

// ── RiskPill ────────────────────────────────────────────────────────────────
// Level number in colored square + "via {source}"

@Composable
fun RiskPill(
    level: Int?,
    via: String,
    modifier: Modifier = Modifier,
    analyzing: Boolean = false,
    error: Boolean = false,
    enabled: Boolean = true,
) {
    val pendingTint = when {
        error -> DangerRed
        !enabled -> AgentBelayColors.inkMuted
        else -> AgentBelayColors.inkTertiary
    }
    val color = if (level != null) riskColor(level) else pendingTint
    Row(
        modifier = modifier
            .height(20.dp)
            .clip(RoundedCornerShape(10.dp))
            .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(10.dp))
            .padding(start = 3.dp, end = 7.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        // Level circle (or pending indicator)
        Box(
            modifier = Modifier
                .size(14.dp)
                .clip(CircleShape)
                .background(color.copy(alpha = 0.18f)),
            contentAlignment = Alignment.Center,
        ) {
            if (level != null) {
                Text(
                    text = "$level",
                    color = color,
                    fontSize = 10.sp,
                    lineHeight = 10.sp,
                    fontWeight = FontWeight.SemiBold,
                    fontFamily = FontFamily.Monospace,
                    maxLines = 1,
                    style = androidx.compose.ui.text.TextStyle(
                        lineHeightStyle = androidx.compose.ui.text.style.LineHeightStyle(
                            alignment = androidx.compose.ui.text.style.LineHeightStyle.Alignment.Center,
                            trim = androidx.compose.ui.text.style.LineHeightStyle.Trim.Both,
                        ),
                    ),
                )
            } else {
                Icon(
                    imageVector = LucideClock,
                    contentDescription = null,
                    tint = color,
                    modifier = Modifier.size(9.dp),
                )
            }
        }
        val prefix = when {
            level != null -> "via"
            !enabled -> "risk analysis off"
            error -> "analysis failed · via"
            analyzing -> "analyzing · via"
            else -> "pending · via"
        }
        Text(
            text = prefix,
            color = AgentBelayColors.inkMuted,
            fontSize = 10.sp,
            lineHeight = 10.sp,
            letterSpacing = (-0.05).sp,
            maxLines = 1,
            style = androidx.compose.ui.text.TextStyle(
                lineHeightStyle = androidx.compose.ui.text.style.LineHeightStyle(
                    alignment = androidx.compose.ui.text.style.LineHeightStyle.Alignment.Center,
                    trim = androidx.compose.ui.text.style.LineHeightStyle.Trim.Both,
                ),
            ),
        )
        if (enabled || level != null) {
            Text(
                text = via,
                color = AgentBelayColors.inkSecondary,
                fontSize = 10.sp,
                lineHeight = 10.sp,
                fontWeight = FontWeight.Medium,
                letterSpacing = (-0.05).sp,
                maxLines = 1,
                style = androidx.compose.ui.text.TextStyle(
                    lineHeightStyle = androidx.compose.ui.text.style.LineHeightStyle(
                        alignment = androidx.compose.ui.text.style.LineHeightStyle.Alignment.Center,
                        trim = androidx.compose.ui.text.style.LineHeightStyle.Trim.Both,
                    ),
                ),
            )
        }
    }
}

// ── SourceTag ───────────────────────────────────────────────────────────────
// Dot + label for source

@Composable
fun SourceTag(
    source: Source,
    modifier: Modifier = Modifier,
) {
    val label = when (source) {
        Source.CLAUDE_CODE -> "Claude Code"
        Source.COPILOT -> "Copilot"
    }
    val dotColor = sourceColor(source)

    Row(
        modifier = modifier
            .height(20.dp)
            .padding(horizontal = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(5.dp),
    ) {
        Box(
            modifier = Modifier
                .size(5.dp)
                .clip(CircleShape)
                .background(dotColor),
        )
        Text(
            text = label,
            color = AgentBelayColors.inkSecondary,
            fontSize = 11.sp,
            lineHeight = 11.sp,
            fontWeight = FontWeight.Medium,
            letterSpacing = (-0.05).sp,
            maxLines = 1,
            style = androidx.compose.ui.text.TextStyle(
                lineHeightStyle = androidx.compose.ui.text.style.LineHeightStyle(
                    alignment = androidx.compose.ui.text.style.LineHeightStyle.Alignment.Center,
                    trim = androidx.compose.ui.text.style.LineHeightStyle.Trim.Both,
                ),
            ),
        )
    }
}

// ── SectionLabel ────────────────────────────────────────────────────────────

@Composable
fun SectionLabel(
    text: String,
    modifier: Modifier = Modifier,
) {
    Text(
        text = text.uppercase(),
        color = AgentBelayColors.inkMuted,
        fontSize = 10.5.sp,
        fontWeight = FontWeight.SemiBold,
        letterSpacing = 0.5.sp,
        maxLines = 1,
        overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
        modifier = modifier,
    )
}

// ── AgentBelayCard ──────────────────────────────────────────────────────────

/**
 * Preview-only override: when non-null, hover-aware components render as if hovered
 * (true) or not (false), bypassing the real InteractionSource. Null means live.
 */
val LocalPreviewHoverOverride = compositionLocalOf<Boolean?> { null }

@Composable
fun AgentBelayCard(
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    content: @Composable () -> Unit,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val liveHovered by interactionSource.collectIsHoveredAsState()
    val isHovered = LocalPreviewHoverOverride.current ?: liveHovered

    Box(
        modifier = modifier
            .clip(RoundedCornerShape(10.dp))
            .background(MaterialTheme.colorScheme.surface)
            .border(
                1.dp,
                if (isHovered && onClick != null) MaterialTheme.colorScheme.outlineVariant
                else MaterialTheme.colorScheme.outline,
                RoundedCornerShape(10.dp),
            )
            .then(
                if (onClick != null) Modifier
                    .hoverable(interactionSource)
                    .clickable(interactionSource = interactionSource, indication = null) { onClick() }
                else Modifier.hoverable(interactionSource)
            ),
    ) {
        content()
    }
}

// ── Toggle ──────────────────────────────────────────────────────────────────
// iOS-style toggle. Width 32, height 18. Sm variant 28×16.

@Composable
fun DesignToggle(
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    size: TagSize = TagSize.MEDIUM,
) {
    val w = if (size == TagSize.SMALL) 28.dp else 32.dp
    val h = if (size == TagSize.SMALL) 16.dp else 18.dp
    val d = if (size == TagSize.SMALL) 12.dp else 14.dp

    val trackColor by animateColorAsState(
        targetValue = if (checked) AccentEmerald else AgentBelayColors.surface2,
        animationSpec = tween(200, easing = FastOutSlowInEasing),
        label = "toggle-track",
    )
    val borderColor by animateColorAsState(
        targetValue = if (checked) Color.Transparent else AgentBelayColors.line1,
        animationSpec = tween(200, easing = FastOutSlowInEasing),
        label = "toggle-border",
    )
    val thumbColor by animateColorAsState(
        targetValue = if (checked) Color.White else AgentBelayColors.inkTertiary,
        animationSpec = tween(200, easing = FastOutSlowInEasing),
        label = "toggle-thumb-color",
    )
    // Thumb travel: 2.dp from left when off, (w - d - 3.dp) from left when on
    val thumbOffset by animateDpAsState(
        targetValue = if (checked) w - d - 3.dp else 2.dp,
        animationSpec = tween(200, easing = FastOutSlowInEasing),
        label = "toggle-thumb-offset",
    )

    Box(
        modifier = modifier
            .width(w)
            .height(h)
            .clip(RoundedCornerShape(h / 2))
            .background(trackColor)
            .border(1.dp, borderColor, RoundedCornerShape(h / 2))
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                role = Role.Switch,
                onClickLabel = if (checked) "Turn off" else "Turn on",
            ) { onCheckedChange(!checked) }
            .semantics {
                toggleableState = if (checked) ToggleableState.On else ToggleableState.Off
                stateDescription = if (checked) "On" else "Off"
            },
    ) {
        Box(
            modifier = Modifier
                .padding(start = thumbOffset)
                .align(Alignment.CenterStart)
                .size(d)
                .clip(CircleShape)
                .background(thumbColor),
        )
    }
}

// ── MetadataField ───────────────────────────────────────────────────────────
// Uppercase label (optional leading icon) + content slot. Used for detail
// fields and meta tiles (approvals detail, history expanded view, stats).

@Composable
fun MetadataField(
    label: String,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    labelGap: Dp = 5.dp,
    content: @Composable () -> Unit,
) {
    Column(modifier = modifier) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(5.dp),
        ) {
            if (icon != null) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = AgentBelayColors.inkMuted,
                    modifier = Modifier.size(10.dp),
                )
            }
            Text(
                text = label.uppercase(),
                color = AgentBelayColors.inkMuted,
                fontSize = 10.sp,
                fontWeight = FontWeight.SemiBold,
                letterSpacing = 0.5.sp,
            )
        }
        Spacer(Modifier.height(labelGap))
        content()
    }
}

// ── HorizontalHairline ──────────────────────────────────────────────────────
// Canonical 1dp divider. Defaults to fillMaxWidth + AgentBelayColors.line1.

@Composable
fun HorizontalHairline(
    modifier: Modifier = Modifier,
    color: Color = AgentBelayColors.line1,
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(1.dp)
            .background(color),
    )
}

// ── IconActionButton ────────────────────────────────────────────────────────
// Small icon-only button with hover background. Used for copy / refresh /
// open-in-browser / dismiss actions across detail panels.

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun IconActionButton(
    icon: ImageVector,
    contentDescription: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    size: Dp = 24.dp,
    iconSize: Dp = 12.dp,
    tint: Color = AgentBelayColors.inkSecondary,
    hoverBackground: Color = AgentBelayColors.surface3,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val liveHover by interactionSource.collectIsHoveredAsState()
    val hovered = LocalPreviewHoverOverride.current ?: liveHover
    val bg = if (enabled && hovered) hoverBackground else Color.Transparent
    val effectiveTint = if (enabled) tint else tint.copy(alpha = 0.38f)
    Box(
        modifier = modifier
            .size(size)
            .clip(RoundedCornerShape(4.dp))
            .background(bg)
            .then(
                if (enabled) Modifier
                    .hoverable(interactionSource)
                    .clickable(
                        interactionSource = interactionSource,
                        indication = null,
                        role = Role.Button,
                    ) { onClick() }
                else Modifier
            ),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = contentDescription,
            tint = effectiveTint,
            modifier = Modifier.size(iconSize),
        )
    }
}

// ── BadgeChip ───────────────────────────────────────────────────────────────
// Small tinted label for counts, tags, inline markers. Padding/font differ
// from ToolTag (which also carries tool-colour semantics and monospace).

@Composable
fun BadgeChip(
    text: String,
    modifier: Modifier = Modifier,
    background: Color = AgentBelayColors.surface,
    textColor: Color = AgentBelayColors.inkMuted,
    mono: Boolean = false,
    fontSize: androidx.compose.ui.unit.TextUnit = 10.5.sp,
    horizontalPadding: Dp = 5.dp,
    verticalPadding: Dp = 1.dp,
    radius: Dp = 3.dp,
) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(radius))
            .background(background)
            .padding(horizontal = horizontalPadding, vertical = verticalPadding),
    ) {
        Text(
            text = text,
            color = textColor,
            fontSize = fontSize,
            fontFamily = if (mono) FontFamily.Monospace else FontFamily.Default,
            maxLines = 1,
        )
    }
}

// ── ColoredIconTile ─────────────────────────────────────────────────────────
// Square tile with tinted background + centered icon. Used for integration
// plugs, category markers, error glyphs. The content-slot overload lets
// callers place a Text (risk level) or any glyph inside.

@Composable
fun ColoredIconTile(
    icon: ImageVector,
    tint: Color,
    contentDescription: String? = null,
    modifier: Modifier = Modifier,
    size: Dp = 36.dp,
    iconSize: Dp = 16.dp,
    radius: Dp = 8.dp,
    bgAlpha: Float = 0.14f,
    borderAlpha: Float = 0.22f,
) {
    ColoredIconTile(
        tint = tint,
        modifier = modifier,
        size = size,
        radius = radius,
        bgAlpha = bgAlpha,
        borderAlpha = borderAlpha,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = contentDescription,
            tint = tint,
            modifier = Modifier.size(iconSize),
        )
    }
}

@Composable
fun ColoredIconTile(
    tint: Color,
    modifier: Modifier = Modifier,
    size: Dp = 36.dp,
    radius: Dp = 8.dp,
    bgAlpha: Float = 0.14f,
    borderAlpha: Float = 0.22f,
    content: @Composable () -> Unit,
) {
    val shape = RoundedCornerShape(radius)
    Box(
        modifier = modifier
            .size(size)
            .clip(shape)
            .background(tint.copy(alpha = bgAlpha))
            .then(
                if (borderAlpha > 0f) Modifier.border(1.dp, tint.copy(alpha = borderAlpha), shape)
                else Modifier
            ),
        contentAlignment = Alignment.Center,
    ) {
        content()
    }
}

// ── Previews ─────────────────────────────────────────────────────────────────

@Preview(widthDp = 480, heightDp = 200)
@Composable
private fun PreviewToolTags() {
    PreviewScaffold {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                ToolTag("Bash")
                ToolTag("WebFetch")
                ToolTag("Write")
                ToolTag("Read")
                ToolTag("AskUserQuestion", toolType = ToolType.ASK_USER_QUESTION)
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                ToolTag("Bash", size = TagSize.SMALL)
                ToolTag("WebFetch", size = TagSize.SMALL)
                ToolTag("Edit", size = TagSize.SMALL)
            }
        }
    }
}

@Preview(widthDp = 480, heightDp = 200)
@Composable
private fun PreviewStatusPills() {
    PreviewScaffold {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                StatusPill(DecisionStatus.PENDING)
                StatusPill(DecisionStatus.APPROVED)
                StatusPill(DecisionStatus.DENIED)
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                StatusPill(DecisionStatus.AUTO_APPROVED)
                StatusPill(DecisionStatus.AUTO_DENIED)
                StatusPill(DecisionStatus.PROTECTION_BLOCKED)
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                StatusPill(DecisionStatus.PROTECTION_LOGGED)
                StatusPill(DecisionStatus.RESOLVED_EXT)
                StatusPill(DecisionStatus.TIMEOUT)
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                StatusPill(DecisionStatus.PENDING, size = TagSize.SMALL)
                StatusPill(DecisionStatus.APPROVED, size = TagSize.SMALL)
                StatusPill(DecisionStatus.DENIED, size = TagSize.SMALL)
            }
        }
    }
}

@Preview(widthDp = 480, heightDp = 120)
@Composable
private fun PreviewRiskPills() {
    PreviewScaffold {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                for (i in 1..5) {
                    RiskPill(level = i, via = "claude")
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                RiskPill(level = 2, via = "copilot")
                RiskPill(level = 4, via = "ollama")
            }
        }
    }
}

@Preview(widthDp = 320, heightDp = 120)
@Composable
private fun PreviewSourceTags() {
    PreviewScaffold {
        Row(
            modifier = Modifier.padding(16.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            SourceTag(Source.CLAUDE_CODE)
            SourceTag(Source.COPILOT)
        }
    }
}

@Preview(widthDp = 360, heightDp = 160)
@Composable
private fun PreviewToggles() {
    PreviewScaffold {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                DesignToggle(checked = true, onCheckedChange = {})
                DesignToggle(checked = false, onCheckedChange = {})
            }
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                DesignToggle(checked = true, onCheckedChange = {}, size = TagSize.SMALL)
                DesignToggle(checked = false, onCheckedChange = {}, size = TagSize.SMALL)
            }
        }
    }
}

@Preview(widthDp = 360, heightDp = 160)
@Composable
private fun PreviewSectionLabels() {
    PreviewScaffold {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            SectionLabel(text = "Up next")
            SectionLabel(text = "Connected")
            SectionLabel(text = "Settings")
        }
    }
}

@Preview(widthDp = 360, heightDp = 120)
@Composable
private fun PreviewHorizontalHairline() {
    PreviewScaffold {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text("above", color = AgentBelayColors.inkPrimary, fontSize = 12.sp)
            HorizontalHairline()
            Text("below", color = AgentBelayColors.inkPrimary, fontSize = 12.sp)
            HorizontalHairline(color = AccentEmerald.copy(alpha = 0.4f))
        }
    }
}

@Preview(widthDp = 360, heightDp = 220)
@Composable
private fun PreviewMetadataField() {
    PreviewScaffold {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            MetadataField(label = "File") {
                Text("/src/main/kotlin/App.kt", color = AgentBelayColors.inkPrimary, fontSize = 12.sp, fontFamily = FontFamily.Monospace)
            }
            MetadataField(label = "Source", icon = FeatherExternalLink) {
                Text("claude-code", color = AgentBelayColors.inkPrimary, fontSize = 12.sp)
            }
        }
    }
}

@Preview(widthDp = 520, heightDp = 200)
@Composable
private fun PreviewBadgeChip() {
    PreviewScaffold {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                BadgeChip(text = "12", mono = true)
                BadgeChip(
                    text = "3",
                    background = AgentBelayColors.surface3,
                    textColor = AgentBelayColors.inkSecondary,
                    mono = true,
                )
                BadgeChip(
                    text = "protected",
                    background = WarnYellow.copy(alpha = 0.12f),
                    textColor = WarnYellow,
                    mono = true,
                )
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                BadgeChip(
                    text = "new",
                    background = AccentEmerald.copy(alpha = 0.14f),
                    textColor = AccentEmerald,
                )
                BadgeChip(
                    text = "info",
                    background = InfoBlue.copy(alpha = 0.14f),
                    textColor = InfoBlue,
                )
                BadgeChip(
                    text = "danger",
                    background = DangerRed.copy(alpha = 0.14f),
                    textColor = DangerRed,
                )
            }
        }
    }
}

@Preview(widthDp = 360, heightDp = 160)
@Composable
private fun PreviewColoredIconTile() {
    PreviewScaffold {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                ColoredIconTile(icon = FeatherExternalLink, tint = AccentEmerald, contentDescription = "Ok")
                ColoredIconTile(icon = LucideCopy, tint = InfoBlue, contentDescription = "Copy")
                ColoredIconTile(icon = LucideClock, tint = WarnYellow, contentDescription = "Clock")
                ColoredIconTile(icon = FeatherExternalLink, tint = DangerRed, contentDescription = "Danger")
            }
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                ColoredIconTile(tint = AccentEmerald) {
                    Text("2", color = AccentEmerald, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, fontFamily = FontFamily.Monospace)
                }
                ColoredIconTile(tint = WarnYellow) {
                    Text("4", color = WarnYellow, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, fontFamily = FontFamily.Monospace)
                }
                ColoredIconTile(tint = DangerRed, borderAlpha = 0f, bgAlpha = 0.12f) {
                    Text("5", color = DangerRed, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, fontFamily = FontFamily.Monospace)
                }
            }
        }
    }
}

@Preview(widthDp = 320, heightDp = 120)
@Composable
private fun PreviewIconActionButton() {
    PreviewScaffold {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                IconActionButton(LucideCopy, "Copy", {})
                IconActionButton(LucideClock, "Recent", {})
                IconActionButton(FeatherExternalLink, "Open", {})
            }
            // Hover state (preview override)
            CompositionLocalProvider(LocalPreviewHoverOverride provides true) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    IconActionButton(LucideCopy, "Copy", {})
                    IconActionButton(LucideClock, "Recent", {})
                    IconActionButton(FeatherExternalLink, "Open", {})
                }
            }
            // Disabled
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                IconActionButton(LucideCopy, "Copy", {}, enabled = false)
                IconActionButton(LucideClock, "Recent", {}, enabled = false)
            }
        }
    }
}
