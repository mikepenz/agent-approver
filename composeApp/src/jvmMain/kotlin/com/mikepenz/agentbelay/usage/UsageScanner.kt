package com.mikepenz.agentbelay.usage

import com.mikepenz.agentbelay.model.Source
import com.mikepenz.agentbelay.model.UsageRecord

/**
 * Cursor for incremental scans. One row per `(harness, sourceFile)` so a scanner
 * only re-reads new bytes / new rows added since the previous run.
 */
data class ScanCursor(
    val sourceFile: String,
    val lastOffset: Long,
    val lastMtimeMillis: Long,
)

/**
 * A scanner reads the on-disk session files written by one harness and emits
 * [UsageRecord]s for assistant turns it has not yet reported.
 *
 * Scanners are responsible for:
 *  - Walking the harness's session directory (mtime-checked).
 *  - Honouring [cursors] so they only emit new entries on each pass.
 *  - Returning enough metadata (sourceFile, sourceOffset) for the ingest
 *    service to update the cursor table after a successful flush.
 *
 * Scanners must NOT compute cost — that's the [com.mikepenz.agentbelay.usage.pricing.CostCalculator]'s
 * job, applied uniformly across harnesses by [UsageIngestService].
 */
interface UsageScanner {
    val source: Source

    /**
     * Walk the harness's session storage and produce token-usage records new
     * since the previous scan. Implementations may throw — the ingest driver
     * isolates per-scanner failures.
     */
    fun scan(cursors: Map<String, ScanCursor>): List<UsageRecord>
}
