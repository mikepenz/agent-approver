package com.mikepenz.agentbelay.storage

import com.mikepenz.agentbelay.model.Source

/**
 * Per-harness rollup for the Usage tab. Token totals are pre-summed across all
 * records within the queried window; cost is summed at row-write time so this
 * stays cheap even at high cardinality.
 */
data class UsageHarnessTotals(
    val harness: Source,
    val requests: Int,
    val sessions: Int,
    val inputTokens: Long,
    val outputTokens: Long,
    val cacheReadTokens: Long,
    val cacheWriteTokens: Long,
    val reasoningTokens: Long,
    val costUsd: Double,
)

/**
 * One bar of the Activity sparkline. [epochDay] is `ts / 86_400_000` so two
 * records on the same UTC day collapse — the UI treats it as an opaque ordinal.
 */
data class UsageDailyCount(
    val epochDay: Long,
    val requests: Int,
)
