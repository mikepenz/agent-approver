package com.mikepenz.agentbelay.protection.modules

import com.mikepenz.agentbelay.model.HookInput
import com.mikepenz.agentbelay.model.ProtectionMode
import kotlinx.serialization.json.JsonPrimitive
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class InlineScriptsModuleTest {

    private val module = InlineScriptsModule

    private fun bashHookInput(command: String) = HookInput(
        sessionId = "test-session",
        toolName = "Bash",
        toolInput = mapOf("command" to JsonPrimitive(command)),
        cwd = "/tmp",
    )

    private fun evaluateRule(ruleId: String, command: String) =
        module.rules.first { it.id == ruleId }.evaluate(bashHookInput(command))

    // --- Module metadata ---

    @Test
    fun moduleMetadata() {
        assertEquals("inline_scripts", module.id)
        assertEquals("Inline Script Prevention", module.name)
        assertEquals(true, module.corrective)
        assertEquals(ProtectionMode.AUTO_BLOCK, module.defaultMode)
        assertEquals(setOf("Bash"), module.applicableTools)
        assertEquals(4, module.rules.size)
    }

    // --- heredoc_script ---

    @Test
    fun heredocScriptCatBlocked() {
        assertNotNull(evaluateRule("heredoc_script", "cat > deploy.sh << 'EOF'"))
    }

    @Test
    fun heredocScriptTeeBlocked() {
        assertNotNull(evaluateRule("heredoc_script", "tee setup.py << EOF"))
    }

    @Test
    fun heredocScriptTxtAllowed() {
        assertNull(evaluateRule("heredoc_script", "cat > notes.txt << EOF"))
    }

    // --- shebang_write ---

    @Test
    fun shebangWriteBlocked() {
        assertNotNull(evaluateRule("shebang_write", "echo '#!/usr/bin/env bash' > script.sh"))
    }

    @Test
    fun shebangWriteAllowed() {
        assertNull(evaluateRule("shebang_write", "echo 'hello' > output.txt"))
    }

    // --- bash_c_complex ---

    @Test
    fun bashCComplex4ChainsBlocked() {
        assertNotNull(evaluateRule("bash_c_complex", "bash -c 'cd /tmp && mkdir foo && touch bar && echo done'"))
    }

    @Test
    fun bashCSimple2ChainsAllowed() {
        assertNull(evaluateRule("bash_c_complex", "bash -c 'cd /tmp && ls'"))
    }

    // --- interpreter_long ---

    @Test
    fun interpreterLongPythonBlocked() {
        val longCode = "x".repeat(160)
        assertNotNull(evaluateRule("interpreter_long", "python -c '$longCode'"))
    }

    @Test
    fun interpreterLongNodeBlocked() {
        val longCode = "x".repeat(160)
        assertNotNull(evaluateRule("interpreter_long", "node -e '$longCode'"))
    }

    @Test
    fun interpreterShortPythonAllowed() {
        assertNull(evaluateRule("interpreter_long", "python -c 'print(42)'"))
    }

    // --- Non-Bash tool is ignored ---

    @Test
    fun nonBashToolIgnored() {
        val input = HookInput(
            sessionId = "test-session",
            toolName = "Write",
            toolInput = mapOf("file_path" to JsonPrimitive("/tmp/foo.txt")),
            cwd = "/tmp",
        )
        val hits = module.rules.mapNotNull { it.evaluate(input) }
        assertTrue(hits.isEmpty())
    }
}
