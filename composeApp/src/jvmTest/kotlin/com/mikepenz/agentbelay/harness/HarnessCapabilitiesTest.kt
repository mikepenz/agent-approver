package com.mikepenz.agentbelay.harness

import com.mikepenz.agentbelay.harness.claudecode.ClaudeCodeHarness
import com.mikepenz.agentbelay.harness.codex.CodexHarness
import com.mikepenz.agentbelay.harness.copilot.CopilotHarness
import com.mikepenz.agentbelay.harness.pi.PiHarness
import com.mikepenz.agentbelay.model.ApprovalRequest
import com.mikepenz.agentbelay.model.HookInput
import com.mikepenz.agentbelay.model.Source
import com.mikepenz.agentbelay.model.ToolType
import kotlin.time.Clock
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Sanity check that capability flags map onto actual response shapes.
 * Catches accidental drift between what the [HarnessCapabilities] flag
 * advertises and what the [HarnessAdapter] implementation actually
 * supports.
 */
class HarnessCapabilitiesTest {

    private fun fakeRequest(source: Source) = ApprovalRequest(
        id = UUID.randomUUID().toString(),
        source = source,
        toolType = ToolType.DEFAULT,
        hookInput = HookInput(sessionId = "s", toolName = "Bash"),
        timestamp = Clock.System.now(),
        rawRequestJson = "{}",
    )

    @Test
    fun `Claude Code advertises the capabilities its envelope supports`() {
        val h = ClaudeCodeHarness()
        assertTrue(h.capabilities.supportsArgRewriting)
        assertTrue(h.capabilities.supportsAlwaysAllowWriteThrough)
        assertTrue(h.capabilities.supportsOutputRedaction)
        assertTrue(h.capabilities.supportsInterruptOnDeny)

        // Allow with arg rewrite emits updatedInput.
        val req = fakeRequest(Source.CLAUDE_CODE)
        val response = h.adapter.buildPermissionAllowResponse(
            req,
            updatedInput = mapOf("command" to JsonPrimitive("rm -rf ./build")),
        )
        val obj = kotlinx.serialization.json.Json.parseToJsonElement(response.body).jsonObject
        val decision = obj["hookSpecificOutput"]!!.jsonObject["decision"]!!.jsonObject
        assertEquals("allow", decision["behavior"]!!.jsonPrimitive.content)
        assertNotNull(decision["updatedInput"])
    }

    @Test
    fun `Claude Code emits updatedToolOutput for redacted PostToolUse`() {
        val h = ClaudeCodeHarness()
        val response = h.adapter.buildPostToolUseRedactedResponse(
            kotlinx.serialization.json.buildJsonObject {
                put("stdout", JsonPrimitive("[REDACTED:api-keys/aws-access-key]"))
            }
        )
        assertNotNull(response)
        val obj = kotlinx.serialization.json.Json.parseToJsonElement(response.body).jsonObject
        assertEquals("PostToolUse", obj["hookSpecificOutput"]!!.jsonObject["hookEventName"]!!.jsonPrimitive.content)
        assertNotNull(obj["hookSpecificOutput"]!!.jsonObject["updatedToolOutput"])
    }

    @Test
    fun `Copilot advertises arg rewriting but not output redaction`() {
        val h = CopilotHarness()
        assertTrue(h.capabilities.supportsArgRewriting)
        assertFalse(h.capabilities.supportsAlwaysAllowWriteThrough)
        assertFalse(h.capabilities.supportsOutputRedaction)
        assertTrue(h.capabilities.supportsInterruptOnDeny)
    }

    @Test
    fun `Copilot post-tool-use redaction returns null sentinel`() {
        val h = CopilotHarness()
        val response = h.adapter.buildPostToolUseRedactedResponse(
            kotlinx.serialization.json.buildJsonObject {
                put("stdout", JsonPrimitive("anything"))
            }
        )
        assertNull(response, "Copilot's postToolUse cannot modify output — adapter must return null")
    }

