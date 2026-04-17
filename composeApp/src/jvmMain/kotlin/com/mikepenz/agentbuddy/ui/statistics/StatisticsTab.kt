package com.mikepenz.agentbuddy.ui.statistics

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mikepenz.agentbuddy.model.DecisionGroup
import com.mikepenz.agentbuddy.model.hasMeaningfulLatency
import com.mikepenz.agentbuddy.storage.LatencyStats
import com.mikepenz.agentbuddy.storage.ProtectionHitCount
import com.mikepenz.agentbuddy.storage.StatsSummary
import kotlin.math.roundToInt

/**
 * Stateless Statistics tab. All data flows in via [state]; the only callback is
 * the time-window selector. Sections are vertically stacked in a scroll column
 * since the host panel is ~350px wide.
 */
@Composable
fun StatisticsTab(
    state: StatsUiState,
    onWindowChange: (TimeWindow) -> Unit,
) {
    val summary = state.summary
    val totalApprovals = (summary.byGroup[DecisionGroup.MANUAL_APPROVE] ?: 0) +
        (summary.byGroup[DecisionGroup.RISK_APPROVE] ?: 0)
    val totalDenials = (summary.byGroup[DecisionGroup.MANUAL_DENY] ?: 0) +
        (summary.byGroup[DecisionGroup.RISK_DENY] ?: 0) +
        (summary.byGroup[DecisionGroup.PROTECTION_BLOCK] ?: 0)
    val totalTimeouts = summary.byGroup[DecisionGroup.TIMEOUT] ?: 0
    val totalExternal = summary.byGroup[DecisionGroup.EXTERNAL] ?: 0

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 8.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        TimeWindowSelector(window = state.window, onWindowChange = onWindowChange)

        if (summary.totalDecisions == 0) {
            EmptyState()
            return@Column
        }

        SummaryGrid(
            total = summary.totalDecisions,
            approvals = totalApprovals,
            denials = totalDenials,
            timeouts = totalTimeouts,
            external = totalExternal,
        )

        SectionTitle("Decisions per day")
        if (summary.perDay.isEmpty()) {
            SectionEmpty("No daily data in this window")
        } else {
            DailyDecisionsChart(daily = summary.perDay, modifier = Modifier.fillMaxWidth())
            Legend(groups = DAILY_STACK_ORDER.filter { (summary.byGroup[it] ?: 0) > 0 })
        }

        SectionTitle("Approval sources")
        SourceBreakdown(
            label = "Approvals",
            total = totalApprovals,
            entries = listOf(
                DecisionGroup.MANUAL_APPROVE to (summary.byGroup[DecisionGroup.MANUAL_APPROVE] ?: 0),
                DecisionGroup.RISK_APPROVE to (summary.byGroup[DecisionGroup.RISK_APPROVE] ?: 0),
            ),
        )
        SourceBreakdown(
            label = "Denials",
            total = totalDenials,
            entries = listOf(
                DecisionGroup.MANUAL_DENY to (summary.byGroup[DecisionGroup.MANUAL_DENY] ?: 0),
                DecisionGroup.RISK_DENY to (summary.byGroup[DecisionGroup.RISK_DENY] ?: 0),
                DecisionGroup.PROTECTION_BLOCK to (summary.byGroup[DecisionGroup.PROTECTION_BLOCK] ?: 0),
            ),
        )

        SectionTitle("Time to decision")
        TimeToDecisionSection(summary = summary)

        SectionTitle("Top protection hits")
        if (summary.topProtections.isEmpty()) {
            SectionEmpty("No protection hits in this window")
        } else {
            ProtectionList(hits = summary.topProtections)
        }
    }
}

@Composable
private fun TimeWindowSelector(window: TimeWindow, onWindowChange: (TimeWindow) -> Unit) {
    val options = listOf(TimeWindow.Last7Days to "7d", TimeWindow.Last30Days to "30d", TimeWindow.AllTime to "All")
    SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
        options.forEachIndexed { index, (value, label) ->
            SegmentedButton(
                selected = window == value,
                onClick = { onWindowChange(value) },
                shape = SegmentedButtonDefaults.itemShape(index = index, count = options.size),
            ) {
                Text(label, fontSize = 12.sp)
            }
        }
    }
}

@Composable
private fun EmptyState() {
    Box(modifier = Modifier.fillMaxSize().padding(top = 64.dp), contentAlignment = Alignment.Center) {
        Text(
            "No data in this window",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
            textAlign = TextAlign.Center,
        )
    }
}

@Composable
private fun SummaryGrid(total: Int, approvals: Int, denials: Int, timeouts: Int, external: Int) {
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        SummaryCard("Total", total.toString(), Modifier.weight(1f))
        SummaryCard("Approved", approvals.toString(), Modifier.weight(1f))
    }
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        SummaryCard("Denied", denials.toString(), Modifier.weight(1f))
        SummaryCard("Timeout", timeouts.toString(), Modifier.weight(1f))
    }
    // Only surface the External card when at least one entry exists, so users
    // who never hit the harness-side decision path don't see a noisy 0.
    if (external > 0) {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            SummaryCard("External", external.toString(), Modifier.weight(1f))
            Spacer(modifier = Modifier.weight(1f))
        }
    }
}

