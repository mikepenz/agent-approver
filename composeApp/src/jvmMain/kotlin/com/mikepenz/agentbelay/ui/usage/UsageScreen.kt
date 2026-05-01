package com.mikepenz.agentbelay.ui.usage

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.animation.core.animateFloat
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.graphicsLayer
import com.mikepenz.agentbelay.ui.components.LocalPreviewHoverOverride
import com.mikepenz.agentbelay.ui.icons.LucideRefreshCw
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.material3.Text
import com.mikepenz.agentbelay.model.Source
import com.mikepenz.agentbelay.ui.components.AgentBelayCard
import com.mikepenz.agentbelay.ui.components.CardSectionHeader
import com.mikepenz.agentbelay.ui.components.HorizontalHairline
import com.mikepenz.agentbelay.ui.components.PillSegmented
import com.mikepenz.agentbelay.ui.theme.AccentEmerald
import com.mikepenz.agentbelay.ui.theme.AgentBelayColors
import com.mikepenz.agentbelay.ui.theme.DangerRed
import com.mikepenz.agentbelay.ui.theme.InfoBlue
import com.mikepenz.agentbelay.ui.theme.PreviewScaffold
import com.mikepenz.agentbelay.ui.theme.SourceClaudeColor
import com.mikepenz.agentbelay.ui.theme.SourceCopilotColor
import com.mikepenz.agentbelay.ui.theme.VioletPurple
import com.mikepenz.agentbelay.ui.theme.WarnYellow

// ── Projections ──────────────────────────────────────────────────────────────

enum class UsageRange(val key: String, val label: String) {
    Last24h("24h", "24h"),
    Last7d("7d", "7d"),
    Last30d("30d", "30d"),
    AllTime("all", "All"),
}

/**
 * One row in the per-harness performance table. All token / cost figures are
 * pre-aggregated in [UsageViewModel] from the `usage_records` table.
 *
 * Latency columns are nullable because we only know p50/p95 when there is
 * approval-history data joined on the same `Source` — fresh installs
 * legitimately lack it.
 */
data class HarnessUsageRow(
    val source: Source,
    val displayName: String,
    val model: String?,
    val accent: Color,
    val active: Boolean,
    val sessions: Int,
    val requests: Int,
    val tokensIn: Long,
    val tokensOut: Long,
    val tokensCacheRead: Long,
    val tokensCacheWrite: Long,
    val reasoningTokens: Long,
    val cost: Double,
    val medianLatencySeconds: Double?,
    val p95LatencySeconds: Double?,
    val errorRate: Double,
    val sparkline: List<Int>,
)

data class UsageScreenData(
    val rows: List<HarnessUsageRow>,
    val totalCost: Double,
    val totalTokensIn: Long,
    val totalTokensOut: Long,
    val totalTokensCache: Long,
    val totalRequests: Int,
    val totalSessions: Int,
) {
    val isEmpty: Boolean get() = rows.isEmpty() || rows.all { it.requests == 0 }
}

// ── Screen ───────────────────────────────────────────────────────────────────

@Composable
fun UsageScreen(
    data: UsageScreenData,
    selectedSource: Source?,
    onSelectSource: (Source) -> Unit,
    range: UsageRange = UsageRange.Last7d,
    onRangeChange: (UsageRange) -> Unit = {},
    loading: Boolean = false,
    refreshing: Boolean = false,
    onRefresh: () -> Unit = {},
    modifier: Modifier = Modifier,
) {
    BoxWithConstraints(modifier = modifier.fillMaxSize().background(AgentBelayColors.background)) {
        val isCompact = maxWidth < 800.dp
        val stackHeader = maxWidth < 580.dp
        Column(modifier = Modifier.fillMaxSize()) {
            UsageHeader(
                range = range,
                onRangeChange = onRangeChange,
                stackPill = stackHeader,
                refreshing = refreshing,
                onRefresh = onRefresh,
            )
            if (data.isEmpty && loading) {
                ScanningState(modifier = Modifier.fillMaxWidth().weight(1f))
            } else if (data.isEmpty) {
                Box(
                    modifier = Modifier.fillMaxWidth().weight(1f),
                    contentAlignment = Alignment.Center,
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            text = "No usage yet",
                            color = AgentBelayColors.inkSecondary,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Medium,
                        )
                        Spacer(Modifier.height(6.dp))
                        Text(
                            text = "Token usage and cost will appear here once a connected harness has activity.",
                            color = AgentBelayColors.inkMuted,
                            fontSize = 12.sp,
                            textAlign = TextAlign.Center,
                        )
                    }
                }
            } else {
                val selected = data.rows.firstOrNull { it.source == selectedSource } ?: data.rows.first()
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                        .verticalScroll(rememberScrollState())
                        .padding(start = 28.dp, end = 28.dp, top = 22.dp, bottom = 28.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp),
                ) {
                    UsageKpiGrid(data = data, columns = if (isCompact) 2 else 4)
                    CostDistributionCard(data = data, range = range)
                    HarnessPerformanceCard(
                        rows = data.rows,
                        selected = selected.source,
                        onSelect = onSelectSource,
                        compact = isCompact,
                    )
                    HarnessDetailCard(row = selected, range = range, compact = isCompact)
                }
            }
        }
    }
}

