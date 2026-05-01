package com.mikepenz.agentbelay.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

/**
 * Shared sort-pill + harness multi-select row used by both the History
 * and Insights screens. Centralising the layout here keeps the two
 * screens' filter chrome visually identical: same control heights, same
 * spacing, same wrap behaviour at narrow widths.
 *
 * The two controls' natural heights already match (PillSegmented MD ≈
 * 34.dp from its 1.dp border + 3.dp padding + 26.dp content; the
 * dropdown trigger is an explicit 34.dp). FlowRow lets the dropdown wrap
 * below the pill on very narrow rails (Insights' 280.dp side rail) while
 * keeping a single line at desktop widths.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun <S, H> SortAndFilterRow(
    sortOptions: List<Pair<S, String>>,
    sortSelected: S,
    onSortChange: (S) -> Unit,
    harnessOptions: List<Pair<H, String>>,
    harnessSelected: Set<H>?,
    onHarnessChange: (Set<H>?) -> Unit,
    modifier: Modifier = Modifier,
    harnessAllLabel: String = "All harnesses",
    harnessLeadingDot: ((H) -> Color)? = null,
) {
    FlowRow(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        PillSegmented(
            options = sortOptions,
            selected = sortSelected,
            onSelect = onSortChange,
        )
        MultiSelectDropdown(
            options = harnessOptions,
            selected = harnessSelected,
            onChange = onHarnessChange,
            allLabel = harnessAllLabel,
            leadingDot = harnessLeadingDot,
        )
    }
}
