package com.mikepenz.agentapprover

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
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
import com.mikepenz.agentapprover.model.RiskAnalysisBackend
import com.mikepenz.agentapprover.risk.ClaudeCliRiskAnalyzer
import com.mikepenz.agentapprover.risk.CopilotInitState
import com.mikepenz.agentapprover.risk.CopilotRiskAnalyzer
import com.mikepenz.agentapprover.risk.RiskAnalyzer
import com.mikepenz.agentapprover.risk.RiskMessageBuilder
import com.mikepenz.agentapprover.protection.ProtectionEngine
import com.mikepenz.agentapprover.protection.modules.*
import com.mikepenz.agentapprover.server.ApprovalServer
import com.mikepenz.agentapprover.state.AppStateManager
import com.mikepenz.agentapprover.storage.DatabaseStorage
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
import co.touchlab.kermit.Logger
import io.github.kdroidfilter.nucleus.notification.linux.LinuxNotificationCenter
import io.github.kdroidfilter.nucleus.notification.linux.Notification
import java.awt.CheckboxMenuItem
import java.awt.Desktop
import java.awt.MenuItem
import java.awt.PopupMenu
import java.awt.SystemTray
import java.awt.TrayIcon
import java.awt.desktop.AppReopenedListener
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
    val maxEntries = settingsStorage.load().maxHistoryEntries
    val databaseStorage = DatabaseStorage(dataDir, maxEntries = maxEntries)
    databaseStorage.migrateFromJson(dataDir)
    val stateManager = AppStateManager(databaseStorage = databaseStorage, settingsStorage = settingsStorage, devMode = devMode)
    stateManager.initialize()

    val protectionEngine = ProtectionEngine(
        modules = listOf(
            DestructiveCommandsModule,
            SensitiveFilesModule,
            SupplyChainRceModule,
            ToolBypassModule,
            InlineScriptsModule,
            PipeAbuseModule,
            UncommittedFilesModule,
            PythonVenvModule,
            AbsolutePathsModule,
            PipedTailHeadModule,
        ),
        settingsProvider = { stateManager.state.value.settings.protectionSettings },
    )

    val hookRegistrar = HookRegistrar

    application {
        var isVisible by remember { mutableStateOf(true) }
        var showPortError by remember { mutableStateOf(false) }
        var popOutState by remember { mutableStateOf<Pair<String, String>?>(null) }
        var showLicenses by remember { mutableStateOf(false) }

        // Handle macOS dock icon click and Quit
        val exitApp = ::exitApplication
        LaunchedEffect(Unit) {
            if (Desktop.isDesktopSupported()) {
                val desktop = Desktop.getDesktop()
                if (desktop.isSupported(Desktop.Action.APP_QUIT_HANDLER)) {
                    desktop.setQuitHandler { _, response ->
                        exitApp()
                        response.performQuit()
                    }
                }
                try {
                    desktop.addAppEventListener(AppReopenedListener {
                        isVisible = true
                    })
                } catch (_: UnsupportedOperationException) {
                    // APP_REOPENED not supported on this platform
                }
            }
        }

        val claudeAnalyzer = remember {
            ClaudeCliRiskAnalyzer(
                model = stateManager.state.value.settings.riskAnalysisModel,
                customSystemPrompt = stateManager.state.value.settings.riskAnalysisCustomPrompt,
            )
        }
        var copilotAnalyzer by remember { mutableStateOf<CopilotRiskAnalyzer?>(null) }
        var activeRiskAnalyzer by remember { mutableStateOf<RiskAnalyzer>(claudeAnalyzer) }
        var copilotModels by remember { mutableStateOf<List<Pair<String, String>>>(emptyList()) }
        var copilotInitState by remember { mutableStateOf(CopilotInitState.IDLE) }

        val server = remember {
            ApprovalServer(stateManager, protectionEngine = protectionEngine, databaseStorage = databaseStorage, onNewApproval = {
                isVisible = true
                // On macOS, try to bring window to front
                if (System.getProperty("os.name").lowercase().contains("mac")) {
                    try {
                        @Suppress("DEPRECATION")
                        Desktop.getDesktop()
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
                copilotAnalyzer?.shutdown()
                databaseStorage.close()
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
        val isLinux = remember { System.getProperty("os.name", "").contains("Linux", ignoreCase = true) }
        val showHideItem = remember { MenuItem(if (isVisible) "Hide" else "Show") }
        val awayModeItem = remember { CheckboxMenuItem("Away Mode", stateManager.state.value.settings.awayMode) }
        DisposableEffect(Unit) {
            val systemTray = if (SystemTray.isSupported()) SystemTray.getSystemTray() else null
            val trayIcon = if (systemTray != null) {
                // On macOS: template image (logo only) + native colored badge overlay
                // On other platforms: full icon with badge baked in
                val icon = TrayIcon(AppIcon.createTrayIconMultiRes(0, drawBadge = !isMacOs))
                icon.isImageAutoSize = false
                icon.toolTip = "Agent Approver"
                icon.addActionListener { isVisible = !isVisible }

                val popup = PopupMenu()
                showHideItem.addActionListener { isVisible = !isVisible }
                popup.add(showHideItem)

                awayModeItem.addItemListener {
                    val current = stateManager.state.value.settings
                    stateManager.updateSettings(current.copy(awayMode = awayModeItem.state))
                }
                popup.add(awayModeItem)

                popup.add(MenuItem("Quit").apply { addActionListener { exitApp() } })
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
        var previousPendingCount by remember { mutableStateOf(0) }
        LaunchedEffect(pendingCount, state.settings.awayMode) {
            if (SystemTray.isSupported()) {
                val systemTray = SystemTray.getSystemTray()
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
            if (isMacOs) {
                io.github.kdroidfilter.nucleus.notification.NotificationCenter.setBadgeCount(pendingCount)
            } else if (isLinux) {
                // Only notify on meaningful transitions (new pending requests arriving)
                if (pendingCount > 0 && pendingCount > previousPendingCount) {
                    LinuxNotificationCenter.notify(
                        Notification(
                            summary = "Agent Approver",
                            body = "$pendingCount pending approval requests"
                        )
                    )
                }
            }
            previousPendingCount = pendingCount
        }
        val settings = state.settings

        // Keep risk analyzer in sync with settings
        LaunchedEffect(
            settings.riskAnalysisBackend,
            settings.riskAnalysisModel,
            settings.riskAnalysisCopilotModel,
            settings.riskAnalysisCustomPrompt,
        ) {
            val effectivePrompt = settings.riskAnalysisCustomPrompt.ifBlank { RiskMessageBuilder.DEFAULT_SYSTEM_PROMPT }

            // Update Claude analyzer settings
            claudeAnalyzer.model = settings.riskAnalysisModel
            claudeAnalyzer.systemPrompt = effectivePrompt

            // Manage Copilot analyzer lifecycle
            when (settings.riskAnalysisBackend) {
                RiskAnalysisBackend.COPILOT -> {
                    var analyzer = copilotAnalyzer
                    if (analyzer == null) {
                        copilotInitState = CopilotInitState.LOADING
                        analyzer = CopilotRiskAnalyzer(
                            model = settings.riskAnalysisCopilotModel,
                            customSystemPrompt = settings.riskAnalysisCustomPrompt,
                        )
                        analyzer.cliPath = settings.riskAnalysisCopilotCliPath
                        try {
                            analyzer.start()
                            copilotAnalyzer = analyzer
                            // Fetch available models
                            analyzer.listModels().onSuccess { models ->
                                copilotModels = models
                            }
                            copilotInitState = CopilotInitState.READY
                        } catch (e: Exception) {
                            Logger.withTag("Main").e(e) { "Failed to start Copilot client" }
                            copilotInitState = CopilotInitState.ERROR
                            activeRiskAnalyzer = claudeAnalyzer
                            return@LaunchedEffect
                        }
                    }
                    analyzer.model = settings.riskAnalysisCopilotModel
                    analyzer.systemPrompt = effectivePrompt
                    analyzer.cliPath = settings.riskAnalysisCopilotCliPath
                    activeRiskAnalyzer = analyzer
                }
                RiskAnalysisBackend.CLAUDE -> {
                    copilotAnalyzer?.shutdown()
                    copilotAnalyzer = null
                    copilotInitState = CopilotInitState.IDLE
                    activeRiskAnalyzer = claudeAnalyzer
                }
            }
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
                                    shape = RoundedCornerShape(4.dp),
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
                            stateManager, hookRegistrar, activeRiskAnalyzer,
                            copilotModels = copilotModels,
                            copilotInitState = copilotInitState,
                            devMode = devMode,
                            onPopOut = { title, content -> popOutState = title to content },
                            onShowLicenses = { showLicenses = true },
                            protectionModules = protectionEngine.modules,
                            protectionEngine = protectionEngine,
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
