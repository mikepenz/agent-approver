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
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.text.font.FontWeight
import com.mikepenz.agentbelay.ui.theme.AccentEmerald
import com.mikepenz.agentbelay.ui.theme.AgentBelayColors
import com.mikepenz.agentbelay.ui.theme.PreviewScaffold

// ── Shared text buttons ─────────────────────────────────────────────────────
// 28dp-tall, 6dp-radius text buttons used across settings, integrations,
// and action rows. Three variants: primary (emerald fill), outline
// (line1 border, transparent fill), ghost (no border, color-tinted hover).

private val ButtonHeight = 28.dp
private val ButtonRadius = 6.dp
private val ButtonHPadding = 12.dp
private val GhostHPadding = 10.dp
private val ButtonFontSize = 12.sp

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun OutlineButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val liveHover by interactionSource.collectIsHoveredAsState()
    val hovered = (LocalPreviewHoverOverride.current ?: liveHover) && enabled
    Box(
        modifier = modifier
            .height(ButtonHeight)
            .clip(RoundedCornerShape(ButtonRadius))
            .background(if (hovered) AgentBelayColors.surface2 else Color.Transparent)
            .border(1.dp, AgentBelayColors.line1, RoundedCornerShape(ButtonRadius))
            .then(
                if (enabled) Modifier
                    .hoverable(interactionSource)
                    .clickable(
                        interactionSource = interactionSource,
                        indication = null,
                        role = Role.Button,
                    ) { onClick() }
                else Modifier
            )
            .padding(horizontal = ButtonHPadding),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = text,
            color = if (enabled) AgentBelayColors.inkSecondary
            else AgentBelayColors.inkSecondary.copy(alpha = 0.38f),
            fontSize = ButtonFontSize,
            fontWeight = FontWeight.Medium,
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun GhostButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    color: Color = AgentBelayColors.inkSecondary,
    enabled: Boolean = true,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val liveHover by interactionSource.collectIsHoveredAsState()
    val hovered = (LocalPreviewHoverOverride.current ?: liveHover) && enabled
    Box(
        modifier = modifier
            .height(ButtonHeight)
            .clip(RoundedCornerShape(ButtonRadius))
            .background(if (hovered) color.copy(alpha = 0.1f) else Color.Transparent)
            .then(
                if (enabled) Modifier
                    .hoverable(interactionSource)
                    .clickable(
                        interactionSource = interactionSource,
                        indication = null,
                        role = Role.Button,
                    ) { onClick() }
                else Modifier
            )
            .padding(horizontal = GhostHPadding),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = text,
            color = if (enabled) color else color.copy(alpha = 0.38f),
            fontSize = ButtonFontSize,
            fontWeight = FontWeight.Medium,
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun PrimaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val liveHover by interactionSource.collectIsHoveredAsState()
    val hovered = (LocalPreviewHoverOverride.current ?: liveHover) && enabled
    val bg = when {
        !enabled -> AccentEmerald.copy(alpha = 0.4f)
        hovered -> AccentEmerald.copy(alpha = 0.9f)
        else -> AccentEmerald
    }
    Box(
        modifier = modifier
            .height(ButtonHeight)
            .clip(RoundedCornerShape(ButtonRadius))
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
            )
            .padding(horizontal = ButtonHPadding),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = text,
            color = Color(0xFF163826),
            fontSize = ButtonFontSize,
            fontWeight = FontWeight.SemiBold,
        )
    }
}

// ── Previews ─────────────────────────────────────────────────────────────────

@Preview(widthDp = 420, heightDp = 260)
@Composable
private fun PreviewActionButtons() {
    PreviewScaffold {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            // Default
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                PrimaryButton("Save", {})
                OutlineButton("Cancel", {})
                GhostButton("Remove", {})
            }
            // Hover (preview override)
            CompositionLocalProvider(LocalPreviewHoverOverride provides true) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    PrimaryButton("Save", {})
                    OutlineButton("Cancel", {})
                    GhostButton("Remove", {})
                }
            }
            // Disabled
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                PrimaryButton("Save", {}, enabled = false)
                OutlineButton("Cancel", {}, enabled = false)
                GhostButton("Remove", {}, enabled = false)
            }
            // Colored ghost
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                GhostButton("Delete", {}, color = com.mikepenz.agentbelay.ui.theme.DangerRed)
                GhostButton("Learn more", {}, color = com.mikepenz.agentbelay.ui.theme.InfoBlue)
            }
        }
    }
}
