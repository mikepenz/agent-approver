package com.mikepenz.agentbuddy.protection

import com.mikepenz.agentbuddy.model.*
import kotlinx.serialization.json.JsonPrimitive
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ProtectionEngineTest {

    private fun bashHookInput(command: String) = HookInput(
        sessionId = "test-session",
        toolName = "Bash",
        toolInput = mapOf("command" to JsonPrimitive(command)),
        cwd = "/tmp",
    )

    private fun writeHookInput(path: String) = HookInput(
        sessionId = "test-session",
        toolName = "Write",
        toolInput = mapOf("file_path" to JsonPrimitive(path)),
        cwd = "/tmp",
    )

    private val dangerousRule = object : ProtectionRule {
        override val id = "test-dangerous"
        override val name = "Dangerous command detector"
        override val description = "Flags commands containing 'dangerous'"
        override fun evaluate(hookInput: HookInput): ProtectionHit? {
            val cmd = CommandParser.bashCommand(hookInput) ?: return null
            if ("dangerous" in cmd) {
                return ProtectionHit(
                    moduleId = "test-module",
                    ruleId = id,
                    message = "Command contains 'dangerous'",
                    mode = ProtectionMode.ASK,
                )
            }
            return null
        }
    }

    private val testModule = object : ProtectionModule {
        override val id = "test-module"
        override val name = "Test Module"
        override val description = "A test protection module"
        override val corrective = false
        override val defaultMode = ProtectionMode.ASK
        override val applicableTools = setOf("Bash")
        override val rules = listOf(dangerousRule)
    }

    private fun engine(
        modules: List<ProtectionModule> = listOf(testModule),
        settings: ProtectionSettings = ProtectionSettings(),
    ) = ProtectionEngine(modules) { settings }

    @Test
    fun noHitsWhenNoModulesMatch() {
        val engine = engine()
        val hits = engine.evaluate(bashHookInput("echo hello"))
        assertTrue(hits.isEmpty(), "Expected no hits for a safe command")
    }

    @Test
    fun hitsWhenRuleMatches() {
        val engine = engine()
        val hits = engine.evaluate(bashHookInput("run dangerous operation"))
        assertEquals(1, hits.size)
        assertEquals("test-module", hits[0].moduleId)
        assertEquals("test-dangerous", hits[0].ruleId)
    }

    @Test
    fun skipsDisabledModule() {
        val settings = ProtectionSettings(
            modules = mapOf("test-module" to ModuleSettings(mode = ProtectionMode.DISABLED))
        )
        val engine = engine(settings = settings)
        val hits = engine.evaluate(bashHookInput("run dangerous operation"))
        assertTrue(hits.isEmpty(), "Expected no hits when module is disabled")
    }

    @Test
    fun skipsDisabledRule() {
        val settings = ProtectionSettings(
            modules = mapOf("test-module" to ModuleSettings(disabledRules = setOf("test-dangerous")))
        )
        val engine = engine(settings = settings)
        val hits = engine.evaluate(bashHookInput("run dangerous operation"))
        assertTrue(hits.isEmpty(), "Expected no hits when rule is disabled")
    }

    @Test
    fun respectsModeOverride() {
        val settings = ProtectionSettings(
            modules = mapOf("test-module" to ModuleSettings(mode = ProtectionMode.LOG_ONLY))
        )
        val engine = engine(settings = settings)
        val hits = engine.evaluate(bashHookInput("run dangerous operation"))
        assertEquals(1, hits.size)
        assertEquals(ProtectionMode.LOG_ONLY, hits[0].mode)
    }

    @Test
    fun skipsNonApplicableTool() {
        val engine = engine()
        val hits = engine.evaluate(writeHookInput("/tmp/foo.txt"))
        assertTrue(hits.isEmpty(), "Expected no hits for non-applicable tool")
    }

    @Test
    fun highestSeverityReturnsStrictestMode() {
        val engine = engine()
        val hits = listOf(
            ProtectionHit("m1", "r1", "msg1", ProtectionMode.LOG_ONLY),
            ProtectionHit("m2", "r2", "msg2", ProtectionMode.ASK),
            ProtectionHit("m3", "r3", "msg3", ProtectionMode.AUTO_BLOCK),
        )
        assertEquals(ProtectionMode.AUTO_BLOCK, engine.highestSeverity(hits))
    }

    @Test
    fun highestSeverityReturnsDisabledForEmptyList() {
        val engine = engine()
        assertEquals(ProtectionMode.DISABLED, engine.highestSeverity(emptyList()))
    }
}
