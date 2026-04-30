package com.mikepenz.agentbelay.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
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
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupProperties
import com.mikepenz.agentbelay.ui.icons.LucideChevronDown
import com.mikepenz.agentbelay.ui.theme.AccentEmerald
import com.mikepenz.agentbelay.ui.theme.AgentBelayColors
import com.mikepenz.agentbelay.ui.theme.DangerRed
import com.mikepenz.agentbelay.ui.theme.InfoBlue
import com.mikepenz.agentbelay.ui.theme.InkMuted
import com.mikepenz.agentbelay.ui.theme.PreviewScaffold
import com.mikepenz.agentbelay.ui.theme.WarnYellow

/**
 * One option in a [ColoredModePicker]. The [color] tints the leading dot,
 * label, border, and background tint.
 */
data class ColoredModeOption<T>(
    val value: T,
    val label: String,
    val color: Color,
)

/**
 * Reusable enum-style picker shared by the Protections and Redaction
 * settings rows. Renders as a 26dp-tall colored pill with a leading dot,
 * a label, and a chevron; tapping opens a small popup that lists all
 * options.
 *
 * Generic over the mode value type [T] so callers can plug in their own
 * enum (`ProtectionMode`, `RedactionMode`, …) without coercion. The
 * [alpha] parameter dims the pill in place when the surrounding feature
 * has a master switch turned off (Redaction's case) — defaults to 1f for
 * the simple Protection case.
 */
@Composable
fun <T> ColoredModePicker(
    options: List<ColoredModeOption<T>>,
    active: ColoredModeOption<T>,
    onSelect: (T) -> Unit,
    modifier: Modifier = Modifier,
    alpha: Float = 1f,
) {
    var open by remember { mutableStateOf(false) }
    val interactionSource = remember { MutableInteractionSource() }
    val hovered by interactionSource.collectIsHoveredAsState()
    Box(modifier = modifier) {
        Row(
            modifier = Modifier
                .height(26.dp)
                .clip(RoundedCornerShape(6.dp))
                .background(active.color.copy(alpha = (if (hovered) 0.14f else 0.10f) * alpha))
                .border(1.dp, active.color.copy(alpha = 0.22f * alpha), RoundedCornerShape(6.dp))
                .hoverable(interactionSource)
                .clickable(interactionSource = interactionSource, indication = null) { open = !open }
                .padding(start = 10.dp, end = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Box(
                modifier = Modifier
                    .size(6.dp)
                    .clip(CircleShape)
                    .background(active.color.copy(alpha = alpha)),
            )
            Text(
                text = active.label,
                color = active.color.copy(alpha = alpha),
                fontSize = 11.5.sp,
                fontWeight = FontWeight.Medium,
            )
            Icon(
                imageVector = LucideChevronDown,
                contentDescription = null,
                tint = active.color.copy(alpha = alpha),
                modifier = Modifier.size(11.dp),
            )
        }
        if (open) {
            Popup(
                alignment = Alignment.TopEnd,
                offset = IntOffset(0, with(LocalDensity.current) { 30.dp.roundToPx() }),
                onDismissRequest = { open = false },
                properties = PopupProperties(focusable = true),
            ) {
                ColoredModePickerPopup(
                    options = options,
                    activeValue = active.value,
                    onSelect = {
                        onSelect(it)
                        open = false
                    },
                )
            }
        }
    }
}

@Composable
private fun <T> ColoredModePickerPopup(
    options: List<ColoredModeOption<T>>,
    activeValue: T,
    onSelect: (T) -> Unit,
) {
    androidx.compose.foundation.layout.Column(
        modifier = Modifier
            .widthIn(min = 160.dp, max = 200.dp)
            .clip(RoundedCornerShape(7.dp))
            .background(AgentBelayColors.surface)
            .border(1.dp, AgentBelayColors.line2, RoundedCornerShape(7.dp))
            .padding(4.dp),
    ) {
        options.forEach { option ->
            val selected = option.value == activeValue
            val optSource = remember(option.value) { MutableInteractionSource() }
            val optHovered by optSource.collectIsHoveredAsState()
            val optBg = when {
                selected -> AgentBelayColors.surface2
                optHovered -> AgentBelayColors.surface2.copy(alpha = 0.5f)
                else -> Color.Transparent
            }
            val optFg = if (selected) option.color else AgentBelayColors.inkPrimary
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(4.dp))
                    .background(optBg)
                    .hoverable(optSource)
                    .clickable(interactionSource = optSource, indication = null) {
                        onSelect(option.value)
                    }
                    .padding(horizontal = 10.dp, vertical = 7.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Box(
                    modifier = Modifier
                        .size(6.dp)
                        .clip(CircleShape)
                        .background(option.color),
                )
                Text(text = option.label, color = optFg, fontSize = 12.sp)
            }
        }
    }
}

/**
 * 30dp colored icon tile used as the leading element in Protections /
 * Redaction module rows. Background is the [color] at 14% alpha; the
 * icon itself takes the [color] directly. [alpha] dims everything for
 * disabled/inert states.
 */
@Composable
fun ColoredIconTile(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    color: Color,
    modifier: Modifier = Modifier,
    alpha: Float = 1f,
    contentDescription: String? = null,
) {
    Box(
        modifier = modifier
            .size(30.dp)
            .clip(RoundedCornerShape(7.dp))
            .background(color.copy(alpha = 0.14f * alpha)),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = contentDescription,
            tint = color.copy(alpha = alpha),
            modifier = Modifier.size(14.dp),
        )
    }
}

@Preview(widthDp = 360, heightDp = 80)
@Composable
private fun PreviewColoredModePickerProtection() {
    val options = listOf(
        ColoredModeOption("off", "Off", InkMuted),
        ColoredModeOption("ask", "Ask", WarnYellow),
        ColoredModeOption("block", "Auto-block", DangerRed),
        ColoredModeOption("log", "Log only", InfoBlue),
    )
    PreviewScaffold {
        Box(Modifier.padding(16.dp)) {
            ColoredModePicker(options = options, active = options[2], onSelect = {})
        }
    }
}

@Preview(widthDp = 360, heightDp = 80)
@Composable
private fun PreviewColoredModePickerRedaction() {
    val options = listOf(
        ColoredModeOption("off", "Off", InkMuted),
        ColoredModeOption("log", "Log only", InfoBlue),
        ColoredModeOption("redact", "Redact", AccentEmerald),
    )
    PreviewScaffold {
        Box(Modifier.padding(16.dp)) {
            ColoredModePicker(options = options, active = options[2], onSelect = {})
        }
    }
}

@Preview(widthDp = 360, heightDp = 80)
@Composable
private fun PreviewColoredModePickerDimmed() {
    val options = listOf(
        ColoredModeOption("redact", "Redact", AccentEmerald),
    )
    PreviewScaffold {
        Box(Modifier.padding(16.dp)) {
            ColoredModePicker(options = options, active = options[0], onSelect = {}, alpha = 0.55f)
        }
    }
}
