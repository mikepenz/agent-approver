package com.mikepenz.agentbelay.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.isCtrlPressed
import androidx.compose.ui.input.key.isMetaPressed
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.type
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.background
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.layout.Box
import com.mikepenz.agentbelay.ui.theme.AgentBelayColors
import androidx.compose.ui.window.ApplicationScope
import androidx.compose.ui.window.WindowPosition
import androidx.compose.ui.window.rememberWindowState
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.ViewModelStoreOwner
import androidx.lifecycle.viewmodel.compose.LocalViewModelStoreOwner
import com.mikepenz.agentbelay.app.AgentBelayTray
import com.mikepenz.agentbelay.app.ApprovalServerRunner
import com.mikepenz.agentbelay.app.rememberPersistedWindowState
import com.mikepenz.agentbelay.ui.shell.LocalCommandPaletteController
import com.mikepenz.agentbelay.ui.shell.rememberCommandPaletteController
import com.mikepenz.agentbelay.di.AppGraph
import kotlinx.coroutines.cancel
import com.mikepenz.agentbelay.ui.approvals.ApprovalsViewModel
import com.mikepenz.agentbelay.ui.detail.ContentDetailWindow
import com.mikepenz.agentbelay.ui.detail.LicensesWindow
import com.mikepenz.agentbelay.ui.theme.AgentBelayTheme
import dev.zacsweers.metrox.viewmodel.LocalMetroViewModelFactory
import io.github.kdroidfilter.nucleus.window.material.MaterialDecoratedWindow
import io.github.kdroidfilter.nucleus.window.material.MaterialTitleBar
import java.awt.Desktop
import java.awt.Dimension
import java.awt.Toolkit
import java.awt.desktop.AppReopenedListener

private const val MIN_WINDOW_WIDTH = 360
private const val MIN_WINDOW_HEIGHT = 360

/**
 * Top-level composable hosting everything inside the desktop `application { }`
 * block. Pulled out of `Main.kt` in Phase 4 so `Main.kt` becomes a thin
 * bootstrap (logging + dataDir + graph + `application { AgentBelayShell() }`).
 *
 * Wires the application-scoped managers ([com.mikepenz.agentbelay.app.TrayManager],
 * [com.mikepenz.agentbelay.app.RiskAnalyzerLifecycle], [ApprovalServerRunner])
 * to the Compose runtime, provides the `LocalViewModelStoreOwner` and
 * `LocalMetroViewModelFactory` composition locals, and renders the main
 * window, port-error window, licenses window, and pop-out detail window.
 *
 * Must be called from inside `androidx.compose.ui.window.application { }` so
 * [exitApplication] is the host's terminator.
 */
