package com.mikepenz.agentbuddy.protection.modules

import com.mikepenz.agentbuddy.model.HookInput
import com.mikepenz.agentbuddy.model.ProtectionMode
import kotlinx.serialization.json.JsonPrimitive
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ToolBypassModuleTest {

    private val module = ToolBypassModule

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
        assertEquals("tool_bypass", module.id)
        assertEquals("Tool-Switching Bypass Detection", module.name)
        assertEquals(false, module.corrective)
        assertEquals(ProtectionMode.ASK, module.defaultMode)
        assertEquals(setOf("Bash"), module.applicableTools)
        assertEquals(6, module.rules.size)
    }

    // --- sed_inline ---

    @Test
    fun sedInlineBlocked() {
        assertNotNull(evaluateRule("sed_inline", "sed -i 's/foo/bar/' file.txt"))
    }

    @Test
    fun sedWithoutInlineAllowed() {
        assertNull(evaluateRule("sed_inline", "sed 's/foo/bar/' file.txt"))
    }

    // --- perl_inline ---

    @Test
    fun perlInlineBlocked() {
        assertNotNull(evaluateRule("perl_inline", "perl -i -pe 's/foo/bar/' file.txt"))
    }

    @Test
    fun perlPiEBlocked() {
        assertNotNull(evaluateRule("perl_inline", "perl -pi -e 's/foo/bar/' file.txt"))
    }

    @Test
    fun perlEWithoutInlineAllowed() {
        assertNull(evaluateRule("perl_inline", "perl -e 'print 42'"))
    }

    // --- python_file_write ---

    @Test
    fun pythonCWithOpenWriteBlocked() {
        assertNotNull(evaluateRule("python_file_write", "python -c \"open('f.txt','w').write('hi')\""))
    }

    @Test
    fun pythonCPrintAllowed() {
        assertNull(evaluateRule("python_file_write", "python -c \"print('hello')\""))
    }

    // --- echo_redirect ---

    @Test
    fun echoRedirectBlocked() {
        assertNotNull(evaluateRule("echo_redirect", "echo hello > file.txt"))
    }

    @Test
    fun printfRedirectBlocked() {
        assertNotNull(evaluateRule("echo_redirect", "printf '%s' data > file.txt"))
    }

    @Test
    fun echoRedirectDevNullAllowed() {
        assertNull(evaluateRule("echo_redirect", "echo hello > /dev/null"))
    }

    @Test
    fun echoWithoutRedirectAllowed() {
        assertNull(evaluateRule("echo_redirect", "echo hello"))
    }

    // --- tee_write ---

    @Test
    fun teeFileBlocked() {
        assertNotNull(evaluateRule("tee_write", "echo hello | tee output.txt"))
    }

    @Test
    fun teeDevNullAllowed() {
        assertNull(evaluateRule("tee_write", "echo hello | tee /dev/null"))
    }

    // --- bash_heredoc ---

    @Test
    fun catRedirectHeredocBlocked() {
        assertNotNull(evaluateRule("bash_heredoc", "cat > config.txt << EOF"))
    }

    @Test
    fun teeHeredocBlocked() {
        assertNotNull(evaluateRule("bash_heredoc", "tee script.sh << EOF"))
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
