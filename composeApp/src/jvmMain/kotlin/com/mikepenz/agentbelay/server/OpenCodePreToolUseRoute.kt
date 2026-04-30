package com.mikepenz.agentbelay.server

import co.touchlab.kermit.Logger
import com.mikepenz.agentbelay.model.*
import com.mikepenz.agentbelay.protection.ProtectionEngine
import com.mikepenz.agentbelay.state.AppStateManager
import io.ktor.http.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.datetime.Clock
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlin.coroutines.cancellation.CancellationException

private val logger = Logger.withTag("OpenCodePreToolUseRoute")

fun Route.openCodePreToolUseRoute(
    stateManager: AppStateManager,
    adapter: OpenCodeAdapter,
    protectionEngine: ProtectionEngine,
    onNewApproval: () -> Unit,
) {
    post("/pre-tool-use-opencode") {
        val rawBody = call.receiveText()
        val request = adapter.parse(rawBody)
        if (request == null) {
            call.respondText("{}", contentType = ContentType.Application.Json)
            return@post
        }

        val hits = protectionEngine.evaluate(request.hookInput)
        stateManager.addPreToolUseEvent(request, hits)
        if (hits.isEmpty()) {
            call.respondText("{}", contentType = ContentType.Application.Json)
            return@post
        }

        val severity = protectionEngine.highestSeverity(hits)
        val combinedMessage = hits.joinToString("; ") { "[${it.moduleId}/${it.ruleId}] ${it.message}" }
        val primaryHit = hits.minByOrNull { it.mode.ordinal } ?: hits.first()

        when (severity) {
            ProtectionMode.AUTO_BLOCK -> {
                val responseJson = buildOpenCodePreToolDeny(combinedMessage)
                logOpenCodeProtectionHit(stateManager, request, Decision.PROTECTION_BLOCKED, primaryHit, combinedMessage, responseJson)
                call.respondText(responseJson, contentType = ContentType.Application.Json)
            }

            ProtectionMode.ASK_AUTO_BLOCK -> {
                handleOpenCodeAskMode(
                    stateManager = stateManager,
                    request = request,
                    combinedMessage = combinedMessage,
                    timeoutMs = stateManager.state.value.settings.defaultTimeoutSeconds * 1000L,
                    onNewApproval = onNewApproval,
                    call = call,
                )
            }

            ProtectionMode.ASK -> {
                handleOpenCodeAskMode(
                    stateManager = stateManager,
                    request = request,
                    combinedMessage = combinedMessage,
                    timeoutMs = Long.MAX_VALUE,
                    onNewApproval = onNewApproval,
                    call = call,
                )
            }

            ProtectionMode.LOG_ONLY -> {
                val responseJson = buildOpenCodePreToolAllow()
                logOpenCodeProtectionHit(stateManager, request, Decision.PROTECTION_LOGGED, primaryHit, combinedMessage, responseJson)
                call.respondText(responseJson, contentType = ContentType.Application.Json)
            }

            ProtectionMode.DISABLED -> {
                call.respondText("{}", contentType = ContentType.Application.Json)
            }
        }
    }
}

private suspend fun handleOpenCodeAskMode(
    stateManager: AppStateManager,
    request: ApprovalRequest,
    combinedMessage: String,
    timeoutMs: Long,
    onNewApproval: () -> Unit,
    call: io.ktor.server.routing.RoutingCall,
) {
    val deferred = CompletableDeferred<ApprovalResult>()
    stateManager.addPending(request, deferred)
    onNewApproval()

    try {
        val result = if (timeoutMs == Long.MAX_VALUE) {
            deferred.await()
        } else {
            withTimeoutOrNull(timeoutMs) { deferred.await() }
        }

        if (result == null) {
            val responseJson = buildOpenCodePreToolDeny(combinedMessage)
            stateManager.resolve(
                requestId = request.id,
                decision = Decision.PROTECTION_BLOCKED,
                feedback = combinedMessage,
                riskAnalysis = null,
                rawResponseJson = responseJson,
            )
            call.respondText(responseJson, contentType = ContentType.Application.Json)
            return
        }

        when (result.decision) {
            Decision.APPROVED, Decision.AUTO_APPROVED, Decision.ALWAYS_ALLOWED,
            Decision.PROTECTION_OVERRIDDEN -> {
                val responseJson = buildOpenCodePreToolAllow()
                stateManager.updateHistoryRawResponse(request.id, responseJson)
                try {
                    call.respondText(responseJson, contentType = ContentType.Application.Json)
                } catch (_: Exception) {
                    logger.w { "Failed to send response for ${request.id} — connection already closed" }
                }
            }

            Decision.DENIED, Decision.AUTO_DENIED, Decision.TIMEOUT,
            Decision.PROTECTION_BLOCKED -> {
                val responseJson = buildOpenCodePreToolDeny(result.feedback?.takeIf { it.isNotBlank() } ?: combinedMessage)
                stateManager.updateHistoryRawResponse(request.id, responseJson)
                try {
                    call.respondText(responseJson, contentType = ContentType.Application.Json)
                } catch (_: Exception) {
                    logger.w { "Failed to send response for ${request.id} — connection already closed" }
                }
            }

            Decision.CANCELLED_BY_CLIENT, Decision.RESOLVED_EXTERNALLY -> {
                // No response needed
            }

            Decision.PROTECTION_LOGGED -> {
                val responseJson = buildOpenCodePreToolAllow()
                stateManager.updateHistoryRawResponse(request.id, responseJson)
                try {
                    call.respondText(responseJson, contentType = ContentType.Application.Json)
                } catch (_: Exception) {
                    logger.w { "Failed to send response for ${request.id} — connection already closed" }
                }
            }
        }
    } catch (_: CancellationException) {
        logger.i { "Connection closed for OpenCode pre-tool-use request ${request.id} — resolving externally" }
        stateManager.resolve(
            requestId = request.id,
            decision = Decision.RESOLVED_EXTERNALLY,
            feedback = "Resolved externally (decided in harness or harness exited)",
            riskAnalysis = null,
            rawResponseJson = null,
        )
    }
}

// Response uses same { behavior: allow/deny } shape as the approval route.
// The plugin checks `behavior` to decide whether to throw.

private fun buildOpenCodePreToolAllow(): String = buildJsonObject {
    put("behavior", "allow")
}.toString()

private fun buildOpenCodePreToolDeny(reason: String): String = buildJsonObject {
    put("behavior", "deny")
    put("message", reason)
}.toString()

private fun logOpenCodeProtectionHit(
    stateManager: AppStateManager,
    request: ApprovalRequest,
    decision: Decision,
    hit: ProtectionHit,
    message: String,
    rawResponseJson: String,
) {
    val result = ApprovalResult(
        request = request,
        decision = decision,
        feedback = message,
        riskAnalysis = null,
        rawResponseJson = rawResponseJson,
        decidedAt = Clock.System.now(),
        protectionModule = hit.moduleId,
        protectionRule = hit.ruleId,
        protectionDetail = message,
    )
    stateManager.addToHistory(result)
}
