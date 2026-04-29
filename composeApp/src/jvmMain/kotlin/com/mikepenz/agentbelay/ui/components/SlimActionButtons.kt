package com.mikepenz.agentbelay.ui.components

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mikepenz.agentbelay.ui.icons.LucideCheck
import com.mikepenz.agentbelay.ui.icons.LucideX
import com.mikepenz.agentbelay.ui.theme.AccentEmerald
import com.mikepenz.agentbelay.ui.theme.AgentBelayColors
import com.mikepenz.agentbelay.ui.theme.DangerRed

/**
 * Compact outlined action button — base for destructive slim actions
 * (Deny / Dismiss / Reject). Hovers to a soft red tint so it reads as
 * destructive without drawing eye away from the primary Allow.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun SlimDenyButton(
    modifier: Modifier = Modifier,
    label: String = "Deny",
    icon: ImageVector? = LucideX,
    onClick: () -> Unit,
) {
    val interaction = remember { MutableInteractionSource() }
    val live by interaction.collectIsHoveredAsState()
    val hovered = LocalPreviewHoverOverride.current ?: live
    val bg = if (hovered) DangerRed.copy(alpha = 0.12f) else AgentBelayColors.surface
    val border = if (hovered) DangerRed.copy(alpha = 0.35f) else AgentBelayColors.line2
    val content = if (hovered) DangerRed else AgentBelayColors.inkPrimary
    Row(
        modifier = modifier
            .height(32.dp)
            .clip(RoundedCornerShape(7.dp))
            .background(bg)
            .border(1.dp, border, RoundedCornerShape(7.dp))
            .hoverable(interaction)
            .clickable(interactionSource = interaction, indication = null) { onClick() },
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center,
    ) {
        if (icon != null) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = content,
                modifier = Modifier.size(12.dp),
            )
            Spacer(Modifier.width(6.dp))
        }
        Text(
            text = label,
            color = content,
            fontSize = 12.sp,
            fontWeight = FontWeight.Medium,
        )
    }
}

/**
 * Emerald-filled primary action — base for approvals in slim contexts
 * (Allow / Submit / Approve). Dims to a disabled surface tone when
 * [enabled] is false so Submit-without-answer reads as unavailable.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun SlimAllowButton(
    modifier: Modifier = Modifier,
    label: String = "Allow",
    icon: ImageVector? = LucideCheck,
    enabled: Boolean = true,
    onClick: () -> Unit,
) {
    val interaction = remember { MutableInteractionSource() }
    val bg = if (enabled) AccentEmerald else AgentBelayColors.surface2
    val borderColor = if (enabled) AccentEmerald.copy(alpha = 0.4f) else AgentBelayColors.line2
    val contentColor = if (enabled) AgentBelayColors.accentEmeraldInk else AgentBelayColors.inkMuted

    val clickableModifier = if (enabled) {
        Modifier
            .hoverable(interaction)
            .clickable(interactionSource = interaction, indication = null) { onClick() }
    } else {
        Modifier
    }

    Row(
        modifier = modifier
            .height(32.dp)
            .clip(RoundedCornerShape(7.dp))
            .background(bg)
            .border(1.dp, borderColor, RoundedCornerShape(7.dp))
            .then(clickableModifier),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center,
    ) {
        if (icon != null) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = contentColor,
                modifier = Modifier.size(12.dp),
            )
            Spacer(Modifier.width(6.dp))
        }
        Text(
            text = label,
            color = contentColor,
            fontSize = 12.sp,
            fontWeight = FontWeight.SemiBold,
        )
    }
}

/**
 * Low-emphasis tertiary link for secondary slim actions like
 * "Allow for session" and "Ask another agent".
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun SlimTertiaryLink(text: String, onClick: () -> Unit) {
    val interaction = remember { MutableInteractionSource() }
    val live by interaction.collectIsHoveredAsState()
    val hovered = LocalPreviewHoverOverride.current ?: live
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(4.dp))
            .hoverable(interaction)
            .clickable(interactionSource = interaction, indication = null) { onClick() }
            .padding(horizontal = 6.dp, vertical = 2.dp),
    ) {
        Text(
            text = text,
            color = if (hovered) AgentBelayColors.inkPrimary else AgentBelayColors.inkTertiary,
            fontSize = 10.5.sp,
        )
    }
}
