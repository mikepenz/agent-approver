package com.mikepenz.agentapprover.protection.modules

import com.mikepenz.agentapprover.model.HookInput
import com.mikepenz.agentapprover.model.ProtectionMode
import kotlinx.serialization.json.JsonPrimitive
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class PythonVenvModuleTest {

    private val module = PythonVenvModule

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
        assertEquals("python_venv", module.id)
        assertTrue(module.corrective)
        assertEquals(ProtectionMode.AUTO_BLOCK, module.defaultMode)
        assertEquals(setOf("Bash"), module.applicableTools)
        assertEquals(3, module.rules.size)
    }

    // --- bare_python ---

    @Test
    fun barePythonBlocked() {
        assertNotNull(evaluateRule("bare_python", "python main.py"))
    }

    @Test
    fun barePython3Blocked() {
        assertNotNull(evaluateRule("bare_python", "python3 main.py"))
    }

    @Test
    fun python3VersionAllowed() {
        assertNull(evaluateRule("bare_python", "python3 --version"))
    }

    @Test
    fun venvPythonAllowed() {
        assertNull(evaluateRule("bare_python", ".venv/bin/python main.py"))
    }

    @Test
    fun uvRunPythonAllowed() {
        assertNull(evaluateRule("bare_python", "uv run python main.py"))
    }

    // --- bare_pip ---

    @Test
    fun pipInstallBlocked() {
        assertNotNull(evaluateRule("bare_pip", "pip install requests"))
    }

    @Test
    fun pip3InstallBlocked() {
        assertNotNull(evaluateRule("bare_pip", "pip3 install flask"))
    }

    @Test
    fun pythonMPipInstallBlocked() {
        assertNotNull(evaluateRule("bare_pip", "python -m pip install pytest"))
    }

    @Test
    fun python3MPipInstallBlocked() {
        assertNotNull(evaluateRule("bare_pip", "python3 -m pip install pytest"))
    }

    @Test
    fun uvPipInstallAllowed() {
        assertNull(evaluateRule("bare_pip", "uv pip install requests"))
    }

    // --- python_m_venv ---

    @Test
    fun pythonMVenvBlocked() {
        assertNotNull(evaluateRule("python_m_venv", "python -m venv .venv"))
    }

    @Test
    fun python3MVenvBlocked() {
        assertNotNull(evaluateRule("python_m_venv", "python3 -m venv myenv"))
    }

    @Test
    fun uvVenvAllowed() {
        assertNull(evaluateRule("python_m_venv", "uv venv"))
    }
}
