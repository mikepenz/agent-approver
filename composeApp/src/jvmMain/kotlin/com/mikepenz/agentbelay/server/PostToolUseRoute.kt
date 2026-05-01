package com.mikepenz.agentbelay.server

import co.touchlab.kermit.Logger
import com.mikepenz.agentbelay.harness.HarnessAdapter
import com.mikepenz.agentbelay.harness.HarnessResponse
import com.mikepenz.agentbelay.model.HookInput
import com.mikepenz.agentbelay.redaction.RedactionEngine
import com.mikepenz.agentbelay.state.AppStateManager
import io.ktor.http.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject

private val logger = Logger.withTag("PostToolUseRoute")

private val json = Json { ignoreUnknownKeys = true }

/**
 * Receives Claude Code `PostToolUse` hook events and serves two purposes:
 *
 *  1. **Race-condition cleanup** — when PostToolUse fires for a tool whose
 *     original PermissionRequest is still parked in [AppStateManager]
 *     (because the harness never closed the HTTP connection — a known
 *     `canUseTool` race in claude-code), we know the tool was approved
 *     somewhere and ran, so we clear the stale pending entry.
 *
 *  2. **Output redaction** — when the active harness reports
 *     `supportsOutputRedaction` and the [RedactionEngine] finds secret
 *     spans (API keys, JWTs, env-var credentials) in the tool's response,
 *     this handler returns `updatedToolOutput` so Claude Code sees the
 *     redacted version instead of the raw output.
 *
 * The handler returns `{}` for the no-op cases so it never blocks tool
 * execution. Redaction is best-effort: any error in the engine is caught
 * and the original output is passed through.
 */
fun Route.postToolUseRoute(
    stateManager: AppStateManager,
    adapter: HarnessAdapter,
    redactionEngine: RedactionEngine,
    supportsOutputRedaction: Boolean,
    path: String = "/post-tool-use",
) {
    post(path) {
        val rawBody = call.receiveText()
        var redactedResponse: HarnessResponse? = null

        try {
            val rawObj = json.parseToJsonElement(rawBody).jsonObject
            val hookInput = json.decodeFromString<HookInput>(rawBody)

            // (1) Race-condition cleanup — independent of redaction.
            if (hookInput.sessionId.isNotBlank() && hookInput.toolName.isNotBlank()) {
                val resolved = stateManager.resolveByCorrelationKey(
                    sessionId = hookInput.sessionId,
                    toolName = hookInput.toolName,
                    toolInput = hookInput.toolInput,
                )
                if (resolved) {
                    logger.i {
                        "PostToolUse cleared stale pending entry: session=${hookInput.sessionId} tool=${hookInput.toolName}"
                    }
                }
            }

            // (2) Redaction pass — only when the harness's PostToolUse can
            // honor an updatedToolOutput response (Claude Code v2.1.121+).
            if (supportsOutputRedaction) {
                val toolResponse = extractToolResponse(rawObj)
                val result = redactionEngine.scan(hookInput.toolName, toolResponse)

                if (result.hits.isNotEmpty()) {
                    val attached = stateManager.attachRedactionHits(
                        sessionId = hookInput.sessionId,
                        toolName = hookInput.toolName,
                        toolInput = hookInput.toolInput,
                        hits = result.hits,
                    )
                    if (!attached) {
                        // No history row to attach to — usually means the
                        // request was auto-allowed without surfacing in the
                        // approval flow. Still log the hit count for ops
                        // visibility; the redaction itself still applies.
                        logger.i {
                            "PostToolUse redaction recorded ${result.hits.size} hit(s) without a history row " +
                                "(session=${hookInput.sessionId} tool=${hookInput.toolName})"
                        }
                    }
                }

                if (result.redactedOutput != null) {
                    redactedResponse = adapter.buildPostToolUseRedactedResponse(result.redactedOutput)
                }
            }
        } catch (e: Exception) {
            // Never block tool execution on redaction or parse errors.
            logger.w(e) { "Failed to handle PostToolUse payload" }
        }

        if (redactedResponse != null) {
            val contentType = try {
                ContentType.parse(redactedResponse.contentType)
            } catch (_: Exception) {
                ContentType.Application.Json
            }
            call.respondText(redactedResponse.body, contentType = contentType)
        } else {
            call.respondText("{}", contentType = ContentType.Application.Json)
        }
    }
}

/**
 * Extracts the tool's response payload from a Claude Code PostToolUse
 * payload. The Claude docs name the field `tool_response`; older /
 * variant payloads may use `tool_output` so we check both.
 */
private fun extractToolResponse(payload: JsonObject): JsonElement? {
    payload["tool_response"]?.let { return it }
    payload["tool_output"]?.let { return it }
    return null
}
