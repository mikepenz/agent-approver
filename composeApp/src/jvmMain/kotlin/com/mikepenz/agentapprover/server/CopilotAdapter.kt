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

            val toolInput: Map<String, JsonElement> = try {
                json.decodeFromString(input.toolArgs)
            } catch (_: Exception) {
                emptyMap()
            }

            val hookInput = HookInput(
                sessionId = UUID.randomUUID().toString(),
                toolName = input.toolName,
                toolInput = toolInput,
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