@Composable
private fun UsageHeader(
    range: UsageRange,
    onRangeChange: (UsageRange) -> Unit,
    stackPill: Boolean,
    refreshing: Boolean = false,
    onRefresh: () -> Unit = {},
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 28.dp, end = 28.dp, top = 18.dp),
    ) {
        val titleBlock: @Composable () -> Unit = {
            Column {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        text = "Usage",
                        color = AgentBelayColors.inkPrimary,
                        fontSize = 20.sp,
                        fontWeight = FontWeight.SemiBold,
                        letterSpacing = (-0.4).sp,
                    )
                    ExperimentalBadge()
                }
                Spacer(Modifier.height(4.dp))
                Text(
                    text = "Tokens, cost & performance across connected harnesses",
                    color = AgentBelayColors.inkTertiary,
                    fontSize = 12.sp,
                )
            }
        }
        val controls: @Composable () -> Unit = {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                RefreshButton(refreshing = refreshing, onClick = onRefresh)
                PillSegmented(
                    options = UsageRange.entries.map { it to it.label },
                    selected = range,
                    onSelect = onRangeChange,
                )
            }
        }
        if (stackPill) {
            titleBlock()
            Spacer(Modifier.height(10.dp))
            controls()
        } else {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth(),
            ) {
                titleBlock()
                controls()
            }
        }
        Spacer(Modifier.height(14.dp))
        HorizontalHairline()
    }
}

// ── KPI grid ─────────────────────────────────────────────────────────────────

@Composable
private fun UsageKpiGrid(data: UsageScreenData, columns: Int) {
    val ratio = if (data.totalTokensIn > 0)
        (data.totalTokensOut.toDouble() / data.totalTokensIn.toDouble() * 100.0)
    else 0.0
    val kpis = listOf(
        UsageKpi("Total cost", fmtCost(data.totalCost), "across ${data.rows.size} harnesses", AccentEmerald),
        UsageKpi("Tokens in", fmtTok(data.totalTokensIn), "+ ${fmtTok(data.totalTokensCache)} cache", null),
        UsageKpi("Tokens out", fmtTok(data.totalTokensOut), "${"%.1f".format(ratio)}% ratio", null),
        UsageKpi("Requests", data.totalRequests.toString(), "${data.totalSessions} sessions", null),
    )
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        kpis.chunked(columns).forEach { row ->
            Row(
                modifier = Modifier.fillMaxWidth().height(IntrinsicSize.Max),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                row.forEach { k ->
                    UsageKpiCard(k, modifier = Modifier.weight(1f).fillMaxHeight())
                }
                val missing = columns - row.size
                if (missing > 0) Spacer(Modifier.weight(missing.toFloat()))
            }
        }
    }
}

private data class UsageKpi(
    val label: String,
    val value: String,
    val sub: String,
    val accent: Color?,
)

