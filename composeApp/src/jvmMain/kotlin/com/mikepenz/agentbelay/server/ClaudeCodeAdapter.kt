package com.mikepenz.agentbelay.server

import co.touchlab.kermit.Logger
import com.mikepenz.agentbelay.harness.HarnessAdapter
import com.mikepenz.agentbelay.model.ApprovalRequest
import com.mikepenz.agentbelay.model.HookInput
import com.mikepenz.agentbelay.model.PermissionSuggestion
import com.mikepenz.agentbelay.model.Source
import com.mikepenz.agentbelay.model.ToolType
import kotlinx.datetime.Clock
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.encodeToJsonElement
import kotlinx.serialization.json.put
import java.util.UUID

/**
 * Claude Code envelope: requests deserialise via [HookInput]'s
 * canonical snake_case schema; responses wrap under
 * `hookSpecificOutput.decision` (PermissionRequest) or
 * `hookSpecificOutput.permissionDecision` (PreToolUse) per the
 * docs at https://docs.claude.com/en/docs/claude-code/hooks.
 */
class ClaudeCodeAdapter : HarnessAdapter {

    private val logger = Logger.withTag("ClaudeCodeAdapter")
    private val json = Json { ignoreUnknownKeys = true }

    override fun parsePermissionRequest(rawJson: String): ApprovalRequest? = parse(rawJson)

    override fun parsePreToolUse(rawJson: String): ApprovalRequest? = parse(rawJson)

    /**
     * Legacy entry point retained for callers that don't yet know the
     * event kind (Claude Code uses one canonical [HookInput] schema for
     * both Permission and PreToolUse, so they share parsing).
     */
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
            com.mikepenz.agentbelay.logging.ErrorReporter.report("Failed to parse Claude Code hook JSON", e)
            null
        }
    }

    override fun buildPermissionAllowResponse(
        request: ApprovalRequest,
        updatedInput: Map<String, JsonElement>?,
    ): String = buildJsonObject {
        put("hookSpecificOutput", buildJsonObject {
            put("hookEventName", "PermissionRequest")
            put("decision", buildJsonObject {
                put("behavior", "allow")
                if (updatedInput != null) {
                    put("updatedInput", JsonObject(updatedInput))
                }
            })
        })
    }.toString()

    override fun buildPermissionAlwaysAllowResponse(
        request: ApprovalRequest,
        suggestions: List<PermissionSuggestion>,
    ): String = buildJsonObject {
        put("hookSpecificOutput", buildJsonObject {
            put("hookEventName", "PermissionRequest")
            put("decision", buildJsonObject {
                put("behavior", "allow")
                put("updatedPermissions", Json.encodeToJsonElement(suggestions))
            })
        })
    }.toString()

    override fun buildPermissionDenyResponse(
        request: ApprovalRequest,
        message: String,
    ): String = buildJsonObject {
        put("hookSpecificOutput", buildJsonObject {
            put("hookEventName", "PermissionRequest")
            put("decision", buildJsonObject {
                put("behavior", "deny")
                put("message", message)
            })
        })
    }.toString()

    override fun buildPreToolUseAllowResponse(): String = buildJsonObject {
        put("hookSpecificOutput", buildJsonObject {
            put("hookEventName", "PreToolUse")
            put("permissionDecision", "allow")
        })
    }.toString()

    override fun buildPreToolUseDenyResponse(reason: String): String = buildJsonObject {
        put("hookSpecificOutput", buildJsonObject {
            put("hookEventName", "PreToolUse")
            put("permissionDecision", "deny")
            put("permissionDecisionReason", reason)
        })
    }.toString()

    override fun buildPostToolUseRedactedResponse(updatedOutput: JsonObject): String = buildJsonObject {
        put("hookSpecificOutput", buildJsonObject {
            put("hookEventName", "PostToolUse")
            put("updatedToolOutput", updatedOutput)
        })
    }.toString()
}
