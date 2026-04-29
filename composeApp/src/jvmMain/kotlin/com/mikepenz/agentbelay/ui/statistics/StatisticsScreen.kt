package com.mikepenz.agentbelay.ui.statistics

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mikepenz.agentbelay.model.ToolType
import com.mikepenz.agentbelay.ui.components.AgentBelayCard
import com.mikepenz.agentbelay.ui.components.CardSectionHeader
import com.mikepenz.agentbelay.ui.components.HorizontalHairline
import com.mikepenz.agentbelay.ui.components.LinearMeter
import com.mikepenz.agentbelay.ui.components.StackedMeter
import com.mikepenz.agentbelay.ui.components.LocalPreviewHoverOverride
import com.mikepenz.agentbelay.ui.components.PillSegmented
import com.mikepenz.agentbelay.ui.components.TagSize
import com.mikepenz.agentbelay.ui.components.ToolTag
import com.mikepenz.agentbelay.ui.icons.LucideArrowDown
import com.mikepenz.agentbelay.ui.icons.LucideArrowUp
import com.mikepenz.agentbelay.ui.icons.LucideMinus
import com.mikepenz.agentbelay.ui.theme.AccentEmerald
import com.mikepenz.agentbelay.ui.theme.DangerRed
import com.mikepenz.agentbelay.ui.theme.AgentBelayColors
import com.mikepenz.agentbelay.ui.theme.InfoBlue
import com.mikepenz.agentbelay.ui.theme.PreviewScaffold
import com.mikepenz.agentbelay.ui.theme.ToolRead
import com.mikepenz.agentbelay.ui.theme.WarnYellow
import androidx.compose.runtime.CompositionLocalProvider

// ── Projections ──────────────────────────────────────────────────────────────

enum class StatsRange(val key: String, val label: String) {
    Last7d("7d", "7 days"),
    Last30d("30d", "30 days"),
    AllTime("all", "All time"),
}

enum class Trend { Up, Down, Flat }

data class Kpi(
    val label: String,
    val value: String,
    /** Formatted delta string (e.g. "+12", "−3%"); null = hide the delta indicator. */
    val delta: String?,
    val trend: Trend,
    val hint: String,
    val accent: Color? = null,
)

data class DailyStat(
    val day: String,
    val auto: Int,
    val deny: Int,
    val protect: Int,
    val ext: Int,
) {
    val total: Int get() = auto + deny + protect + ext
}

data class BreakdownItem(
    val label: String,
    val value: String,
    val pct: Int,
    val color: Color,
)

data class ToolStat(
    val tool: String,
    val count: Int,
    val auto: Int,
    val deny: Int,
    val protect: Int,
)

data class StatsScreenData(
    val kpis: List<Kpi>,
    val daily: List<DailyStat>,
    val tools: List<ToolStat>,
    val approvalBreakdown: List<BreakdownItem>,
    val approvalTotal: Int,
    val denialBreakdown: List<BreakdownItem>,
    val denialTotal: Int,
    val responseBreakdown: List<BreakdownItem>,
) {
    val isEmpty: Boolean
        get() = kpis.isEmpty() && daily.all { it.total == 0 } && tools.isEmpty()
}

// ── Screen ───────────────────────────────────────────────────────────────────

