package com.mikepenz.agentbelay.protection.modules

import com.mikepenz.agentbelay.model.HookInput
import com.mikepenz.agentbelay.model.ProtectionMode
import kotlinx.serialization.json.JsonPrimitive
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class AbsolutePathsModuleTest {

    private val module = AbsolutePathsModule

    private fun bashHookInput(command: String, cwd: String = "/Users/dev/project") = HookInput(
        sessionId = "test-session",
        toolName = "Bash",
        toolInput = mapOf("command" to JsonPrimitive(command)),
        cwd = cwd,
    )

    private fun evaluateRule(ruleId: String, command: String, cwd: String = "/Users/dev/project") =
        module.rules.first { it.id == ruleId }.evaluate(bashHookInput(command, cwd))

    // --- Module metadata ---

    @Test
    fun moduleMetadata() {
        assertEquals("absolute_paths", module.id)
        assertTrue(module.corrective)
        assertEquals(ProtectionMode.AUTO_BLOCK, module.defaultMode)
        assertEquals(setOf("Bash"), module.applicableTools)
        assertEquals(2, module.rules.size)
    }

    // --- project_absolute ---

    @Test
    fun projectAbsolutePathBlocked() {
        assertNotNull(evaluateRule("project_absolute", "cat /Users/dev/project/src/main.kt"))
    }

    @Test
    fun relativePathAllowed() {
        assertNull(evaluateRule("project_absolute", "cat ./src/main.kt"))
    }

    @Test
    fun systemPathAllowedForProjectRule() {
        assertNull(evaluateRule("project_absolute", "ls /usr/local/bin"))
    }

    // --- home_absolute ---

    @Test
    fun homePathOutsideProjectBlocked() {
        val home = System.getProperty("user.home")
        // Use a path under actual home but outside the test cwd
        assertNotNull(evaluateRule("home_absolute", "cat $home/other-project/file.txt"))
    }

    @Test
    fun systemPathsAllowed() {
        assertNull(evaluateRule("home_absolute", "/usr/bin/env python3"))
        assertNull(evaluateRule("home_absolute", "ls /tmp/build"))
        assertNull(evaluateRule("home_absolute", "/bin/bash -c echo"))
        assertNull(evaluateRule("home_absolute", "/opt/homebrew/bin/brew install"))
    }

    @Test
    fun projectPathNotBlockedByHomeRule() {
        // Paths inside cwd should not be flagged by the home rule
        assertNull(evaluateRule("home_absolute", "cat /Users/dev/project/src/main.kt"))
    }
}