@Composable
private fun UsageKpiCard(kpi: UsageKpi, modifier: Modifier = Modifier) {
    AgentBelayCard(modifier = modifier) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(5.dp),
            ) {
                if (kpi.accent != null) {
                    Box(
                        modifier = Modifier
                            .size(5.dp)
                            .clip(CircleShape)
                            .background(kpi.accent),
                    )
                }
                Text(
                    text = kpi.label.uppercase(),
                    color = AgentBelayColors.inkMuted,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.SemiBold,
                    letterSpacing = 0.4.sp,
                )
            }
            Spacer(Modifier.height(10.dp))
            Text(
                text = kpi.value,
                color = AgentBelayColors.inkPrimary,
                fontSize = 24.sp,
                fontWeight = FontWeight.SemiBold,
                letterSpacing = (-0.6).sp,
                fontFamily = FontFamily.Default,
                maxLines = 1,
                softWrap = false,
                overflow = TextOverflow.Clip,
            )
            Spacer(Modifier.height(4.dp))
            Text(
                text = kpi.sub,
                color = AgentBelayColors.inkMuted,
                fontSize = 11.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

// ── Cost distribution ───────────────────────────────────────────────────────

@Composable
private fun CostDistributionCard(data: UsageScreenData, range: UsageRange) {
    AgentBelayCard(modifier = Modifier.fillMaxWidth()) {
        Column {
            CardSectionHeader(
                title = "Cost distribution",
                subtitle = "By harness · last ${range.label}",
                trailing = {
                    Text(
                        text = fmtCost(data.totalCost),
                        color = AgentBelayColors.inkPrimary,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.SemiBold,
                        letterSpacing = (-0.3).sp,
                    )
                },
            )
            Column(modifier = Modifier.padding(16.dp)) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(10.dp)
                        .clip(RoundedCornerShape(4.dp))
                        .background(AgentBelayColors.surface2),
                    horizontalArrangement = Arrangement.spacedBy(2.dp),
                ) {
                    val total = data.totalCost.coerceAtLeast(0.0001)
                    data.rows.filter { it.cost > 0 }.forEach { r ->
                        val w = (r.cost / total).toFloat().coerceIn(0.005f, 1f)
                        Box(
                            modifier = Modifier
                                .weight(w)
                                .fillMaxHeight()
                                .background(r.accent),
                        )
                    }
                }
                Spacer(Modifier.height(14.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(18.dp),
                ) {
                    data.rows.forEach { r ->
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Box(
                                modifier = Modifier
                                    .size(9.dp)
                                    .clip(RoundedCornerShape(2.dp))
                                    .background(r.accent),
                            )
                            Text(r.displayName, color = AgentBelayColors.inkSecondary, fontSize = 12.sp)
                            val pct = if (data.totalCost > 0) (r.cost / data.totalCost * 100.0) else 0.0
                            Text(
                                text = "${fmtCost(r.cost)} · ${pct.toInt()}%",
                                color = AgentBelayColors.inkTertiary,
                                fontSize = 12.sp,
                            )
                        }
                    }
                }
            }
        }
    }
}

// ── Per-harness performance table ───────────────────────────────────────────

@Composable
private fun HarnessPerformanceCard(
    rows: List<HarnessUsageRow>,
    selected: Source?,
    onSelect: (Source) -> Unit,
    compact: Boolean,
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.Bottom,
        ) {
            Text(
                text = "Harness performance",
                color = AgentBelayColors.inkPrimary,
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold,
                letterSpacing = (-0.1).sp,
            )
            Text(
                text = "Click a row for breakdown",
                color = AgentBelayColors.inkMuted,
                fontSize = 11.sp,
            )
        }
        AgentBelayCard(modifier = Modifier.fillMaxWidth()) {
            Column {
                HarnessHeaderRow(compact = compact)
                rows.forEachIndexed { idx, row ->
                    HarnessTableRow(
                        row = row,
                        compact = compact,
                        active = row.source == selected,
                        onClick = { onSelect(row.source) },
                    )
                    if (idx < rows.lastIndex) HorizontalHairline()
                }
            }
        }
    }
}

@Composable
private fun HarnessHeaderRow(compact: Boolean) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 18.dp, vertical = 10.dp),
        horizontalArrangement = Arrangement.spacedBy(14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        ColLabel("Harness", weight = 1.6f, alignEnd = false)
        ColLabel("Tokens", weight = 1f)
        if (!compact) ColLabel("Cache hit", weight = 1f)
        ColLabel("Cost", weight = 1f)
        if (!compact) ColLabel("p50 / p95", weight = 1f)
        ColLabel("Errors", weight = 0.8f)
    }
    HorizontalHairline()
}

@Composable
private fun RowScope.ColLabel(
    text: String,
    weight: Float,
    alignEnd: Boolean = true,
) {
    Box(
        modifier = Modifier.weight(weight),
        contentAlignment = if (alignEnd) Alignment.CenterEnd else Alignment.CenterStart,
    ) {
        Text(
            text = text.uppercase(),
            color = AgentBelayColors.inkMuted,
            fontSize = 10.sp,
            fontWeight = FontWeight.SemiBold,
            letterSpacing = 0.5.sp,
        )
    }
}