@Composable
fun StatisticsScreen(
    data: StatsScreenData,
    modifier: Modifier = Modifier,
    range: StatsRange = StatsRange.Last7d,
    onRangeChange: (StatsRange) -> Unit = {},
) {
    BoxWithConstraints(modifier = modifier.fillMaxSize().background(AgentBelayColors.background)) {
        val isCompact = maxWidth < 800.dp
        val stackHeader = maxWidth < 580.dp
        Column(modifier = Modifier.fillMaxSize()) {
            StatsHeader(range = range, onRangeChange = onRangeChange, stackPill = stackHeader)
            if (data.isEmpty) {
                Box(
                    modifier = Modifier.fillMaxWidth().weight(1f),
                    contentAlignment = Alignment.Center,
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            text = "No decisions yet",
                            color = AgentBelayColors.inkSecondary,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Medium,
                        )
                        Spacer(Modifier.height(6.dp))
                        Text(
                            text = "Stats will appear here once requests start flowing through.",
                            color = AgentBelayColors.inkMuted,
                            fontSize = 12.sp,
                            textAlign = TextAlign.Center,
                        )
                    }
                }
            } else {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                        .verticalScroll(rememberScrollState())
                        .padding(start = 28.dp, end = 28.dp, top = 22.dp, bottom = 28.dp)
                        .animateContentSize(),
                    verticalArrangement = Arrangement.spacedBy(14.dp),
                ) {
                    KpiGrid(kpis = data.kpis, columns = if (isCompact) 3 else 6)
                    if (isCompact) {
                        DecisionsPerDayCard(
                            daily = data.daily,
                            modifier = Modifier.fillMaxWidth(),
                        )
                        BreakdownCard(
                            approvalTotal = data.approvalTotal,
                            approvals = data.approvalBreakdown,
                            denialTotal = data.denialTotal,
                            denials = data.denialBreakdown,
                            response = data.responseBreakdown,
                            modifier = Modifier.fillMaxWidth(),
                        )
                    } else {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(14.dp),
                        ) {
                            DecisionsPerDayCard(
                                daily = data.daily,
                                modifier = Modifier.weight(1.6f),
                            )
                            BreakdownCard(
                                approvalTotal = data.approvalTotal,
                                approvals = data.approvalBreakdown,
                                denialTotal = data.denialTotal,
                                denials = data.denialBreakdown,
                                response = data.responseBreakdown,
                                modifier = Modifier.weight(1f),
                            )
                        }
                    }
                    MostRequestedToolsCard(tools = data.tools)
                }
            }
        }
    }
}

@Composable
private fun StatsHeader(
    range: StatsRange,
    onRangeChange: (StatsRange) -> Unit,
    stackPill: Boolean = false,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 28.dp, end = 28.dp, top = 18.dp)
            .animateContentSize(),
    ) {
        val titleBlock: @Composable () -> Unit = {
            Column {
                Text(
                    text = "Stats",
                    color = AgentBelayColors.inkPrimary,
                    fontSize = 20.sp,
                    fontWeight = FontWeight.SemiBold,
                    letterSpacing = (-0.4).sp,
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    text = "Decision patterns, response times and policy outcomes",
                    color = AgentBelayColors.inkTertiary,
                    fontSize = 12.sp,
                )
            }
        }
        val pill: @Composable () -> Unit = {
            PillSegmented(
                options = StatsRange.entries.map { it to it.label },
                selected = range,
                onSelect = onRangeChange,
            )
        }
        if (stackPill) {
            titleBlock()
            Spacer(Modifier.height(10.dp))
            pill()
        } else {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth(),
            ) {
                titleBlock()
                pill()
            }
        }
        Spacer(Modifier.height(14.dp))
        HorizontalHairline()
    }
}

// ── KPI grid ─────────────────────────────────────────────────────────────────

