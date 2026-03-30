package com.mikepenz.agentapprover

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.WindowPosition
import androidx.compose.ui.window.WindowState
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import io.github.kdroidfilter.nucleus.window.material.MaterialDecoratedWindow
import io.github.kdroidfilter.nucleus.window.material.MaterialTitleBar
import com.mikepenz.agentapprover.hook.HookRegistrar
import com.mikepenz.agentapprover.risk.RiskAnalyzer
import com.mikepenz.agentapprover.server.ApprovalServer
import com.mikepenz.agentapprover.state.AppStateManager
import com.mikepenz.agentapprover.storage.HistoryStorage
import com.mikepenz.agentapprover.storage.SettingsStorage
import com.mikepenz.agentapprover.ui.App
import com.mikepenz.agentapprover.ui.detail.ContentDetailWindow
import com.mikepenz.agentapprover.ui.detail.LicensesWindow
import com.mikepenz.agentapprover.platform.AppIcon
import com.mikepenz.agentapprover.platform.MacOsTrayBadge
import com.mikepenz.agentapprover.ui.theme.AgentApproverTheme
import com.mikepenz.agentapprover.ui.theme.configureLogging
import androidx.compose.ui.graphics.painter.BitmapPainter
import androidx.compose.ui.graphics.toComposeImageBitmap
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


private const val DEFAULT_WINDOW_WIDTH = 420
private const val DEFAULT_WINDOW_HEIGHT = 480

