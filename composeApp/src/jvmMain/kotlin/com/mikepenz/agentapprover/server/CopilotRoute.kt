package com.mikepenz.agentapprover.server

import co.touchlab.kermit.Logger
import com.mikepenz.agentapprover.model.Decision
import com.mikepenz.agentapprover.state.AppStateManager
import io.ktor.http.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlin.coroutines.cancellation.CancellationException

private val logger = Logger.withTag("CopilotRoute")

/** Tools that are auto-allowed without showing an approval card. */
private val COPILOT_AUTO_ALLOW_TOOLS = setOf("report_intent")

fun Route.copilotApprovalRoute(
    stateManager: AppStateManager,
    adapter: CopilotAdapter,
    onNewApproval: () -> Unit,
) {
    post("/approve-copilot") {
        val rawBody = call.receiveText()
        val request = adapter.parse(rawBody)
        if (request == null) {
            call.respondText("Invalid request", status = HttpStatusCode.BadRequest)
            return@post
        }

        // Auto-allow non-actionable tools
        if (request.hookInput.toolName in COPILOT_AUTO_ALLOW_TOOLS) {
            call.respondText(
                copilotAllowResponse().toString(),
                contentType = ContentType.Application.Json,
            )
            return@post
        }

        val deferred = CompletableDeferred<com.mikepenz.agentapprover.model.ApprovalResult>()
        stateManager.addPending(request, deferred)
        onNewApproval()

        val settings = stateManager.state.value.settings
        val hasInfiniteTimeout = settings.awayMode
        val timeoutMs = if (hasInfiniteTimeout) Long.MAX_VALUE else settings.defaultTimeoutSeconds * 1000L

        try {
            val result = if (hasInfiniteTimeout) {
                deferred.await()
            } else {
                withTimeoutOrNull(timeoutMs) { deferred.await() }
            }

            if (result == null) {
                val responseJson = copilotDenyResponse("Request timed out").toString()
                stateManager.resolve(
                    requestId = request.id,
                    decision = Decision.TIMEOUT,
                    feedback = "Request timed out",
                    riskAnalysis = null,
                    rawResponseJson = responseJson,
                )
                call.respondText(responseJson, contentType = ContentType.Application.Json)
                return@post
            }

            val responseJson = when (result.decision) {
                Decision.APPROVED, Decision.AUTO_APPROVED, Decision.ALWAYS_ALLOWED ->
                    copilotAllowResponse().toString()
                Decision.DENIED, Decision.AUTO_DENIED, Decision.TIMEOUT ->
                    copilotDenyResponse(result.feedback ?: "Request denied").toString()
                Decision.CANCELLED_BY_CLIENT, Decision.RESOLVED_EXTERNALLY -> null
                Decision.PROTECTION_BLOCKED ->
                    copilotDenyResponse(result.feedback ?: "Blocked by protection rule").toString()
                Decision.PROTECTION_LOGGED, Decision.PROTECTION_OVERRIDDEN ->
                    copilotAllowResponse().toString()
            }

            if (responseJson != null) {
                stateManager.updateHistoryRawResponse(request.id, responseJson)
                try {
                    call.respondText(responseJson, contentType = ContentType.Application.Json)
                } catch (_: Exception) {
                    logger.w { "Failed to send response for ${request.id} — connection already closed" }
                }
            }
        } catch (_: CancellationException) {
            if (!deferred.isCompleted) {
                logger.i { "Connection closed for Copilot request ${request.id} — resolved externally" }
                stateManager.resolve(
                    requestId = request.id,
                    decision = Decision.RESOLVED_EXTERNALLY,
                    feedback = "Resolved externally",
                    riskAnalysis = null,
                    rawResponseJson = null,
                )
                deferred.cancel()
            }
        }
    }
}

private fun copilotAllowResponse() = buildJsonObject {
    put("permissionDecision", "allow")
}

private fun copilotDenyResponse(reason: String) = buildJsonObject {
    put("permissionDecision", "deny")
    put("permissionDecisionReason", reason)
}
