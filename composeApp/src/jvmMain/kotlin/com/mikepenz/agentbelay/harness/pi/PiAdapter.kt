package com.mikepenz.agentbelay.harness.pi

import co.touchlab.kermit.Logger
import com.mikepenz.agentbelay.harness.HarnessAdapter
import com.mikepenz.agentbelay.harness.HarnessResponse
import com.mikepenz.agentbelay.model.ApprovalRequest
import com.mikepenz.agentbelay.model.HookInput
import com.mikepenz.agentbelay.model.PermissionSuggestion
import com.mikepenz.agentbelay.model.Source
import com.mikepenz.agentbelay.model.ToolType
import kotlin.time.Clock
import kotlin.time.Instant
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

private val PI_TOOL_NAME_MAP = mapOf(
    "bash" to "Bash",
    "read" to "Read",
    "write" to "Write",
    "edit" to "Edit",
    "glob" to "Glob",
    "grep" to "Grep",
    "fetch" to "WebFetch",
    "web_fetch" to "WebFetch",
)

/**
 * Parses payloads emitted by the generated Pi extension and returns the
 * simple `{ behavior, message }` envelope that extension consumes.
 */
class PiAdapter : HarnessAdapter {

    private val logger = Logger.withTag("PiAdapter")
    private val json = Json { ignoreUnknownKeys = true }

    override fun parsePermissionRequest(rawJson: String): ApprovalRequest? = parse(rawJson)
    override fun parsePreToolUse(rawJson: String): ApprovalRequest? = parse(rawJson)

    fun parse(rawJson: String): ApprovalRequest? {
        return try {
            val obj = json.parseToJsonElement(rawJson).jsonObject
            val rawToolName = stringField(obj, "toolName", "tool_name")
            if (rawToolName.isNullOrBlank()) {
                logger.w { "Missing or blank toolName in Pi payload" }
                return null
            }

            val hookInput = HookInput(
                sessionId = stringField(obj, "sessionId", "session_id") ?: UUID.randomUUID().toString(),
                toolName = PI_TOOL_NAME_MAP[rawToolName] ?: rawToolName,
                toolInput = extractToolInput(obj),
                cwd = stringField(obj, "cwd") ?: "",
                hookEventName = "PermissionRequest",
            )

            ApprovalRequest(
                id = UUID.randomUUID().toString(),
                source = Source.PI,
                toolType = ToolType.DEFAULT,
                hookInput = hookInput,
                timestamp = extractTimestamp(obj),
                rawRequestJson = rawJson,
            )
        } catch (e: Exception) {
            com.mikepenz.agentbelay.logging.ErrorReporter.report("Failed to parse Pi hook JSON", e)
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
                // Fall through to now.
            }
        }
        return Clock.System.now()
    }

    override fun buildPermissionAllowResponse(
        request: ApprovalRequest,
        updatedInput: Map<String, JsonElement>?,
    ): HarnessResponse = HarnessResponse(buildJsonObject {
        put("behavior", "allow")
    }.toString())

    override fun buildPermissionAlwaysAllowResponse(
        request: ApprovalRequest,
        suggestions: List<PermissionSuggestion>,
    ): HarnessResponse = buildPermissionAllowResponse(request, updatedInput = null)

    override fun buildPermissionDenyResponse(
        request: ApprovalRequest,
        message: String,
    ): HarnessResponse = HarnessResponse(buildJsonObject {
        put("behavior", "deny")
        put("message", message)
    }.toString())

    override fun buildPreToolUseAllowResponse(): HarnessResponse = HarnessResponse(buildJsonObject {
        put("behavior", "allow")
    }.toString())

    override fun buildPreToolUseDenyResponse(reason: String): HarnessResponse = HarnessResponse(buildJsonObject {
        put("behavior", "deny")
        put("message", reason)
    }.toString())

    override fun buildPostToolUseRedactedResponse(updatedOutput: JsonObject): HarnessResponse? = null
}
