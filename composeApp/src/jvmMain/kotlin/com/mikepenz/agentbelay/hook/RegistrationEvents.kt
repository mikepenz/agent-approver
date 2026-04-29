package com.mikepenz.agentbelay.hook

import com.mikepenz.agentbelay.di.AppScope
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow

/**
 * App-scoped event bus for hook registration changes.
 * [SettingsViewModel] emits after register/unregister; [AppViewModel]
 * collects to refresh the sidebar registration indicators.
 */
@Inject
@SingleIn(AppScope::class)
class RegistrationEvents {
    private val _changes = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    val changes: SharedFlow<Unit> = _changes

    fun emit() {
        _changes.tryEmit(Unit)
    }
}