@Composable
private fun SummaryCard(label: String, value: String, modifier: Modifier) {
    Card(
        modifier = modifier,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Text(label, fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(value, fontSize = 20.sp, fontWeight = FontWeight.SemiBold)
        }
    }
}

@Composable
private fun SectionTitle(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.titleSmall,
        modifier = Modifier.padding(top = 4.dp),
    )
}

@Composable
private fun SectionEmpty(text: String) {
    Text(
        text,
        fontSize = 12.sp,
        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
    )
}

@Composable
private fun Legend(groups: List<DecisionGroup>) {
    if (groups.isEmpty()) return
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        groups.chunked(2).forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                row.forEach { group ->
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        Box(
                            modifier = Modifier
                                .size(10.dp)
                                .clip(CircleShape)
                                .background(decisionGroupColor(group)),
                        )
                        Text(decisionGroupLabel(group), fontSize = 10.sp)
                    }
                }
            }
        }
    }
}

@Composable
private fun SourceBreakdown(label: String, total: Int, entries: List<Pair<DecisionGroup, Int>>) {
    Column {
        Text(
            "$label ($total)",
            fontSize = 12.sp,
            fontWeight = FontWeight.Medium,
            modifier = Modifier.padding(bottom = 2.dp),
        )
        if (total == 0) {
            SectionEmpty("None")
            return@Column
        }
        entries.filter { it.second > 0 }.forEach { (group, count) ->
            val pct = (100.0 * count / total).roundToInt()
            Row(
                modifier = Modifier.fillMaxWidth().padding(vertical = 1.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(
                    modifier = Modifier
                        .size(8.dp)
                        .clip(CircleShape)
                        .background(decisionGroupColor(group)),
                )
                Text(
                    decisionGroupLabel(group),
                    fontSize = 11.sp,
                    modifier = Modifier.padding(start = 6.dp).weight(1f),
                )
                Text("$count ($pct%)", fontSize = 11.sp)
            }
            // Inline progress bar.
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 14.dp, bottom = 2.dp)
                    .height(4.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant),
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth(count.toFloat() / total.toFloat())
                        .height(4.dp)
                        .background(decisionGroupColor(group)),
                )
            }
        }
    }
}

@Composable
private fun TimeToDecisionSection(summary: StatsSummary) {
    val groups = listOf(
        DecisionGroup.MANUAL_APPROVE,
        DecisionGroup.RISK_APPROVE,
        DecisionGroup.MANUAL_DENY,
        DecisionGroup.RISK_DENY,
    )
    val anySamples = groups.any { (summary.latencyByGroup[it]?.count ?: 0) > 0 }
    if (!anySamples) {
        SectionEmpty("No deliberated decisions yet")
        return
    }
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        groups.forEach { group ->
            val stats = summary.latencyByGroup[group]
            if (stats != null && stats.count > 0) {
                LatencyStatsRow(group, stats)
            }
        }
    }
    LatencyHistogramChart(
        histogramByGroup = summary.latencyHistogramByGroup,
        modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
    )
    Legend(groups = groups.filter { it.hasMeaningfulLatency && (summary.latencyByGroup[it]?.count ?: 0) > 0 })
}

@Composable
private fun LatencyStatsRow(group: DecisionGroup, stats: LatencyStats) {
    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Box(
            modifier = Modifier
                .size(8.dp)
                .clip(CircleShape)
                .background(decisionGroupColor(group)),
        )
        Text(
            decisionGroupLabel(group),
            fontSize = 11.sp,
            modifier = Modifier.padding(start = 6.dp).weight(1f),
        )
        Text(
            "avg ${formatSeconds(stats.avgSeconds)} · p50 ${formatSeconds(stats.p50Seconds)} · p90 ${formatSeconds(stats.p90Seconds)}",
            fontSize = 10.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun ProtectionList(hits: List<ProtectionHitCount>) {
    val max = hits.maxOf { it.count }.coerceAtLeast(1)
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        hits.forEach { hit ->
            Column(modifier = Modifier.fillMaxWidth()) {
                Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        "${hit.moduleId} · ${hit.ruleId}",
                        fontSize = 11.sp,
                        modifier = Modifier.weight(1f),
                    )
                    Text(hit.count.toString(), fontSize = 11.sp, fontWeight = FontWeight.Medium)
                }
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 1.dp, bottom = 2.dp)
                        .height(4.dp)
                        .clip(RoundedCornerShape(2.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant),
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth(hit.count.toFloat() / max.toFloat())
                            .height(4.dp)
                            .background(MaterialTheme.colorScheme.secondary),
                    )
                }
            }
        }
    }
}

private fun formatSeconds(seconds: Double): String = when {
    seconds < 1.0 -> "${(seconds * 1000).roundToInt()}ms"
    seconds < 60.0 -> "${seconds.roundToInt()}s"
    seconds < 3600.0 -> "${(seconds / 60).roundToInt()}m"
    else -> "${(seconds / 3600).roundToInt()}h"
}
