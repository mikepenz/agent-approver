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

    /**
     * Lock guarding disk writes (settings + database). The in-memory state
     * update via [MutableStateFlow.update] is already atomic, but two
     * concurrent callers can race at the disk-write step and corrupt the
     * persisted file by writing in the wrong order. Holding this monitor
     * around the persistence call serialises writes from any thread.
     */
    private val persistLock = Any()

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
        // The whole resolve flow runs inside [persistLock]: snapshot →
        // claim-and-remove from pending → DB insert → deferred completion.
        // This makes resolution idempotent under concurrent callers (e.g.
        // user clicks Approve while the auto-deny countdown completes): the
        // first thread removes the request, the second sees it gone and
        // bails, so we get exactly one DB insert and one deferred completion.
        synchronized(persistLock) {
            val current = _state.value
            val request = current.pendingApprovals.find { it.id == requestId } ?: return
            val result = ApprovalResult(
                request = request,
                decision = decision,
                feedback = feedback,
                riskAnalysis = riskAnalysis ?: current.riskResults[requestId],
                rawResponseJson = rawResponseJson,
                decidedAt = Clock.System.now(),
            )
            // Atomically claim the request before any side effects so a
            // concurrent caller can't race past the find() above.
            _state.update { snapshot ->
                val newHistory = (listOf(result) + snapshot.history).let { list ->
                    val max = snapshot.settings.maxHistoryEntries
                    if (list.size > max) list.take(max) else list
                }
                snapshot.copy(
                    pendingApprovals = snapshot.pendingApprovals.filter { it.id != requestId },
                    history = newHistory,
                    riskResults = snapshot.riskResults - requestId,
                )
            }
            databaseStorage?.insert(result)
            pendingDeferreds.remove(requestId)?.complete(result)
        }
    }

    fun getAndClearUpdatedInput(requestId: String): Map<String, JsonElement>? {
        return pendingUpdatedInputs.remove(requestId)
    }

    fun updateHistoryRawResponse(requestId: String, rawResponseJson: String) {
        synchronized(persistLock) {
            databaseStorage?.updateRawResponse(requestId, rawResponseJson)
            _state.update { current ->
                val updatedHistory = current.history.map { result ->
                    if (result.request.id == requestId) result.copy(rawResponseJson = rawResponseJson) else result
                }
                current.copy(history = updatedHistory)
            }
        }
    }

    fun updateRiskResult(requestId: String, analysis: RiskAnalysis) {
        _state.update { it.copy(riskResults = it.riskResults + (requestId to analysis)) }
    }

    fun updateSettings(settings: AppSettings) {
        // Take the lock around BOTH the in-memory update and the disk save so
        // concurrent callers land in the same order in memory and on disk.
        // Without this, thread A could update memory to A then yield, thread
        // B could update memory to B and save B to disk, then thread A could
        // resume and save A to disk — leaving disk with A (older) and memory
        // with B (newer).
        synchronized(persistLock) {
            _state.update { it.copy(settings = settings) }
            settingsStorage?.save(settings)
        }
    }

    fun addToHistory(result: ApprovalResult) {
        synchronized(persistLock) {
            _state.update { current ->
                val newHistory = (listOf(result) + current.history).let { list ->
                    val max = current.settings.maxHistoryEntries
                    if (list.size > max) list.take(max) else list
                }
                current.copy(history = newHistory)
            }
            databaseStorage?.insert(result)
        }
    }

    fun clearHistory() {
        synchronized(persistLock) {
            _state.update { it.copy(history = emptyList()) }
            databaseStorage?.clearAll()
        }
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
