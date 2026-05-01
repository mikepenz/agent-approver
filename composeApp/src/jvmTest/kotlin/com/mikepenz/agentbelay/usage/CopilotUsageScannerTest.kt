package com.mikepenz.agentbelay.usage

import com.mikepenz.agentbelay.usage.scanner.CopilotUsageScanner
import java.io.File
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals

class CopilotUsageScannerTest {

    private lateinit var root: File

    @BeforeTest
    fun setUp() {
        root = File(System.getProperty("java.io.tmpdir"), "copilot-${System.currentTimeMillis()}")
        root.mkdirs()
    }

    @AfterTest
    fun tearDown() {
        root.deleteRecursively()
    }

    @Test
    fun extracts_output_tokens_from_legacy_assistant_event() {
        val workspaceDir = File(root, "history/ws-1/session-state").apply { mkdirs() }
        val file = File(workspaceDir, "events.jsonl")
        file.writeText(
            """
            {"type":"user.message","data":{"text":"hi"}}
            {"type":"assistant.message","timestamp":"2026-04-30T10:00:01Z","message":{"messageId":"m1","model":"gpt-5-codex","outputTokens":420}}
            """.trimIndent()
        )
        val scanner = CopilotUsageScanner(rootOverride = File(root, "history"))
        val records = scanner.scan(emptyMap())
        assertEquals(1, records.size)
        assertEquals(420L, records.first().outputTokens)
        assertEquals(0L, records.first().inputTokens)
        assertEquals("gpt-5-codex", records.first().model)
    }
}
