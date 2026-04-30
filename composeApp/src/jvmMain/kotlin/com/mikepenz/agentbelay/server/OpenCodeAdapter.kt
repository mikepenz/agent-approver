package com.mikepenz.agentbelay.server

import co.touchlab.kermit.Logger
import com.mikepenz.agentbelay.model.ApprovalRequest
import com.mikepenz.agentbelay.model.HookInput
import com.mikepenz.agentbelay.model.Source
import com.mikepenz.agentbelay.model.ToolType
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

/**
 * Tool name mapping from OpenCode's native tool names to the canonical
 * display names used in Agent Buddy's UI. OpenCode uses a mix of lowercase
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
 * Parses OpenCode plugin payloads into the canonical [ApprovalRequest] model.
 *
 * The Agent Buddy plugin for OpenCode sends a JSON payload shaped as:
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
 * This adapter is intentionally lenient — missing fields produce sensible
 * defaults rather than parse failures, because the plugin we control can
 * evolve independently of the server.
 */
class OpenCodeAdapter {

    private val logger = Logger.withTag("OpenCodeAdapter")
    private val json = Json { ignoreUnknownKeys = true }

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
}