    @Test
    fun `Copilot deny includes interrupt true`() {
        val h = CopilotHarness()
        val response = h.adapter.buildPermissionDenyResponse(fakeRequest(Source.COPILOT), "blocked")
        val obj = kotlinx.serialization.json.Json.parseToJsonElement(response.body).jsonObject
        assertEquals("deny", obj["behavior"]!!.jsonPrimitive.content)
        assertEquals(true, obj["interrupt"]!!.jsonPrimitive.content.toBoolean())
    }

    @Test
    fun `Copilot allow with modifiedArgs round trips`() {
        val h = CopilotHarness()
        val response = h.adapter.buildPermissionAllowResponse(
            fakeRequest(Source.COPILOT),
            updatedInput = mapOf("command" to JsonPrimitive("rm -rf ./build")),
        )
        val obj = kotlinx.serialization.json.Json.parseToJsonElement(response.body).jsonObject
        assertEquals("allow", obj["behavior"]!!.jsonPrimitive.content)
        assertNotNull(obj["modifiedArgs"])
    }

    @Test
    fun `Codex advertises arg rewriting but not output redaction`() {
        val h = CodexHarness()
        assertTrue(h.capabilities.supportsArgRewriting)
        assertFalse(h.capabilities.supportsAlwaysAllowWriteThrough)
        assertFalse(h.capabilities.supportsOutputRedaction)
        assertFalse(h.capabilities.supportsDefer)
        assertTrue(h.capabilities.supportsInterruptOnDeny)
    }

    @Test
    fun `Codex post-tool-use redaction returns null sentinel`() {
        val h = CodexHarness()
        val response = h.adapter.buildPostToolUseRedactedResponse(
            kotlinx.serialization.json.buildJsonObject {
                put("stdout", JsonPrimitive("anything"))
            }
        )
        assertNull(response, "Codex's postToolUse redaction is gated on a follow-up — adapter must return null today")
    }

    @Test
    fun `Codex allow with updatedInput mirrors Claude's hookSpecificOutput shape`() {
        val h = CodexHarness()
        val response = h.adapter.buildPermissionAllowResponse(
            fakeRequest(Source.CODEX),
            updatedInput = mapOf("command" to JsonPrimitive("ls /safe")),
        )
        val obj = kotlinx.serialization.json.Json.parseToJsonElement(response.body).jsonObject
        val decision = obj["hookSpecificOutput"]!!.jsonObject["decision"]!!.jsonObject
        assertEquals("allow", decision["behavior"]!!.jsonPrimitive.content)
        assertNotNull(decision["updatedInput"])
    }

    @Test
    fun `Codex pre-tool-use allow emits no permission decision`() {
        val h = CodexHarness()
        val response = h.adapter.buildPreToolUseAllowResponse()
        val obj = kotlinx.serialization.json.Json.parseToJsonElement(response.body).jsonObject
        assertFalse(obj.containsKey("hookSpecificOutput"))
        assertFalse(response.body.contains("permissionDecision"))
    }

    @Test
    fun `Copilot always-allow falls back to plain allow`() {
        val h = CopilotHarness()
        val response = h.adapter.buildPermissionAlwaysAllowResponse(fakeRequest(Source.COPILOT), suggestions = emptyList())
        val obj = kotlinx.serialization.json.Json.parseToJsonElement(response.body).jsonObject
        // No updatedPermissions field (Copilot doesn't support write-through).
        assertNull(obj["updatedPermissions"])
        assertEquals("allow", obj["behavior"]!!.jsonPrimitive.content)
    }

    @Test
    fun `Pi advertises extension-backed deny but no rewriting or redaction`() {
        val h = PiHarness()
        assertFalse(h.capabilities.supportsArgRewriting)
        assertFalse(h.capabilities.supportsAlwaysAllowWriteThrough)
        assertFalse(h.capabilities.supportsOutputRedaction)
        assertTrue(h.capabilities.supportsInterruptOnDeny)

        val response = h.adapter.buildPermissionDenyResponse(fakeRequest(Source.PI), "blocked")
        val obj = kotlinx.serialization.json.Json.parseToJsonElement(response.body).jsonObject
        assertEquals("deny", obj["behavior"]!!.jsonPrimitive.content)
        assertEquals("blocked", obj["message"]!!.jsonPrimitive.content)
    }
}
