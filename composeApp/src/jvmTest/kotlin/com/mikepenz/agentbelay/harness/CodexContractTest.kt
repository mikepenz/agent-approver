package com.mikepenz.agentbelay.harness

import com.mikepenz.agentbelay.harness.codex.CodexHarness
import com.mikepenz.agentbelay.testutil.GoldenPayloads
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

/**
 * Contract-test plug-in for [CodexHarness].
 *
 * Codex's hooks crate is deliberately Claude-Code-shaped so envelopes
 * mirror Claude's `hookSpecificOutput.decision.{behavior, message,
 * updatedInput}` layout (codex-rs/hooks/).
 */
class CodexContractTest : AbstractHarnessContractTest() {
    override val harness: Harness = CodexHarness()

    override fun goldenBashPermissionRequest(): String =
        GoldenPayloads.read("codex/permission_request_bash_ls.json")

    override fun goldenEditPermissionRequest(): String =
        GoldenPayloads.read("codex/permission_request_apply_patch.json")

    override fun goldenMalformedPermissionRequest(): String =
        GoldenPayloads.read("codex/permission_request_malformed.json")

    override fun goldenPreToolUsePayload(): String =
        GoldenPayloads.read("codex/pre_tool_use_bash.json")

    override fun assertPermissionAllowEnvelope(responseJson: String) {
        val decision = decisionObject(responseJson)
        assertEquals("allow", decision["behavior"]!!.jsonPrimitive.content)
    }

    override fun assertPermissionDenyEnvelope(responseJson: String, expectedMessage: String) {
        val decision = decisionObject(responseJson)
        assertEquals("deny", decision["behavior"]!!.jsonPrimitive.content)
        assertEquals(expectedMessage, decision["message"]!!.jsonPrimitive.content)
    }

    override fun assertContainsUpdatedInput(responseJson: String, key: String, value: String) {
        val decision = decisionObject(responseJson)
        val updatedInput = decision["updatedInput"]?.jsonObject
        assertNotNull(updatedInput, "Codex allow-with-rewrite must surface updatedInput")
        assertEquals(value, updatedInput[key]!!.jsonPrimitive.content)
    }

    private fun decisionObject(responseJson: String): JsonObject {
        val root = Json.parseToJsonElement(responseJson).jsonObject
        val hso = root["hookSpecificOutput"]!!.jsonObject
        assertContains(
            listOf("PermissionRequest"),
            hso["hookEventName"]!!.jsonPrimitive.content,
        )
        return hso["decision"]!!.jsonObject
    }

    @Test
    fun `apply_patch is normalised to canonical Write tool name`() {
        val req = harness.adapter.parsePermissionRequest(
            GoldenPayloads.read("codex/permission_request_apply_patch.json"),
        )
        assertNotNull(req, "apply_patch fixture must parse")
        assertEquals(
            "Write", req.hookInput.toolName,
            "Codex's apply_patch primitive must alias to Claude-style Write so the rest of Belay sees one canonical name",
        )
    }
}
