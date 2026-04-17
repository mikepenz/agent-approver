package com.mikepenz.agentbuddy.capability

import com.mikepenz.agentbuddy.capability.modules.ResponseCompressionCapability
import com.mikepenz.agentbuddy.model.CapabilityModuleSettings
import com.mikepenz.agentbuddy.model.CompressionIntensity
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ResponseCompressionCapabilityTest {

    @Test
    fun `declares userPromptSubmit as its only required hook event`() {
        assertEquals(
            setOf(HookEvent.USER_PROMPT_SUBMIT),
            ResponseCompressionCapability.requiredHookEvents,
        )
    }

    @Test
    fun `supports both Claude Code and Copilot CLI`() {
        assertEquals(
            setOf(AgentTarget.CLAUDE_CODE, AgentTarget.COPILOT_CLI),
            ResponseCompressionCapability.supportedTargets,
        )
    }

    @Test
    fun `lite intensity emits the lite preset + golden rule`() {
        val text = ResponseCompressionCapability.contextInjection(
            settings = CapabilityModuleSettings(enabled = true, intensity = CompressionIntensity.LITE),
            event = HookEvent.USER_PROMPT_SUBMIT,
        )
        assertTrue(text.contains("— lite]"))
        assertTrue(text.contains("cut filler"))
        assertTrue(text.contains(ResponseCompressionCapability.GOLDEN_RULE))
    }

    @Test
    fun `full intensity emits fragments guidance`() {
        val text = ResponseCompressionCapability.contextInjection(
            settings = CapabilityModuleSettings(enabled = true, intensity = CompressionIntensity.FULL),
            event = HookEvent.USER_PROMPT_SUBMIT,
        )
        assertTrue(text.contains("— full]"))
        assertTrue(text.contains("fragments"))
        assertTrue(text.contains(ResponseCompressionCapability.GOLDEN_RULE))
    }

    @Test
    fun `ultra intensity emits telegraphic guidance`() {
        val text = ResponseCompressionCapability.contextInjection(
            settings = CapabilityModuleSettings(enabled = true, intensity = CompressionIntensity.ULTRA),
            event = HookEvent.USER_PROMPT_SUBMIT,
        )
        assertTrue(text.contains("— ultra]"))
        assertTrue(text.contains("Telegraphic"))
        assertTrue(text.contains(ResponseCompressionCapability.GOLDEN_RULE))
    }

    @Test
    fun `defaults to full intensity when unspecified`() {
        val text = ResponseCompressionCapability.contextInjection(
            settings = CapabilityModuleSettings(enabled = true, intensity = null),
            event = HookEvent.USER_PROMPT_SUBMIT,
        )
        assertTrue(text.contains("— full]"))
    }

    @Test
    fun `userPromptSubmit returns a non-blank injection`() {
        // The enum only contains USER_PROMPT_SUBMIT today, so this test
        // verifies the one supported event always produces injection text.
        val text = ResponseCompressionCapability.contextInjection(
            settings = CapabilityModuleSettings(enabled = true),
            event = HookEvent.USER_PROMPT_SUBMIT,
        )
        assertFalse(text.isBlank())
    }
}