@Composable
private fun HarnessTableRow(
    row: HarnessUsageRow,
    compact: Boolean,
    active: Boolean,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(if (active) AgentBelayColors.surface2 else Color.Transparent)
            .clickable(onClick = onClick)
            .padding(horizontal = 18.dp, vertical = 14.dp),
        horizontalArrangement = Arrangement.spacedBy(14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // Harness column
        Box(modifier = Modifier.weight(1.6f)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Box(
                    modifier = Modifier
                        .size(7.dp)
                        .clip(CircleShape)
                        .background(row.accent),
                )
                Column {
                    Text(
                        text = row.displayName,
                        color = AgentBelayColors.inkPrimary,
                        fontSize = 12.5.sp,
                        fontWeight = FontWeight.Medium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        text = row.model ?: "—",
                        color = AgentBelayColors.inkMuted,
                        fontSize = 10.5.sp,
                        fontFamily = FontFamily.Monospace,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
        Cell(weight = 1f) { CellText(fmtTok(row.tokensIn + row.tokensOut), AgentBelayColors.inkSecondary) }
        if (!compact) {
            Cell(weight = 1f) {
                val pct = cacheHitPct(row)
                CellText(if (pct == null) "—" else "$pct%", AgentBelayColors.inkSecondary)
            }
        }
        Cell(weight = 1f) {
            CellText(fmtCost(row.cost), AgentBelayColors.inkPrimary, weight = FontWeight.Medium)
        }
        if (!compact) {
            Cell(weight = 1f) {
                val text = when {
                    row.medianLatencySeconds == null -> "—"
                    row.p95LatencySeconds == null -> "${fmtSec(row.medianLatencySeconds)} / —"
                    else -> "${fmtSec(row.medianLatencySeconds)} / ${fmtSec(row.p95LatencySeconds)}"
                }
                CellText(text, AgentBelayColors.inkSecondary)
            }
        }
        Cell(weight = 0.8f) {
            val color = when {
                row.errorRate >= 1.0 -> WarnYellow
                row.errorRate > 0 -> AgentBelayColors.inkSecondary
                else -> AccentEmerald
            }
            CellText("${"%.1f".format(row.errorRate)}%", color)
        }
    }
}

@Composable
private fun RowScope.Cell(
    weight: Float,
    content: @Composable () -> Unit,
) {
    Box(modifier = Modifier.weight(weight), contentAlignment = Alignment.CenterEnd) {
        content()
    }
}

@Composable
private fun CellText(text: String, color: Color, weight: FontWeight = FontWeight.Normal) {
    Text(
        text = text,
        color = color,
        fontSize = 12.sp,
        fontWeight = weight,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        fontFamily = FontFamily.Default,
    )
}

// ── Detail panel ────────────────────────────────────────────────────────────

@Composable
private fun HarnessDetailCard(row: HarnessUsageRow, range: UsageRange, compact: Boolean) {
    AgentBelayCard(modifier = Modifier.fillMaxWidth()) {
        Column {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 18.dp, vertical = 14.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Box(
                    modifier = Modifier
                        .size(8.dp)
                        .clip(RoundedCornerShape(2.dp))
                        .background(row.accent),
                )
                CellText(row.displayName, AgentBelayColors.inkPrimary, FontWeight.SemiBold)
                if (row.model != null) {
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(4.dp))
                            .background(AgentBelayColors.surface2)
                            .border(1.dp, AgentBelayColors.line1, RoundedCornerShape(4.dp))
                            .padding(horizontal = 6.dp, vertical = 2.dp),
                    ) {
                        androidx.compose.material3.Text(
                            text = row.model,
                            color = AgentBelayColors.inkMuted,
                            fontSize = 10.5.sp,
                            fontFamily = FontFamily.Monospace,
                        )
                    }
                }
                Spacer(Modifier.weight(1f))
                StateBadge(active = row.active)
            }
            HorizontalHairline()
            // Stats row
            val stats = listOf(
                Triple("Input tokens", fmtTok(row.tokensIn), "${row.requests} requests"),
                Triple("Output tokens", fmtTok(row.tokensOut), if (row.tokensIn > 0) "${"%.1f".format(row.tokensOut.toDouble() / row.tokensIn * 100)}% ratio" else "—"),
                Triple("Cache hit", fmtTok(row.tokensCacheRead), cacheSavedLabel(row)),
                Triple("Spend", fmtCost(row.cost), avgPerRequestLabel(row)),
            )
            val cols = if (compact) 2 else 4
            stats.chunked(cols).forEach { rowItems ->
                Row(modifier = Modifier.fillMaxWidth().height(IntrinsicSize.Max)) {
                    rowItems.forEachIndexed { i, (label, value, sub) ->
                        DetailStat(label = label, value = value, sub = sub, modifier = Modifier.weight(1f).fillMaxHeight())
                        if (i < rowItems.lastIndex) {
                            Box(
                                modifier = Modifier
                                    .width(1.dp)
                                    .fillMaxHeight()
                                    .background(AgentBelayColors.line1),
                            )
                        }
                    }
                }
                HorizontalHairline()
            }
            // Performance row
            Row(modifier = Modifier.fillMaxWidth().height(IntrinsicSize.Max)) {
                PerfBlock(
                    title = "Latency",
                    desc = "Per-request decision time",
                    modifier = Modifier.weight(1f).fillMaxHeight(),
                ) {
                    Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                        BigStat(value = row.medianLatencySeconds?.let { fmtSec(it) } ?: "—", label = "p50")
                        BigStat(value = row.p95LatencySeconds?.let { fmtSec(it) } ?: "—", label = "p95", muted = true)
                    }
                }
                Box(
                    modifier = Modifier
                        .width(1.dp)
                        .fillMaxHeight()
                        .background(AgentBelayColors.line1),
                )
                PerfBlock(
                    title = "Activity",
                    desc = "Last ${range.label} · request volume",
                    modifier = Modifier.weight(1f).fillMaxHeight(),
                ) {
                    Sparkline(data = row.sparkline, color = row.accent, modifier = Modifier.fillMaxWidth().height(48.dp))
                }
            }
        }
    }
}

