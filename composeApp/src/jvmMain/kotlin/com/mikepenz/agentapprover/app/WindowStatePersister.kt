package com.mikepenz.agentapprover.app

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.WindowPosition
import androidx.compose.ui.window.WindowState
import com.mikepenz.agentapprover.storage.SettingsStorage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.withContext

private const val DEFAULT_WINDOW_WIDTH = 420
private const val DEFAULT_WINDOW_HEIGHT = 480
private const val WINDOW_STATE_DEBOUNCE_MS = 500L

/**
 * Compose helper that builds a [WindowState] from the position/size persisted
 * in [SettingsStorage] and registers a debounced effect to write any changes
 * back to disk on `Dispatchers.IO`. Replaces the inline window-state plumbing
 * that used to live in `Main.kt`.
 */
@OptIn(FlowPreview::class)
@Composable
fun rememberPersistedWindowState(settingsStorage: SettingsStorage): WindowState {
    val windowState = remember {
        val settings = settingsStorage.load()
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
                    withContext(Dispatchers.IO) {
                        val current = settingsStorage.load()
                        settingsStorage.save(
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