@Composable
private fun KpiGrid(kpis: List<Kpi>, columns: Int = 6) {
    Column(
        modifier = Modifier.fillMaxWidth().animateContentSize(),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        kpis.chunked(columns).forEach { rowKpis ->
            Row(
                modifier = Modifier.fillMaxWidth().height(IntrinsicSize.Max),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                rowKpis.forEach { kpi ->
                    KpiCard(
                        kpi = kpi,
                        modifier = Modifier.weight(1f).fillMaxHeight(),
                    )
                }
                // Pad a short trailing row so its items retain the same cell
                // width as the other rows instead of expanding to fill.
                val missing = columns - rowKpis.size
                if (missing > 0) {
                    Spacer(modifier = Modifier.weight(missing.toFloat()))
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun KpiCard(kpi: Kpi, modifier: Modifier = Modifier) {
    val trendColor = when (kpi.trend) {
        Trend.Up -> AccentEmerald
        Trend.Down -> DangerRed
        Trend.Flat -> AgentBelayColors.inkMuted
    }
    val trendIcon = when (kpi.trend) {
        Trend.Up -> LucideArrowUp
        Trend.Down -> LucideArrowDown
        Trend.Flat -> LucideMinus
    }
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
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Spacer(Modifier.height(10.dp))
            // FlowRow + softWrap=false so that when the card is too narrow to fit
            // "value + delta" side-by-side, the delta chip wraps to a new line as a
            // whole rather than breaking the delta text character-by-character.
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
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
                if (kpi.delta != null) {
                    Row(
                        modifier = Modifier.padding(bottom = 3.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(2.dp),
                    ) {
                        Icon(
                            imageVector = trendIcon,
                            contentDescription = null,
                            tint = trendColor,
                            modifier = Modifier.size(10.dp),
                        )
                        Text(
                            text = kpi.delta,
                            color = trendColor,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Medium,
                            maxLines = 1,
                            softWrap = false,
                            overflow = TextOverflow.Clip,
                        )
                    }
                }
            }
            Spacer(Modifier.height(4.dp))
            Text(
                text = kpi.hint,
                color = AgentBelayColors.inkMuted,
                fontSize = 11.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

// ── Decisions per day ────────────────────────────────────────────────────────

@Composable
private fun DecisionsPerDayCard(
    daily: List<DailyStat>,
    modifier: Modifier = Modifier,
) {
    AgentBelayCard(modifier = modifier) {
        Column {
            CardSectionHeader(
                title = "Decisions per day",
                subtitle = "Stacked by outcome",
                trailing = {
                    Legend(
                        items = listOf(
                            "Auto approve" to AccentEmerald,
                            "Deny" to DangerRed,
                            "Protection" to InfoBlue,
                            "External" to WarnYellow,
                        ),
                    )
                },
            )
            StackedDayChart(
                daily = daily,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(240.dp)
                    .padding(start = 20.dp, end = 20.dp, top = 24.dp, bottom = 16.dp),
            )
        }
    }
}

@Composable
private fun StackedDayChart(
    daily: List<DailyStat>,
    modifier: Modifier = Modifier,
    maxY: Int = 5,
) {
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        // Y axis labels column — share label row height with the chart's label row
        Column(modifier = Modifier.fillMaxHeight()) {
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.SpaceBetween,
            ) {
                for (n in maxY downTo 0) {
                    Text(
                        text = "$n",
                        color = AgentBelayColors.inkMuted,
                        fontSize = 10.5.sp,
                    )
                }
            }
            Spacer(Modifier.height(8.dp))
            // Reserve same height as x-axis day label
            Text(
                text = " ",
                color = AgentBelayColors.inkMuted,
                fontSize = 10.5.sp,
                fontFamily = FontFamily.Monospace,
            )
        }

        // Hoisted into the @Composable scope so the non-composable drawBehind
        // block can read a theme-aware color.
        val gridlineColor = AgentBelayColors.line1
        // Chart area
        Column(modifier = Modifier.weight(1f).fillMaxHeight()) {
            Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
                // Dashed gridlines in back — 5 lines at 0%, 20%, 40%, 60%, 80%
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .drawBehind {
                            val dash = PathEffect.dashPathEffect(floatArrayOf(4f, 4f), 0f)
                            val color = gridlineColor
                            for (i in 0 until maxY) {
                                val y = size.height * (i.toFloat() / maxY.toFloat())
                                drawLine(
                                    color = color,
                                    start = androidx.compose.ui.geometry.Offset(0f, y),
                                    end = androidx.compose.ui.geometry.Offset(size.width, y),
                                    strokeWidth = 1f,
                                    pathEffect = dash,
                                    cap = StrokeCap.Butt,
                                )
                            }
                        },
                )
                // Bars in front
                Row(
                    modifier = Modifier.fillMaxSize(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    daily.forEach { day ->
                        DayBar(
                            day = day,
                            maxY = maxY,
                            modifier = Modifier.weight(1f).fillMaxHeight(),
                        )
                    }
                }
                // Empty-state overlay when there is no data in the selected range.
                val isEmpty = daily.all { it.auto + it.deny + it.protect + it.ext == 0 }
                if (isEmpty) {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            text = "No decisions in this range",
                            color = AgentBelayColors.inkTertiary,
                            fontSize = 12.sp,
                        )
                    }
                }
            }
            Spacer(Modifier.height(8.dp))
            // Stride labels so they never crowd: every 1 for ≤7, every 5 for ≤30, every 7 otherwise.
            val labelStride = when {
                daily.size <= 7 -> 1
                daily.size <= 14 -> 2
                daily.size <= 31 -> 5
                else -> 7
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                daily.forEachIndexed { index, day ->
                    Box(
                        modifier = Modifier.weight(1f),
                        contentAlignment = Alignment.Center,
                    ) {
                        if (index % labelStride == 0) {
                            // Show only the day part ("MM-DD" → "DD") to keep labels compact.
                            Text(
                                text = day.day.substringAfterLast('-'),
                                color = AgentBelayColors.inkMuted,
                                fontSize = 10.5.sp,
                                fontFamily = FontFamily.Monospace,
                                maxLines = 1,
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun DayBar(
    day: DailyStat,
    maxY: Int,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier) {
        val fillFraction = if (maxY == 0) 0f else (day.total.toFloat() / maxY.toFloat()).coerceIn(0f, 1f)
        val emptyFraction = 1f - fillFraction
        if (emptyFraction > 0f) {
            Spacer(modifier = Modifier.fillMaxWidth().weight(emptyFraction))
        }
        if (fillFraction > 0f && day.total > 0) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(fillFraction)
                    .clip(RoundedCornerShape(4.dp)),
                verticalArrangement = Arrangement.spacedBy(1.dp),
            ) {
                val total = day.total.toFloat()
                if (day.ext > 0) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(day.ext / total)
                            .background(WarnYellow),
                    )
                }
                if (day.protect > 0) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(day.protect / total)
                            .background(InfoBlue),
                    )
                }
                if (day.deny > 0) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(day.deny / total)
                            .background(DangerRed),
                    )
                }
                if (day.auto > 0) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(day.auto / total)
                            .background(AccentEmerald),
                    )
                }
            }
        }
    }
}

