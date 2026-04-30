package com.mikepenz.agentbelay.server

import co.touchlab.kermit.Logger
import com.mikepenz.agentbelay.harness.Harness
import com.mikepenz.agentbelay.harness.HookEvent
import com.mikepenz.agentbelay.model.ApprovalRequest
import com.mikepenz.agentbelay.model.ApprovalResult
import com.mikepenz.agentbelay.model.Decision
import com.mikepenz.agentbelay.model.ProtectionHit
import com.mikepenz.agentbelay.model.ProtectionMode
import com.mikepenz.agentbelay.protection.ProtectionEngine
import com.mikepenz.agentbelay.state.AppStateManager
import io.ktor.http.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.datetime.Clock
import kotlin.coroutines.cancellation.CancellationException

/**
 * Generic permission-request route that works for any [Harness]. Mounts
 * `POST <harness.transport.endpoints()[PERMISSION_REQUEST]>` and
 * suspends until the user resolves the approval (or the timeout fires,
 * or the agent disconnects).
 *
 * Per-harness behaviour is funnelled through the [Harness] interface:
 *  - The endpoint path comes from [HarnessTransport.endpoints].
 *  - Wire envelope (parse + build) comes from [HarnessAdapter].
 *  - Auto-allow short-circuits via [Harness.autoAllowTools].
 *  - Indefinite-wait policy (Away Mode + harness-specific tool types)
 *    via [Harness.shouldWaitIndefinitely].
 *
 * No-op when the harness's transport doesn't declare a
 * [HookEvent.PERMISSION_REQUEST] endpoint.
 */
