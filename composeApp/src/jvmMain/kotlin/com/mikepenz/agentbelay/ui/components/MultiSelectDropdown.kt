package com.mikepenz.agentbelay.ui.components

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
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupProperties
import com.mikepenz.agentbelay.ui.icons.LucideCheck
import com.mikepenz.agentbelay.ui.icons.LucideChevronDown
import com.mikepenz.agentbelay.ui.theme.AccentEmerald
import com.mikepenz.agentbelay.ui.theme.AgentBelayColors

/**
 * Multi-select dropdown styled to match the rest of the app.
 *
 * Trigger: pill-tall (34.dp) chip with `surface` background, `line1`
 * border, 7.dp corner radius, hover-driven `surface2` swap, summary
 * label + chevron — same chrome as the Settings dropdown so visual
 * grammar stays consistent across screens.
 *
 * Popup: a [Popup]-anchored panel using `surface` + `line2` border
 * (matches `ColoredModePicker`'s popup) rather than the bare Material3
 * DropdownMenu, which paints over the app theme. Each item is a hover-
 * highlighted row with an emerald check, optional leading dot, and the
 * label.
 *
 * `null` selection = "no filter" (everything visible). The "All" row
 * at the top of the popup resets to that state.
 */
@Composable
fun <T> MultiSelectDropdown(
    options: List<Pair<T, String>>,
    selected: Set<T>?,
    onChange: (Set<T>?) -> Unit,
    modifier: Modifier = Modifier,
    allLabel: String = "All",
    leadingDot: ((T) -> Color)? = null,
    initiallyOpen: Boolean = false,
) {
    var open by remember { mutableStateOf(initiallyOpen) }
    val effective = selected ?: emptySet()
    val isAll = selected == null || selected.size == options.size
    val summary = when {
        options.isEmpty() -> allLabel
        isAll -> allLabel
        effective.size == 1 -> options.firstOrNull { it.first == effective.first() }?.second ?: allLabel
        else -> "${effective.size} selected"
    }

    Box(modifier = modifier) {
        Trigger(
            label = summary,
            onClick = { if (options.isNotEmpty()) open = !open },
            enabled = options.isNotEmpty(),
        )
        if (open) {
            // Anchor below the trigger; align right so a wider popup grows
            // into existing screen space rather than off the edge.
            Popup(
                alignment = Alignment.TopStart,
                offset = IntOffset(0, with(LocalDensity.current) { 38.dp.roundToPx() }),
                onDismissRequest = { open = false },
                properties = PopupProperties(focusable = true),
            ) {
                MultiSelectPopup(
                    allLabel = allLabel,
                    isAllSelected = selected == null,
                    options = options,
                    selected = effective,
                    leadingDot = leadingDot,
                    onAll = { onChange(null) },
                    onToggle = { id ->
                        val next = if (id in effective) effective - id else effective + id
                        onChange(next.takeIf { it.isNotEmpty() })
                    },
                )
            }
        }
    }
}

@Composable
private fun Trigger(label: String, onClick: () -> Unit, enabled: Boolean) {
    val interactionSource = remember { MutableInteractionSource() }
    val liveHovered by interactionSource.collectIsHoveredAsState()
    val isHovered = LocalPreviewHoverOverride.current ?: liveHovered

    Row(
        modifier = Modifier
            .height(34.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(if (isHovered && enabled) AgentBelayColors.surface2 else AgentBelayColors.surface)
            .border(
                1.dp,
                if (isHovered && enabled) AgentBelayColors.line2 else AgentBelayColors.line1,
                RoundedCornerShape(8.dp),
            )
            .hoverable(interactionSource, enabled = enabled)
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                enabled = enabled,
            ) { onClick() }
            .padding(horizontal = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(
            text = label,
            color = if (enabled) AgentBelayColors.inkPrimary else AgentBelayColors.inkMuted,
            fontSize = 12.sp,
            fontWeight = FontWeight.Medium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Icon(
            imageVector = LucideChevronDown,
            contentDescription = null,
            tint = AgentBelayColors.inkMuted,
            modifier = Modifier.size(12.dp),
        )
    }
}

@Composable
private fun <T> MultiSelectPopup(
    allLabel: String,
    isAllSelected: Boolean,
    options: List<Pair<T, String>>,
    selected: Set<T>,
    leadingDot: ((T) -> Color)?,
    onAll: () -> Unit,
    onToggle: (T) -> Unit,
) {
    Column(
        modifier = Modifier
            .widthIn(min = 200.dp, max = 320.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(AgentBelayColors.surface)
            .border(1.dp, AgentBelayColors.line2, RoundedCornerShape(8.dp))
            .padding(4.dp),
        verticalArrangement = Arrangement.spacedBy(1.dp),
    ) {
        PopupRow(
            label = allLabel,
            checked = isAllSelected,
            leadingDot = null,
            onClick = onAll,
            emphasis = true,
        )
        if (options.isNotEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(1.dp)
                    .padding(horizontal = 4.dp)
                    .background(AgentBelayColors.line1),
            )
        }
        options.forEach { (id, label) ->
            PopupRow(
                label = label,
                checked = id in selected,
                leadingDot = leadingDot?.invoke(id),
                onClick = { onToggle(id) },
                emphasis = false,
            )
        }
    }
}

@Composable
private fun PopupRow(
    label: String,
    checked: Boolean,
    leadingDot: Color?,
    onClick: () -> Unit,
    emphasis: Boolean,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val liveHovered by interactionSource.collectIsHoveredAsState()
    val hovered = LocalPreviewHoverOverride.current ?: liveHovered
    val bg = when {
        checked -> AgentBelayColors.surface2
        hovered -> AgentBelayColors.surface2.copy(alpha = 0.5f)
        else -> Color.Transparent
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(5.dp))
            .background(bg)
            .hoverable(interactionSource)
            .clickable(interactionSource = interactionSource, indication = null) { onClick() }
            .padding(horizontal = 10.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        // Reserve a fixed-width "check" column so labels line up regardless
        // of selection state. Showing the check at full opacity for
        // checked-rows, blank otherwise.
        Box(modifier = Modifier.size(12.dp), contentAlignment = Alignment.Center) {
            if (checked) {
                Icon(
                    imageVector = LucideCheck,
                    contentDescription = "selected",
                    tint = AccentEmerald,
                    modifier = Modifier.size(12.dp),
                )
            }
        }
        if (leadingDot != null) {
            Box(
                modifier = Modifier
                    .size(7.dp)
                    .clip(CircleShape)
                    .background(leadingDot),
            )
        } else if (!emphasis) {
            // Pad to align with rows that have a leading dot, so labels
            // line up vertically across the menu.
            Spacer(modifier = Modifier.width(7.dp))
        }
        Text(
            text = label,
            color = if (emphasis) AgentBelayColors.inkSecondary else AgentBelayColors.inkPrimary,
            fontSize = 12.5.sp,
            fontWeight = if (emphasis) FontWeight.SemiBold else FontWeight.Normal,
            letterSpacing = if (emphasis) 0.2.sp else 0.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}