// ── Breakdown card ───────────────────────────────────────────────────────────

@Composable
private fun BreakdownCard(
    approvalTotal: Int,
    approvals: List<BreakdownItem>,
    denialTotal: Int,
    denials: List<BreakdownItem>,
    response: List<BreakdownItem>,
    modifier: Modifier = Modifier,
) {
    AgentBelayCard(modifier = modifier) {
        Column {
            CardSectionHeader(
                title = "Breakdown",
                subtitle = "By source and timing",
            )
            Column(
                modifier = Modifier.fillMaxWidth().padding(18.dp),
                verticalArrangement = Arrangement.spacedBy(18.dp),
            ) {
                BreakdownGroup(
                    title = "Approvals",
                    total = approvalTotal,
                    items = approvals,
                    showPct = true,
                )
                BreakdownGroup(
                    title = "Denials",
                    total = denialTotal,
                    items = denials,
                    showPct = true,
                )
                BreakdownGroup(
                    title = "Response time",
                    total = null,
                    items = response,
                    showPct = false,
                )
            }
        }
    }
}

@Composable
private fun BreakdownGroup(
    title: String,
    total: Int?,
    items: List<BreakdownItem>,
    showPct: Boolean,
) {
    Column {
        Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
            Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(
                    text = title,
                    color = AgentBelayColors.inkPrimary,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold,
                    letterSpacing = (-0.05).sp,
                )
                if (total != null) {
                    Text(
                        text = "· $total",
                        color = AgentBelayColors.inkMuted,
                        fontSize = 12.sp,
                    )
                }
            }
        }
        Spacer(Modifier.height(10.dp))
        Column(verticalArrangement = Arrangement.spacedBy(9.dp)) {
            items.forEach { item ->
                BreakdownRow(item = item, showPct = showPct)
            }
        }
    }
}