@Composable
private fun DetailStat(label: String, value: String, sub: String, modifier: Modifier = Modifier) {
    Column(modifier = modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        androidx.compose.material3.Text(
            text = label.uppercase(),
            color = AgentBelayColors.inkMuted,
            fontSize = 10.sp,
            fontWeight = FontWeight.SemiBold,
            letterSpacing = 0.5.sp,
        )
        androidx.compose.material3.Text(
            text = value,
            color = AgentBelayColors.inkPrimary,
            fontSize = 18.sp,
            fontWeight = FontWeight.SemiBold,
            letterSpacing = (-0.4).sp,
        )
        androidx.compose.material3.Text(
            text = sub,
            color = AgentBelayColors.inkMuted,
            fontSize = 11.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun PerfBlock(
    title: String,
    desc: String,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    Column(modifier = modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        androidx.compose.material3.Text(
            text = title,
            color = AgentBelayColors.inkPrimary,
            fontSize = 12.sp,
            fontWeight = FontWeight.SemiBold,
        )
        androidx.compose.material3.Text(
            text = desc,
            color = AgentBelayColors.inkMuted,
            fontSize = 10.5.sp,
        )
        Spacer(Modifier.height(2.dp))
        content()
    }
}

@Composable
private fun BigStat(value: String, label: String, muted: Boolean = false) {
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        androidx.compose.material3.Text(
            text = value,
            color = if (muted) AgentBelayColors.inkSecondary else AgentBelayColors.inkPrimary,
            fontSize = 22.sp,
            fontWeight = FontWeight.SemiBold,
            letterSpacing = (-0.5).sp,
        )
        androidx.compose.material3.Text(
            text = label,
            color = AgentBelayColors.inkMuted,
            fontSize = 10.5.sp,
            fontWeight = FontWeight.SemiBold,
            letterSpacing = 0.4.sp,
        )
    }
}

@Composable
private fun StateBadge(active: Boolean) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(5.dp)) {
        Box(
            modifier = Modifier
                .size(6.dp)
                .clip(CircleShape)
                .background(if (active) AccentEmerald else AgentBelayColors.inkSubtle),
        )
        androidx.compose.material3.Text(
            text = if (active) "ACTIVE" else "IDLE",
            color = if (active) AccentEmerald else AgentBelayColors.inkMuted,
            fontSize = 10.sp,
            fontWeight = FontWeight.SemiBold,
            letterSpacing = 0.4.sp,
        )
    }
}

// ── Sparkline ───────────────────────────────────────────────────────────────

@Composable
private fun Sparkline(data: List<Int>, color: Color, modifier: Modifier = Modifier) {
    if (data.isEmpty()) {
        Box(modifier = modifier, contentAlignment = Alignment.CenterStart) {
            androidx.compose.material3.Text(
                text = "no data",
                color = AgentBelayColors.inkMuted,
                fontSize = 11.sp,
            )
        }
        return
    }
    val max = (data.maxOrNull() ?: 1).coerceAtLeast(1)
    Box(
        modifier = modifier.drawBehind {
            if (data.size < 2) return@drawBehind
            val stepX = size.width / (data.size - 1).coerceAtLeast(1)
            val path = Path().apply {
                data.forEachIndexed { i, v ->
                    val x = stepX * i
                    val y = size.height - (v.toFloat() / max.toFloat()) * size.height
                    if (i == 0) moveTo(x, y) else lineTo(x, y)
                }
            }
            drawPath(
                path = path,
                color = color,
                style = Stroke(width = 1.6f, cap = StrokeCap.Round),
            )
            // Subtle baseline
            drawLine(
                color = color.copy(alpha = 0.15f),
                start = Offset(0f, size.height - 0.5f),
                end = Offset(size.width, size.height - 0.5f),
                strokeWidth = 1f,
            )
        },
    )
}

