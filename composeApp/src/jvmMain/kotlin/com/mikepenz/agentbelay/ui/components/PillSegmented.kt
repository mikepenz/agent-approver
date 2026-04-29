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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.isTraversalGroup
import androidx.compose.ui.semantics.selected as semanticsSelected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mikepenz.agentbelay.ui.theme.AgentBelayColors

/** Size variant for [PillSegmented], mirroring the JSX `size` prop on `Segmented variant="pill"`. */
enum class PillSegmentedSize { SM, MD }

/**
 * Pill-style segmented control. Canonical spec is UI.jsx `Segmented variant="pill"`:
 *
 * - Container: `background: var(--surface)`, `border: 1px solid var(--line)`,
 *   `borderRadius: 8`, inner `padding: 3`, `gap: 2`.
 * - Active pill: `background: var(--surface-3)`, `color: var(--ink)`.
 * - Inactive pill: `background: transparent`, `color: var(--ink-3)`.
 * - Sizes: sm → `padding: 4px 9px`, `font: 11.5`. md → `padding: 5px 12px`, `font: 12.5`.
 *
 * Deliberately different from the Settings screen's older PillSegmented (which had a green
 * accent tint on the active state); we follow the JSX faithfully here.
 */
@Composable
fun <T> PillSegmented(
    options: List<Pair<T, String>>,
    selected: T,
    onSelect: (T) -> Unit,
    modifier: Modifier = Modifier,
    size: PillSegmentedSize = PillSegmentedSize.MD,
) {
    Row(
        modifier = modifier
            .semantics { isTraversalGroup = true }
            .clip(RoundedCornerShape(8.dp))
            .background(AgentBelayColors.surface)
            .border(1.dp, AgentBelayColors.line1, RoundedCornerShape(8.dp))
            .padding(3.dp),
        horizontalArrangement = Arrangement.spacedBy(2.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        options.forEach { (value, label) ->
            val active = value == selected
            val interactionSource = remember { MutableInteractionSource() }
            val liveHover by interactionSource.collectIsHoveredAsState()
            val isHovered = LocalPreviewHoverOverride.current ?: liveHover

            val bg = when {
                active -> AgentBelayColors.surface3
                isHovered -> Color(0x0FFFFFFF)
                else -> Color.Transparent
            }
            val fg = if (active) AgentBelayColors.inkPrimary else AgentBelayColors.inkTertiary

            val (hPad, vPad, fontSize) = when (size) {
                PillSegmentedSize.SM -> Triple(9.dp, 4.dp, 11.5.sp)
                PillSegmentedSize.MD -> Triple(12.dp, 5.dp, 12.5.sp)
            }

            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(5.dp))
                    .background(bg)
                    .hoverable(interactionSource)
                    .clickable(
                        interactionSource = interactionSource,
                        indication = null,
                        role = Role.Tab,
                        onClickLabel = label,
                    ) { onSelect(value) }
                    .semantics { semanticsSelected = active }
                    .padding(horizontal = hPad, vertical = vPad),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = label,
                    color = fg,
                    fontSize = fontSize,
                    fontWeight = FontWeight.Medium,
                    letterSpacing = (-0.05).sp,
                )
            }
        }
    }
}