@Composable
fun ApplicationScope.AgentBelayShell(graph: AppGraph, devMode: Boolean, exitApplication: () -> Unit) {
    val stateManager = graph.stateManager
    val trayManager = graph.trayManager
    val isVisible by trayManager.visible.collectAsState()

    // Shared command-palette state. Toggled by the in-window ⌘K / Ctrl+K
    // handler in `App.kt` (focus-scoped only — no OS-level hotkey).
    val paletteController = rememberCommandPaletteController()

    var showPortError by remember { mutableStateOf(false) }
    var popOutState by remember { mutableStateOf<Pair<String, String>?>(null) }
    var showLicenses by remember { mutableStateOf(false) }

    // Application-scoped ViewModelStore — survives the main window being
    // hidden so VMs (notably ApprovalsViewModel) keep running their side
    // effects in the background.
    val viewModelStoreOwner = remember {
        object : ViewModelStoreOwner {
            override val viewModelStore: ViewModelStore = ViewModelStore()
        }
    }
    DisposableEffect(viewModelStoreOwner) {
        onDispose { viewModelStoreOwner.viewModelStore.clear() }
    }

    // Eagerly instantiate ApprovalsViewModel so its risk-analysis side effect
    // begins at startup, not at first window mount. See Phase 2.
    LaunchedEffect(Unit) {
        ViewModelProvider.create(viewModelStoreOwner, graph.metroViewModelFactory)[ApprovalsViewModel::class]
    }

    // Hoist AppViewModel so the window-level key handler can drive tab
    // selection (⌘1…⌘5 / Ctrl+1…Ctrl+5) without waiting for any Compose
    // descendant to hold focus.
    val appViewModel = remember {
        ViewModelProvider.create(viewModelStoreOwner, graph.metroViewModelFactory)[AppViewModel::class]
    }
    val visibleTabCount = visibleTabs(devMode).size

    // Start the risk analyzer lifecycle on the application coroutine scope so
    // it survives the main window being closed. Stopped by the DisposableEffect
    // below.
    LaunchedEffect(Unit) { graph.riskAnalyzerLifecycle.start() }
    LaunchedEffect(Unit) { graph.globalHotkeyManager.start() }

    // macOS dock-icon click + Quit handler.
    LaunchedEffect(Unit) {
        if (Desktop.isDesktopSupported()) {
            val desktop = Desktop.getDesktop()
            if (desktop.isSupported(Desktop.Action.APP_QUIT_HANDLER)) {
                desktop.setQuitHandler { _, response ->
                    exitApplication()
                    response.performQuit()
                }
            }
            try {
                desktop.addAppEventListener(AppReopenedListener { trayManager.show() })
            } catch (_: UnsupportedOperationException) {
                // APP_REOPENED not supported on this platform.
            }
        }
    }

    // Native system tray (Compose-owned via ComposeNativeTray). The Capabilities
    // sub-menu lists every registered CapabilityModule; toggling a checkbox
    // writes back through AppStateManager so it stays in sync with the
    // Settings tab.
    AgentBelayTray(
        trayManager = trayManager,
        stateManager = stateManager,
        capabilityEngine = graph.capabilityEngine,
        environment = graph.environment,
        exitApplication = exitApplication,
    )

    // OS-level dock badge + Linux notifications — app-scoped so they survive
    // the main window being hidden.
    DisposableEffect(Unit) {
        val closer = graph.trayNotificationsManager.start()
        onDispose { closer.close() }
    }

    // Approval server: try to start, route BindException → port-error window.
    LaunchedEffect(Unit) {
        when (graph.approvalServerRunner.start(onNewApproval = { trayManager.show() })) {
            ApprovalServerRunner.StartResult.Ok -> Unit
            ApprovalServerRunner.StartResult.PortInUse -> showPortError = true
        }
    }

    // Top-level cleanup: stop server, shut down Copilot, close DB, cancel
    // the application coroutine scope so any background jobs (tray observers,
    // analyzer lifecycle) drain. Order matters: stop the inbound HTTP server
    // first so no new approvals arrive mid-shutdown.
    DisposableEffect(Unit) {
        onDispose {
            graph.approvalServerRunner.stop()
            graph.riskAnalyzerLifecycle.shutdown()
            graph.globalHotkeyManager.shutdown()
            graph.databaseStorage.close()
            graph.environment.appScope.cancel()
        }
    }

    if (showPortError) {
        PortInUseWindow(
            themeMode = stateManager.state.value.settings.themeMode,
            port = stateManager.state.value.settings.serverPort,
            onExit = exitApplication,
        )
    }

    val windowState = rememberPersistedWindowState(stateManager)
    val appState by stateManager.state.collectAsState()

    if (isVisible) {
        AgentBelayTheme(themeMode = appState.settings.themeMode) {
            MaterialDecoratedWindow(
                onCloseRequest = { trayManager.hide() },
                title = "Agent Belay",
                state = windowState,
                onPreviewKeyEvent = { event ->
                    if (event.type != KeyEventType.KeyDown ||
                        !(event.isMetaPressed || event.isCtrlPressed)
                    ) {
                        false
                    } else when (event.key) {
                        Key.K -> { paletteController.toggle(); true }
                        Key.One, Key.Two, Key.Three, Key.Four, Key.Five -> {
                            val index = when (event.key) {
                                Key.One -> 0
                                Key.Two -> 1
                                Key.Three -> 2
                                Key.Four -> 3
                                else -> 4
                            }
                            if (index < visibleTabCount) {
                                appViewModel.selectTab(index)
                                true
                            } else false
                        }
                        else -> false
                    }
                },
            ) {
                LaunchedEffect(appState.settings.alwaysOnTop) {
                    window.isAlwaysOnTop = appState.settings.alwaysOnTop
                }
                LaunchedEffect(Unit) {
                    // Enforce a sensible minimum so the tab row and content
                    // never collapse below their usable width.
                    window.minimumSize = Dimension(MIN_WINDOW_WIDTH, MIN_WINDOW_HEIGHT)
                }
                MaterialTitleBar {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.align(Alignment.CenterHorizontally),
                    ) {
                        Text(
                            "Agent Belay",
                            color = AgentBelayColors.inkSecondary,
                            fontSize = 12.5.sp,
                            fontWeight = FontWeight.SemiBold,
                            letterSpacing = (-0.1).sp,
                        )
                        if (devMode) {
                            Box(
                                modifier = Modifier
                                    .size(3.dp)
                                    .clip(CircleShape)
                                    .background(AgentBelayColors.inkSubtle),
                            )
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
                    CompositionLocalProvider(
                        LocalViewModelStoreOwner provides viewModelStoreOwner,
                        LocalMetroViewModelFactory provides graph.metroViewModelFactory,
                        LocalCommandPaletteController provides paletteController,
                    ) {
                        App(
                            onPopOut = { title, content -> popOutState = title to content },
                            onShowLicenses = { showLicenses = true },
                            onExpand = {
                                val gc = window.graphicsConfiguration
                                val screenBounds = gc.bounds
                                val insets = Toolkit.getDefaultToolkit().getScreenInsets(gc)
                                val availableWidth = screenBounds.width - insets.left - insets.right
                                val availableHeight = screenBounds.height - insets.top - insets.bottom
                                val desiredWidthPx = (800 * gc.defaultTransform.scaleX).toInt()
                                val desiredHeightPx = window.height.coerceAtLeast(600)
                                val targetWidthPx = desiredWidthPx.coerceAtMost(availableWidth)
                                val targetHeightPx = desiredHeightPx.coerceAtMost(availableHeight)
                                if (window.width < targetWidthPx || window.height < targetHeightPx) {
                                    window.setSize(
                                        targetWidthPx.coerceAtLeast(window.width.coerceAtMost(availableWidth)),
                                        targetHeightPx.coerceAtLeast(window.height.coerceAtMost(availableHeight)),
                                    )
                                    window.setLocationRelativeTo(null)
                                }
                            },
                        )
                    }
                }
            }
        }
    }

    if (showLicenses) {
        LicensesWindow(onClose = { showLicenses = false })
    }

    popOutState?.let { (title, content) ->
        ContentDetailWindow(
            title = title,
            content = content,
            onClose = { popOutState = null },
        )
    }
}

// ── Previews (iter 3) ──────────────────────────────────────────────────────

@androidx.compose.ui.tooling.preview.Preview(widthDp = 1280, heightDp = 860)
@Composable
private fun PreviewShellComposition() {
    com.mikepenz.agentbelay.ui.theme.PreviewScaffold {
        androidx.compose.runtime.CompositionLocalProvider(
            com.mikepenz.agentbelay.ui.shell.LocalCommandPaletteController provides
                remember { com.mikepenz.agentbelay.ui.shell.CommandPaletteController() },
        ) {
            Row(modifier = Modifier.fillMaxSize()) {
                com.mikepenz.agentbelay.ui.shell.AppSidebar(
                    selectedTab = AppTab.Approvals,
                    onTabSelect = {},
                    pendingCount = 3,
                    appVersion = "1.0.0",
                    serverPort = 19532,
                    agentRegistrations = listOf(
                        com.mikepenz.agentbelay.ui.AgentRegistration("Claude Code", true),
                        com.mikepenz.agentbelay.ui.AgentRegistration("GitHub Copilot", true),
                    ),
                )
                Box(modifier = Modifier.fillMaxSize()) {
                    com.mikepenz.agentbelay.ui.approvals.ApprovalsScreen(
                        items = listOf(
                            com.mikepenz.agentbelay.ui.approvals.ApprovalQueueItem(
                                id = "1",
                                tool = "Bash",
                                source = com.mikepenz.agentbelay.model.Source.CLAUDE_CODE,
                                summary = "rm -rf node_modules && pnpm install",
                                risk = 3, via = "claude", timestamp = "14:02:11",
                                elapsedSeconds = 18, ttlSeconds = 60, session = "abc",
                                prompt = "", workingDir = "/project",
                                riskAssessment = "Filesystem delete, approved 3x recently.",
                            ),
                            com.mikepenz.agentbelay.ui.approvals.ApprovalQueueItem(
                                id = "2",
                                tool = "Edit",
                                source = com.mikepenz.agentbelay.model.Source.COPILOT,
                                summary = "Write src/foo.kt (+12 / −3)",
                                risk = 1, via = "copilot", timestamp = "14:01:44",
                                elapsedSeconds = 45, ttlSeconds = 60, session = "def",
                                prompt = "", workingDir = "/project",
                                riskAssessment = "Safe edit.",
                            ),
                        ),
                    )
                }
            }
        }
    }
}

@androidx.compose.ui.tooling.preview.Preview(widthDp = 1280, heightDp = 860)
@Composable
private fun PreviewShellCompositionLight() {
    com.mikepenz.agentbelay.ui.theme.PreviewScaffold(
        themeMode = com.mikepenz.agentbelay.model.ThemeMode.LIGHT,
    ) {
        androidx.compose.runtime.CompositionLocalProvider(
            com.mikepenz.agentbelay.ui.shell.LocalCommandPaletteController provides
                remember { com.mikepenz.agentbelay.ui.shell.CommandPaletteController() },
        ) {
            Row(modifier = Modifier.fillMaxSize()) {
                com.mikepenz.agentbelay.ui.shell.AppSidebar(
                    selectedTab = AppTab.History,
                    onTabSelect = {},
                    pendingCount = 0,
                    appVersion = "1.0.0",
                    serverPort = 19532,
                    agentRegistrations = listOf(
                        com.mikepenz.agentbelay.ui.AgentRegistration("Claude Code", true),
                        com.mikepenz.agentbelay.ui.AgentRegistration("GitHub Copilot", false),
                    ),
                )
                Box(modifier = Modifier.fillMaxSize()) {
                    com.mikepenz.agentbelay.ui.approvals.ApprovalsScreen(items = emptyList())
                }
            }
        }
    }
}

@Composable
private fun PortInUseWindow(
    themeMode: com.mikepenz.agentbelay.model.ThemeMode,
    port: Int,
    onExit: () -> Unit,
) {
    val windowState = rememberWindowState(
        size = DpSize(460.dp, 320.dp),
        position = WindowPosition(Alignment.Center),
    )
    AgentBelayTheme(themeMode = themeMode) {
        MaterialDecoratedWindow(
            onCloseRequest = onExit,
            title = "Agent Belay",
            state = windowState,
        ) {
            MaterialTitleBar {
                Text(
                    "Agent Belay",
                    color = AgentBelayColors.inkSecondary,
                    fontSize = 12.5.sp,
                    fontWeight = FontWeight.SemiBold,
                    letterSpacing = (-0.1).sp,
                    modifier = Modifier.align(Alignment.CenterHorizontally),
                )
            }
            PortInUseBody(port = port, onExit = onExit)
        }
    }
}

@Composable
private fun PortInUseBody(port: Int, onExit: () -> Unit) {
    Surface(
        modifier = Modifier.fillMaxSize(),
        color = AgentBelayColors.background,
    ) {
        Column(
            modifier = Modifier.fillMaxSize().padding(horizontal = 28.dp, vertical = 24.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp, Alignment.CenterVertically),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .clip(CircleShape)
                    .background(com.mikepenz.agentbelay.ui.theme.DangerRed.copy(alpha = 0.14f)),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    "!",
                    color = com.mikepenz.agentbelay.ui.theme.DangerRed,
                    fontSize = 20.sp,
                    fontWeight = FontWeight.SemiBold,
                )
            }
            Text(
                "Port $port is already in use",
                color = AgentBelayColors.inkPrimary,
                fontSize = 15.sp,
                fontWeight = FontWeight.SemiBold,
                letterSpacing = (-0.1).sp,
            )
            Text(
                "Another instance of Agent Belay may already be running. Quit the other " +
                    "instance, or change the server port in Settings, then relaunch.",
                color = AgentBelayColors.inkTertiary,
                fontSize = 12.5.sp,
                lineHeight = 18.sp,
                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
            )
            com.mikepenz.agentbelay.ui.components.PrimaryButton(
                text = "Exit",
                onClick = onExit,
            )
        }
    }
}

@androidx.compose.ui.tooling.preview.Preview(widthDp = 460, heightDp = 280)
@Composable
private fun PreviewPortInUseBody() {
    com.mikepenz.agentbelay.ui.theme.PreviewScaffold {
        PortInUseBody(port = 19532, onExit = {})
    }
}

@androidx.compose.ui.tooling.preview.Preview(widthDp = 460, heightDp = 280)
@Composable
private fun PreviewPortInUseBodyLight() {
    com.mikepenz.agentbelay.ui.theme.PreviewScaffold(
        themeMode = com.mikepenz.agentbelay.model.ThemeMode.LIGHT,
    ) {
        PortInUseBody(port = 19532, onExit = {})
    }
}
