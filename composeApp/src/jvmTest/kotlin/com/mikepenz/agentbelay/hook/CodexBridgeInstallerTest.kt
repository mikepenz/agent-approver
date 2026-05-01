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
        assertTrue(extracted.contains("[[hooks.PermissionRequest.hooks]]"))
        assertTrue(extracted.contains("[[hooks.PreToolUse.hooks]]"))
        assertTrue(extracted.contains("[[hooks.PostToolUse.hooks]]"))
        assertTrue(extracted.contains("codex-approve.sh"))
        assertTrue(extracted.contains("codex-pre-tool-use.sh"))
        assertTrue(extracted.contains("codex-post-tool-use.sh"))

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
    fun `managed block uses Codex command hook shape`() {
        val block = CodexBridgeInstaller.buildManagedBlock(port = 24680)
        assertFalse(block.contains("type    = \"http\""))
        assertFalse(block.contains("url     = "))
        assertTrue(block.contains("type = \"command\""))
        assertTrue(block.contains("timeout = 300"))
    }
}
