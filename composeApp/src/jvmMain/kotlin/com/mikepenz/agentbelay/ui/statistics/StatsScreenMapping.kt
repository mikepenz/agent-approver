package com.mikepenz.agentbelay.ui.statistics

import com.mikepenz.agentbelay.model.ApprovalResult
import com.mikepenz.agentbelay.model.DecisionGroup
import com.mikepenz.agentbelay.model.group
import com.mikepenz.agentbelay.storage.StatsSummary
import com.mikepenz.agentbelay.ui.theme.AccentEmerald
import com.mikepenz.agentbelay.ui.theme.DangerRed
import com.mikepenz.agentbelay.ui.theme.InfoBlue
import com.mikepenz.agentbelay.ui.theme.ToolRead
import com.mikepenz.agentbelay.ui.theme.WarnYellow
import kotlin.time.Clock
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.minus
import kotlinx.datetime.toLocalDateTime
import kotlin.math.roundToInt

internal fun StatsSummary.toScreenData(
    window: TimeWindow,
    decidedInWindow: List<ApprovalResult>,
    previousSummary: StatsSummary? = null,
): StatsScreenData {
    val autoApproves = byGroup[DecisionGroup.RISK_APPROVE] ?: 0
    val manualApproves = byGroup[DecisionGroup.MANUAL_APPROVE] ?: 0
    val manualDenies = byGroup[DecisionGroup.MANUAL_DENY] ?: 0
    val riskDenies = byGroup[DecisionGroup.RISK_DENY] ?: 0
    val protectionHits = byGroup[DecisionGroup.PROTECTION_BLOCK] ?: 0
    val external = byGroup[DecisionGroup.EXTERNAL] ?: 0
    val totalApprovals = autoApproves + manualApproves
    val totalDenials = manualDenies + riskDenies + protectionHits

    val autoApproveRate = if (totalDecisions > 0) (100.0 * autoApproves / totalDecisions).roundToInt() else 0
    val manualDenyRate = if (totalDecisions > 0) (100.0 * manualDenies / totalDecisions).roundToInt() else 0
    val protectionPct = if (totalDecisions > 0) (100.0 * protectionHits / totalDecisions).roundToInt() else 0
    val externalPct = if (totalDecisions > 0) (100.0 * external / totalDecisions).roundToInt() else 0

    val p50 = latencyByGroup.values.filter { it.count > 0 }
        .takeIf { it.isNotEmpty() }
        ?.let { it.sumOf { s -> s.p50Seconds * s.count } / it.sumOf { s -> s.count } }

    // Previous-period values for delta computation (null when no prior data available).
    val prev = previousSummary
    val prevAutoApproves = prev?.let { (it.byGroup[DecisionGroup.RISK_APPROVE] ?: 0) + (it.byGroup[DecisionGroup.MANUAL_APPROVE] ?: 0) }
    val prevManualDenies = prev?.let { it.byGroup[DecisionGroup.MANUAL_DENY] ?: 0 }
    val prevRiskDenies = prev?.let { it.byGroup[DecisionGroup.RISK_DENY] ?: 0 }
    val prevProtectionHits = prev?.let { it.byGroup[DecisionGroup.PROTECTION_BLOCK] ?: 0 }
    val prevExternal = prev?.let { it.byGroup[DecisionGroup.EXTERNAL] ?: 0 }
    val prevAutoApproveRate = prev?.let { s ->
        if (s.totalDecisions > 0) (100.0 * (prevAutoApproves!!) / s.totalDecisions).roundToInt() else 0
    }
    val prevManualDenyRate = prev?.let { s ->
        if (s.totalDecisions > 0) (100.0 * (prevManualDenies!!) / s.totalDecisions).roundToInt() else 0
    }
    val prevP50 = prev?.latencyByGroup?.values?.filter { it.count > 0 }
        ?.takeIf { it.isNotEmpty() }
        ?.let { it.sumOf { s -> s.p50Seconds * s.count } / it.sumOf { s -> s.count } }

    val (totalDelta, totalTrend) = intDelta(totalDecisions, prev?.totalDecisions)
    val (autoRateDelta, autoRateTrend) = pctDelta(autoApproveRate, prevAutoApproveRate)
    val (denyRateDelta, denyRateTrend) = pctDelta(manualDenyRate, prevManualDenyRate)
    val (protDelta, protTrend) = intDelta(protectionHits, prevProtectionHits)
    val (extDelta, extTrend) = intDelta(external, prevExternal)
    val (latDelta, latTrend) = latencyDelta(p50, prevP50)

    val kpis = listOf(
        Kpi("Total decisions", "$totalDecisions", totalDelta, totalTrend, windowHint(window)),
        Kpi("Auto-approve rate", "$autoApproveRate%", autoRateDelta, autoRateTrend, "$autoApproves of $totalDecisions", accent = AccentEmerald),
        Kpi("Manual deny", "$manualDenyRate%", denyRateDelta, denyRateTrend, "$manualDenies of $totalDecisions", accent = DangerRed),
        Kpi("Protection hits", "$protectionHits", protDelta, protTrend, "$protectionPct% of total", accent = InfoBlue),
        Kpi("External resolve", "$external", extDelta, extTrend, "$externalPct% of total", accent = WarnYellow),
        Kpi("Median response", p50?.let { formatSecondsShort(it) } ?: "—", latDelta, latTrend, "p50 decision"),
    )

    val tz = TimeZone.currentSystemDefault()
    val today = Clock.System.now().toLocalDateTime(tz).date
    val byDate = perDay.associate { it.date to it.byGroup }
    val dailyDates: List<LocalDate> = when (window) {
        TimeWindow.Last7Days -> (6 downTo 0).map { today.minus(it, DateTimeUnit.DAY) }
        TimeWindow.Last30Days -> (29 downTo 0).map { today.minus(it, DateTimeUnit.DAY) }
        TimeWindow.AllTime -> perDay.map { it.date }.ifEmpty { listOf(today) }
    }
    val daily = dailyDates.map { date ->
        val groups = byDate[date].orEmpty()
        DailyStat(
            day = "%02d-%02d".format(date.month.ordinal + 1, date.day),
            auto = (groups[DecisionGroup.RISK_APPROVE] ?: 0) + (groups[DecisionGroup.MANUAL_APPROVE] ?: 0),
            deny = (groups[DecisionGroup.MANUAL_DENY] ?: 0) + (groups[DecisionGroup.RISK_DENY] ?: 0),
            protect = groups[DecisionGroup.PROTECTION_BLOCK] ?: 0,
            ext = groups[DecisionGroup.EXTERNAL] ?: 0,
        )
    }

    val tools = decidedInWindow
        .groupBy { it.request.hookInput.toolName.ifBlank { "Unknown" } }
        .map { (tool, items) ->
            val auto = items.count { it.decision.group().let { g -> g == DecisionGroup.MANUAL_APPROVE || g == DecisionGroup.RISK_APPROVE } }
            val deny = items.count { it.decision.group().let { g -> g == DecisionGroup.MANUAL_DENY || g == DecisionGroup.RISK_DENY } }
            val prot = items.count { it.decision.group() == DecisionGroup.PROTECTION_BLOCK }
            ToolStat(tool = tool, count = items.size, auto = auto, deny = deny, protect = prot)
        }
        .sortedByDescending { it.count }
        .take(5)

    val approvalBreakdown = if (totalApprovals > 0) {
        listOf(
            BreakdownItem("Auto approve", "$autoApproves", (100.0 * autoApproves / totalApprovals).roundToInt(), AccentEmerald),
            BreakdownItem("Manual allow", "$manualApproves", (100.0 * manualApproves / totalApprovals).roundToInt(), ToolRead),
        )
    } else emptyList()

    val denialBreakdown = if (totalDenials > 0) buildList {
        add(BreakdownItem("Manual deny", "$manualDenies", (100.0 * manualDenies / totalDenials).roundToInt(), DangerRed))
        if (riskDenies > 0) {
            add(BreakdownItem("Risk deny", "$riskDenies", (100.0 * riskDenies / totalDenials).roundToInt(), WarnYellow))
        }
        add(BreakdownItem("Protection", "$protectionHits", (100.0 * protectionHits / totalDenials).roundToInt(), InfoBlue))
    } else emptyList()

    val responseBreakdown = run {
        val samples = latencyByGroup.values.filter { it.count > 0 }
        if (samples.isEmpty()) {
            emptyList()
        } else {
            val totalCount = samples.sumOf { it.count }
            val p50s = samples.sumOf { it.p50Seconds * it.count } / totalCount
            val p90s = samples.sumOf { it.p90Seconds * it.count } / totalCount
            val scale = (p90s.coerceAtLeast(p50s) * 1.2).coerceAtLeast(1.0)
            listOf(
                BreakdownItem("p50", formatSecondsShort(p50s), ((p50s / scale) * 100).roundToInt().coerceIn(0, 100), AccentEmerald),
                BreakdownItem("p90", formatSecondsShort(p90s), ((p90s / scale) * 100).roundToInt().coerceIn(0, 100), WarnYellow),
            )
        }
    }

    return StatsScreenData(
        kpis = kpis,
        daily = daily,
        tools = tools,
        approvalBreakdown = approvalBreakdown,
        approvalTotal = totalApprovals,
        denialBreakdown = denialBreakdown,
        denialTotal = totalDenials,
        responseBreakdown = responseBreakdown,
    )
}

