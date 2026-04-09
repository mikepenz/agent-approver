package com.mikepenz.agentapprover.server

import co.touchlab.kermit.Logger
import com.mikepenz.agentapprover.model.ApprovalRequest
import com.mikepenz.agentapprover.model.HookInput
import com.mikepenz.agentapprover.model.Source
import com.mikepenz.agentapprover.model.ToolType
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull
import java.util.UUID

private val TOOL_NAME_MAP = mapOf(
    // Official GitHub Copilot agent tool names
    "run_terminal_cmd"       to "Bash",
    "create_file"            to "Write",
    "replace_string_in_file" to "Edit",
    "insert_edit_into_file"  to "Edit",
    "read_file"              to "Read",
    "list_dir"               to "LS",
    "file_search"            to "Glob",
    "grep_search"            to "Grep",
    "fetch"                  to "WebFetch",
    "web_fetch"              to "WebFetch",
    // Lowercase aliases (older versions / tests)
    "bash"   to "Bash",
    "edit"   to "Edit",
    "create" to "Write",
    "view"   to "Read",
)

private fun normalizeToolInput(toolName: String, input: Map<String, JsonElement>): Map<String, JsonElement> {
    return when (toolName) {
        "Write", "Edit", "Read" -> {
            if ("file_path" !in input && "path" in input) {
                input.toMutableMap().also { it["file_path"] = it.remove("path")!! }
            } else input
        }
        else -> input
    }
}

/**
 * Parses GitHub Copilot CLI hook payloads into the canonical [ApprovalRequest]
 * model. Copilot CLI delivers three distinct payload shapes depending on hook
 * event casing and version:
 *
 *  1. **camelCase `preToolUse`** (legacy / pre-v1.0.21): flat `toolName` +
 *     `toolArgs` (a STRING containing JSON), `cwd`, `timestamp` (epoch ms).
 *  2. **camelCase `permissionRequest`** (v1.0.16+): `hookName`, `sessionId`,
 *     `toolName`, `toolInput` (a JSON OBJECT, not a string), `cwd`,
 *     `timestamp`, `permissionSuggestions`.
 *  3. **PascalCase `PreToolUse` / `PermissionRequest`** (v1.0.21+): VS
 *     Code-compatible snake_case payload — `tool_name`, `tool_input`,
 *     `session_id`, `hook_event_name`, `cwd`. Identical to Claude Code's
 *     payload, which lets Agent Approver use the documented Claude response
 *     format for these events.
 *
 * The adapter parses the raw JSON into a [JsonObject] once and then walks the
 * known field names for each piece — this is more verbose than a typed
 * deserialiser but resilient to all three layouts simultaneously.
 */
class CopilotAdapter {

    private val logger = Logger.withTag("CopilotAdapter")
    private val json = Json { ignoreUnknownKeys = true }

    fun parse(rawJson: String): ApprovalRequest? {
        return try {
            val obj = json.parseToJsonElement(rawJson).jsonObject

            val rawToolName = stringField(obj, "toolName", "tool_name")
            if (rawToolName.isNullOrBlank()) {
                logger.w { "Missing or blank toolName" }
                return null
            }

            val canonicalToolName = TOOL_NAME_MAP[rawToolName] ?: rawToolName
            val toolInput = extractToolInput(obj)
            val normalizedInput = normalizeToolInput(canonicalToolName, toolInput)

            val sessionId = stringField(obj, "sessionId", "session_id")
                ?: UUID.randomUUID().toString()

            val hookEventName = stringField(obj, "hook_event_name", "hookEventName", "hookName")
                ?: "preToolUse"

            val cwd = stringField(obj, "cwd") ?: ""

            val hookInput = HookInput(
                sessionId = sessionId,
                toolName = canonicalToolName,
                toolInput = normalizedInput,
                cwd = cwd,
                hookEventName = hookEventName,
            )

            ApprovalRequest(
                id = UUID.randomUUID().toString(),
                source = Source.COPILOT,
                toolType = ToolType.DEFAULT,
                hookInput = hookInput,
                timestamp = extractTimestamp(obj),
                rawRequestJson = rawJson,
            )
        } catch (e: Exception) {
            logger.w(e) { "Failed to parse Copilot hook JSON" }
            null
        }
    }

    /**
     * Returns the first non-null string value among [keys] from [obj], or null
     * if none are present. Used to handle multiple field-name conventions
     * (camelCase / snake_case) without an explicit serializer.
     */
    private fun stringField(obj: JsonObject, vararg keys: String): String? {
        for (key in keys) {
            val element = obj[key] as? JsonPrimitive ?: continue
            val value = element.contentOrNull ?: continue
            if (value.isNotEmpty()) return value
        }
        return null
    }

    /**
     * Extracts the tool input map from one of the three known shapes:
     *  - `toolInput` as JSON object (camelCase permissionRequest, snake_case)
     *  - `tool_input` as JSON object (snake_case)
     *  - `toolArgs` as a JSON string that needs to be parsed (camelCase preToolUse)
     */
    private fun extractToolInput(obj: JsonObject): Map<String, JsonElement> {
        // Object form — preferred when present
        (obj["toolInput"] as? JsonObject)?.let { return it }
        (obj["tool_input"] as? JsonObject)?.let { return it }
        // String form — needs a second parse pass
        val argsString = (obj["toolArgs"] as? JsonPrimitive)?.contentOrNull
        if (!argsString.isNullOrBlank()) {
            return try {
                json.decodeFromString<Map<String, JsonElement>>(argsString)
            } catch (_: Exception) {
                emptyMap()
            }
        }
        return emptyMap()
    }

    /**
     * Extracts the request timestamp. The camelCase shapes carry an epoch-ms
     * `timestamp` (long); the snake_case shape uses ISO 8601 strings (which we
     * fall back to parsing best-effort, otherwise use "now").
     */
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
}
