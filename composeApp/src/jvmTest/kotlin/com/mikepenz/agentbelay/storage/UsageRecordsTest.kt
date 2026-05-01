package com.mikepenz.agentbelay.storage

import com.mikepenz.agentbelay.model.Source
import com.mikepenz.agentbelay.model.UsageRecord
import com.mikepenz.agentbelay.usage.ScanCursor
import kotlinx.datetime.Instant
import java.io.File
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

class UsageRecordsTest {

    private lateinit var tempDir: File
    private lateinit var storage: DatabaseStorage

    @BeforeTest
    fun setUp() {
        tempDir = File(System.getProperty("java.io.tmpdir"), "usage-${System.currentTimeMillis()}")
        tempDir.mkdirs()
        storage = DatabaseStorage(tempDir.absolutePath)
    }

    @AfterTest
    fun tearDown() {
        storage.close()
        tempDir.deleteRecursively()
    }

    private fun rec(
        harness: Source,
        sessionId: String,
        ts: Instant,
        input: Long,
        output: Long,
        cost: Double = 0.0,
        dedup: String,
    ) = UsageRecord(
        harness = harness,
        sessionId = sessionId,
        timestamp = ts,
        model = "claude-sonnet-4-5",
        inputTokens = input,
        outputTokens = output,
        cacheReadTokens = 0L,
        cacheWriteTokens = 0L,
        reasoningTokens = 0L,
        costUsd = cost,
        durationMs = null,
        sourceFile = "/tmp/x.jsonl",
        sourceOffset = 0L,
        dedupKey = dedup,
    )

    @Test
    fun insert_and_aggregate_totals() {
        val now = Instant.parse("2026-04-30T10:00:00Z")
        val recs = listOf(
            rec(Source.CLAUDE_CODE, "s1", now, 100, 20, 0.01, "k1"),
            rec(Source.CLAUDE_CODE, "s1", now, 200, 40, 0.02, "k2"),
            rec(Source.CODEX, "x1", now, 50, 10, 0.005, "k3"),
        )
        assertEquals(3, storage.insertUsageRecords(recs))
        val totals = storage.queryUsageTotals().associateBy { it.harness }
        assertEquals(300L, totals[Source.CLAUDE_CODE]?.inputTokens)
        assertEquals(60L, totals[Source.CLAUDE_CODE]?.outputTokens)
        assertEquals(2, totals[Source.CLAUDE_CODE]?.requests)
        assertEquals(1, totals[Source.CLAUDE_CODE]?.sessions)
        assertEquals(1, totals[Source.CODEX]?.requests)
    }

    @Test
    fun insert_is_idempotent_on_dedup_key() {
        val now = Instant.parse("2026-04-30T10:00:00Z")
        val first = listOf(rec(Source.CLAUDE_CODE, "s1", now, 100, 20, 0.01, "k1"))
        assertEquals(1, storage.insertUsageRecords(first))
        // Second pass with same dedup key — should ignore.
        assertEquals(0, storage.insertUsageRecords(first).coerceAtMost(0))
        assertEquals(1, storage.usageRecordCount())
    }

    @Test
    fun cursor_round_trip() {
        val cur = ScanCursor("/foo", lastOffset = 1024L, lastMtimeMillis = 555L)
        storage.upsertUsageCursor(Source.CLAUDE_CODE, cur)
        val loaded = storage.loadUsageCursors(Source.CLAUDE_CODE)
        assertNotNull(loaded["/foo"])
        assertEquals(1024L, loaded["/foo"]!!.lastOffset)

        // Update.
        storage.upsertUsageCursor(Source.CLAUDE_CODE, cur.copy(lastOffset = 2048L))
        assertEquals(2048L, storage.loadUsageCursors(Source.CLAUDE_CODE)["/foo"]!!.lastOffset)
    }

    @Test
    fun time_window_filter() {
        val ts1 = Instant.parse("2026-04-29T10:00:00Z")
        val ts2 = Instant.parse("2026-04-30T10:00:00Z")
        storage.insertUsageRecords(
            listOf(
                rec(Source.CLAUDE_CODE, "s", ts1, 100, 10, 0.01, "a"),
                rec(Source.CLAUDE_CODE, "s", ts2, 200, 20, 0.02, "b"),
            ),
        )
        val recent = storage.queryUsageTotals(sinceMillis = ts2.toEpochMilliseconds()).first()
        assertEquals(200L, recent.inputTokens)
        assertEquals(1, recent.requests)
    }
}
