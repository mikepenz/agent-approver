package com.mikepenz.agentbelay.usage

import com.mikepenz.agentbelay.usage.scanner.PiUsageScanner
import java.io.File
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals

class PiUsageScannerTest {

    private lateinit var root: File

    @BeforeTest
    fun setUp() {
        root = File(System.getProperty("java.io.tmpdir"), "pi-${System.currentTimeMillis()}")
        root.mkdirs()
    }

    @AfterTest
    fun tearDown() {
        root.deleteRecursively()
    }

    @Test
    fun parses_assistant_usage_with_pi_field_names() {
        val sessionDir = File(root, "sessions/proj-a/sess-1").apply { mkdirs() }
        val file = File(sessionDir, "log.jsonl")
        file.writeText(
            """
            {"role":"user","timestamp":"2026-04-30T10:00:00Z","content":"hi"}
            {"role":"assistant","timestamp":"2026-04-30T10:00:01Z","model":"claude-sonnet-4-6","usage":{"input":1500,"output":300,"cacheRead":4000,"cacheWrite":200},"id":"msg-1"}
            """.trimIndent()
        )
        val scanner = PiUsageScanner(rootOverride = File(root, "sessions"))
        val records = scanner.scan(emptyMap())
        assertEquals(1, records.size)
        val r = records.first()
        assertEquals(1500L, r.inputTokens)
        assertEquals(300L, r.outputTokens)
        assertEquals(4000L, r.cacheReadTokens)
        assertEquals(200L, r.cacheWriteTokens)
        assertEquals("claude-sonnet-4-6", r.model)
    }
}
