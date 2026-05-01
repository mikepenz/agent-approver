package com.mikepenz.agentbelay.usage

import com.mikepenz.agentbelay.usage.scanner.ClaudeCodeUsageScanner
import java.io.File
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class ClaudeCodeUsageScannerTest {

    private lateinit var root: File

    @BeforeTest
    fun setUp() {
        root = File(System.getProperty("java.io.tmpdir"), "ccode-${System.currentTimeMillis()}")
        root.mkdirs()
    }

    @AfterTest
    fun tearDown() {
        root.deleteRecursively()
    }

    @Test
    fun parses_assistant_usage_into_record() {
        val sessionDir = File(root, "projects/-Users-test").apply { mkdirs() }
        val sessionFile = File(sessionDir, "abcd-1234.jsonl")
        sessionFile.writeText(
            """
            {"type":"user","message":{"content":"hello"},"timestamp":"2026-04-30T10:00:00Z"}
            {"type":"assistant","sessionId":"abcd-1234","message":{"model":"claude-sonnet-4-5","usage":{"input_tokens":120,"output_tokens":48,"cache_creation_input_tokens":2000,"cache_read_input_tokens":11200}},"timestamp":"2026-04-30T10:00:01Z"}
            {"type":"assistant","sessionId":"abcd-1234","message":{"model":"claude-sonnet-4-5","usage":{"input_tokens":80,"output_tokens":12,"cache_creation_input_tokens":0,"cache_read_input_tokens":13200}},"timestamp":"2026-04-30T10:00:05Z"}
            """.trimIndent()
        )

        val scanner = ClaudeCodeUsageScanner(rootOverride = File(root, "projects"))
        val records = scanner.scan(emptyMap())
        assertEquals(2, records.size)

        val first = records.first()
        assertEquals(120L, first.inputTokens)
        assertEquals(48L, first.outputTokens)
        assertEquals(11200L, first.cacheReadTokens)
        assertEquals(2000L, first.cacheWriteTokens)
        assertEquals("claude-sonnet-4-5", first.model)
        assertEquals("abcd-1234", first.sessionId)
        assertNotNull(first.dedupKey)
        assertTrue(first.dedupKey != records[1].dedupKey, "dedup keys must differ across turns")
    }

    @Test
    fun second_scan_with_cursor_skips_already_seen_lines() {
        val sessionDir = File(root, "projects/-x").apply { mkdirs() }
        val sessionFile = File(sessionDir, "s.jsonl")
        sessionFile.writeText(
            """{"type":"assistant","sessionId":"s","message":{"model":"claude-sonnet-4-5","usage":{"input_tokens":1,"output_tokens":1,"cache_creation_input_tokens":0,"cache_read_input_tokens":0}},"timestamp":"2026-04-30T10:00:00Z"}""" + "\n"
        )

        val scanner = ClaudeCodeUsageScanner(rootOverride = File(root, "projects"))
        val first = scanner.scan(emptyMap())
        assertEquals(1, first.size)

        val cursors = mapOf(
            sessionFile.absolutePath to ScanCursor(
                sourceFile = sessionFile.absolutePath,
                lastOffset = sessionFile.length(),
                lastMtimeMillis = sessionFile.lastModified(),
            )
        )
        val second = scanner.scan(cursors)
        assertEquals(0, second.size, "scanner with up-to-date cursor must be a no-op")
    }
}
