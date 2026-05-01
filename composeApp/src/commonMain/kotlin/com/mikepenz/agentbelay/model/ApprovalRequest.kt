package com.mikepenz.agentbelay.model

import kotlin.time.Instant
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject

@Serializable
data class HookInput(
    @SerialName("session_id") val sessionId: String,
    @SerialName("tool_name") val toolName: String,
    @SerialName("tool_input") val toolInput: Map<String, JsonElement> = emptyMap(),
    @SerialName("permission_suggestions") val permissionSuggestions: List<PermissionSuggestion> = emptyList(),
    val cwd: String = "",
    @SerialName("hook_event_name") val hookEventName: String = "",
    @SerialName("permission_mode") val permissionMode: String = "",
    @SerialName("agent_id") val agentId: String? = null,
    @SerialName("agent_type") val agentType: String? = null,
)

@Serializable
data class ApprovalRequest(
    val id: String,
    val source: Source,
    val toolType: ToolType,
    val hookInput: HookInput,
    val timestamp: Instant,
    val rawRequestJson: String,
)

@Serializable
enum class ToolType { DEFAULT, ASK_USER_QUESTION, PLAN }

@Serializable
enum class Source { CLAUDE_CODE, COPILOT, OPENCODE, @SerialName("PI") PI, CODEX }
