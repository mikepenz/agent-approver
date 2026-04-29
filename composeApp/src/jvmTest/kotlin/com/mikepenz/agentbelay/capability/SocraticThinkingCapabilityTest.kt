package com.mikepenz.agentbelay.capability

import com.mikepenz.agentbelay.capability.modules.SocraticThinkingCapability
import com.mikepenz.agentbelay.model.CapabilityModuleSettings
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class SocraticThinkingCapabilityTest {

    @Test
    fun `declares sessionStart as its only required hook event`() {
        assertEquals(
            setOf(HookEvent.SESSION_START),
            SocraticThinkingCapability.requiredHookEvents,
        )
    }

    @Test
    fun `supports both Claude Code and Copilot CLI`() {
        assertEquals(
            setOf(AgentTarget.CLAUDE_CODE, AgentTarget.COPILOT_CLI),
            SocraticThinkingCapability.supportedTargets,
        )
    }

    @Test
    fun `sessionStart returns the socratic prompt`() {
        val text = SocraticThinkingCapability.contextInjection(
            settings = CapabilityModuleSettings(enabled = true),
            event = HookEvent.SESSION_START,
        )
        assertFalse(text.isBlank())
        assertTrue(text.contains("Phase 1"))
        assertTrue(text.contains("clarifying questions"))
        assertTrue(text.contains("Phase 2"))
        assertTrue(text.contains("assumptions"))
        assertTrue(text.contains("Phase 3"))
    }

    @Test
    fun `userPromptSubmit returns empty string`() {
        val text = SocraticThinkingCapability.contextInjection(
            settings = CapabilityModuleSettings(enabled = true),
            event = HookEvent.USER_PROMPT_SUBMIT,
        )
        assertEquals("", text)
    }
}
