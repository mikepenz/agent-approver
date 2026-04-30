package com.mikepenz.agentbelay.server

import co.touchlab.kermit.Logger
import com.mikepenz.agentbelay.harness.HarnessAdapter
import com.mikepenz.agentbelay.model.ApprovalRequest
import com.mikepenz.agentbelay.model.HookInput
import com.mikepenz.agentbelay.model.PermissionSuggestion
import com.mikepenz.agentbelay.model.Source
import com.mikepenz.agentbelay.model.ToolType
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.longOrNull
import kotlinx.serialization.json.put
import java.util.UUID

/**
 * Tool name mapping from OpenCode's native tool names to the canonical
 * display names used in Agent Belay's UI. OpenCode uses a mix of lowercase
 * tool names similar to Claude Code / Copilot.
 */
private val TOOL_NAME_MAP = mapOf(
    "read" to "Read",
    "edit" to "Edit",
    "write" to "Write",
    "bash" to "Bash",
    "glob" to "Glob",
    "grep" to "Grep",
    "fetch" to "WebFetch",
    "web_fetch" to "WebFetch",
    "create" to "Write",
    "delete" to "Delete",
    "list" to "LS",
    "view" to "Read",
    "run" to "Bash",
)

/**
 * Parses OpenCode plugin payloads into the canonical [ApprovalRequest] model
 * and builds the simple `{ behavior, message }` envelope back. The plugin
 * shipped by [com.mikepenz.agentbelay.hook.OpenCodeBridgeInstaller] is the
 * sole producer of these payloads, so the schema is whatever we decide to
 * emit there:
 *
 * ```json
 * {
 *   "toolName": "edit",
 *   "toolInput": { "filePath": "/path/to/file", ... },
 *   "cwd": "/working/dir",
 *   "sessionId": "abc-123",
 *   "timestamp": 1714500000000
 * }
 * ```
 *
 * Since we control both ends, parsing is intentionally lenient — missing
 * fields produce sensible defaults rather than errors.
 */
class OpenCodeAdapter : HarnessAdapter {

    private val logger = Logger.withTag("OpenCodeAdapter")
    private val json = Json { ignoreUnknownKeys = true }

    override fun parsePermissionRequest(rawJson: String): ApprovalRequest? = parse(rawJson)

    override fun parsePreToolUse(rawJson: String): ApprovalRequest? = parse(rawJson)

    fun parse(rawJson: String): ApprovalRequest? {
        return try {
            val obj = json.parseToJsonElement(rawJson).jsonObject

            val rawToolName = stringField(obj, "toolName", "tool_name")
            if (rawToolName.isNullOrBlank()) {
                logger.w { "Missing or blank toolName in OpenCode payload" }
                return null
            }

            val canonicalToolName = TOOL_NAME_MAP[rawToolName] ?: rawToolName
            val toolInput = extractToolInput(obj)

            val sessionId = stringField(obj, "sessionId", "session_id")
                ?: UUID.randomUUID().toString()

            val cwd = stringField(obj, "cwd") ?: ""

            val hookInput = HookInput(
                sessionId = sessionId,
                toolName = canonicalToolName,
                toolInput = toolInput,
                cwd = cwd,
                hookEventName = "PermissionRequest",
            )

            ApprovalRequest(
                id = UUID.randomUUID().toString(),
                source = Source.OPENCODE,
                toolType = ToolType.DEFAULT,
                hookInput = hookInput,
                timestamp = extractTimestamp(obj),
                rawRequestJson = rawJson,
            )
        } catch (e: Exception) {
            com.mikepenz.agentbelay.logging.ErrorReporter.report("Failed to parse OpenCode hook JSON", e)
            null
        }
    }

    private fun stringField(obj: JsonObject, vararg keys: String): String? {
        for (key in keys) {
            val element = obj[key] as? JsonPrimitive ?: continue
            val value = element.contentOrNull ?: continue
            if (value.isNotEmpty()) return value
        }
        return null
    }

    private fun extractToolInput(obj: JsonObject): Map<String, JsonElement> {
        (obj["toolInput"] as? JsonObject)?.let { return it }
        (obj["tool_input"] as? JsonObject)?.let { return it }
        return emptyMap()
    }

    private fun extractTimestamp(obj: JsonObject): Instant {
        val element = obj["timestamp"] as? JsonPrimitive ?: return Clock.System.now()
        element.longOrNull?.let { if (it > 0) return Instant.fromEpochMilliseconds(it) }
        val text = element.contentOrNull
        if (!text.isNullOrBlank()) {
            try {
                return Instant.parse(text)
            } catch (_: Exception) {
                // Fall through to "now"
            }
        }
        return Clock.System.now()
    }

    // Response shape for our OpenCode plugin: a flat
    // `{ "behavior": "allow" | "deny", "message"?: string }` object — same
    // envelope is reused for permissionRequest and preToolUse since the
    // plugin only inspects `behavior` to decide whether to throw.

    override fun buildPermissionAllowResponse(
        request: ApprovalRequest,
        updatedInput: Map<String, JsonElement>?,
    ): String = buildJsonObject {
        put("behavior", "allow")
    }.toString()

    override fun buildPermissionAlwaysAllowResponse(
        request: ApprovalRequest,
        suggestions: List<PermissionSuggestion>,
    ): String {
        // OpenCode does not support write-through permission persistence
        // via the plugin envelope; collapse to a plain allow.
        return buildPermissionAllowResponse(request, updatedInput = null)
    }

    override fun buildPermissionDenyResponse(
        request: ApprovalRequest,
        message: String,
    ): String = buildJsonObject {
        put("behavior", "deny")
        put("message", message)
    }.toString()

    override fun buildPreToolUseAllowResponse(): String = buildJsonObject {
        put("behavior", "allow")
    }.toString()

    override fun buildPreToolUseDenyResponse(reason: String): String = buildJsonObject {
        put("behavior", "deny")
        put("message", reason)
    }.toString()

    /**
     * OpenCode's plugin pipeline doesn't expose a post-tool result-mutation
     * hook; returning null tells the route to pass the original output
     * through untouched.
     */
    override fun buildPostToolUseRedactedResponse(updatedOutput: JsonObject): String? = null
}