// ── Refresh button ──────────────────────────────────────────────────────────

/**
 * Manual "force a scan now" trigger sitting next to the range pill. Spins
 * while the ingest service is mid-pass; otherwise it's a static icon.
 * Disabled-while-refreshing prevents back-to-back clicks from queueing
 * passes (the service's mutex would serialize them anyway, but visually it
 * looks broken).
 */
@Composable
private fun RefreshButton(refreshing: Boolean, onClick: () -> Unit) {
    val infinite = androidx.compose.animation.core.rememberInfiniteTransition(label = "refresh-spin")
    val rotation by infinite.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = androidx.compose.animation.core.infiniteRepeatable(
            animation = androidx.compose.animation.core.tween(
                durationMillis = 900,
                easing = androidx.compose.animation.core.LinearEasing,
            ),
        ),
        label = "refresh-rotation",
    )
    val interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() }
    val liveHovered by interactionSource.collectIsHoveredAsState()
    val isHovered = LocalPreviewHoverOverride.current ?: liveHovered

    Box(
        modifier = Modifier
            .size(28.dp)
            .clip(RoundedCornerShape(6.dp))
            .background(if (isHovered && !refreshing) AgentBelayColors.surface2 else AgentBelayColors.background)
            .border(
                1.dp,
                if (isHovered && !refreshing) AgentBelayColors.line2 else AgentBelayColors.line1,
                RoundedCornerShape(6.dp),
            )
            .hoverable(interactionSource, enabled = !refreshing)
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                enabled = !refreshing,
                role = androidx.compose.ui.semantics.Role.Button,
                onClickLabel = "Refresh usage",
            ) { onClick() },
        contentAlignment = Alignment.Center,
    ) {
        androidx.compose.material3.Icon(
            imageVector = LucideRefreshCw,
            contentDescription = if (refreshing) "Refreshing usage" else "Refresh usage",
            tint = if (refreshing) AccentEmerald else AgentBelayColors.inkTertiary,
            modifier = Modifier
                .size(13.dp)
                .then(
                    if (refreshing) Modifier.graphicsLayer { rotationZ = rotation }
                    else Modifier,
                ),
        )
    }
}

// ── Experimental badge ──────────────────────────────────────────────────────

/**
 * Small "EXPERIMENTAL" pill rendered to the right of the Usage title. The
 * Usage tab depends on harness session-file formats that vary across releases
 * (OpenCode SQLite columns, Pi/Copilot field names) — surfacing this lets
 * users know totals may be approximate while we stabilize the parsers.
 */
@Composable
private fun ExperimentalBadge() {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(4.dp))
            .background(WarnYellow.copy(alpha = 0.16f))
            .border(1.dp, WarnYellow.copy(alpha = 0.45f), RoundedCornerShape(4.dp))
            .padding(horizontal = 7.dp, vertical = 2.dp),
    ) {
        Text(
            text = "EXPERIMENTAL",
            color = WarnYellow,
            fontSize = 9.5.sp,
            fontWeight = FontWeight.SemiBold,
            letterSpacing = 0.6.sp,
        )
    }
}

// ── Scanning state ──────────────────────────────────────────────────────────

/**
 * Shown while the ingest service is still doing its first pass — the harness
 * sessions are being read off disk and token totals are being computed. We
 * surface this explicitly because "No usage yet" would be misleading: the
 * scanner may already have records on disk and just hasn't reported them yet.
 */
@Composable
private fun ScanningState(modifier: Modifier = Modifier) {
    Box(modifier = modifier, contentAlignment = Alignment.Center) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            ScanningPulse()
            Text(
                text = "Scanning harness sessions…",
                color = AgentBelayColors.inkSecondary,
                fontSize = 14.sp,
                fontWeight = FontWeight.Medium,
            )
            Text(
                text = "Reading Claude Code, Codex, Copilot, OpenCode and Pi session files to compute token usage and cost.",
                color = AgentBelayColors.inkMuted,
                fontSize = 12.sp,
                textAlign = TextAlign.Center,
                modifier = Modifier.width(380.dp),
            )
        }
    }
}

