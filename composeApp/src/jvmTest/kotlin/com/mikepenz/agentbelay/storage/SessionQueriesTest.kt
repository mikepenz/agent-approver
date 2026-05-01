package com.mikepenz.agentbelay.storage

import com.mikepenz.agentbelay.model.Source
import com.mikepenz.agentbelay.model.UsageRecord
import kotlin.time.Instant
import java.io.File
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class SessionQueriesTest {

    private lateinit var tempDir: File
    private lateinit var storage: DatabaseStorage

    @BeforeTest
    fun setUp() {
        tempDir = File(System.getProperty("java.io.tmpdir"), "session-q-${System.currentTimeMillis()}")
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
        cacheRead: Long = 0,
        model: String? = "claude-sonnet-4-5",
        cost: Double = 0.0,
        dedup: String,
    ) = UsageRecord(
        harness = harness,
        sessionId = sessionId,
        timestamp = ts,
        model = model,
        inputTokens = input,
        outputTokens = output,
        cacheReadTokens = cacheRead,
        cacheWriteTokens = 0L,
        reasoningTokens = 0L,
        costUsd = cost,
        durationMs = null,
        sourceFile = "/tmp/x.jsonl",
        sourceOffset = 0L,
        dedupKey = dedup,
    )

    @Test
    fun queryUsageBySession_returns_chronological_records_for_one_session() {
        val t0 = Instant.parse("2026-04-30T10:00:00Z")
        val t1 = Instant.parse("2026-04-30T10:05:00Z")
        val t2 = Instant.parse("2026-04-30T10:10:00Z")
        storage.insertUsageRecords(
            listOf(
                rec(Source.CLAUDE_CODE, "s-target", t1, 100, 20, dedup = "k1"),
                rec(Source.CLAUDE_CODE, "s-other", t0, 99, 99, dedup = "k-other"),
                rec(Source.CLAUDE_CODE, "s-target", t2, 200, 40, dedup = "k2"),
                rec(Source.CLAUDE_CODE, "s-target", t0, 50, 10, dedup = "k0"),
            ),
        )

        val rows = storage.queryUsageBySession("s-target")
        assertEquals(3, rows.size, "must scope to the requested session")
        assertEquals(listOf(t0, t1, t2), rows.map { it.timestamp })
        assertEquals(50L, rows[0].inputTokens)
    }

    @Test
    fun listRecentSessions_filters_by_minimum_turns_and_orders_by_last_activity() {
        val base = Instant.parse("2026-04-30T10:00:00Z").toEpochMilliseconds()
        // session "active": 5 turns over 5 minutes
        // session "trickle": 2 turns
        // session "newer": 6 turns, last turn after "active" — should sort first
        val records = mutableListOf<UsageRecord>()
        repeat(5) { i ->
            records.add(
                rec(
                    Source.CLAUDE_CODE, "active",
                    Instant.fromEpochMilliseconds(base + i * 60_000L),
                    1000, 100, dedup = "active-$i", cost = 0.01,
                ),
            )
        }
        repeat(2) { i ->
            records.add(
                rec(
                    Source.CODEX, "trickle",
                    Instant.fromEpochMilliseconds(base + i * 60_000L),
                    100, 50, dedup = "trickle-$i", cost = 0.001,
                ),
            )
        }
        repeat(6) { i ->
            records.add(
                rec(
                    Source.CLAUDE_CODE, "newer",
                    Instant.fromEpochMilliseconds(base + 10_000_000L + i * 60_000L),
                    500, 60, dedup = "newer-$i", cost = 0.005,
                ),
            )
        }
        storage.insertUsageRecords(records)

        val sessions = storage.listRecentSessions(minTurns = 5)
        assertEquals(2, sessions.size, "trickle (2 turns) must be excluded")
        assertEquals("newer", sessions[0].sessionId, "most recent activity sorts first")
        assertEquals("active", sessions[1].sessionId)

        val newer = sessions[0]
        assertEquals(6, newer.turnCount)
        assertEquals(3000L, newer.totalInputTokens)
        assertEquals(360L, newer.totalOutputTokens)
        assertTrue(newer.totalCostUsd > 0.029 && newer.totalCostUsd < 0.031)
        assertNotNull(newer.model)
    }

    @Test
    fun listRecentSessions_picks_dominant_model_when_session_switches() {
        val base = Instant.parse("2026-04-30T10:00:00Z").toEpochMilliseconds()
        // 4 sonnet turns + 2 opus → sonnet wins.
        val records = buildList {
            repeat(4) { i ->
                add(rec(
                    Source.CLAUDE_CODE, "mixed",
                    Instant.fromEpochMilliseconds(base + i * 60_000L),
                    200, 30, model = "claude-sonnet-4-5", dedup = "mixed-s-$i",
                ))
            }
            repeat(2) { i ->
                add(rec(
                    Source.CLAUDE_CODE, "mixed",
                    Instant.fromEpochMilliseconds(base + 1_000_000L + i * 60_000L),
                    400, 80, model = "claude-opus-4-5", dedup = "mixed-o-$i",
                ))
            }
        }
        storage.insertUsageRecords(records)

        val sessions = storage.listRecentSessions(minTurns = 5)
        assertEquals(1, sessions.size)
        assertEquals("claude-sonnet-4-5", sessions[0].model)
    }

    @Test
    fun listRecentSessions_honours_since_filter() {
        val cutoff = Instant.parse("2026-04-30T12:00:00Z").toEpochMilliseconds()
        val records = buildList {
            // before cutoff
            repeat(6) { i ->
                add(rec(
                    Source.CLAUDE_CODE, "old",
                    Instant.fromEpochMilliseconds(cutoff - 1_000_000L - i * 60_000L),
                    100, 10, dedup = "old-$i",
                ))
            }
            // after cutoff
            repeat(6) { i ->
                add(rec(
                    Source.CLAUDE_CODE, "new",
                    Instant.fromEpochMilliseconds(cutoff + i * 60_000L),
                    100, 10, dedup = "new-$i",
                ))
            }
        }
        storage.insertUsageRecords(records)

        val sessions = storage.listRecentSessions(sinceMillis = cutoff, minTurns = 5)
        assertEquals(1, sessions.size)
        assertEquals("new", sessions[0].sessionId)
    }
}
