package com.mikepenz.agentbuddy.server

import co.touchlab.kermit.Logger
import com.mikepenz.agentbuddy.model.*
import com.mikepenz.agentbuddy.protection.ProtectionEngine
import com.mikepenz.agentbuddy.state.AppStateManager
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

private val logger = Logger.withTag("CopilotPreToolUseRoute")

fun Route.copilotPreToolUseRoute(
    stateManager: AppStateManager,
    adapter: CopilotAdapter,
    protectionEngine: ProtectionEngine,
    onNewApproval: () -> Unit,
) {
    post("/pre-tool-use-copilot") {
        val rawBody = call.receiveText()
        val request = adapter.parse(rawBody)
        if (request == null) {
            // Don't block on parse errors — allow
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
        val primaryHit = hits.first()

        when (severity) {
            ProtectionMode.AUTO_BLOCK -> {
                val responseJson = buildCopilotDenyResponse(combinedMessage)
                logCopilotProtectionHit(stateManager, request, Decision.PROTECTION_BLOCKED, primaryHit, combinedMessage, responseJson)
                call.respondText(responseJson, contentType = ContentType.Application.Json)
            }

            ProtectionMode.ASK_AUTO_BLOCK -> {
                handleCopilotAskMode(
                    stateManager = stateManager,
                    request = request,
                    combinedMessage = combinedMessage,
                    timeoutMs = stateManager.state.value.settings.defaultTimeoutSeconds * 1000L,
                    onNewApproval = onNewApproval,
                    call = call,
                )
            }

            ProtectionMode.ASK -> {
                handleCopilotAskMode(
                    stateManager = stateManager,
                    request = request,
                    combinedMessage = combinedMessage,
                    timeoutMs = Long.MAX_VALUE,
                    onNewApproval = onNewApproval,
                    call = call,
                )
            }

            ProtectionMode.LOG_ONLY -> {
                val responseJson = buildCopilotAllowResponse()
                logCopilotProtectionHit(stateManager, request, Decision.PROTECTION_LOGGED, primaryHit, combinedMessage, responseJson)
                call.respondText(responseJson, contentType = ContentType.Application.Json)
            }

            ProtectionMode.DISABLED -> {
                call.respondText("{}", contentType = ContentType.Application.Json)
            }
        }
    }
}

private suspend fun handleCopilotAskMode(
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
            val responseJson = buildCopilotDenyResponse(combinedMessage)
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
                val responseJson = buildCopilotAllowResponse()
                stateManager.updateHistoryRawResponse(request.id, responseJson)
                try {
                    call.respondText(responseJson, contentType = ContentType.Application.Json)
                } catch (_: Exception) {
                    logger.w { "Failed to send response for ${request.id} — connection already closed" }
                }
            }

            Decision.DENIED, Decision.AUTO_DENIED, Decision.TIMEOUT,
            Decision.PROTECTION_BLOCKED -> {
                val responseJson = buildCopilotDenyResponse(result.feedback?.takeIf { it.isNotBlank() } ?: combinedMessage)
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
                val responseJson = buildCopilotAllowResponse()
                stateManager.updateHistoryRawResponse(request.id, responseJson)
                try {
                    call.respondText(responseJson, contentType = ContentType.Application.Json)
                } catch (_: Exception) {
                    logger.w { "Failed to send response for ${request.id} — connection already closed" }
                }
            }
        }
    } catch (_: CancellationException) {
        // Client disconnected (HttpRequestLifecycle propagates Netty
        // channelInactive into call.coroutineContext) or server shutting
        // down. The harness either decided in its own TUI, timed out, or
        // exited. Swallow the cancellation here: rethrowing would propagate
        // into Ktor's shared pipeline parent scope and cause every
        // subsequent call to be cancelled on arrival.
        //
        // resolve() is idempotent: if a concurrent caller already
        // completed the deferred, resolve() sees the request gone from
        // pending and returns immediately.
        logger.i { "Connection closed for Copilot pre-tool-use request ${request.id} — resolving externally" }
        stateManager.resolve(
            requestId = request.id,
            decision = Decision.RESOLVED_EXTERNALLY,
            feedback = "Resolved externally (decided in harness or harness exited)",
            riskAnalysis = null,
            rawResponseJson = null,
        )
    }
}

// Response shape for Copilot CLI's preToolUse hook, per the official
// docs.github.com "Hooks configuration" reference page:
//
//     {"permissionDecision": "allow|deny|ask",
//      "permissionDecisionReason": "..."}
//
// This is **distinct** from the permissionRequest hook output schema
// (`{behavior, message, interrupt}` — see CopilotRoute.kt). The two hook
// events share the same protocol family but use different field names.
//
// The docs note "(only 'deny' is currently processed)" for preToolUse — so
// returning "allow" is a no-op and the user will still be prompted at the
// permissionRequest stage. That's fine for the protection-engine route: we
// only need it to block (deny) for hits and pass through (allow) otherwise,
// and the actual user-facing approval is handled by CopilotRoute.

private fun buildCopilotAllowResponse(): String = buildJsonObject {
    put("permissionDecision", "allow")
}.toString()

private fun buildCopilotDenyResponse(reason: String): String = buildJsonObject {
    put("permissionDecision", "deny")
    put("permissionDecisionReason", reason)
}.toString()

private fun logCopilotProtectionHit(
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
