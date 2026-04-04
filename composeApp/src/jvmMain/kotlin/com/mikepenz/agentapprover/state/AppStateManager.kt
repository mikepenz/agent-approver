package com.mikepenz.agentapprover.state

import com.mikepenz.agentapprover.model.*
import com.mikepenz.agentapprover.storage.DatabaseStorage
import com.mikepenz.agentapprover.storage.SettingsStorage
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.datetime.Clock
import kotlinx.serialization.json.JsonElement

data class AppState(
    val pendingApprovals: List<ApprovalRequest> = emptyList(),
    val history: List<ApprovalResult> = emptyList(),
    val settings: AppSettings = AppSettings(),
    val riskResults: Map<String, RiskAnalysis> = emptyMap(),
    val preToolUseLog: List<PreToolUseEvent> = emptyList(),
)

class AppStateManager(
    private val databaseStorage: DatabaseStorage? = null,
    private val settingsStorage: SettingsStorage? = null,
    val devMode: Boolean = false,
) {
    private val _state = MutableStateFlow(AppState())
    val state: StateFlow<AppState> = _state.asStateFlow()

    private val pendingDeferreds = mutableMapOf<String, CompletableDeferred<ApprovalResult>>()
    private val pendingUpdatedInputs = mutableMapOf<String, Map<String, JsonElement>>()

    fun initialize() {
        val settings = settingsStorage?.load() ?: AppSettings()
        val history = databaseStorage?.loadAll() ?: emptyList()
        _state.value = AppState(settings = settings, history = history)
    }

    fun addPending(request: ApprovalRequest, deferred: CompletableDeferred<ApprovalResult>? = null) {
        if (deferred != null) pendingDeferreds[request.id] = deferred
        _state.update { it.copy(pendingApprovals = listOf(request) + it.pendingApprovals) }
    }

    fun removePending(requestId: String) {
        _state.update { it.copy(pendingApprovals = it.pendingApprovals.filter { r -> r.id != requestId }) }
    }

    fun resolve(
        requestId: String,
        decision: Decision,
        feedback: String?,
        riskAnalysis: RiskAnalysis?,
        rawResponseJson: String?,
        updatedInput: Map<String, JsonElement>? = null,
    ) {
        if (updatedInput != null) {
            pendingUpdatedInputs[requestId] = updatedInput
        }
        _state.update { current ->
            val request = current.pendingApprovals.find { it.id == requestId } ?: return@update current
            val result = ApprovalResult(
                request = request,
                decision = decision,
                feedback = feedback,
                riskAnalysis = riskAnalysis ?: current.riskResults[requestId],
                rawResponseJson = rawResponseJson,
                decidedAt = Clock.System.now(),
            )
            databaseStorage?.insert(result)
            val newHistory = (listOf(result) + current.history).let { list ->
                val max = current.settings.maxHistoryEntries
                if (list.size > max) list.take(max) else list
            }
            pendingDeferreds.remove(requestId)?.complete(result)
            current.copy(
                pendingApprovals = current.pendingApprovals.filter { it.id != requestId },
                history = newHistory,
                riskResults = current.riskResults - requestId,
            )
        }
    }

    fun getAndClearUpdatedInput(requestId: String): Map<String, JsonElement>? {
        return pendingUpdatedInputs.remove(requestId)
    }

    fun updateHistoryRawResponse(requestId: String, rawResponseJson: String) {
        databaseStorage?.updateRawResponse(requestId, rawResponseJson)
        _state.update { current ->
            val updatedHistory = current.history.map { result ->
                if (result.request.id == requestId) result.copy(rawResponseJson = rawResponseJson) else result
            }
            current.copy(history = updatedHistory)
        }
    }

    fun updateRiskResult(requestId: String, analysis: RiskAnalysis) {
        _state.update { it.copy(riskResults = it.riskResults + (requestId to analysis)) }
    }

    fun updateSettings(settings: AppSettings) {
        _state.update { it.copy(settings = settings) }
        settingsStorage?.save(settings)
    }

    fun addToHistory(result: ApprovalResult) {
        databaseStorage?.insert(result)
        _state.update { current ->
            val newHistory = (listOf(result) + current.history).let { list ->
                val max = current.settings.maxHistoryEntries
                if (list.size > max) list.take(max) else list
            }
            current.copy(history = newHistory)
        }
    }

    fun clearHistory() {
        _state.update { it.copy(history = emptyList()) }
        databaseStorage?.clearAll()
    }

    fun addPreToolUseEvent(request: ApprovalRequest, hits: List<ProtectionHit>) {
        if (!devMode) return
        val event = PreToolUseEvent(
            request = request,
            hits = hits,
            conclusion = conclusionFromHits(hits),
            timestamp = request.timestamp,
        )
        _state.update { current ->
            val newLog = (listOf(event) + current.preToolUseLog).take(200)
            current.copy(preToolUseLog = newLog)
        }
    }

    fun clearPreToolUseLog() {
        _state.update { it.copy(preToolUseLog = emptyList()) }
    }
}
