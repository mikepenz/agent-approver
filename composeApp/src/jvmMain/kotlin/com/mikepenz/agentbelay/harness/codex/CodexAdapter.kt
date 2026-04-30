package com.mikepenz.agentbelay.harness.codex

import co.touchlab.kermit.Logger
import com.mikepenz.agentbelay.harness.HarnessAdapter
import com.mikepenz.agentbelay.harness.HarnessResponse
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
import kotlinx.serialization.json.put
import java.util.UUID

/**
 * Codex CLI envelope. The OpenAI Codex `hooks` crate was deliberately
 * Claude-Code-shaped: event names match (`PreToolUse`, `PermissionRequest`),
 * matcher group config matches, and `apply_patch` aliases `Write`/`Edit`
 * "for compatibility with hook configurations that describe edits using
 * Claude Code-style names" (codex-rs/core/src/tools/hook_names.rs).
 *
 * Net result: this adapter mirrors [com.mikepenz.agentbelay.harness.claudecode.ClaudeCodeAdapter]
 * almost exactly. Two deltas:
 *
 *  1. `apply_patch` is normalised to `Write` on parse so the rest of Belay
 *     (Protection Engine, UI, history) treats Codex edits the same as
 *     Claude's.
 *  2. Output redaction is unsupported today — Codex's PostToolUse shape
 *     varies per tool and `PostToolUseRoute` is currently Claude-only.
 *     Returns null from [buildPostToolUseRedactedResponse]; capability
 *     flag stays off until the redaction route is generalised.
 */
class CodexAdapter : HarnessAdapter {

    private val logger = Logger.withTag("CodexAdapter")
    private val json = Json { ignoreUnknownKeys = true }

    override fun parsePermissionRequest(rawJson: String): ApprovalRequest? = parse(rawJson)

    override fun parsePreToolUse(rawJson: String): ApprovalRequest? = parse(rawJson)

    private fun parse(rawJson: String): ApprovalRequest? {
        return try {
            val raw = json.decodeFromString<HookInput>(rawJson)

            if (raw.sessionId.isBlank()) {
                logger.w { "Missing session_id" }
                return null
            }
            if (raw.toolName.isBlank()) {
                logger.w { "Missing tool_name" }
                return null
            }

            val normalisedToolName = when (raw.toolName) {
                "apply_patch" -> "Write"
                else -> raw.toolName
            }
            val hookInput = if (normalisedToolName == raw.toolName) raw else raw.copy(toolName = normalisedToolName)

            val toolType = when (hookInput.toolName) {
                "AskUserQuestion" -> ToolType.ASK_USER_QUESTION
                "Plan", "ExitPlanMode" -> ToolType.PLAN
                else -> ToolType.DEFAULT
            }

            ApprovalRequest(
                id = UUID.randomUUID().toString(),
                source = Source.CODEX,
                toolType = toolType,
                hookInput = hookInput,
                timestamp = Clock.System.now(),
                rawRequestJson = rawJson,
            )
        } catch (e: Exception) {
            com.mikepenz.agentbelay.logging.ErrorReporter.report("Failed to parse Codex hook JSON", e)
            null
        }
    }

    override fun buildPermissionAllowResponse(
        request: ApprovalRequest,
        updatedInput: Map<String, JsonElement>?,
    ): HarnessResponse = HarnessResponse(buildJsonObject {
        put("hookSpecificOutput", buildJsonObject {
            put("hookEventName", "PermissionRequest")
            put("decision", buildJsonObject {
                put("behavior", "allow")
                if (updatedInput != null) {
                    put("updatedInput", JsonObject(updatedInput))
                }
            })
        })
    }.toString())

    override fun buildPermissionAlwaysAllowResponse(
        request: ApprovalRequest,
        suggestions: List<PermissionSuggestion>,
    ): HarnessResponse = buildPermissionAllowResponse(request, updatedInput = null)

    override fun buildPermissionDenyResponse(
        request: ApprovalRequest,
        message: String,
    ): HarnessResponse = HarnessResponse(buildJsonObject {
        put("hookSpecificOutput", buildJsonObject {
            put("hookEventName", "PermissionRequest")
            put("decision", buildJsonObject {
                put("behavior", "deny")
                put("message", message)
            })
        })
    }.toString())

    override fun buildPreToolUseAllowResponse(): HarnessResponse = HarnessResponse(buildJsonObject {
        put("hookSpecificOutput", buildJsonObject {
            put("hookEventName", "PreToolUse")
            put("permissionDecision", "allow")
        })
    }.toString())

    override fun buildPreToolUseDenyResponse(reason: String): HarnessResponse = HarnessResponse(buildJsonObject {
        put("hookSpecificOutput", buildJsonObject {
            put("hookEventName", "PreToolUse")
            put("permissionDecision", "deny")
            put("permissionDecisionReason", reason)
        })
    }.toString())

    override fun buildPostToolUseRedactedResponse(updatedOutput: JsonObject): HarnessResponse? = null
}
