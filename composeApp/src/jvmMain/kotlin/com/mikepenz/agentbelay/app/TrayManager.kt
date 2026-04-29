package com.mikepenz.agentbelay.app

import com.mikepenz.agentbelay.di.AppScope
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Application-scoped owner of the main window's visibility state.
 *
 * Previously also managed the AWT tray icon + macOS dock badge + Linux
 * notifications; those have moved to the Compose `AgentBelayTray`
 * composable (tray icon + menu) and [TrayNotificationsManager] (dock badge
 * + Linux toast) respectively. What remains here is the canonical
 * `visible` flag that [com.mikepenz.agentbelay.server.ApprovalServer] flips
 * on a new incoming approval and the Compose layer observes to decide
 * whether to render the main window.
 */
@SingleIn(AppScope::class)
@Inject
class TrayManager {
    private val _visible = MutableStateFlow(true)
    val visible: StateFlow<Boolean> = _visible.asStateFlow()

    fun show() { _visible.value = true }
    fun hide() { _visible.value = false }
    fun toggle() { _visible.value = !_visible.value }
}
