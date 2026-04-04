package com.mikepenz.agentapprover.protection.modules

import com.mikepenz.agentapprover.model.HookInput
import com.mikepenz.agentapprover.model.ProtectionMode
import kotlinx.serialization.json.JsonPrimitive
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

class PipeAbuseModuleTest {

    private val module = PipeAbuseModule

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
        assertEquals("pipe_abuse", module.id)
        assertEquals(false, module.corrective)
        assertEquals(ProtectionMode.ASK_AUTO_BLOCK, module.defaultMode)
        assertEquals(setOf("Bash"), module.applicableTools)
        assertEquals(2, module.rules.size)
    }

    // --- bulk_permission_change ---

    @Test
    fun xargsChmodBlocked() {
        assertNotNull(evaluateRule("bulk_permission_change", "find . -name '*.sh' | xargs chmod +x"))
    }

    @Test
    fun xargsChownBlocked() {
        assertNotNull(evaluateRule("bulk_permission_change", "find . | xargs chown root"))
    }

    @Test
    fun xargsGrepAllowed() {
        assertNull(evaluateRule("bulk_permission_change", "find . | xargs grep pattern"))
    }

    // --- write_then_execute ---

    @Test
    fun writeThenExecuteBlocked() {
        assertNotNull(evaluateRule("write_then_execute", "cat > script.sh << 'EOF'\necho hello\nEOF\n&& bash script.sh"))
    }

    @Test
    fun teeThenExecuteBlocked() {
        assertNotNull(evaluateRule("write_then_execute", "tee deploy.py << 'EOF'\nprint('hi')\nEOF\n&& python deploy.py"))
    }

    @Test
    fun justWriteAllowed() {
        assertNull(evaluateRule("write_then_execute", "cat > script.sh << 'EOF'\necho hello\nEOF"))
    }

    @Test
    fun writeThenExecuteWithSemicolonBlocked() {
        assertNotNull(evaluateRule("write_then_execute", "tee run.sh << 'EOF'\necho test\nEOF\n; bash run.sh"))
    }
}
