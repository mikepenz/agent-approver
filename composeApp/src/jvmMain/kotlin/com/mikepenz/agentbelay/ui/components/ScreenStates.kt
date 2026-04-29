package com.mikepenz.agentbelay.ui.components

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.clickable
import com.mikepenz.agentbelay.ui.icons.LucideX
import com.mikepenz.agentbelay.ui.icons.LucideZap
import com.mikepenz.agentbelay.ui.theme.DangerRed
import com.mikepenz.agentbelay.ui.theme.AgentBelayColors

/**
 * Skeleton row with a subtly-pulsing neutral block. Used by screen-level
 * loading states (Approvals / History / Statistics / Settings) as a
 * placeholder while data streams in. Colour-wise this deliberately stays in
 * the `surface`/`surfaceVariant` range so it blends with either theme.
 */
@Composable
fun SkeletonRow(
    modifier: Modifier = Modifier,
    height: androidx.compose.ui.unit.Dp = 14.dp,
    width: androidx.compose.ui.unit.Dp? = null,
) {
    val infinite = rememberInfiniteTransition(label = "skeleton")
    val alpha by infinite.animateFloat(
        initialValue = 0.35f,
        targetValue = 0.65f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1100),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "skeletonAlpha",
    )
    val baseModifier = when {
        width != null -> modifier.width(width).height(height)
        else -> modifier.fillMaxWidth().height(height)
    }
    Box(
        modifier = baseModifier
            .clip(RoundedCornerShape(4.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = alpha)),
    )
}

/**
 * Full-surface loading state with a short title + 5 skeleton rows. Respects
 * the active theme (background pulled from MaterialTheme). Kept free of
 * business data so it reads equally well in Approvals, History, Statistics
 * and Settings previews.
 */
@Composable
fun ScreenLoadingState(
    label: String = "Loading…",
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(24.dp),
    ) {
        Column(
            verticalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(
                text = label,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = 12.sp,
                fontWeight = FontWeight.SemiBold,
            )
            repeat(5) {
                SkeletonRow(height = 16.dp, width = if (it % 2 == 0) null else 300.dp)
                SkeletonRow(height = 10.dp, width = 220.dp)
                Spacer(Modifier.height(4.dp))
            }
        }
    }
}

/**
 * Inline banner-style error state with a retry affordance. Sits inside a
 * DangerRed-tinted surface so it reads as a failure without dominating the
 * whole screen. [onRetry] is exposed so wiring into a real screen stays
 * trivial — previews pass a no-op.
 */
@Composable
fun ScreenErrorState(
    title: String = "Something went wrong",
    message: String = "We couldn't load this view. Check your connection and try again.",
    onRetry: () -> Unit = {},
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier
                .width(420.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(DangerRed.copy(alpha = 0.08f))
                .border(1.dp, DangerRed.copy(alpha = 0.30f), RoundedCornerShape(12.dp))
                .padding(horizontal = 24.dp, vertical = 20.dp),
        ) {
            ColoredIconTile(
                icon = LucideX,
                tint = DangerRed,
                radius = 10.dp,
                bgAlpha = 0.15f,
                borderAlpha = 0.30f,
                iconSize = 18.dp,
            )
            Text(
                text = title,
                color = AgentBelayColors.inkPrimary,
                fontSize = 15.sp,
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                text = message,
                color = AgentBelayColors.inkSecondary,
                fontSize = 12.sp,
                textAlign = TextAlign.Center,
            )
            RetryButton(onClick = onRetry)
        }
    }
}

@Composable
private fun RetryButton(onClick: () -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        modifier = Modifier
            .clip(RoundedCornerShape(8.dp))
            .background(AgentBelayColors.surface)
            .border(1.dp, AgentBelayColors.line2, RoundedCornerShape(8.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 7.dp),
    ) {
        Icon(
            imageVector = LucideZap,
            contentDescription = null,
            tint = AgentBelayColors.inkPrimary,
            modifier = Modifier.size(12.dp),
        )
        Text(
            text = "Retry",
            color = AgentBelayColors.inkPrimary,
            fontSize = 12.sp,
            fontWeight = FontWeight.SemiBold,
        )
    }
}
