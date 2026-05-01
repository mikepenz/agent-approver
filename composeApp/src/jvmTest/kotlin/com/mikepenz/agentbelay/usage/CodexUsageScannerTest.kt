package com.mikepenz.agentbelay.usage

import com.mikepenz.agentbelay.usage.scanner.CodexUsageScanner
import java.io.File
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class CodexUsageScannerTest {

    private lateinit var root: File

    @BeforeTest
    fun setUp() {
        root = File(System.getProperty("java.io.tmpdir"), "codex-${System.currentTimeMillis()}")
        root.mkdirs()
    }

    @AfterTest
    fun tearDown() {
        root.deleteRecursively()
    }

    @Test
    fun normalizes_openai_cached_input_to_anthropic_style() {
        val dir = File(root, "sessions/2026/04/30").apply { mkdirs() }
        val file = File(dir, "rollout-001.jsonl")
        file.writeText(
            """
            {"type":"event_msg","timestamp":"2026-04-30T10:00:00Z","payload":{"model":"gpt-5-codex","session_id":"sess-x"}}
            {"type":"event_msg","timestamp":"2026-04-30T10:00:01Z","payload":{"type":"token_count","info":{"last_token_usage":{"input_tokens":12000,"cached_input_tokens":10000,"output_tokens":456,"reasoning_output_tokens":120}}}}
            """.trimIndent()
        )

        val scanner = CodexUsageScanner(rootOverride = File(root, "sessions"))
        val records = scanner.scan(emptyMap())
        assertEquals(1, records.size)

        val rec = records.first()
        // raw 12000 input includes 10000 cached → uncached = 2000
        assertEquals(2000L, rec.inputTokens)
        assertEquals(10000L, rec.cacheReadTokens)
        assertEquals(456L, rec.outputTokens)
        assertEquals(120L, rec.reasoningTokens)
        assertEquals("gpt-5-codex", rec.model)
        assertEquals("sess-x", rec.sessionId)
        assertTrue(rec.dedupKey.startsWith("codex:"))
    }
}
