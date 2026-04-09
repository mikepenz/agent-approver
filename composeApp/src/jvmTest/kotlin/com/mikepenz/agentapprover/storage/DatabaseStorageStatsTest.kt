package com.mikepenz.agentapprover.storage

import com.mikepenz.agentapprover.model.*
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import java.io.File
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import kotlin.math.roundToLong
import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.milliseconds

class DatabaseStorageStatsTest {

    private lateinit var tempDir: File
    private lateinit var storage: DatabaseStorage
    private val now: Instant = Clock.System.now()

    @BeforeTest
    fun setUp() {
        tempDir = File(System.getProperty("java.io.tmpdir"), "stats-test-${System.currentTimeMillis()}")
        tempDir.mkdirs()
        storage = DatabaseStorage(tempDir.absolutePath)
    }

    @AfterTest
    fun tearDown() {
        storage.close()
        tempDir.deleteRecursively()
    }

    private fun makeResult(
        id: String,
        decision: Decision,
        decidedAtOffsetDays: Int,
        latencySeconds: Double = 0.0,
        protectionModule: String? = null,
        protectionRule: String? = null,
    ): ApprovalResult {
        val decided = now - decidedAtOffsetDays.days
        // Use millisecond precision so sub-second latencies (e.g., 0.5s) survive
        // and exercise the `<1s` histogram bucket as intended.
        val requested = decided - (latencySeconds * 1000).roundToLong().milliseconds
        return ApprovalResult(
            request = ApprovalRequest(
                id = id,
                source = Source.CLAUDE_CODE,
                toolType = ToolType.DEFAULT,
                hookInput = HookInput(sessionId = "s", toolName = "Bash"),
                timestamp = requested,
                rawRequestJson = "{}",
            ),
            decision = decision,
            decidedAt = decided,
            protectionModule = protectionModule,
            protectionRule = protectionRule,
        )
    }

    @Test
    fun countsByGroup() {
        storage.insert(makeResult("1", Decision.APPROVED, 0, latencySeconds = 10.0))
        storage.insert(makeResult("2", Decision.APPROVED, 1, latencySeconds = 20.0))
        storage.insert(makeResult("3", Decision.AUTO_APPROVED, 0, latencySeconds = 0.5))
        storage.insert(makeResult("4", Decision.DENIED, 2, latencySeconds = 30.0))
        storage.insert(makeResult("5", Decision.TIMEOUT, 0))
        storage.insert(makeResult("6", Decision.PROTECTION_BLOCKED, 0, protectionModule = "files", protectionRule = "envFile"))

        val stats = storage.queryStats(since = null)

        assertEquals(6, stats.totalDecisions)
        assertEquals(2, stats.byGroup[DecisionGroup.MANUAL_APPROVE])
        assertEquals(1, stats.byGroup[DecisionGroup.RISK_APPROVE])
        assertEquals(1, stats.byGroup[DecisionGroup.MANUAL_DENY])
        assertEquals(1, stats.byGroup[DecisionGroup.TIMEOUT])
        assertEquals(1, stats.byGroup[DecisionGroup.PROTECTION_BLOCK])
    }

    @Test
    fun perDayBucketsByDecidedAt() {
        storage.insert(makeResult("a", Decision.APPROVED, 0))
        storage.insert(makeResult("b", Decision.APPROVED, 0))
        storage.insert(makeResult("c", Decision.APPROVED, 1))
        storage.insert(makeResult("d", Decision.DENIED, 1))

        val stats = storage.queryStats(since = null)

        assertTrue(stats.perDay.size >= 2, "expected at least 2 daily buckets, got ${stats.perDay.size}")
        val totalApprovedAcrossDays = stats.perDay.sumOf { it.byGroup[DecisionGroup.MANUAL_APPROVE] ?: 0 }
        assertEquals(3, totalApprovedAcrossDays)
        val totalDeniedAcrossDays = stats.perDay.sumOf { it.byGroup[DecisionGroup.MANUAL_DENY] ?: 0 }
        assertEquals(1, totalDeniedAcrossDays)
    }

    @Test
    fun latencyStatsForDeliberatedGroups() {
        storage.insert(makeResult("1", Decision.APPROVED, 0, latencySeconds = 1.0))
        storage.insert(makeResult("2", Decision.APPROVED, 0, latencySeconds = 5.0))
        storage.insert(makeResult("3", Decision.APPROVED, 0, latencySeconds = 10.0))
        storage.insert(makeResult("4", Decision.APPROVED, 0, latencySeconds = 100.0))
        // Protection rows must NOT contribute to latency.
        storage.insert(makeResult("5", Decision.PROTECTION_BLOCKED, 0, latencySeconds = 0.0, protectionModule = "m", protectionRule = "r"))

        val stats = storage.queryStats(since = null)
        val manual = stats.latencyByGroup[DecisionGroup.MANUAL_APPROVE]
        assertNotNull(manual)
        assertEquals(4, manual.count)
        assertTrue(manual.avgSeconds > 0)
        assertEquals(null, stats.latencyByGroup[DecisionGroup.PROTECTION_BLOCK])
    }

    @Test
    fun latencyHistogramBucketsAreOrdered() {
        // 0.5s -> bucket 0 (<1s)
        // 3s   -> bucket 1 (1–5s)
        // 100s -> bucket 4 (1–5m)
        storage.insert(makeResult("1", Decision.APPROVED, 0, latencySeconds = 0.5))
        storage.insert(makeResult("2", Decision.APPROVED, 0, latencySeconds = 3.0))
        storage.insert(makeResult("3", Decision.APPROVED, 0, latencySeconds = 100.0))

        val stats = storage.queryStats(since = null)
        val histogram = stats.latencyHistogramByGroup[DecisionGroup.MANUAL_APPROVE]
        assertNotNull(histogram)
        assertEquals(StatsSummary.BUCKET_LABELS.size, histogram.size)
        assertEquals(1, histogram[0])
        assertEquals(1, histogram[1])
        assertEquals(1, histogram[4])
    }

    @Test
    fun topProtectionsRankedByCount() {
        repeat(3) { storage.insert(makeResult("a$it", Decision.PROTECTION_BLOCKED, 0, protectionModule = "files", protectionRule = "envFile")) }
        repeat(1) { storage.insert(makeResult("b$it", Decision.PROTECTION_BLOCKED, 0, protectionModule = "shell", protectionRule = "rm")) }
        repeat(2) { storage.insert(makeResult("c$it", Decision.PROTECTION_LOGGED, 0, protectionModule = "net", protectionRule = "curl")) }

        val stats = storage.queryStats(since = null)
        val top = stats.topProtections
        assertEquals(3, top.size)
        assertEquals("files", top[0].moduleId)
        assertEquals(3, top[0].count)
        assertEquals("net", top[1].moduleId)
        assertEquals(2, top[1].count)
    }

    @Test
    fun sinceFilterExcludesOlderRows() {
        storage.insert(makeResult("recent", Decision.APPROVED, 1))
        storage.insert(makeResult("older", Decision.APPROVED, 30))

        val sevenDaysAgo = now - 7.days
        val stats = storage.queryStats(since = sevenDaysAgo)

        assertEquals(1, stats.totalDecisions)
        assertEquals(1, stats.byGroup[DecisionGroup.MANUAL_APPROVE])
    }
}
