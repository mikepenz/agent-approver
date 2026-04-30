package com.mikepenz.agentbelay.hook

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class CodexBridgeInstallerTest {

    @Test
    fun `managed block round-trips through strip`() {
        val original = """
            |# user keys above
            |model = "gpt-5"
            |
            |[some.user.section]
            |key = "value"
        """.trimMargin()

        val block = CodexBridgeInstaller.buildManagedBlock(port = 19532)
        val combined = original + "\n\n" + block + "\n"

        val extracted = CodexBridgeInstaller.extractManagedBlock(combined)
        assertNotNull(extracted, "marker block must be extractable")
        assertTrue(extracted.contains("/approve-codex"))
        assertTrue(extracted.contains("/pre-tool-use-codex"))

        val stripped = CodexBridgeInstaller.stripManagedBlock(combined)
        assertFalse(stripped.contains(">>> agent-belay >>>"), "begin marker must be gone")
        assertFalse(stripped.contains("<<< agent-belay <<<"), "end marker must be gone")
        assertTrue(stripped.contains("model = \"gpt-5\""), "user content must be preserved")
        assertTrue(stripped.contains("[some.user.section]"), "user sections must be preserved")
    }

    @Test
    fun `strip is a no-op when no managed block is present`() {
        val text = """
            |[hooks]
            |custom = "thing"
        """.trimMargin()
        assertEquals(text, CodexBridgeInstaller.stripManagedBlock(text))
    }

    @Test
    fun `port is embedded in both endpoint URLs`() {
        val block = CodexBridgeInstaller.buildManagedBlock(port = 24680)
        assertTrue(block.contains("http://localhost:24680/approve-codex"))
        assertTrue(block.contains("http://localhost:24680/pre-tool-use-codex"))
    }
}
