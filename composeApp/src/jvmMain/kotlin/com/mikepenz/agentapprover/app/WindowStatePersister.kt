package com.mikepenz.agentapprover.app

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.WindowPosition
import androidx.compose.ui.window.WindowState
import com.mikepenz.agentapprover.state.AppStateManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.withContext

private const val DEFAULT_WINDOW_WIDTH = 420
private const val DEFAULT_WINDOW_HEIGHT = 480
private const val WINDOW_STATE_DEBOUNCE_MS = 500L

/**
 * Compose helper that builds a [WindowState] from the position/size already
 * loaded into [AppStateManager]'s in-memory settings, and registers a
 * debounced effect to persist any changes back through
 * [AppStateManager.updateSettings].
 *
 * Going through `stateManager` (rather than reading/writing `SettingsStorage`
 * directly) keeps this composable aligned with two app-wide guarantees:
 *
 * 1. **No disk reads on the UI thread.** The in-memory state is already
 *    populated at app startup, so the `remember { }` snapshot is free.
 * 2. **No read-modify-write races.** [AppStateManager.updateSettings] is
 *    synchronized internally (around both the in-memory update and the disk
 *    save), so a window-state save composed against the latest in-memory
 *    snapshot can never overwrite a concurrent Away Mode / theme / port
 *    change made elsewhere.
 *
 * The save itself is dispatched onto [Dispatchers.IO] inside the debounce
 * collector because `updateSettings` performs synchronous file I/O — running
 * it on the Compose dispatcher would jank window drag/resize.
 */
@OptIn(FlowPreview::class)
@Composable
fun rememberPersistedWindowState(stateManager: AppStateManager): WindowState {
    val windowState = remember {
        val settings = stateManager.state.value.settings
        val position = if (settings.windowX != null && settings.windowY != null) {
            WindowPosition.Absolute(settings.windowX.dp, settings.windowY.dp)
        } else {
            WindowPosition.PlatformDefault
        }
        val size = DpSize(
            width = (settings.windowWidth ?: DEFAULT_WINDOW_WIDTH).dp,
            height = (settings.windowHeight ?: DEFAULT_WINDOW_HEIGHT).dp,
        )
        WindowState(position = position, size = size)
    }

    LaunchedEffect(windowState) {
        snapshotFlow { windowState.position to windowState.size }
            .distinctUntilChanged()
            .debounce(WINDOW_STATE_DEBOUNCE_MS)
            .collect { (pos, size) ->
                if (pos is WindowPosition.Absolute) {
                    // Dispatch to IO so the synchronous file save inside
                    // updateSettings doesn't run on the Compose dispatcher
                    // and jank window drag/resize.
                    withContext(Dispatchers.IO) {
                        val current = stateManager.state.value.settings
                        stateManager.updateSettings(
                            current.copy(
                                windowX = pos.x.value.toInt(),
                                windowY = pos.y.value.toInt(),
                                windowWidth = size.width.value.toInt(),
                                windowHeight = size.height.value.toInt(),
                            ),
                        )
                    }
                }
            }
    }

    return windowState
}
