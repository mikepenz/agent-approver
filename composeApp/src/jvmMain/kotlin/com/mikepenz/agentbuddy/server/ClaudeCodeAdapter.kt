package com.mikepenz.agentbuddy.server

import co.touchlab.kermit.Logger
import com.mikepenz.agentbuddy.model.ApprovalRequest
import com.mikepenz.agentbuddy.model.HookInput
import com.mikepenz.agentbuddy.model.Source
import com.mikepenz.agentbuddy.model.ToolType
import kotlinx.datetime.Clock
import kotlinx.serialization.json.Json
import java.util.UUID

class ClaudeCodeAdapter {

    private val logger = Logger.withTag("ClaudeCodeAdapter")
    private val json = Json { ignoreUnknownKeys = true }

    fun parse(rawJson: String): ApprovalRequest? {
        return try {
            val hookInput = json.decodeFromString<HookInput>(rawJson)

            if (hookInput.sessionId.isBlank()) {
                logger.w { "Missing session_id" }
                return null
            }
            if (hookInput.toolName.isBlank()) {
                logger.w { "Missing tool_name" }
                return null
            }

            val toolType = when (hookInput.toolName) {
                "AskUserQuestion" -> ToolType.ASK_USER_QUESTION
                "Plan", "ExitPlanMode" -> ToolType.PLAN
                else -> ToolType.DEFAULT
            }

            ApprovalRequest(
                id = UUID.randomUUID().toString(),
                source = Source.CLAUDE_CODE,
                toolType = toolType,
                hookInput = hookInput,
                timestamp = Clock.System.now(),
                rawRequestJson = rawJson,
            )
        } catch (e: Exception) {
            com.mikepenz.agentbuddy.logging.ErrorReporter.report("Failed to parse Claude Code hook JSON", e)
            null
        }
    }
}
