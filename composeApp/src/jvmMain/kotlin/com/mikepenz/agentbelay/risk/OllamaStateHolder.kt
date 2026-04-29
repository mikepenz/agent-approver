package com.mikepenz.agentbelay.risk

import com.mikepenz.agentbelay.di.AppScope
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Snapshot of timing metrics returned by `/api/chat`. All durations in
 * milliseconds; counts are token counts. `null` until the first call
 * completes successfully.
 */
data class OllamaMetrics(
    val totalMs: Long,
    val loadMs: Long,
    val promptEvalMs: Long,
    val evalMs: Long,
    val promptTokens: Int,
    val evalTokens: Int,
)

/**
 * App-scoped publisher for the Ollama backend's lifecycle UI state.
 *
 * Mirrors [CopilotStateHolder]: [RiskAnalyzerLifecycle] writes to it as the
 * Ollama analyzer is started/stopped, and [com.mikepenz.agentbelay.ui.settings.SettingsViewModel]
 * reads it to render the model dropdown, connection badge, error + metrics.
 */
@SingleIn(AppScope::class)
@Inject
class OllamaStateHolder {
    private val _models = MutableStateFlow<List<String>>(emptyList())
    val models: StateFlow<List<String>> = _models.asStateFlow()

    private val _initState = MutableStateFlow(OllamaInitState.IDLE)
    val initState: StateFlow<OllamaInitState> = _initState.asStateFlow()

    private val _lastError = MutableStateFlow<String?>(null)
    val lastError: StateFlow<String?> = _lastError.asStateFlow()

    private val _lastMetrics = MutableStateFlow<OllamaMetrics?>(null)
    val lastMetrics: StateFlow<OllamaMetrics?> = _lastMetrics.asStateFlow()

    private val _version = MutableStateFlow<String?>(null)
    val version: StateFlow<String?> = _version.asStateFlow()

    fun setModels(models: List<String>) {
        _models.value = models
    }

    fun setInitState(state: OllamaInitState) {
        _initState.value = state
    }

    fun setLastError(error: String?) {
        _lastError.value = error
    }

    fun setLastMetrics(metrics: OllamaMetrics?) {
        _lastMetrics.value = metrics
    }

    fun setVersion(version: String?) {
        _version.value = version
    }
}
