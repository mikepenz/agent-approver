package com.mikepenz.agentbuddy.risk

import com.mikepenz.agentbuddy.di.AppScope
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * App-scoped publisher for the Ollama backend's lifecycle UI state.
 *
 * Mirrors [CopilotStateHolder]: [RiskAnalyzerLifecycle] writes to it as the
 * Ollama analyzer is started/stopped, and [com.mikepenz.agentbuddy.ui.settings.SettingsViewModel]
 * reads it to render the model dropdown and connection badge.
 */
@SingleIn(AppScope::class)
@Inject
class OllamaStateHolder {
    private val _models = MutableStateFlow<List<String>>(emptyList())
    val models: StateFlow<List<String>> = _models.asStateFlow()

    private val _initState = MutableStateFlow(OllamaInitState.IDLE)
    val initState: StateFlow<OllamaInitState> = _initState.asStateFlow()

    fun setModels(models: List<String>) {
        _models.value = models
    }

    fun setInitState(state: OllamaInitState) {
        _initState.value = state
    }
}
