package com.mikepenz.agentbelay.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mikepenz.agentbelay.ui.theme.AgentBelayColors
import com.mikepenz.agentbelay.ui.theme.PreviewScaffold

/**
 * Titled header for cards (statistics, approvals detail panels). Title +
 * subtitle on the left, optional [trailing] slot on the right (legend,
 * action button, toggle…). Ends with a 1dp hairline unless disabled.
 */
@Composable
fun CardSectionHeader(
    title: String,
    subtitle: String? = null,
    modifier: Modifier = Modifier,
    trailing: (@Composable () -> Unit)? = null,
    withDivider: Boolean = true,
) {
    Column(modifier = modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    text = title,
                    color = AgentBelayColors.inkPrimary,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold,
                    letterSpacing = (-0.1).sp,
                )
                if (subtitle != null) {
                    Text(
                        text = subtitle,
                        color = AgentBelayColors.inkMuted,
                        fontSize = 11.5.sp,
                    )
                }
            }
            trailing?.invoke()
        }
        if (withDivider) {
            HorizontalHairline()
        }
    }
}

@Preview(widthDp = 480, heightDp = 280)
@Composable
private fun PreviewCardSectionHeader() {
    PreviewScaffold {
        Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
            CardSectionHeader(
                title = "Most-requested tools",
                subtitle = "Ordered by total calls",
            )
            CardSectionHeader(
                title = "Breakdown",
                subtitle = "Past 7 days",
                trailing = {
                    Text(
                        text = "12 entries",
                        color = AgentBelayColors.inkMuted,
                        fontSize = 11.sp,
                    )
                },
            )
            CardSectionHeader(
                title = "Title only",
                withDivider = false,
            )
        }
    }
}