/** Null delta = hidden indicator. */
private fun intDelta(current: Int, previous: Int?): Pair<String?, Trend> {
    if (previous == null) return null to Trend.Flat
    val diff = current - previous
    return when {
        diff > 0 -> "+$diff" to Trend.Up
        diff < 0 -> "$diff" to Trend.Down
        else -> null to Trend.Flat
    }
}

private fun pctDelta(currentPct: Int, previousPct: Int?): Pair<String?, Trend> {
    if (previousPct == null) return null to Trend.Flat
    val diff = currentPct - previousPct
    return when {
        diff > 0 -> "+${diff}pp" to Trend.Up
        diff < 0 -> "${diff}pp" to Trend.Down
        else -> null to Trend.Flat
    }
}

/** For latency, lower is better: a decrease in p50 is Trend.Up (improved). */
private fun latencyDelta(current: Double?, previous: Double?): Pair<String?, Trend> {
    if (current == null || previous == null) return null to Trend.Flat
    val diff = current - previous
    if (kotlin.math.abs(diff) < 0.05) return null to Trend.Flat
    val formatted = if (diff > 0) "+${formatSecondsShort(diff)}" else "−${formatSecondsShort(-diff)}"
    val trend = if (diff < 0) Trend.Up else Trend.Down
    return formatted to trend
}

private fun windowHint(window: TimeWindow): String = when (window) {
    TimeWindow.Last7Days -> "last 7 days"
    TimeWindow.Last30Days -> "last 30 days"
    TimeWindow.AllTime -> "all time"
}

private fun formatSecondsShort(seconds: Double): String = when {
    seconds < 1.0 -> "${(seconds * 1000).roundToInt()}ms"
    seconds < 60.0 -> "%.1fs".format(seconds)
    seconds < 3600.0 -> "${(seconds / 60).roundToInt()}m"
    else -> "${(seconds / 3600).roundToInt()}h"
}
