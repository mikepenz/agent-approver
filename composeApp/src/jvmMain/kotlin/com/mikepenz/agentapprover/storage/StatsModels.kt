package com.mikepenz.agentapprover.storage

import com.mikepenz.agentapprover.model.DecisionGroup
import kotlinx.datetime.LocalDate

/**
 * Aggregated statistics over the `history` table for the Statistics tab.
 * Computed by [DatabaseStorage.queryStats]; pure data, safe to pass through state.
 */
data class StatsSummary(
    val totalDecisions: Int,
    val byGroup: Map<DecisionGroup, Int>,
    val perDay: List<DailyCount>,
    val latencyByGroup: Map<DecisionGroup, LatencyStats>,
    val latencyHistogramByGroup: Map<DecisionGroup, List<Int>>,
    val topProtections: List<ProtectionHitCount>,
) {
    companion object {
        val EMPTY = StatsSummary(0, emptyMap(), emptyList(), emptyMap(), emptyMap(), emptyList())

        /**
         * Latency histogram bucket boundaries in seconds (upper-exclusive).
         * The final bucket captures everything `>= 3600`.
         * Labels in [BUCKET_LABELS] are aligned 1:1.
         */
        val BUCKET_UPPER_BOUNDS_SECONDS: List<Double> = listOf(1.0, 5.0, 15.0, 60.0, 300.0, 900.0, 3600.0)
        val BUCKET_LABELS: List<String> = listOf("<1s", "1–5s", "5–15s", "15–60s", "1–5m", "5–15m", "15m–1h", ">1h")

        fun bucketIndex(seconds: Double): Int {
            for ((i, upper) in BUCKET_UPPER_BOUNDS_SECONDS.withIndex()) {
                if (seconds < upper) return i
            }
            return BUCKET_UPPER_BOUNDS_SECONDS.size
        }
    }
}

data class DailyCount(
    val date: LocalDate,
    val byGroup: Map<DecisionGroup, Int>,
)

data class LatencyStats(
    val count: Int,
    val avgSeconds: Double,
    val p50Seconds: Double,
    val p90Seconds: Double,
)

data class ProtectionHitCount(
    val moduleId: String,
    val ruleId: String,
    val count: Int,
)
