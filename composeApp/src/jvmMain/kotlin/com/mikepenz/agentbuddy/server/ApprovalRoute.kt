package com.mikepenz.agentbuddy.server

import co.touchlab.kermit.Logger
import com.mikepenz.agentbuddy.model.Decision
import com.mikepenz.agentbuddy.model.PermissionSuggestion
import com.mikepenz.agentbuddy.model.ToolType
import com.mikepenz.agentbuddy.state.AppStateManager
import io.ktor.http.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.encodeToJsonElement
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlin.coroutines.cancellation.CancellationException

private val logger = Logger.withTag("ApprovalRoute")

fun Route.approvalRoute(
    stateManager: AppStateManager,
    adapter: ClaudeCodeAdapter,
    onNewApproval: () -> Unit,
) {
    post("/approve") {
        val rawBody = call.receiveText()
        val request = adapter.parse(rawBody)
        if (request == null) {
            call.respondText("Invalid request", status = HttpStatusCode.BadRequest)
            return@post
        }

        val deferred = CompletableDeferred<com.mikepenz.agentbuddy.model.ApprovalResult>()
        stateManager.addPending(request, deferred)
        onNewApproval()

        val settings = stateManager.state.value.settings
        val hasInfiniteTimeout = settings.awayMode ||
            request.toolType == ToolType.PLAN ||
            request.toolType == ToolType.ASK_USER_QUESTION
        val timeoutMs = if (hasInfiniteTimeout) Long.MAX_VALUE else settings.defaultTimeoutSeconds * 1000L

        try {
            val result = if (hasInfiniteTimeout) {
                // Infinite wait — rely on CancellationException when client disconnects
                deferred.await()
            } else {
                withTimeoutOrNull(timeoutMs) { deferred.await() }
            }

            if (result == null) {
                // Timeout (only reachable for finite-timeout requests)
                val responseJson = buildDenyResponse("Request timed out").toString()
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
                Decision.APPROVED, Decision.AUTO_APPROVED -> {
                    val updatedInput = stateManager.getAndClearUpdatedInput(request.id)
                    buildAllowResponse(updatedInput).toString()
                }
                Decision.ALWAYS_ALLOWED -> {
                    buildAlwaysAllowResponse(request.hookInput.permissionSuggestions).toString()
                }
                Decision.DENIED, Decision.AUTO_DENIED, Decision.TIMEOUT ->
                    buildDenyResponse(result.feedback ?: "Request denied").toString()
                Decision.CANCELLED_BY_CLIENT, Decision.RESOLVED_EXTERNALLY -> null
                Decision.PROTECTION_BLOCKED ->
                    buildDenyResponse(result.feedback ?: "Blocked by protection rule").toString()
                Decision.PROTECTION_LOGGED, Decision.PROTECTION_OVERRIDDEN -> {
                    val updatedInput = stateManager.getAndClearUpdatedInput(request.id)
                    buildAllowResponse(updatedInput).toString()
                }
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
            logger.i { "Connection closed for request ${request.id} — resolving externally" }
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

private fun buildAllowResponse(updatedInput: Map<String, JsonElement>? = null) = buildJsonObject {
    put("hookSpecificOutput", buildJsonObject {
        put("hookEventName", "PermissionRequest")
        put("decision", buildJsonObject {
            put("behavior", "allow")
            if (updatedInput != null) {
                put("updatedInput", JsonObject(updatedInput))
            }
        })
    })
}

private fun buildAlwaysAllowResponse(suggestions: List<PermissionSuggestion>) = buildJsonObject {
    put("hookSpecificOutput", buildJsonObject {
        put("hookEventName", "PermissionRequest")
        put("decision", buildJsonObject {
            put("behavior", "allow")
            put("updatedPermissions", Json.encodeToJsonElement(suggestions))
        })
    })
}

private fun buildDenyResponse(message: String) = buildJsonObject {
    put("hookSpecificOutput", buildJsonObject {
        put("hookEventName", "PermissionRequest")
        put("decision", buildJsonObject {
            put("behavior", "deny")
            put("message", message)
        })
    })
}
