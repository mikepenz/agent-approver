package com.mikepenz.agentapprover.ui.statistics

import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.mikepenz.agentapprover.model.DecisionGroup
import com.mikepenz.agentapprover.storage.DailyCount
import com.mikepenz.agentapprover.storage.StatsSummary
import com.patrykandpatrick.vico.compose.cartesian.CartesianChartHost
import com.patrykandpatrick.vico.compose.cartesian.axis.HorizontalAxis
import com.patrykandpatrick.vico.compose.cartesian.axis.VerticalAxis
import com.patrykandpatrick.vico.compose.cartesian.data.CartesianChartModelProducer
import com.patrykandpatrick.vico.compose.cartesian.data.CartesianValueFormatter
import com.patrykandpatrick.vico.compose.cartesian.data.columnSeries
import com.patrykandpatrick.vico.compose.cartesian.layer.ColumnCartesianLayer
import com.patrykandpatrick.vico.compose.cartesian.layer.rememberColumnCartesianLayer
import com.patrykandpatrick.vico.compose.cartesian.rememberCartesianChart
import com.patrykandpatrick.vico.compose.cartesian.rememberVicoScrollState
import com.patrykandpatrick.vico.compose.cartesian.rememberVicoZoomState
import com.patrykandpatrick.vico.compose.common.Fill
import com.patrykandpatrick.vico.compose.common.component.rememberLineComponent
import com.patrykandpatrick.vico.compose.common.data.ExtraStore

/**
 * Order of stack layers in [DailyDecisionsChart]. Kept stable so colors line up
 * with the legend in [StatisticsTab].
 */
internal val DAILY_STACK_ORDER: List<DecisionGroup> = listOf(
    DecisionGroup.MANUAL_APPROVE,
    DecisionGroup.RISK_APPROVE,
    DecisionGroup.MANUAL_DENY,
    DecisionGroup.RISK_DENY,
    DecisionGroup.PROTECTION_BLOCK,
    DecisionGroup.TIMEOUT,
)

/** Stable color mapping shared by chart fills and legend chips. */
@Composable
internal fun decisionGroupColor(group: DecisionGroup): Color = when (group) {
    DecisionGroup.MANUAL_APPROVE -> MaterialTheme.colorScheme.primary
    DecisionGroup.RISK_APPROVE -> MaterialTheme.colorScheme.tertiary
    DecisionGroup.MANUAL_DENY -> MaterialTheme.colorScheme.error
    DecisionGroup.RISK_DENY -> MaterialTheme.colorScheme.errorContainer
    DecisionGroup.PROTECTION_BLOCK -> MaterialTheme.colorScheme.secondary
    DecisionGroup.PROTECTION_LOG -> MaterialTheme.colorScheme.outline
    DecisionGroup.TIMEOUT -> MaterialTheme.colorScheme.onSurfaceVariant
    DecisionGroup.OTHER -> MaterialTheme.colorScheme.surfaceVariant
}

internal fun decisionGroupLabel(group: DecisionGroup): String = when (group) {
    DecisionGroup.MANUAL_APPROVE -> "Manual approve"
    DecisionGroup.RISK_APPROVE -> "Auto approve"
    DecisionGroup.MANUAL_DENY -> "Manual deny"
    DecisionGroup.RISK_DENY -> "Auto deny"
    DecisionGroup.PROTECTION_BLOCK -> "Protection"
    DecisionGroup.PROTECTION_LOG -> "Protection log"
    DecisionGroup.TIMEOUT -> "Timeout"
    DecisionGroup.OTHER -> "Other"
}

private val DailyLabelKey = ExtraStore.Key<List<String>>()
private val HistogramLabelKey = ExtraStore.Key<List<String>>()

/**
 * Stacked column chart showing per-day decision counts. Each stack contains one
 * segment per [DecisionGroup] in [DAILY_STACK_ORDER]; absent groups contribute 0.
 */
