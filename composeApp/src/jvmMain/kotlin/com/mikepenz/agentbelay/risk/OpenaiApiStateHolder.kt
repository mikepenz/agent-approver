package com.mikepenz.agentbelay.risk

import com.mikepenz.agentbelay.di.AppScope
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Snapshot of timing metrics returned by `/v1/chat/completions`. All durations
 * in milliseconds; counts are token counts. `null` until the first call
 * completes successfully.
 */
data class OpenaiApiMetrics(
    val totalMs: Long,
    val promptTokens: Int,
    val evalTokens: Int,
)

/**
 * App-scoped publisher for the OpenAI API (llama.cpp) backend's lifecycle UI state.
 *
 * Mirrors [OllamaStateHolder]: [RiskAnalyzerLifecycle] writes to it as the
 * analyzer is started/stopped, and SettingsViewModel reads it to render the
 * model dropdown, connection badge, error and metrics.
 */
@SingleIn(AppScope::class)
@Inject
class OpenaiApiStateHolder {
    private val _models = MutableStateFlow<List<String>>(emptyList())
    val models: StateFlow<List<String>> = _models.asStateFlow()

    private val _initState = MutableStateFlow(OpenaiApiInitState.IDLE)
    val initState: StateFlow<OpenaiApiInitState> = _initState.asStateFlow()

    private val _lastError = MutableStateFlow<String?>(null)
    val lastError: StateFlow<String?> = _lastError.asStateFlow()

    private val _lastMetrics = MutableStateFlow<OpenaiApiMetrics?>(null)
    val lastMetrics: StateFlow<OpenaiApiMetrics?> = _lastMetrics.asStateFlow()

    fun setModels(models: List<String>) {
        _models.value = models
    }

    fun setInitState(state: OpenaiApiInitState) {
        _initState.value = state
    }

    fun setLastError(error: String?) {
        _lastError.value = error
    }

    fun setLastMetrics(metrics: OpenaiApiMetrics?) {
        _lastMetrics.value = metrics
    }
}