@OptIn(FlowPreview::class)
fun main(args: Array<String>) {
    // Enable macOS template images so tray icon adapts to menu bar background color
    System.setProperty("apple.awt.enableTemplateImages", "true")

    configureLogging()

    val devMode = "--dev" in args || System.getProperty("agentapprover.devmode") == "true"

    val dataDir = getAppDataDir()
    File(dataDir).mkdirs()

    val appScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    val settingsStorage = SettingsStorage(dataDir)
    val historyStorage = HistoryStorage(dataDir, appScope)
    val stateManager = AppStateManager(historyStorage, settingsStorage)
    stateManager.initialize()

    val hookRegistrar = HookRegistrar

    application {
        var isVisible by remember { mutableStateOf(true) }
        var showPortError by remember { mutableStateOf(false) }
        var popOutState by remember { mutableStateOf<Pair<String, String>?>(null) }
        var showLicenses by remember { mutableStateOf(false) }

        // Handle macOS dock icon click and Quit
        val exitApp = ::exitApplication
        LaunchedEffect(Unit) {
            if (java.awt.Desktop.isDesktopSupported()) {
                val desktop = java.awt.Desktop.getDesktop()
                desktop.setQuitHandler { _, response ->
                    exitApp()
                    response.performQuit()
                }
                desktop.addAppEventListener(java.awt.desktop.AppReopenedListener {
                    isVisible = true
                })
            }
        }

        val riskAnalyzer = remember {
            RiskAnalyzer(
                model = stateManager.state.value.settings.riskAnalysisModel,
                customSystemPrompt = stateManager.state.value.settings.riskAnalysisCustomPrompt,
            )
        }

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
                historyStorage.shutdown()
                appScope.cancel()
            }
        }

        if (showPortError) {
            Window(
                onCloseRequest = { exitApplication() },
                title = "Port In Use",
                state = rememberWindowState(
                    size = DpSize(400.dp, 200.dp),
                    position = WindowPosition(Alignment.Center),
                ),
            ) {
                AgentApproverTheme(themeMode = stateManager.state.value.settings.themeMode) {
                    Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                        Column(
                            modifier = Modifier.fillMaxSize().padding(24.dp),
                            verticalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterVertically),
                            horizontalAlignment = Alignment.CenterHorizontally,
                        ) {
                            Text("The server port is already in use. Another instance may be running.",
                                style = MaterialTheme.typography.bodyMedium)
                            Button(onClick = { exitApplication() }) { Text("Exit") }
                        }
                    }
                }
            }
        }

        val state by stateManager.state.collectAsState()
        val pendingCount = state.pendingApprovals.size

        // Use AWT SystemTray directly for proper MultiResolutionImage HiDPI support
        val isMacOs = remember { System.getProperty("os.name", "").contains("Mac", ignoreCase = true) }
        val showHideItem = remember { java.awt.MenuItem(if (isVisible) "Hide" else "Show") }
        val awayModeItem = remember { java.awt.CheckboxMenuItem("Away Mode", stateManager.state.value.settings.awayMode) }
        DisposableEffect(Unit) {
            val systemTray = if (java.awt.SystemTray.isSupported()) java.awt.SystemTray.getSystemTray() else null
            val trayIcon = if (systemTray != null) {
                // On macOS: template image (logo only) + native colored badge overlay
                // On other platforms: full icon with badge baked in
                val icon = java.awt.TrayIcon(AppIcon.createTrayIconMultiRes(0, drawBadge = !isMacOs))
                icon.isImageAutoSize = false
                icon.toolTip = "Agent Approver"
                icon.addActionListener { isVisible = !isVisible }

                val popup = java.awt.PopupMenu()
                showHideItem.addActionListener { isVisible = !isVisible }
                popup.add(showHideItem)

                awayModeItem.addItemListener {
                    val current = stateManager.state.value.settings
                    stateManager.updateSettings(current.copy(awayMode = awayModeItem.state))
                }
                popup.add(awayModeItem)

                popup.add(java.awt.MenuItem("Quit").apply { addActionListener { exitApp() } })
                icon.popupMenu = popup

                systemTray.add(icon)
                // Add colored badge overlay (macOS only, after tray icon is added so peer exists)
                if (isMacOs) MacOsTrayBadge.update(icon, 0)
                icon
            } else null
            onDispose { trayIcon?.let { systemTray?.remove(it) } }
        }

        // Update tray menu label and icon when state changes
        LaunchedEffect(isVisible) {
            showHideItem.label = if (isVisible) "Hide" else "Show"
        }
        LaunchedEffect(state.settings.awayMode) {
            awayModeItem.state = state.settings.awayMode
        }
        LaunchedEffect(pendingCount, state.settings.awayMode) {
            if (java.awt.SystemTray.isSupported()) {
                val systemTray = java.awt.SystemTray.getSystemTray()
                systemTray.trayIcons.firstOrNull()?.let { icon ->
                    icon.image = AppIcon.createTrayIconMultiRes(pendingCount, drawBadge = !isMacOs)
                    if (isMacOs) MacOsTrayBadge.update(icon, pendingCount)
                    icon.toolTip = buildString {
                        append("Agent Approver")
                        if (pendingCount > 0) append(" ($pendingCount pending)")
                        if (state.settings.awayMode) append(" [Away]")
                    }
                }
            }
            // macOS dock badge via Nucleus notification API
            io.github.kdroidfilter.nucleus.notification.NotificationCenter.setBadgeCount(pendingCount)
        }
        val settings = state.settings

        // Keep risk analyzer in sync with settings
        LaunchedEffect(settings.riskAnalysisModel, settings.riskAnalysisCustomPrompt) {
            riskAnalyzer.model = settings.riskAnalysisModel
            riskAnalyzer.systemPrompt = settings.riskAnalysisCustomPrompt.ifBlank { RiskAnalyzer.DEFAULT_SYSTEM_PROMPT }
        }

        val windowState = remember {
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

        // Debounce-save window position and size changes
        LaunchedEffect(windowState) {
            snapshotFlow { windowState.position to windowState.size }
                .distinctUntilChanged()
                .debounce(500L)
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
                                )
                            )
                        }
                    }
                }
        }

        if (isVisible) {
            AgentApproverTheme(themeMode = settings.themeMode) {
                MaterialDecoratedWindow(
                    onCloseRequest = { isVisible = false },
                    title = "Agent Approver",
                    state = windowState,
                ) {
                    LaunchedEffect(settings.alwaysOnTop) {
                        window.isAlwaysOnTop = settings.alwaysOnTop
                    }
                    MaterialTitleBar {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            modifier = Modifier.align(Alignment.CenterHorizontally),
                        ) {
                            Text(
                                "Agent Approver",
                                color = MaterialTheme.colorScheme.onSurface,
                                style = MaterialTheme.typography.titleSmall,
                            )
                            if (devMode) {
                                Surface(
                                    shape = androidx.compose.foundation.shape.RoundedCornerShape(4.dp),
                                    color = MaterialTheme.colorScheme.tertiary.copy(alpha = 0.2f),
                                ) {
                                    Text(
                                        "DEV",
                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 1.dp),
                                        color = MaterialTheme.colorScheme.tertiary,
                                        style = MaterialTheme.typography.labelSmall,
                                    )
                                }
                            }
                        }
                    }
                    Surface(
                        modifier = Modifier.fillMaxSize(),
                        color = MaterialTheme.colorScheme.background,
                    ) {
                        App(
                            stateManager, hookRegistrar, riskAnalyzer,
                            devMode = devMode,
                            onPopOut = { title, content -> popOutState = title to content },
                            onShowLicenses = { showLicenses = true },
                        )
                    }
                }
            }
        }

        // Licenses window
        if (showLicenses) {
            LicensesWindow(onClose = { showLicenses = false })
        }

        // Pop-out detail window
        popOutState?.let { (title, content) ->
            ContentDetailWindow(
                title = title,
                content = content,
                onClose = { popOutState = null },
            )
        }
    }
}
