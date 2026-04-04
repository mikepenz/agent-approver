package com.mikepenz.agentapprover.server

import co.touchlab.kermit.Logger
import com.mikepenz.agentapprover.model.ApprovalRequest
import com.mikepenz.agentapprover.model.HookInput
import com.mikepenz.agentapprover.model.Source
import com.mikepenz.agentapprover.model.ToolType
import kotlinx.datetime.Instant
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import java.util.UUID

@Serializable
private data class CopilotHookInput(
    val toolName: String = "",
    val toolArgs: String = "{}",
    val timestamp: Long = 0,
    val cwd: String = "",
)

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

class CopilotAdapter {

    private val logger = Logger.withTag("CopilotAdapter")
    private val json = Json { ignoreUnknownKeys = true }

    fun parse(rawJson: String): ApprovalRequest? {
        return try {
            val input = json.decodeFromString<CopilotHookInput>(rawJson)

            if (input.toolName.isBlank()) {
                logger.w { "Missing or blank toolName" }
                return null
            }

            val canonicalToolName = TOOL_NAME_MAP[input.toolName] ?: input.toolName

            val toolInput: Map<String, JsonElement> = try {
                json.decodeFromString(input.toolArgs)
            } catch (_: Exception) {
                emptyMap()
            }

            val normalizedInput = normalizeToolInput(canonicalToolName, toolInput)

            val hookInput = HookInput(
                sessionId = UUID.randomUUID().toString(),
                toolName = canonicalToolName,
                toolInput = normalizedInput,
                cwd = input.cwd,
                hookEventName = "preToolUse",
            )

            val timestamp = if (input.timestamp > 0) {
                Instant.fromEpochMilliseconds(input.timestamp)
            } else {
                kotlinx.datetime.Clock.System.now()
            }

            ApprovalRequest(
                id = UUID.randomUUID().toString(),
                source = Source.COPILOT,
                toolType = ToolType.DEFAULT,
                hookInput = hookInput,
                timestamp = timestamp,
                rawRequestJson = rawJson,
            )
        } catch (e: Exception) {
            logger.w(e) { "Failed to parse Copilot hook JSON" }
            null
        }
    }
}