@Composable
fun DailyDecisionsChart(daily: List<DailyCount>, modifier: Modifier = Modifier) {
    val modelProducer = remember { CartesianChartModelProducer() }
    val colors = DAILY_STACK_ORDER.map { decisionGroupColor(it) }

    LaunchedEffect(daily) {
        // Always run a transaction so a non-empty → empty transition clears the
        // chart. Skipping the transaction would leave stale series rendered.
        modelProducer.runTransaction {
            columnSeries {
                if (daily.isNotEmpty()) {
                    DAILY_STACK_ORDER.forEach { group ->
                        series(daily.map { it.byGroup[group] ?: 0 })
                    }
                }
            }
            extras {
                it[DailyLabelKey] = if (daily.isNotEmpty()) {
                    daily.map { d -> d.date.toString().substring(5) }
                } else {
                    emptyList()
                }
            }
        }
    }

    CartesianChartHost(
        chart = rememberCartesianChart(
            rememberColumnCartesianLayer(
                columnProvider = ColumnCartesianLayer.ColumnProvider.series(
                    colors.map { color -> rememberLineComponent(fill = Fill(color), thickness = 12.dp) }
                ),
                mergeMode = { ColumnCartesianLayer.MergeMode.Stacked },
            ),
            startAxis = VerticalAxis.rememberStart(),
            bottomAxis = HorizontalAxis.rememberBottom(
                valueFormatter = CartesianValueFormatter { context, x, _ ->
                    val labels = context.model.extraStore[DailyLabelKey]
                    labels.getOrNull(x.toInt()) ?: ""
                },
            ),
        ),
        modelProducer = modelProducer,
        modifier = modifier.height(180.dp),
        scrollState = rememberVicoScrollState(scrollEnabled = false),
        zoomState = rememberVicoZoomState(zoomEnabled = false),
    )
}

/** Fixed series order for [LatencyHistogramChart]. Held constant across recompositions
 *  so the chart's `rememberLineComponent` slot count never changes. */
internal val LATENCY_SERIES_ORDER: List<DecisionGroup> = listOf(
    DecisionGroup.MANUAL_APPROVE,
    DecisionGroup.RISK_APPROVE,
    DecisionGroup.MANUAL_DENY,
    DecisionGroup.RISK_DENY,
)

/**
 * Grouped column chart with one series per [DecisionGroup] in [LATENCY_SERIES_ORDER].
 * The series count is fixed (not derived from the data) so Compose's positional
 * `remember` slots stay stable across recompositions; missing groups simply
 * contribute all-zero buckets.
 */
@Composable
fun LatencyHistogramChart(
    histogramByGroup: Map<DecisionGroup, List<Int>>,
    modifier: Modifier = Modifier,
) {
    val modelProducer = remember { CartesianChartModelProducer() }
    val colors = LATENCY_SERIES_ORDER.map { decisionGroupColor(it) }

    LaunchedEffect(histogramByGroup) {
        modelProducer.runTransaction {
            columnSeries {
                LATENCY_SERIES_ORDER.forEach { group ->
                    val buckets = histogramByGroup[group] ?: List(StatsSummary.BUCKET_LABELS.size) { 0 }
                    series(buckets)
                }
            }
            extras { it[HistogramLabelKey] = StatsSummary.BUCKET_LABELS }
        }
    }

    CartesianChartHost(
        chart = rememberCartesianChart(
            rememberColumnCartesianLayer(
                columnProvider = ColumnCartesianLayer.ColumnProvider.series(
                    colors.map { color -> rememberLineComponent(fill = Fill(color), thickness = 8.dp) }
                ),
            ),
            startAxis = VerticalAxis.rememberStart(),
            bottomAxis = HorizontalAxis.rememberBottom(
                valueFormatter = CartesianValueFormatter { context, x, _ ->
                    val labels = context.model.extraStore[HistogramLabelKey]
                    labels.getOrNull(x.toInt()) ?: ""
                },
            ),
        ),
        modelProducer = modelProducer,
        modifier = modifier.height(160.dp),
        scrollState = rememberVicoScrollState(scrollEnabled = false),
        zoomState = rememberVicoZoomState(zoomEnabled = false),
    )
}
