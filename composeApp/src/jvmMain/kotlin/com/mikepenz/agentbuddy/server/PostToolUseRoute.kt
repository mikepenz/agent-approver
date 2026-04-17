package com.mikepenz.agentbuddy.server

import co.touchlab.kermit.Logger
import com.mikepenz.agentbuddy.model.HookInput
import com.mikepenz.agentbuddy.state.AppStateManager
import io.ktor.http.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import kotlinx.serialization.json.Json

private val logger = Logger.withTag("PostToolUseRoute")

private val json = Json { ignoreUnknownKeys = true }

/**
 * Receives Claude Code `PostToolUse` hook events and uses them as a
 * secondary correlation channel: when PostToolUse fires for a tool whose
 * original PermissionRequest is still parked in [AppStateManager]
 * (because the harness never closed the HTTP connection — a known
 * canUseTool race in claude-code), we know the tool was approved
 * somewhere and ran, so we can clear the stale pending entry.
 *
 * The handler always returns 200 immediately so it never blocks tool
 * execution. The hook event itself is fire-and-forget from our side —
 * Claude Code does not consume our response body for PostToolUse.
 */
fun Route.postToolUseRoute(stateManager: AppStateManager) {
    post("/post-tool-use") {
        val rawBody = call.receiveText()
        try {
            val hookInput = json.decodeFromString<HookInput>(rawBody)
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
        } catch (e: Exception) {
            // Never block tool execution on a parse error — just log.
            logger.w(e) { "Failed to parse PostToolUse payload" }
        }
        call.respondText("{}", contentType = ContentType.Application.Json)
    }
}