@Composable
private fun ScanningPulse() {
    val infinite = androidx.compose.animation.core.rememberInfiniteTransition(label = "scanning-pulse")
    val alpha by infinite.animateFloat(
        initialValue = 0.25f,
        targetValue = 1f,
        animationSpec = androidx.compose.animation.core.infiniteRepeatable(
            animation = androidx.compose.animation.core.tween(900),
            repeatMode = androidx.compose.animation.core.RepeatMode.Reverse,
        ),
        label = "scanning-alpha",
    )
    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        repeat(3) { i ->
            Box(
                modifier = Modifier
                    .size(8.dp)
                    .clip(CircleShape)
                    .background(AccentEmerald.copy(alpha = if (i == 1) alpha else alpha * 0.6f)),
            )
        }
    }
}

// ── Helpers ─────────────────────────────────────────────────────────────────

private fun cacheHitPct(row: HarnessUsageRow): Int? {
    val totalReadable = row.tokensIn + row.tokensCacheRead
    if (totalReadable <= 0) return null
    return (row.tokensCacheRead.toDouble() / totalReadable.toDouble() * 100.0).toInt()
}

private fun cacheSavedLabel(row: HarnessUsageRow): String {
    if (row.tokensCacheRead == 0L) return "no caching"
    val totalReadable = row.tokensIn + row.tokensCacheRead
    val pct = if (totalReadable > 0)
        (row.tokensCacheRead.toDouble() / totalReadable.toDouble() * 100.0).toInt()
    else 0
    return "$pct% saved"
}

private fun avgPerRequestLabel(row: HarnessUsageRow): String {
    if (row.requests == 0) return "—"
    val per1k = row.cost / row.requests * 1000.0
    return "avg ${fmtCost(per1k)}/1k req"
}

internal fun fmtTok(n: Long): String = when {
    n >= 1_000_000 -> "${"%.2f".format(n / 1_000_000.0)}M"
    n >= 1_000 -> "${(n / 1_000.0).toInt()}k"
    else -> n.toString()
}

internal fun fmtCost(n: Double): String = "$" + "%.2f".format(n)
internal fun fmtSec(n: Double): String = "${"%.1f".format(n)}s"

internal fun colorForSource(source: Source): Color = when (source) {
    Source.CLAUDE_CODE -> SourceClaudeColor
    Source.COPILOT -> SourceCopilotColor
    Source.OPENCODE -> InfoBlue
    Source.PI -> VioletPurple
    Source.CODEX -> WarnYellow
}

internal fun displayNameForSource(source: Source): String = when (source) {
    Source.CLAUDE_CODE -> "Claude Code"
    Source.COPILOT -> "GitHub Copilot"
    Source.OPENCODE -> "OpenCode"
    Source.PI -> "Pi"
    Source.CODEX -> "Codex"
}

// ── Previews ────────────────────────────────────────────────────────────────

private fun sampleData(): UsageScreenData {
    val rows = listOf(
        HarnessUsageRow(
            source = Source.CLAUDE_CODE,
            displayName = "Claude Code",
            model = "claude-sonnet-4-5",
            accent = SourceClaudeColor,
            active = true,
            sessions = 12, requests = 487,
            tokensIn = 2_840_000, tokensOut = 412_000,
            tokensCacheRead = 11_200_000, tokensCacheWrite = 0, reasoningTokens = 0,
            cost = 9.84,
            medianLatencySeconds = 1.8, p95LatencySeconds = 4.2,
            errorRate = 0.4,
            sparkline = listOf(12, 18, 22, 15, 28, 34, 30, 26, 38, 42, 36, 48),
        ),
        HarnessUsageRow(
            source = Source.COPILOT,
            displayName = "GitHub Copilot",
            model = "gpt-5-codex",
            accent = SourceCopilotColor,
            active = true,
            sessions = 8, requests = 312,
            tokensIn = 1_120_000, tokensOut = 184_000,
            tokensCacheRead = 0, tokensCacheWrite = 0, reasoningTokens = 0,
            cost = 4.21,
            medianLatencySeconds = 0.9, p95LatencySeconds = 2.1,
            errorRate = 1.1,
            sparkline = listOf(8, 12, 14, 18, 16, 22, 20, 18, 24, 28, 26, 30),
        ),
        HarnessUsageRow(
            source = Source.CODEX,
            displayName = "Codex",
            model = "gpt-5-codex",
            accent = WarnYellow,
            active = false,
            sessions = 2, requests = 41,
            tokensIn = 186_000, tokensOut = 22_000,
            tokensCacheRead = 540_000, tokensCacheWrite = 0, reasoningTokens = 8_000,
            cost = 0.62,
            medianLatencySeconds = 1.4, p95LatencySeconds = 3.8,
            errorRate = 0.0,
            sparkline = listOf(4, 6, 3, 5, 8, 4, 2, 3, 5, 4, 2, 2),
        ),
    )
    val totalCost = rows.sumOf { it.cost }
    return UsageScreenData(
        rows = rows,
        totalCost = totalCost,
        totalTokensIn = rows.sumOf { it.tokensIn },
        totalTokensOut = rows.sumOf { it.tokensOut },
        totalTokensCache = rows.sumOf { it.tokensCacheRead + it.tokensCacheWrite },
        totalRequests = rows.sumOf { it.requests },
        totalSessions = rows.sumOf { it.sessions },
    )
}

