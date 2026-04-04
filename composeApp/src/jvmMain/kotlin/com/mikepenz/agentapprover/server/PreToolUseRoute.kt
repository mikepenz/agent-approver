package com.mikepenz.agentapprover.server

import co.touchlab.kermit.Logger
import com.mikepenz.agentapprover.model.*
import com.mikepenz.agentapprover.protection.ProtectionEngine
import com.mikepenz.agentapprover.state.AppStateManager
import com.mikepenz.agentapprover.storage.DatabaseStorage
import io.ktor.http.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.datetime.Clock
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import java.util.UUID
import kotlin.coroutines.cancellation.CancellationException

private val logger = Logger.withTag("PreToolUseRoute")

fun Route.preToolUseRoute(
    stateManager: AppStateManager,
    adapter: ClaudeCodeAdapter,
    protectionEngine: ProtectionEngine,
    databaseStorage: DatabaseStorage?,
    onNewApproval: () -> Unit,
) {
    post("/pre-tool-use") {
        val rawBody = call.receiveText()
        val request = adapter.parse(rawBody)
        if (request == null) {
            // Don't block on parse errors — allow
            call.respondText("{}", contentType = ContentType.Application.Json)
            return@post
        }

        val hits = protectionEngine.evaluate(request.hookInput)
        if (hits.isEmpty()) {
            call.respondText("{}", contentType = ContentType.Application.Json)
            return@post
        }

        val severity = protectionEngine.highestSeverity(hits)
        val combinedMessage = hits.joinToString("; ") { "[${it.moduleId}/${it.ruleId}] ${it.message}" }
        val primaryHit = hits.first()

        when (severity) {
            ProtectionMode.AUTO_BLOCK -> {
                logProtectionHit(databaseStorage, request, Decision.PROTECTION_BLOCKED, primaryHit, combinedMessage)
                val responseJson = buildJsonObject { put("error", combinedMessage) }.toString()
                call.respondText(responseJson, contentType = ContentType.Application.Json)
            }

            ProtectionMode.ASK_AUTO_BLOCK -> {
                handleAskMode(
                    stateManager = stateManager,
                    databaseStorage = databaseStorage,
                    request = request,
                    primaryHit = primaryHit,
                    combinedMessage = combinedMessage,
                    timeoutMs = stateManager.state.value.settings.defaultTimeoutSeconds * 1000L,
                    onNewApproval = onNewApproval,
                    call = call,
                )
            }

            ProtectionMode.ASK -> {
                handleAskMode(
                    stateManager = stateManager,
                    databaseStorage = databaseStorage,
                    request = request,
                    primaryHit = primaryHit,
                    combinedMessage = combinedMessage,
                    timeoutMs = Long.MAX_VALUE,
                    onNewApproval = onNewApproval,
                    call = call,
                )
            }

            ProtectionMode.LOG_ONLY -> {
                logProtectionHit(databaseStorage, request, Decision.PROTECTION_LOGGED, primaryHit, combinedMessage)
                call.respondText("{}", contentType = ContentType.Application.Json)
            }

            ProtectionMode.DISABLED -> {
                // Should not happen since engine filters these, but allow just in case
                call.respondText("{}", contentType = ContentType.Application.Json)
            }
        }
    }
}

private suspend fun handleAskMode(
    stateManager: AppStateManager,
    databaseStorage: DatabaseStorage?,
    request: ApprovalRequest,
    primaryHit: ProtectionHit,
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
            // Timeout — auto-deny
            logProtectionHit(databaseStorage, request, Decision.PROTECTION_BLOCKED, primaryHit, combinedMessage)
            val responseJson = buildJsonObject { put("error", combinedMessage) }.toString()
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
                logProtectionHit(databaseStorage, request, Decision.PROTECTION_OVERRIDDEN, primaryHit, combinedMessage)
                val responseJson = "{}"
                stateManager.updateHistoryRawResponse(request.id, responseJson)
                try {
                    call.respondText(responseJson, contentType = ContentType.Application.Json)
                } catch (_: Exception) {
                    logger.w { "Failed to send response for ${request.id} — connection already closed" }
                }
            }

            Decision.DENIED, Decision.AUTO_DENIED, Decision.TIMEOUT,
            Decision.PROTECTION_BLOCKED -> {
                logProtectionHit(databaseStorage, request, Decision.PROTECTION_BLOCKED, primaryHit, combinedMessage)
                val responseJson = buildJsonObject { put("error", result.feedback ?: combinedMessage) }.toString()
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
                logProtectionHit(databaseStorage, request, Decision.PROTECTION_LOGGED, primaryHit, combinedMessage)
                val responseJson = "{}"
                stateManager.updateHistoryRawResponse(request.id, responseJson)
                try {
                    call.respondText(responseJson, contentType = ContentType.Application.Json)
                } catch (_: Exception) {
                    logger.w { "Failed to send response for ${request.id} — connection already closed" }
                }
            }
        }
    } catch (_: CancellationException) {
        if (!deferred.isCompleted) {
            logger.i { "Connection closed for request ${request.id} — resolved externally" }
            stateManager.resolve(
                requestId = request.id,
                decision = Decision.RESOLVED_EXTERNALLY,
                feedback = "Resolved externally (approved/denied in Claude)",
                riskAnalysis = null,
                rawResponseJson = null,
            )
            deferred.cancel()
        }
    }
}

private fun logProtectionHit(
    databaseStorage: DatabaseStorage?,
    request: ApprovalRequest,
    decision: Decision,
    hit: ProtectionHit,
    message: String,
) {
    val result = ApprovalResult(
        request = request,
        decision = decision,
        feedback = message,
        riskAnalysis = null,
        rawResponseJson = null,
        decidedAt = Clock.System.now(),
        protectionModule = hit.moduleId,
        protectionRule = hit.ruleId,
        protectionDetail = message,
    )
    databaseStorage?.insert(result)
}