@Composable
private fun BreakdownRow(item: BreakdownItem, showPct: Boolean) {
    Column {
        Row(
            horizontalArrangement = Arrangement.SpaceBetween,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(
                text = item.label,
                color = AgentBelayColors.inkSecondary,
                fontSize = 11.5.sp,
            )
            Text(
                text = if (showPct) "${item.value} · ${item.pct}%" else item.value,
                color = AgentBelayColors.inkTertiary,
                fontSize = 11.5.sp,
            )
        }
        Spacer(Modifier.height(5.dp))
        LinearMeter(progress = item.pct / 100f, color = item.color)
    }
}

// ── Most-requested tools ─────────────────────────────────────────────────────

@Composable
private fun MostRequestedToolsCard(tools: List<ToolStat>) {
    AgentBelayCard(modifier = Modifier.fillMaxWidth()) {
        Column {
            CardSectionHeader(
                title = "Most-requested tools",
                subtitle = "Ordered by total calls",
                trailing = {
                    Legend(
                        items = listOf(
                            "Approved" to AccentEmerald,
                            "Denied" to DangerRed,
                            "Protection" to InfoBlue,
                        ),
                    )
                },
            )
            val maxCount = tools.maxOfOrNull { it.count } ?: 1
            tools.forEachIndexed { idx, tool ->
                ToolStatRow(tool = tool, maxCount = maxCount)
                if (idx < tools.lastIndex) {
                    HorizontalHairline()
                }
            }
        }
    }
}

@Composable
private fun ToolStatRow(tool: ToolStat, maxCount: Int) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 18.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(18.dp),
    ) {
        Box(modifier = Modifier.width(170.dp)) {
            ToolTag(
                toolName = tool.tool,
                toolType = if (tool.tool == "AskUserQuestion") ToolType.ASK_USER_QUESTION else ToolType.DEFAULT,
                size = TagSize.SMALL,
            )
        }
        StackBar(tool = tool, maxCount = maxCount, modifier = Modifier.weight(1f))
        Row(
            modifier = Modifier.width(100.dp),
            horizontalArrangement = Arrangement.End,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = "${tool.count}",
                color = AgentBelayColors.inkPrimary,
                fontSize = 12.sp,
                fontWeight = FontWeight.Medium,
            )
            Spacer(Modifier.width(4.dp))
            Text(
                text = "calls",
                color = AgentBelayColors.inkMuted,
                fontSize = 12.sp,
            )
        }
    }
}

@Composable
private fun StackBar(tool: ToolStat, maxCount: Int, modifier: Modifier = Modifier) {
    val widthFraction = (tool.count.toFloat() / maxCount.coerceAtLeast(1).toFloat()).coerceIn(0f, 1f)
    val total = tool.count.toFloat().coerceAtLeast(1f)
    val unaccounted = (tool.count - tool.auto - tool.deny - tool.protect).coerceAtLeast(0)
    StackedMeter(
        modifier = modifier,
        maxFraction = widthFraction,
        segments = listOf(
            (tool.auto / total) to AccentEmerald,
            (tool.deny / total) to DangerRed,
            (tool.protect / total) to InfoBlue,
            (unaccounted / total) to Color.Transparent,
        ),
    )
}

// ── Shared ───────────────────────────────────────────────────────────────────