private fun emptyData(): UsageScreenData = UsageScreenData(
    rows = emptyList(),
    totalCost = 0.0,
    totalTokensIn = 0,
    totalTokensOut = 0,
    totalTokensCache = 0,
    totalRequests = 0,
    totalSessions = 0,
)

@Preview(widthDp = 1088, heightDp = 1100)
@Composable
private fun PreviewUsageScreen() {
    PreviewScaffold {
        val data = remember { sampleData() }
        UsageScreen(
            data = data,
            selectedSource = Source.CLAUDE_CODE,
            onSelectSource = {},
            range = UsageRange.Last7d,
        )
    }
}

@Preview(widthDp = 720, heightDp = 1400)
@Composable
private fun PreviewUsageScreenCompact() {
    PreviewScaffold {
        val data = remember { sampleData() }
        UsageScreen(
            data = data,
            selectedSource = Source.COPILOT,
            onSelectSource = {},
            range = UsageRange.Last30d,
        )
    }
}

@Preview(widthDp = 510, heightDp = 1600)
@Composable
private fun PreviewUsageScreenSlim() {
    PreviewScaffold {
        val data = remember { sampleData() }
        UsageScreen(
            data = data,
            selectedSource = Source.CLAUDE_CODE,
            onSelectSource = {},
            range = UsageRange.Last24h,
        )
    }
}

@Preview(widthDp = 1088, heightDp = 600)
@Composable
private fun PreviewUsageScreenEmpty() {
    PreviewScaffold {
        UsageScreen(
            data = emptyData(),
            selectedSource = null,
            onSelectSource = {},
            range = UsageRange.Last7d,
        )
    }
}

@Preview(widthDp = 1088, heightDp = 600)
@Composable
private fun PreviewUsageScreenScanning() {
    // Initial-load state: ingest hasn't finished its first pass, so the data
    // table is empty *and* loading=true. Distinct from the post-scan empty
    // state above, which says "no usage yet".
    PreviewScaffold {
        UsageScreen(
            data = emptyData(),
            selectedSource = null,
            onSelectSource = {},
            range = UsageRange.Last7d,
            loading = true,
        )
    }
}

@Preview(widthDp = 1088, heightDp = 1100)
@Composable
private fun PreviewUsageScreenRefreshing() {
    PreviewScaffold {
        val data = remember { sampleData() }
        UsageScreen(
            data = data,
            selectedSource = Source.CLAUDE_CODE,
            onSelectSource = {},
            range = UsageRange.Last7d,
            refreshing = true,
        )
    }
}

@Preview(widthDp = 1088, heightDp = 600)
@Composable
private fun PreviewUsageScreenScanningLight() {
    PreviewScaffold(themeMode = com.mikepenz.agentbelay.model.ThemeMode.LIGHT) {
        UsageScreen(
            data = emptyData(),
            selectedSource = null,
            onSelectSource = {},
            range = UsageRange.Last7d,
            loading = true,
        )
    }
}

@Preview(widthDp = 1088, heightDp = 1100)
@Composable
private fun PreviewUsageScreenLight() {
    PreviewScaffold(themeMode = com.mikepenz.agentbelay.model.ThemeMode.LIGHT) {
        val data = remember { sampleData() }
        UsageScreen(
            data = data,
            selectedSource = Source.CLAUDE_CODE,
            onSelectSource = {},
            range = UsageRange.Last7d,
        )
    }
}

@Preview(widthDp = 480, heightDp = 200)
@Composable
private fun PreviewSparkline() {
    PreviewScaffold {
        Column(modifier = Modifier.padding(20.dp)) {
            Sparkline(
                data = listOf(8, 12, 14, 18, 16, 22, 20, 18, 24, 28, 26, 30),
                color = SourceClaudeColor,
                modifier = Modifier.fillMaxWidth().height(60.dp),
            )
        }
    }
}
