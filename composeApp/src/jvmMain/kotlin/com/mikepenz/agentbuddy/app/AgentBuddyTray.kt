package com.mikepenz.agentbuddy.app

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.painter.BitmapPainter
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.graphics.toComposeImageBitmap
import androidx.compose.ui.window.ApplicationScope
import com.kdroid.composetray.tray.api.Tray
import com.kdroid.composetray.utils.isMenuBarInDarkMode
import com.mikepenz.agentbuddy.capability.CapabilityEngine
import com.mikepenz.agentbuddy.di.AppEnvironment
import com.mikepenz.agentbuddy.model.CapabilityModuleSettings
import com.mikepenz.agentbuddy.platform.AppIcon
import com.mikepenz.agentbuddy.state.AppStateManager
import kotlinx.coroutines.launch
import java.awt.image.BufferedImage
import java.awt.image.MultiResolutionImage

/**
 * Compose-owned system tray for Agent Buddy. Built on ComposeNativeTray's
 * platform-native `Tray` composable so the menu recomposes from
 * [AppStateManager.state] — the "Away Mode" and per-`CapabilityModule`
 * checkboxes stay in sync with the Settings tab for free.
 *
 * The tray icon carries its own baked-in colored badge (red when pending > 0,
 * green otherwise) rendered by [AppIcon.createTrayIconMultiRes]. macOS loses
 * the previous template-image + JNA overlay behaviour in exchange for the
 * cross-platform Compose-native pipeline; flagged in CHANGELOG.
 */
@Composable
fun ApplicationScope.AgentBuddyTray(
    trayManager: TrayManager,
    stateManager: AppStateManager,
    capabilityEngine: CapabilityEngine,
    environment: AppEnvironment,
    exitApplication: () -> Unit,
) {
    val state by stateManager.state.collectAsState()
    val visible by trayManager.visible.collectAsState()
    val pendingCount = state.pendingApprovals.size
    val awayMode = state.settings.awayMode
    val autoDecisionsEnabled = state.settings.autoDecisionsEnabled
    val capSettings = state.settings.capabilitySettings

    // Menu-bar theme: white logo on dark bars, black on light. Observed via
    // ComposeNativeTray's detector so we recompose on live theme changes.
    val darkMenuBar = isMenuBarInDarkMode()
    val logoColor = if (darkMenuBar) java.awt.Color.WHITE else java.awt.Color.BLACK

    val icon: Painter = remember(pendingCount, logoColor) {
        AppIcon.createTrayIconMultiRes(
            pendingCount = pendingCount,
            drawBadge = true,
            logoColor = logoColor,
        ).toPainter()
    }
    val tooltip = buildString {
        append("Agent Buddy")
        if (pendingCount > 0) append(" ($pendingCount pending)")
        if (awayMode) append(" [Away]")
    }

    Tray(
        icon = icon,
        tooltip = tooltip,
        primaryAction = { trayManager.toggle() },
    ) {
        Item(label = if (visible) "Hide" else "Show") { trayManager.toggle() }

        CheckableItem(
            label = "Away Mode",
            checked = awayMode,
            onCheckedChange = { checked ->
                environment.appScope.launch {
                    val current = stateManager.state.value.settings
                    stateManager.updateSettings(current.copy(awayMode = checked))
                }
            },
        )

        CheckableItem(
            label = "Auto-decisions",
            checked = autoDecisionsEnabled,
            onCheckedChange = { checked ->
                environment.appScope.launch {
                    val current = stateManager.state.value.settings
                    stateManager.updateSettings(current.copy(autoDecisionsEnabled = checked))
                }
            },
        )

        SubMenu(label = "Capabilities") {
            capabilityEngine.modules.forEach { module ->
                val enabled = capSettings.modules[module.id]?.enabled == true
                CheckableItem(
                    label = module.name,
                    checked = enabled,
                    onCheckedChange = { checked ->
                        environment.appScope.launch {
                            val current = stateManager.state.value.settings
                            val existing = current.capabilitySettings.modules[module.id]
                                ?: CapabilityModuleSettings()
                            val updated = current.capabilitySettings.copy(
                                modules = current.capabilitySettings.modules +
                                    (module.id to existing.copy(enabled = checked)),
                            )
                            stateManager.updateSettings(current.copy(capabilitySettings = updated))
                        }
                    },
                )
            }
        }

        Divider()

        Item(label = "Quit") { exitApplication() }
    }
}

/** Picks the highest-resolution [BufferedImage] variant and wraps it as a Compose [Painter]. */
private fun java.awt.Image.toPainter(): Painter {
    val bi: BufferedImage = when (this) {
        is BufferedImage -> this
        is MultiResolutionImage -> {
            // Prefer the 2x variant (44×44 for a 22pt tray icon).
            (getResolutionVariant(44.0, 44.0) as? BufferedImage) ?: rasterize(this)
        }
        else -> rasterize(this)
    }
    return BitmapPainter(bi.toComposeImageBitmap())
}

private fun rasterize(image: java.awt.Image): BufferedImage {
    val width = image.getWidth(null).coerceAtLeast(1)
    val height = image.getHeight(null).coerceAtLeast(1)
    val bi = BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB)
    val g = bi.createGraphics()
    try {
        g.drawImage(image, 0, 0, null)
    } finally {
        g.dispose()
    }
    return bi
}
