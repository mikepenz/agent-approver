package com.mikepenz.agentbelay.server

import co.touchlab.kermit.Logger
import com.mikepenz.agentbelay.model.Decision
import com.mikepenz.agentbelay.state.AppStateManager
import io.ktor.http.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.withTimeoutOrNull
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
        val request = adapter.parsePermissionRequest(rawBody)
        if (request == null) {
            call.respondText("Invalid request", status = HttpStatusCode.BadRequest)
            return@post
        }

        // Auto-allow non-actionable tools
        if (request.hookInput.toolName in COPILOT_AUTO_ALLOW_TOOLS) {
            call.respondText(
                adapter.buildPermissionAllowResponse(request, updatedInput = null),
                contentType = ContentType.Application.Json,
            )
            return@post
        }

        val deferred = CompletableDeferred<com.mikepenz.agentbelay.model.ApprovalResult>()
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
                val responseJson = adapter.buildPermissionDenyResponse(request, "Request timed out")
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
                    adapter.buildPermissionAllowResponse(request, updatedInput = null)
                Decision.DENIED, Decision.AUTO_DENIED, Decision.TIMEOUT ->
                    adapter.buildPermissionDenyResponse(request, result.feedback ?: "Request denied")
                Decision.CANCELLED_BY_CLIENT, Decision.RESOLVED_EXTERNALLY -> null
                Decision.PROTECTION_BLOCKED ->
                    adapter.buildPermissionDenyResponse(request, result.feedback ?: "Blocked by protection rule")
                Decision.PROTECTION_LOGGED, Decision.PROTECTION_OVERRIDDEN ->
                    adapter.buildPermissionAllowResponse(request, updatedInput = null)
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
            // Client disconnected (HttpRequestLifecycle propagates Netty
            // channelInactive into call.coroutineContext) or server shutting
            // down. The harness either decided in its own TUI, timed out, or
            // exited. Swallow the cancellation here: rethrowing would
            // propagate into Ktor's shared pipeline parent scope and cause
            // every subsequent call to be cancelled on arrival.
            //
            // resolve() is idempotent: if a concurrent caller already
            // completed the deferred, resolve() sees the request gone from
            // pending and returns immediately.
            logger.i { "Connection closed for Copilot request ${request.id} — resolving externally" }
            stateManager.resolve(
                requestId = request.id,
                decision = Decision.RESOLVED_EXTERNALLY,
                feedback = "Resolved externally (decided in harness or harness exited)",
                riskAnalysis = null,
                rawResponseJson = null,
            )
        }
    }
}

