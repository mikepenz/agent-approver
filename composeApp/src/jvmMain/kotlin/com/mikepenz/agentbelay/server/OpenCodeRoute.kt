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
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlin.coroutines.cancellation.CancellationException

private val logger = Logger.withTag("OpenCodeRoute")

fun Route.openCodeApprovalRoute(
    stateManager: AppStateManager,
    adapter: OpenCodeAdapter,
    onNewApproval: () -> Unit,
) {
    post("/approve-opencode") {
        val rawBody = call.receiveText()
        val request = adapter.parse(rawBody)
        if (request == null) {
            call.respondText("Invalid request", status = HttpStatusCode.BadRequest)
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
                val responseJson = openCodeDenyResponse("Request timed out").toString()
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
                    openCodeAllowResponse().toString()
                Decision.DENIED, Decision.AUTO_DENIED, Decision.TIMEOUT ->
                    openCodeDenyResponse(result.feedback ?: "Request denied").toString()
                Decision.CANCELLED_BY_CLIENT, Decision.RESOLVED_EXTERNALLY -> null
                Decision.PROTECTION_BLOCKED ->
                    openCodeDenyResponse(result.feedback ?: "Blocked by protection rule").toString()
                Decision.PROTECTION_LOGGED, Decision.PROTECTION_OVERRIDDEN ->
                    openCodeAllowResponse().toString()
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
            logger.i { "Connection closed for OpenCode request ${request.id} — resolving externally" }
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

// Response shape for our OpenCode plugin. We control both sides so we use a
// simple flat object: `{ "behavior": "allow" }` or
// `{ "behavior": "deny", "message": "..." }`.

private fun openCodeAllowResponse() = buildJsonObject {
    put("behavior", "allow")
}

private fun openCodeDenyResponse(reason: String) = buildJsonObject {
    put("behavior", "deny")
    put("message", reason)
}
