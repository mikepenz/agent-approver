package com.mikepenz.agentbelay.ui.settings

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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mikepenz.agentbelay.ui.components.AgentBelayCard
import com.mikepenz.agentbelay.ui.components.HorizontalHairline
import com.mikepenz.agentbelay.ui.theme.AccentEmerald
import com.mikepenz.agentbelay.ui.theme.AgentBelayColors

// ── Design-aligned shared helpers used across the Settings sub-tabs ─────────
// These mirror Settings.jsx `SettingGroup` / `SettingRow` (JSX padding 14x16,
// 1px AgentBelayColors.line1 separators between rows, card with maxWidth 780).

/** Section header with title + optional subtitle + card wrapper for rows. */
@Composable
internal fun SettingSection(
    title: String,
    desc: String? = null,
    content: @Composable () -> Unit,
) {
    Column(modifier = Modifier.fillMaxWidth().widthIn(max = 780.dp)) {
        Text(
            text = title,
            color = AgentBelayColors.inkPrimary,
            fontSize = 14.sp,
            fontWeight = FontWeight.SemiBold,
            letterSpacing = (-0.1).sp,
        )
        if (desc != null) {
            Spacer(Modifier.height(3.dp))
            Text(
                text = desc,
                color = AgentBelayColors.inkTertiary,
                fontSize = 12.5.sp,
                lineHeight = 18.sp,
            )
        }
        Spacer(Modifier.height(12.dp))
        AgentBelayCard(modifier = Modifier.fillMaxWidth()) {
            Column { content() }
        }
    }
}

/**
 * Row with label + optional subtitle on the left and optional trailing control.
 * First row skips the top divider to mimic the JSX `borderTop: none` on `first`.
 */
@Composable
internal fun SettingItem(
    label: String,
    desc: String? = null,
    first: Boolean = false,
    right: @Composable (() -> Unit)? = null,
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        if (!first) {
            HorizontalHairline()
        }
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(20.dp),
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = label,
                    color = AgentBelayColors.inkPrimary,
                    fontSize = 13.sp,
                    letterSpacing = (-0.05).sp,
                    maxLines = 2,
                    overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                )
                if (desc != null) {
                    Spacer(Modifier.height(3.dp))
                    Text(
                        text = desc,
                        color = AgentBelayColors.inkTertiary,
                        fontSize = 11.5.sp,
                        lineHeight = 17.sp,
                        maxLines = 3,
                        overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                    )
                }
            }
            if (right != null) {
                Box { right() }
            }
        }
    }
}

/**
 * Editable inline input matching JSX `TextInput mono`. 32dp tall, 6dp radius,
 * 10dp horizontal padding, transparent caret tinted with AgentBelayColors.inkPrimary.
 */
@Composable
internal fun SettingsTextInput(
    value: String,
    onChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    width: Dp = 120.dp,
    suffix: String? = null,
    mono: Boolean = false,
    keyboardType: KeyboardType = KeyboardType.Text,
) {
    val textStyle = LocalTextStyle.current.merge(
        TextStyle(
            color = AgentBelayColors.inkPrimary,
            fontSize = 12.5.sp,
            fontFamily = if (mono) FontFamily.Monospace else FontFamily.Default,
        ),
    )
    Row(
        modifier = modifier
            .width(width)
            .height(32.dp)
            .clip(RoundedCornerShape(6.dp))
            .background(AgentBelayColors.background)
            .border(1.dp, AgentBelayColors.line1, RoundedCornerShape(6.dp))
            .padding(horizontal = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        BasicTextField(
            value = value,
            onValueChange = onChange,
            modifier = Modifier.weight(1f),
            textStyle = textStyle,
            singleLine = true,
            cursorBrush = SolidColor(AgentBelayColors.inkPrimary),
            keyboardOptions = KeyboardOptions(keyboardType = keyboardType),
        )
        if (suffix != null) {
            Text(
                text = suffix,
                color = AgentBelayColors.inkMuted,
                fontSize = 11.sp,
                fontFamily = FontFamily.Monospace,
            )
        }
    }
}

// Button variants moved to ui/components/ActionButtons.kt as
// OutlineButton / GhostButton / PrimaryButton.

// ── Legacy helpers kept for files not migrated in this pass ─────────────────

@Composable
internal fun SectionHeader(title: String) {
    Text(
        text = title,
        style = MaterialTheme.typography.titleMedium,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(top = 4.dp),
    )
}

@Composable
internal fun StatusBadge(text: String, color: Color) {
    Surface(
        shape = MaterialTheme.shapes.small,
        color = color.copy(alpha = 0.15f),
    ) {
        Text(
            text = text,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
            style = MaterialTheme.typography.labelSmall,
            color = color,
        )
    }
}

@Composable
internal fun SettingsTextField(
    label: String,
    value: String,
    onValueChange: (String) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(label, style = MaterialTheme.typography.bodyMedium)
        androidx.compose.material3.OutlinedTextField(
            value = value,
            onValueChange = onValueChange,
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            textStyle = MaterialTheme.typography.bodySmall,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
        )
    }
}

@Composable
internal fun SettingsSwitch(
    label: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    enabled: Boolean = true,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            label,
            style = MaterialTheme.typography.bodyMedium,
            color = if (enabled) MaterialTheme.colorScheme.onSurface
            else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f),
        )
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            enabled = enabled,
        )
    }
}

@Composable
internal fun SettingsDiscreteSlider(
    label: String,
    value: Int,
    stops: List<Int>,
    stopLabels: List<String>,
    onValueChange: (Int) -> Unit,
    enabled: Boolean = true,
) {
    require(stops.size == stopLabels.size && stops.size >= 2)
    val labelColor = if (enabled) MaterialTheme.colorScheme.onSurface
    else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
    val tickColor = if (enabled) MaterialTheme.colorScheme.onSurfaceVariant
    else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.38f)

    val currentIndex = stops.indexOf(value).let { if (it < 0) 0 else it }

    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(label, style = MaterialTheme.typography.bodyMedium, color = labelColor)
            Text(
                text = stopLabels[currentIndex],
                style = MaterialTheme.typography.labelMedium,
                color = labelColor,
            )
        }
        androidx.compose.material3.Slider(
            value = currentIndex.toFloat(),
            onValueChange = { f ->
                val idx = f.toInt().coerceIn(0, stops.lastIndex)
                val newStop = stops[idx]
                if (newStop != value) onValueChange(newStop)
            },
            valueRange = 0f..(stops.size - 1).toFloat(),
            steps = (stops.size - 2).coerceAtLeast(0),
            enabled = enabled,
        )
        androidx.compose.ui.layout.Layout(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 10.dp),
            content = {
                stopLabels.forEach { text ->
                    Text(
                        text = text,
                        style = MaterialTheme.typography.labelSmall,
                        color = tickColor,
                        textAlign = TextAlign.Center,
                    )
                }
            },
        ) { measurables, constraints ->
            val placeables = measurables.map { it.measure(constraints.copy(minWidth = 0)) }
            val width = constraints.maxWidth
            val height = placeables.maxOfOrNull { it.height } ?: 0
            layout(width, height) {
                placeables.forEachIndexed { i, p ->
                    val fraction =
                        if (placeables.size == 1) 0.5f else i.toFloat() / (placeables.size - 1)
                    val centerX = (fraction * width).toInt()
                    val x = (centerX - p.width / 2).coerceIn(0, width - p.width)
                    p.placeRelative(x, 0)
                }
            }
        }
    }
}