@Composable
private fun Legend(items: List<Pair<String, Color>>) {
    Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
        items.forEach { (label, color) ->
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                Box(
                    modifier = Modifier
                        .size(7.dp)
                        .clip(RoundedCornerShape(2.dp))
                        .background(color),
                )
                Text(
                    text = label,
                    color = AgentBelayColors.inkTertiary,
                    fontSize = 10.5.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

// ── Previews ─────────────────────────────────────────────────────────────────

private fun sampleStats(): StatsScreenData = StatsScreenData(
    kpis = listOf(
        Kpi("Total decisions", "48", "+12", Trend.Up, "vs prior 7d"),
        Kpi("Auto-approve rate", "44%", "+6", Trend.Up, "21 of 48", accent = AccentEmerald),
        Kpi("Manual deny", "25%", "−3", Trend.Down, "12 of 48", accent = DangerRed),
        Kpi("Protection hits", "9", "+2", Trend.Up, "19% of total", accent = InfoBlue),
        Kpi("External resolve", "6", "0", Trend.Flat, "12% of total", accent = WarnYellow),
        Kpi("Median response", "4.2s", "−1.1s", Trend.Up, "p50 decision"),
    ),
    daily = listOf(
        DailyStat("04-11", auto = 1, deny = 2, protect = 0, ext = 0),
        DailyStat("04-12", auto = 2, deny = 0, protect = 1, ext = 0),
        DailyStat("04-13", auto = 0, deny = 1, protect = 0, ext = 0),
        DailyStat("04-14", auto = 1, deny = 1, protect = 0, ext = 0),
        DailyStat("04-15", auto = 2, deny = 1, protect = 0, ext = 1),
        DailyStat("04-16", auto = 1, deny = 1, protect = 0, ext = 1),
        DailyStat("04-17", auto = 2, deny = 1, protect = 1, ext = 1),
    ),
    tools = listOf(
        ToolStat("Bash", 18, auto = 11, deny = 5, protect = 2),
        ToolStat("WebFetch", 14, auto = 7, deny = 6, protect = 1),
        ToolStat("Write", 9, auto = 4, deny = 2, protect = 3),
        ToolStat("AskUserQuestion", 5, auto = 0, deny = 3, protect = 0),
        ToolStat("Edit", 2, auto = 2, deny = 0, protect = 0),
    ),
    approvalBreakdown = listOf(
        BreakdownItem("Auto approve", "18", 86, AccentEmerald),
        BreakdownItem("Manual allow", "3", 14, ToolRead),
    ),
    approvalTotal = 21,
    denialBreakdown = listOf(
        BreakdownItem("Manual deny", "9", 75, DangerRed),
        BreakdownItem("Protection", "3", 25, InfoBlue),
    ),
    denialTotal = 12,
    responseBreakdown = listOf(
        BreakdownItem("p50", "4.2s", 30, AccentEmerald),
        BreakdownItem("p90", "18s", 68, WarnYellow),
        BreakdownItem("p99", "112s", 95, DangerRed),
    ),
)

private fun emptyStats(): StatsScreenData = StatsScreenData(
    kpis = emptyList(),
    daily = emptyList(),
    tools = emptyList(),
    approvalBreakdown = emptyList(),
    approvalTotal = 0,
    denialBreakdown = emptyList(),
    denialTotal = 0,
    responseBreakdown = emptyList(),
)

private fun highVolumeStats(): StatsScreenData = StatsScreenData(
    kpis = listOf(
        Kpi("Total decisions", "312", "+88", Trend.Up, "vs prior 30d"),
        Kpi("Auto-approve rate", "62%", "+11", Trend.Up, "193 of 312", accent = AccentEmerald),
        Kpi("Manual deny", "14%", "−5", Trend.Down, "44 of 312", accent = DangerRed),
        Kpi("Protection hits", "38", "+12", Trend.Up, "12% of total", accent = InfoBlue),
        Kpi("External resolve", "21", "+3", Trend.Up, "7% of total", accent = WarnYellow),
        Kpi("Median response", "2.1s", "−0.6s", Trend.Up, "p50 decision"),
    ),
    daily = listOf(
        DailyStat("03-18", auto = 9, deny = 3, protect = 1, ext = 0),
        DailyStat("03-19", auto = 11, deny = 2, protect = 2, ext = 1),
        DailyStat("03-20", auto = 7, deny = 4, protect = 1, ext = 0),
        DailyStat("03-21", auto = 14, deny = 3, protect = 1, ext = 2),
        DailyStat("03-22", auto = 12, deny = 5, protect = 3, ext = 1),
        DailyStat("03-23", auto = 10, deny = 2, protect = 2, ext = 0),
        DailyStat("03-24", auto = 15, deny = 4, protect = 2, ext = 1),
    ),
    tools = listOf(
        ToolStat("Bash", 92, auto = 64, deny = 22, protect = 6),
        ToolStat("WebFetch", 64, auto = 38, deny = 19, protect = 7),
        ToolStat("Write", 51, auto = 32, deny = 14, protect = 5),
        ToolStat("Edit", 40, auto = 36, deny = 3, protect = 1),
        ToolStat("AskUserQuestion", 18, auto = 0, deny = 14, protect = 0),
    ),
    approvalBreakdown = listOf(
        BreakdownItem("Auto approve", "162", 84, AccentEmerald),
        BreakdownItem("Manual allow", "31", 16, ToolRead),
    ),
    approvalTotal = 193,
    denialBreakdown = listOf(
        BreakdownItem("Manual deny", "44", 54, DangerRed),
        BreakdownItem("Protection", "38", 46, InfoBlue),
    ),
    denialTotal = 82,
    responseBreakdown = listOf(
        BreakdownItem("p50", "2.1s", 22, AccentEmerald),
        BreakdownItem("p90", "9s", 54, WarnYellow),
        BreakdownItem("p99", "48s", 88, DangerRed),
    ),
)

@Preview(widthDp = 1088, heightDp = 1200)
@Composable
private fun PreviewStatisticsScreen() {
    PreviewScaffold {
        StatisticsScreen(data = sampleStats())
    }
}

@Preview(widthDp = 720, heightDp = 1600)
@Composable
private fun PreviewStatisticsScreenCompact() {
    // Regression: under 800.dp the screen used to require horizontal scroll.
    // Now the KPI grid wraps to 2×3 and the chart/breakdown/tools stack
    // vertically instead.
    PreviewScaffold {
        StatisticsScreen(data = sampleStats())
    }
}

@Preview(widthDp = 510, heightDp = 1800)
@Composable
private fun PreviewStatisticsScreenSlim() {
    // Just above the 500.dp slim-app cutoff. Header stacks its range pill
    // below the title and KPI cards in each row are equal-height.
    PreviewScaffold {
        StatisticsScreen(data = sampleStats())
    }
}

@Preview(widthDp = 1088, heightDp = 1200)
@Composable
private fun PreviewStatisticsScreenHighVolume() {
    PreviewScaffold {
        StatisticsScreen(data = highVolumeStats())
    }
}

@Preview(widthDp = 1088, heightDp = 520)
@Composable
private fun PreviewStatisticsScreenEmpty() {
    PreviewScaffold {
        StatisticsScreen(data = emptyStats())
    }
}

@Preview(widthDp = 360, heightDp = 80)
@Composable
private fun PreviewRangePills7d() {
    PreviewScaffold {
        Column(modifier = Modifier.padding(14.dp)) {
            PillSegmented(
                options = StatsRange.entries.map { it to it.label },
                selected = StatsRange.Last7d,
                onSelect = {},
            )
        }
    }
}

@Preview(widthDp = 360, heightDp = 80)
@Composable
private fun PreviewRangePills30d() {
    PreviewScaffold {
        Column(modifier = Modifier.padding(14.dp)) {
            PillSegmented(
                options = StatsRange.entries.map { it to it.label },
                selected = StatsRange.Last30d,
                onSelect = {},
            )
        }
    }
}

@Preview(widthDp = 360, heightDp = 80)
@Composable
private fun PreviewRangePillsAllTime() {
    PreviewScaffold {
        Column(modifier = Modifier.padding(14.dp)) {
            PillSegmented(
                options = StatsRange.entries.map { it to it.label },
                selected = StatsRange.AllTime,
                onSelect = {},
            )
        }
    }
}

@Preview(widthDp = 360, heightDp = 80)
@Composable
private fun PreviewRangePillsHover() {
    PreviewScaffold {
        CompositionLocalProvider(LocalPreviewHoverOverride provides true) {
            Column(modifier = Modifier.padding(14.dp)) {
                PillSegmented(
                    options = StatsRange.entries.map { it to it.label },
                    selected = StatsRange.Last7d,
                    onSelect = {},
                )
            }
        }
    }
}

@Preview(widthDp = 1088, heightDp = 280)
@Composable
private fun PreviewKpiGrid() {
    PreviewScaffold {
        Column(modifier = Modifier.padding(28.dp)) {
            KpiGrid(kpis = sampleStats().kpis)
        }
    }
}

@Preview(widthDp = 1088, heightDp = 280)
@Composable
private fun PreviewKpiGridTrends() {
    PreviewScaffold {
        Column(modifier = Modifier.padding(28.dp)) {
            KpiGrid(
                kpis = listOf(
                    Kpi("Trend up", "48", "+12", Trend.Up, "vs prior 7d"),
                    Kpi("Trend down", "12", "−5", Trend.Down, "vs prior 7d"),
                    Kpi("Trend flat", "24", "0", Trend.Flat, "vs prior 7d"),
                    Kpi("Accent ok", "44%", "+6", Trend.Up, "21 of 48", accent = AccentEmerald),
                    Kpi("Accent danger", "25%", "−3", Trend.Down, "12 of 48", accent = DangerRed),
                    Kpi("Accent warn", "6", "0", Trend.Flat, "12% of total", accent = WarnYellow),
                ),
            )
        }
    }
}

@Preview(widthDp = 560, heightDp = 240)
@Composable
private fun PreviewKpiGridNarrow() {
    // Regression: at narrow card widths the delta chip used to be squeezed
    // until the delta text wrapped one character per line. Now FlowRow drops
    // the delta chip to a second row and the value + delta stay readable.
    // Fixed-width Box reproduces the narrow-window condition regardless of
    // the preview renderer's viewport width.
    PreviewScaffold {
        Box(modifier = Modifier.padding(28.dp).requiredWidth(500.dp)) {
            KpiGrid(
                kpis = listOf(
                    Kpi("Total decisions", "19", "−125", Trend.Down, "last 7 days"),
                    Kpi("Auto-approve rate", "26%", "−5pp", Trend.Down, "5 of 19", accent = AccentEmerald),
                    Kpi("Manual deny", "11%", "+5pp", Trend.Up, "2 of 19", accent = DangerRed),
                    Kpi("Protection hits", "1", "+1", Trend.Up, "5% of total", accent = InfoBlue),
                ),
            )
        }
    }
}

@Preview(widthDp = 680, heightDp = 320)
@Composable
private fun PreviewDailyChart() {
    PreviewScaffold {
        Column(modifier = Modifier.padding(28.dp)) {
            DecisionsPerDayCard(daily = sampleStats().daily, modifier = Modifier.fillMaxWidth())
        }
    }
}

@Preview(widthDp = 680, heightDp = 320)
@Composable
private fun PreviewDailyChartEmpty() {
    PreviewScaffold {
        Column(modifier = Modifier.padding(28.dp)) {
            DecisionsPerDayCard(
                daily = List(7) { DailyStat("04-${11 + it}", 0, 0, 0, 0) },
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

@Preview(widthDp = 420, heightDp = 420)
@Composable
private fun PreviewBreakdown() {
    val d = sampleStats()
    PreviewScaffold {
        Column(modifier = Modifier.padding(28.dp)) {
            BreakdownCard(
                approvalTotal = d.approvalTotal,
                approvals = d.approvalBreakdown,
                denialTotal = d.denialTotal,
                denials = d.denialBreakdown,
                response = d.responseBreakdown,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

@Preview(widthDp = 1088, heightDp = 420)
@Composable
private fun PreviewMostRequestedTools() {
    PreviewScaffold {
        Column(modifier = Modifier.padding(28.dp)) {
            MostRequestedToolsCard(tools = sampleStats().tools)
        }
    }
}

// ── Light theme & state coverage (iter 3) ──────────────────────────────────

@Preview(widthDp = 1088, heightDp = 1200)
@Composable
private fun PreviewStatisticsScreenLight() {
    PreviewScaffold(themeMode = com.mikepenz.agentbelay.model.ThemeMode.LIGHT) {
        StatisticsScreen(data = sampleStats())
    }
}

@Preview(widthDp = 1088, heightDp = 860)
@Composable
private fun PreviewStatisticsLoading() {
    PreviewScaffold {
        com.mikepenz.agentbelay.ui.components.ScreenLoadingState(label = "Computing statistics…")
    }
}

@Preview(widthDp = 1088, heightDp = 860)
@Composable
private fun PreviewStatisticsError() {
    PreviewScaffold {
        com.mikepenz.agentbelay.ui.components.ScreenErrorState(
            title = "Statistics unavailable",
            message = "Could not compute totals over the selected range.",
        )
    }
}
