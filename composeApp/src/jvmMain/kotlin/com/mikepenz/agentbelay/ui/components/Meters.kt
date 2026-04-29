package com.mikepenz.agentbelay.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.mikepenz.agentbelay.ui.theme.AccentEmerald
import com.mikepenz.agentbelay.ui.theme.AgentBelayColors
import com.mikepenz.agentbelay.ui.theme.DangerRed
import com.mikepenz.agentbelay.ui.theme.InfoBlue
import com.mikepenz.agentbelay.ui.theme.PreviewScaffold
import com.mikepenz.agentbelay.ui.theme.WarnYellow

/**
 * Thin single-color horizontal progress meter. [progress] is coerced to [0f, 1f].
 * Used for breakdown rows and similar proportional indicators.
 */
@Composable
fun LinearMeter(
    progress: Float,
    color: Color,
    modifier: Modifier = Modifier,
    height: Dp = 3.dp,
    radius: Dp = 2.dp,
    trackColor: Color = AgentBelayColors.surface2,
) {
    val clamped = progress.coerceIn(0f, 1f)
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(height)
            .clip(RoundedCornerShape(radius))
            .background(trackColor),
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth(clamped)
                .fillMaxHeight()
                .background(color),
        )
    }
}

/**
 * Multi-segment proportional meter. [maxFraction] controls how much of the
 * parent the filled track spans (use 1f when segments already sum to the
 * whole; use [value]/[maxValue] when scaling against a larger reference).
 * Segments are laid out left-to-right weighted by their fractional value.
 */
@Composable
fun StackedMeter(
    segments: List<Pair<Float, Color>>,
    modifier: Modifier = Modifier,
    maxFraction: Float = 1f,
    height: Dp = 8.dp,
    gap: Dp = 1.dp,
    radius: Dp = 2.dp,
    trackColor: Color = AgentBelayColors.surface2,
) {
    val clampedMax = maxFraction.coerceIn(0f, 1f)
    Box(modifier = modifier) {
        Row(
            modifier = Modifier
                .fillMaxWidth(clampedMax)
                .height(height)
                .clip(RoundedCornerShape(radius))
                .background(trackColor),
            horizontalArrangement = Arrangement.spacedBy(gap),
        ) {
            segments.forEach { (weight, color) ->
                if (weight > 0f) {
                    Box(
                        modifier = Modifier
                            .weight(weight)
                            .fillMaxHeight()
                            .background(color),
                    )
                }
            }
        }
    }
}

@Preview(widthDp = 420, heightDp = 220)
@Composable
private fun PreviewLinearMeter() {
    PreviewScaffold {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            LinearMeter(progress = 0f, color = AccentEmerald)
            LinearMeter(progress = 0.25f, color = AccentEmerald)
            LinearMeter(progress = 0.5f, color = InfoBlue)
            LinearMeter(progress = 0.75f, color = WarnYellow)
            LinearMeter(progress = 1f, color = DangerRed)
            Spacer(Modifier.height(4.dp))
            LinearMeter(progress = 0.6f, color = AccentEmerald, height = 6.dp, radius = 3.dp)
            LinearMeter(progress = 0.4f, color = InfoBlue, height = 10.dp, radius = 4.dp)
        }
    }
}

@Preview(widthDp = 420, heightDp = 220)
@Composable
private fun PreviewStackedMeter() {
    PreviewScaffold {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            StackedMeter(
                segments = listOf(
                    7f to AccentEmerald,
                    2f to DangerRed,
                    1f to InfoBlue,
                ),
                maxFraction = 1f,
            )
            StackedMeter(
                segments = listOf(
                    5f to AccentEmerald,
                    3f to DangerRed,
                ),
                maxFraction = 0.6f,
            )
            StackedMeter(
                segments = listOf(
                    1f to AccentEmerald,
                    1f to WarnYellow,
                    1f to DangerRed,
                    1f to InfoBlue,
                ),
                maxFraction = 0.3f,
            )
        }
    }
}
