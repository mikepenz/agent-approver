package com.mikepenz.agentbelay.state

import co.touchlab.kermit.Logger
import com.mikepenz.agentbelay.logging.Logging
import com.mikepenz.agentbelay.model.*
import com.mikepenz.agentbelay.storage.DatabaseStorage
import com.mikepenz.agentbelay.storage.SettingsStorage
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.datetime.Clock
import kotlinx.serialization.json.JsonElement
import java.util.concurrent.ConcurrentHashMap

data class AppState(
    val pendingApprovals: List<ApprovalRequest> = emptyList(),
    val history: List<ApprovalResult> = emptyList(),
    val settings: AppSettings = AppSettings(),
    val riskResults: Map<String, RiskAnalysis> = emptyMap(),
    val preToolUseLog: List<PreToolUseEvent> = emptyList(),
    val notices: List<AppNotice> = emptyList(),
)

data class AppNotice(
    val id: String,
    val message: String,
    val detail: String? = null,
    val timestamp: kotlinx.datetime.Instant = Clock.System.now(),
)

class AppStateManager(
    private val databaseStorage: DatabaseStorage? = null,
    private val settingsStorage: SettingsStorage? = null,
    val devMode: Boolean = false,
) {
    private val logger = Logger.withTag("AppStateManager")

    private val _state = MutableStateFlow(AppState())
    val state: StateFlow<AppState> = _state.asStateFlow()

    private val pendingDeferreds = ConcurrentHashMap<String, CompletableDeferred<ApprovalResult>>()
    private val pendingUpdatedInputs = ConcurrentHashMap<String, Map<String, JsonElement>>()

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
        Logging.verbose = settings.verboseLogging
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
        // The whole resolve flow runs inside [persistLock]: snapshot →
        // claim-and-remove from pending → record updated input → DB insert
        // → deferred completion. This makes resolution idempotent under
        // concurrent callers (e.g. user clicks Approve while the auto-deny
        // countdown completes): the first thread removes the request, the
        // second sees it gone and bails, so we get exactly one DB insert
        // and one deferred completion.
        synchronized(persistLock) {
            val current = _state.value
            val request = current.pendingApprovals.find { it.id == requestId } ?: return
            // Record updated input only after we've confirmed we're the
            // winning resolver, otherwise a losing concurrent caller would
            // leave a stale entry that getAndClearUpdatedInput could later
            // pick up.
            if (updatedInput != null) {
                pendingUpdatedInputs[requestId] = updatedInput
            }
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
            // Always complete the deferred even if the DB write fails: the
            // HTTP route on the other side is awaiting a decision, so a DB
            // outage must NOT translate into a hung request. Errors are
            // logged so they're still visible.
            val deferred = pendingDeferreds.remove(requestId)
            try {
                databaseStorage?.insert(result)
            } catch (e: Exception) {
                logger.e(e) { "Failed to persist resolved approval $requestId" }
            } finally {
                deferred?.complete(result)
            }
        }
    }

    fun getAndClearUpdatedInput(requestId: String): Map<String, JsonElement>? {
        return pendingUpdatedInputs.remove(requestId)
    }

    /**
     * Resolves a pending approval that matches an external "tool already
     * ran" signal — typically a Claude Code PostToolUse hook event arriving
     * for a tool whose original PermissionRequest is still parked because
     * the harness never closed the connection (a known canUseTool race in
     * claude-code).
     *
     * Matches by `(sessionId, toolName, toolInput)`. The PermissionRequest
     * payload has no `tool_use_id` (anthropics/claude-code#13938), so this
     * triple is the strongest correlation key available; collisions only
     * happen when the same tool is invoked with byte-identical inputs in
     * the same session, which is rare in practice.
     *
     * Returns true if a pending entry was found and resolved.
     */
    fun resolveByCorrelationKey(
        sessionId: String,
        toolName: String,
        toolInput: Map<String, JsonElement>,
    ): Boolean {
        val match = _state.value.pendingApprovals.firstOrNull { req ->
            req.hookInput.sessionId == sessionId &&
                req.hookInput.toolName == toolName &&
                req.hookInput.toolInput == toolInput
        } ?: return false
        resolve(
            requestId = match.id,
            decision = Decision.RESOLVED_EXTERNALLY,
            feedback = "Resolved externally (PostToolUse received — tool ran)",
            riskAnalysis = null,
            rawResponseJson = null,
        )
        return true
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

    /**
     * Attaches PostToolUse [RedactionHit]s to the existing history entry
     * matched by ([sessionId], [toolName], [toolInput]). Used when the
     * post-tool redaction pass fires after the original PermissionRequest
     * has already been persisted. Returns true if a row was found and
     * updated, false otherwise (in which case the caller should not
     * fail — redaction is best-effort observability when no history row
     * exists, e.g. fully auto-allowed Copilot tools).
     */
    fun attachRedactionHits(
        sessionId: String,
        toolName: String,
        toolInput: Map<String, JsonElement>,
        hits: List<RedactionHit>,
    ): Boolean {
        if (hits.isEmpty()) return false
        synchronized(persistLock) {
            val match = _state.value.history.firstOrNull { result ->
                result.request.hookInput.sessionId == sessionId &&
                    result.request.hookInput.toolName == toolName &&
                    result.request.hookInput.toolInput == toolInput
            } ?: return false
            databaseStorage?.updateRedactionHits(match.request.id, hits)
            _state.update { current ->
                val updatedHistory = current.history.map { result ->
                    if (result.request.id == match.request.id) result.copy(redactionHits = hits) else result
                }
                current.copy(history = updatedHistory)
            }
            return true
        }
    }

    fun updateRiskResult(requestId: String, analysis: RiskAnalysis) {
        synchronized(persistLock) {
            // Drop late-arriving results for already-resolved requests to
            // avoid stale entries that resolve() will never clean up.
            if (_state.value.pendingApprovals.none { it.id == requestId }) return
            _state.update { it.copy(riskResults = it.riskResults + (requestId to analysis)) }
        }
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
            Logging.verbose = settings.verboseLogging
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

    /**
     * Resolves all pending approvals as [Decision.RESOLVED_EXTERNALLY].
     * Called during server shutdown so that waiting HTTP handlers get a
     * response instead of hanging indefinitely.
     */
    fun resolveAllPending() {
        val pending = synchronized(persistLock) { _state.value.pendingApprovals }
        for (request in pending) {
            resolve(
                requestId = request.id,
                decision = Decision.RESOLVED_EXTERNALLY,
                feedback = "Server shutting down",
                riskAnalysis = null,
                rawResponseJson = null,
            )
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

    /**
     * Append a user-visible notice and log to file. Called by [ErrorReporter]
     * whenever defensive parsing or a composable render catches an exception,
     * so users can report unexpected payload shapes instead of silently losing
     * an approval card.
     */
    fun reportError(message: String, throwable: Throwable? = null) {
        logger.e(throwable) { message }
        val notice = AppNotice(
            id = java.util.UUID.randomUUID().toString(),
            message = message,
            detail = throwable?.let { "${it::class.java.simpleName}: ${it.message}" },
        )
        _state.update { current ->
            current.copy(notices = (listOf(notice) + current.notices).take(10))
        }
    }

    fun dismissNotice(id: String) {
        _state.update { it.copy(notices = it.notices.filter { n -> n.id != id }) }
    }
}
