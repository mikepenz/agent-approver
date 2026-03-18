package com.mikepenz.agentapprover.state

import com.mikepenz.agentapprover.model.*
import com.mikepenz.agentapprover.storage.HistoryStorage
import com.mikepenz.agentapprover.storage.SettingsStorage
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.datetime.Clock

data class AppState(
    val pendingApprovals: List<ApprovalRequest> = emptyList(),
    val history: List<ApprovalResult> = emptyList(),
    val settings: AppSettings = AppSettings(),
    val riskResults: Map<String, RiskAnalysis> = emptyMap(),
)

class AppStateManager(
    private val historyStorage: HistoryStorage? = null,
    private val settingsStorage: SettingsStorage? = null,
) {
    private val _state = MutableStateFlow(AppState())
    val state: StateFlow<AppState> = _state.asStateFlow()

    private val pendingDeferreds = mutableMapOf<String, CompletableDeferred<ApprovalResult>>()

    fun initialize() {
        val settings = settingsStorage?.load() ?: AppSettings()
        val history = historyStorage?.load() ?: emptyList()
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
    ) {
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
            val newHistory = listOf(result) + current.history
            historyStorage?.save(newHistory)
            pendingDeferreds.remove(requestId)?.complete(result)
            current.copy(
                pendingApprovals = current.pendingApprovals.filter { it.id != requestId },
                history = newHistory,
                riskResults = current.riskResults - requestId,
            )
        }
    }

    fun updateRiskResult(requestId: String, analysis: RiskAnalysis) {
        _state.update { it.copy(riskResults = it.riskResults + (requestId to analysis)) }
    }

    fun updateSettings(settings: AppSettings) {
        _state.update { it.copy(settings = settings) }
        settingsStorage?.save(settings)
    }

    fun clearHistory() {
        _state.update { it.copy(history = emptyList()) }
        historyStorage?.save(emptyList())
    }
}