fun Route.harnessApprovalRoute(
    harness: Harness,
    stateManager: AppStateManager,
    onNewApproval: () -> Unit,
) {
    val path = harness.transport.endpoints()[HookEvent.PERMISSION_REQUEST] ?: return
    val logger = Logger.withTag("${harness.source.name}ApprovalRoute")
    val adapter = harness.adapter

    post(path) {
        val rawBody = call.receiveText()
        val request = adapter.parsePermissionRequest(rawBody)
        if (request == null) {
            call.respondText("Invalid request", status = HttpStatusCode.BadRequest)
            return@post
        }

        // Auto-allow non-actionable tools without surfacing a UI card.
        if (request.hookInput.toolName in harness.autoAllowTools) {
            call.respondText(
                adapter.buildPermissionAllowResponse(request, updatedInput = null),
                contentType = ContentType.Application.Json,
            )
            return@post
        }

        val deferred = CompletableDeferred<ApprovalResult>()
        stateManager.addPending(request, deferred)
        onNewApproval()

        val settings = stateManager.state.value.settings
        val hasInfiniteTimeout = harness.shouldWaitIndefinitely(request, settings.awayMode)
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
                Decision.APPROVED, Decision.AUTO_APPROVED -> {
                    val updatedInput = stateManager.getAndClearUpdatedInput(request.id)
                    adapter.buildPermissionAllowResponse(request, updatedInput)
                }
                Decision.ALWAYS_ALLOWED ->
                    adapter.buildPermissionAlwaysAllowResponse(
                        request,
                        request.hookInput.permissionSuggestions,
                    )
                Decision.DENIED, Decision.AUTO_DENIED, Decision.TIMEOUT ->
                    adapter.buildPermissionDenyResponse(request, result.feedback ?: "Request denied")
                Decision.CANCELLED_BY_CLIENT, Decision.RESOLVED_EXTERNALLY -> null
                Decision.PROTECTION_BLOCKED ->
                    adapter.buildPermissionDenyResponse(
                        request,
                        result.feedback ?: "Blocked by protection rule",
                    )
                Decision.PROTECTION_LOGGED, Decision.PROTECTION_OVERRIDDEN -> {
                    val updatedInput = stateManager.getAndClearUpdatedInput(request.id)
                    adapter.buildPermissionAllowResponse(request, updatedInput)
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

/**
 * Generic pre-tool-use route. Runs the [ProtectionEngine] against the
 * incoming request and routes the outcome through one of four modes:
 *
 *  - [ProtectionMode.AUTO_BLOCK] — deny immediately, log to history.
 *  - [ProtectionMode.ASK_AUTO_BLOCK] — surface in UI with a
 *    finite timeout that auto-blocks on expiry.
 *  - [ProtectionMode.ASK] — surface in UI and wait indefinitely.
 *  - [ProtectionMode.LOG_ONLY] — allow but record the hit.
 *
 * No-op when the harness doesn't declare a [HookEvent.PRE_TOOL_USE] endpoint.
 */
fun Route.harnessPreToolUseRoute(
    harness: Harness,
    stateManager: AppStateManager,
    protectionEngine: ProtectionEngine,
    onNewApproval: () -> Unit,
) {
    val path = harness.transport.endpoints()[HookEvent.PRE_TOOL_USE] ?: return
    val logger = Logger.withTag("${harness.source.name}PreToolUseRoute")
    val adapter = harness.adapter

    post(path) {
        val rawBody = call.receiveText()
        val request = adapter.parsePreToolUse(rawBody)
        if (request == null) {
            // Don't block on parse errors — allow.
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
                val responseJson = adapter.buildPreToolUseDenyResponse(combinedMessage)
                logProtectionHit(stateManager, request, Decision.PROTECTION_BLOCKED, primaryHit, combinedMessage, responseJson)
                call.respondText(responseJson, contentType = ContentType.Application.Json)
            }

            ProtectionMode.ASK_AUTO_BLOCK -> {
                handleProtectionAskMode(
                    harness = harness,
                    stateManager = stateManager,
                    request = request,
                    combinedMessage = combinedMessage,
                    timeoutMs = stateManager.state.value.settings.defaultTimeoutSeconds * 1000L,
                    onNewApproval = onNewApproval,
                    call = call,
                    logger = logger,
                )
            }

            ProtectionMode.ASK -> {
                handleProtectionAskMode(
                    harness = harness,
                    stateManager = stateManager,
                    request = request,
                    combinedMessage = combinedMessage,
                    timeoutMs = Long.MAX_VALUE,
                    onNewApproval = onNewApproval,
                    call = call,
                    logger = logger,
                )
            }

            ProtectionMode.LOG_ONLY -> {
                val responseJson = adapter.buildPreToolUseAllowResponse()
                logProtectionHit(stateManager, request, Decision.PROTECTION_LOGGED, primaryHit, combinedMessage, responseJson)
                call.respondText(responseJson, contentType = ContentType.Application.Json)
            }

            ProtectionMode.DISABLED -> {
                // Should not happen — engine filters DISABLED rules out before they reach here.
                call.respondText("{}", contentType = ContentType.Application.Json)
            }
        }
    }
}

private suspend fun handleProtectionAskMode(
    harness: Harness,
    stateManager: AppStateManager,
    request: ApprovalRequest,
    combinedMessage: String,
    timeoutMs: Long,
    onNewApproval: () -> Unit,
    call: io.ktor.server.routing.RoutingCall,
    logger: Logger,
) {
    val adapter = harness.adapter
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
            val responseJson = adapter.buildPreToolUseDenyResponse(combinedMessage)
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

        val responseJson = when (result.decision) {
            Decision.APPROVED, Decision.AUTO_APPROVED, Decision.ALWAYS_ALLOWED,
            Decision.PROTECTION_OVERRIDDEN, Decision.PROTECTION_LOGGED ->
                adapter.buildPreToolUseAllowResponse()

            Decision.DENIED, Decision.AUTO_DENIED, Decision.TIMEOUT,
            Decision.PROTECTION_BLOCKED ->
                adapter.buildPreToolUseDenyResponse(
                    result.feedback?.takeIf { it.isNotBlank() } ?: combinedMessage,
                )

            Decision.CANCELLED_BY_CLIENT, Decision.RESOLVED_EXTERNALLY -> null
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
        logger.i { "Connection closed for pre-tool-use request ${request.id} — resolving externally" }
        stateManager.resolve(
            requestId = request.id,
            decision = Decision.RESOLVED_EXTERNALLY,
            feedback = "Resolved externally (decided in harness or harness exited)",
            riskAnalysis = null,
            rawResponseJson = null,
        )
    }
}

private fun logProtectionHit(
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
