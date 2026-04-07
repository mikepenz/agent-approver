package com.mikepenz.agentapprover.protection.modules

import com.mikepenz.agentapprover.model.HookInput
import com.mikepenz.agentapprover.model.ProtectionMode
import kotlinx.serialization.json.JsonPrimitive
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class PipedTailHeadModuleTest {

    private val module = PipedTailHeadModule

    private fun bashHookInput(command: String) = HookInput(
        sessionId = "test-session",
        toolName = "Bash",
        toolInput = mapOf("command" to JsonPrimitive(command)),
        cwd = "/project",
    )

    private fun evaluateRule(ruleId: String, command: String) =
        module.rules.first { it.id == ruleId }.evaluate(bashHookInput(command))

    // --- Module metadata ---

    @Test
    fun moduleMetadata() {
        assertEquals("piped_tail_head", module.id)
        assertTrue(module.corrective)
        assertEquals(ProtectionMode.AUTO_BLOCK, module.defaultMode)
        assertEquals(setOf("Bash"), module.applicableTools)
        assertEquals(2, module.rules.size)
    }

    // --- piped_tail: allowed (fast commands) ---

    @Test
    fun pipedTailFromCatAllowed() {
        assertNull(evaluateRule("piped_tail", "cat file.txt | tail -n 20"))
    }

    @Test
    fun pipedTailFromGrepAllowed() {
        assertNull(evaluateRule("piped_tail", "grep foo bar.log | tail -5"))
    }

    @Test
    fun pipedTailFromGrepWithSpacesAllowed() {
        assertNull(evaluateRule("piped_tail", "grep foo bar.log |  tail -5"))
    }

    @Test
    fun pipedTailFromRgAllowed() {
        assertNull(evaluateRule("piped_tail", "rg pattern src/ | tail -20"))
    }

    @Test
    fun pipedTailFromFindSortAllowed() {
        assertNull(evaluateRule("piped_tail", "find . -name '*.kt' | sort | tail -20"))
    }

    @Test
    fun pipedTailFromSedAllowed() {
        assertNull(evaluateRule("piped_tail", "sed -n '1,10p' file.txt | tail -5"))
    }

    @Test
    fun pipedTailFromJqAllowed() {
        assertNull(evaluateRule("piped_tail", "cat data.json | jq '.items[]' | tail -10"))
    }

    // --- piped_tail: blocked (expensive commands) ---

    @Test
    fun pipedTailFromCurlBlocked() {
        assertNotNull(evaluateRule("piped_tail", "curl -s https://example.com | tail -20"))
    }

    @Test
    fun pipedTailFromBuildBlocked() {
        assertNotNull(evaluateRule("piped_tail", "./gradlew build | tail -50"))
    }

    @Test
    fun pipedTailFromMvnBlocked() {
        assertNotNull(evaluateRule("piped_tail", "mvn test 2>&1 | tail -30"))
    }

    @Test
    fun pipedTailFromMakeBlocked() {
        assertNotNull(evaluateRule("piped_tail", "make all | tail -20"))
    }

    @Test
    fun pipedTailFromDockerBlocked() {
        assertNotNull(evaluateRule("piped_tail", "docker build . | tail -10"))
    }

    @Test
    fun pipedTailMixedPipelineBlocked() {
        // curl is expensive even though sort is fast
        assertNotNull(evaluateRule("piped_tail", "curl -s https://example.com | sort | tail -20"))
    }

    // --- piped_tail: file usage (no pipe, always allowed) ---

    @Test
    fun tailOnFileAllowed() {
        assertNull(evaluateRule("piped_tail", "tail -n 20 /tmp/output.log"))
    }

    @Test
    fun tailWithoutPipeAllowed() {
        assertNull(evaluateRule("piped_tail", "tail -f server.log"))
    }

    // --- piped_head: allowed (fast commands) ---

    @Test
    fun pipedHeadFromCatAllowed() {
        assertNull(evaluateRule("piped_head", "cat file.txt | head -n 10"))
    }

    @Test
    fun pipedHeadFromLsAllowed() {
        assertNull(evaluateRule("piped_head", "ls -la | head -5"))
    }

    @Test
    fun pipedHeadFromLsWithSpacesAllowed() {
        assertNull(evaluateRule("piped_head", "ls -la |  head -5"))
    }

    @Test
    fun pipedHeadFromFindSortAllowed() {
        assertNull(evaluateRule("piped_head", "find . -type f | sort | head -20"))
    }

    @Test
    fun pipedHeadFromGrepAllowed() {
        assertNull(evaluateRule("piped_head", "grep -r TODO src/ | head -10"))
    }

    // --- piped_head: blocked (expensive commands) ---

    @Test
    fun pipedHeadFromCurlBlocked() {
        assertNotNull(evaluateRule("piped_head", "curl -s https://example.com/api | head -5"))
    }

    @Test
    fun pipedHeadFromNpmBlocked() {
        assertNotNull(evaluateRule("piped_head", "npm install 2>&1 | head -20"))
    }

    @Test
    fun pipedHeadFromPythonBlocked() {
        assertNotNull(evaluateRule("piped_head", "python3 script.py | head -10"))
    }

    // --- piped_head: file usage (no pipe, always allowed) ---

    @Test
    fun headOnFileAllowed() {
        assertNull(evaluateRule("piped_head", "head -n 10 /tmp/output.log"))
    }

    @Test
    fun headWithoutPipeAllowed() {
        assertNull(evaluateRule("piped_head", "head -20 README.md"))
    }

    // --- Non-Bash tool ---

    @Test
    fun nonBashToolIgnored() {
        val input = HookInput(
            sessionId = "test-session",
            toolName = "Edit",
            toolInput = mapOf("command" to JsonPrimitive("cat file | tail")),
            cwd = "/project",
        )
        module.rules.forEach { rule ->
            assertNull(rule.evaluate(input))
        }
    }
}
