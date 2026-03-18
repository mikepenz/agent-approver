package com.mikepenz.agentapprover.server

import co.touchlab.kermit.Logger
import com.mikepenz.agentapprover.model.Decision
import com.mikepenz.agentapprover.model.ToolType
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

        val deferred = CompletableDeferred<com.mikepenz.agentapprover.model.ApprovalResult>()
        stateManager.addPending(request, deferred)
        onNewApproval()

        val settings = stateManager.state.value.settings
        val timeoutMs = when (request.toolType) {
            ToolType.PLAN, ToolType.ASK_USER_QUESTION -> Long.MAX_VALUE
            else -> settings.defaultTimeoutSeconds * 1000L
        }

        try {
            val result = withTimeoutOrNull(timeoutMs) { deferred.await() }

            if (result == null) {
                // Timeout
                stateManager.resolve(
                    requestId = request.id,
                    decision = Decision.TIMEOUT,
                    feedback = "Request timed out",
                    riskAnalysis = null,
                    rawResponseJson = null,
                )
                call.respondText(
                    buildDenyResponse("Request timed out").toString(),
                    contentType = ContentType.Application.Json,
                )
                return@post
            }

            when (result.decision) {
                Decision.APPROVED, Decision.AUTO_APPROVED -> {
                    call.respondText(
                        buildAllowResponse().toString(),
                        contentType = ContentType.Application.Json,
                    )
                }

                Decision.DENIED, Decision.AUTO_DENIED, Decision.TIMEOUT -> {
                    call.respondText(
                        buildDenyResponse(result.feedback ?: "Request denied").toString(),
                        contentType = ContentType.Application.Json,
                    )
                }

                Decision.CANCELLED_BY_CLIENT -> {
                    // Do not respond
                }
            }
        } catch (_: CancellationException) {
            // Client disconnected
            stateManager.resolve(
                requestId = request.id,
                decision = Decision.CANCELLED_BY_CLIENT,
                feedback = null,
                riskAnalysis = null,
                rawResponseJson = null,
            )
        }
    }
}

private fun buildAllowResponse() = buildJsonObject {
    put("hookSpecificOutput", buildJsonObject {
        put("hookEventName", "PermissionRequest")
        put("decision", buildJsonObject {
            put("behavior", "allow")
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
