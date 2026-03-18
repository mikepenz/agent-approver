package com.mikepenz.agentapprover

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.window.Tray
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState
import io.github.kdroidfilter.nucleus.window.material.MaterialDecoratedWindow
import io.github.kdroidfilter.nucleus.window.material.MaterialTitleBar
import com.mikepenz.agentapprover.hook.HookRegistrar
import com.mikepenz.agentapprover.risk.RiskAnalyzer
import com.mikepenz.agentapprover.server.ApprovalServer
import com.mikepenz.agentapprover.state.AppStateManager
import com.mikepenz.agentapprover.storage.HistoryStorage
import com.mikepenz.agentapprover.storage.SettingsStorage
import com.mikepenz.agentapprover.ui.App
import com.mikepenz.agentapprover.ui.theme.AgentApproverTheme
import com.mikepenz.agentapprover.ui.theme.configureLogging
import androidx.compose.ui.graphics.painter.BitmapPainter
import androidx.compose.ui.graphics.toComposeImageBitmap
import java.awt.image.BufferedImage
import java.io.File
import java.net.BindException

fun getAppDataDir(): String {
    val osName = System.getProperty("os.name").lowercase()
    val home = System.getProperty("user.home")
    return when {
        osName.contains("mac") -> "$home/Library/Application Support/AgentApprover"
        osName.contains("win") -> "${System.getenv("APPDATA") ?: "$home/AppData/Roaming"}/AgentApprover"
        else -> "$home/.local/share/AgentApprover"
    }
}

private fun createTrayIcon(): BufferedImage {
    // macOS menu bar icons should be 22x22 (or 44x44 for retina)
    val size = 22
    val image = BufferedImage(size, size, BufferedImage.TYPE_INT_ARGB)
    val g = image.createGraphics()
    g.setRenderingHint(java.awt.RenderingHints.KEY_ANTIALIASING, java.awt.RenderingHints.VALUE_ANTIALIAS_ON)
    // Shield outline
    g.color = java.awt.Color(140, 92, 246) // Purple matching primary
    g.fillRoundRect(3, 2, 16, 18, 6, 6)
    // Checkmark
    g.color = java.awt.Color.WHITE
    g.stroke = java.awt.BasicStroke(2.2f, java.awt.BasicStroke.CAP_ROUND, java.awt.BasicStroke.JOIN_ROUND)
    g.drawLine(7, 12, 10, 15)
    g.drawLine(10, 15, 15, 7)
    g.dispose()
    return image
}

fun main() {
    configureLogging()

    val dataDir = getAppDataDir()
    File(dataDir).mkdirs()

    val settingsStorage = SettingsStorage(dataDir)
    val historyStorage = HistoryStorage(dataDir)
    val stateManager = AppStateManager(historyStorage, settingsStorage)
    stateManager.initialize()

    val hookRegistrar = HookRegistrar

    application {
        var isVisible by remember { mutableStateOf(true) }
        var showPortError by remember { mutableStateOf(false) }
        val coroutineScope = rememberCoroutineScope()

        val riskAnalyzer = remember(coroutineScope) { RiskAnalyzer(coroutineScope) }

        val server = remember {
            ApprovalServer(stateManager, onNewApproval = {
                isVisible = true
                // On macOS, try to bring window to front
                if (System.getProperty("os.name").lowercase().contains("mac")) {
                    try {
                        @Suppress("DEPRECATION")
                        java.awt.Desktop.getDesktop()
                    } catch (_: Exception) {
                        // ignore
                    }
                }
            })
        }

        LaunchedEffect(Unit) {
            try {
                val port = stateManager.state.value.settings.serverPort
                server.start(port)
            } catch (e: BindException) {
                showPortError = true
            } catch (e: Exception) {
                if (e.cause is BindException) {
                    showPortError = true
                } else {
                    throw e
                }
            }
        }

        DisposableEffect(Unit) {
            onDispose {
                server.stop()
            }
        }

        if (showPortError) {
            AlertDialog(
                onDismissRequest = { exitApplication() },
                title = { Text("Port In Use") },
                text = { Text("The server port is already in use. Another instance may be running.") },
                confirmButton = {
                    TextButton(onClick = { exitApplication() }) {
                        Text("Exit")
                    }
                },
            )
        }

        val trayIcon = remember { BitmapPainter(createTrayIcon().toComposeImageBitmap()) }
        Tray(
            icon = trayIcon,
            tooltip = "Agent Approver",
            menu = {
                Item("Show/Hide") { isVisible = !isVisible }
                Item("Quit") { exitApplication() }
            },
        )

        val settings = stateManager.state.value.settings
        val windowState = rememberWindowState()

        if (isVisible) {
            AgentApproverTheme {
                MaterialDecoratedWindow(
                    onCloseRequest = { isVisible = false },
                    title = "Agent Approver",
                    state = windowState,
                ) {
                    LaunchedEffect(settings.alwaysOnTop) {
                        window.isAlwaysOnTop = settings.alwaysOnTop
                    }
                    MaterialTitleBar {
                        Text(
                            "Agent Approver",
                            color = MaterialTheme.colorScheme.onSurface,
                            style = MaterialTheme.typography.titleSmall,
                        )
                    }
                    Surface(
                        modifier = Modifier.fillMaxSize(),
                        color = MaterialTheme.colorScheme.background,
                    ) {
                        App(stateManager, hookRegistrar, riskAnalyzer, coroutineScope)
                    }
                }
            }
        }
    }
}
