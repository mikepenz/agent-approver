package com.mikepenz.agentbuddy.risk

import com.mikepenz.agentbuddy.di.AppScope
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * App-scoped publisher for Copilot lifecycle UI state.
 *
 * `Main.kt` still owns the actual Copilot analyzer lifecycle in Phase 3 (this
 * moves into a dedicated manager in Phase 4). This holder bridges the
 * `LaunchedEffect` that watches settings changes to consumers in the DI graph
 * — notably [com.mikepenz.agentbuddy.ui.settings.SettingsViewModel], which
 * needs to display the available models and current init state.
 */
@SingleIn(AppScope::class)
@Inject
class CopilotStateHolder {
    private val _models = MutableStateFlow<List<Pair<String, String>>>(emptyList())
    val models: StateFlow<List<Pair<String, String>>> = _models.asStateFlow()

    private val _initState = MutableStateFlow(CopilotInitState.IDLE)
    val initState: StateFlow<CopilotInitState> = _initState.asStateFlow()

    fun setModels(models: List<Pair<String, String>>) {
        _models.value = models
    }

    fun setInitState(state: CopilotInitState) {
        _initState.value = state
    }
}
