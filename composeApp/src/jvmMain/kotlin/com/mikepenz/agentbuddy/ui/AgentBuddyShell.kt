package com.mikepenz.agentbuddy.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
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
import com.mikepenz.agentbuddy.ui.theme.AgentBuddyColors
import androidx.compose.ui.window.ApplicationScope
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.WindowPosition
import androidx.compose.ui.window.rememberWindowState
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.ViewModelStoreOwner
import androidx.lifecycle.viewmodel.compose.LocalViewModelStoreOwner
import com.mikepenz.agentbuddy.app.AgentBuddyTray
import com.mikepenz.agentbuddy.app.ApprovalServerRunner
import com.mikepenz.agentbuddy.app.rememberPersistedWindowState
import com.mikepenz.agentbuddy.ui.shell.LocalCommandPaletteController
import com.mikepenz.agentbuddy.ui.shell.rememberCommandPaletteController
import com.mikepenz.agentbuddy.di.AppGraph
import kotlinx.coroutines.cancel
import com.mikepenz.agentbuddy.ui.approvals.ApprovalsViewModel
import com.mikepenz.agentbuddy.ui.detail.ContentDetailWindow
import com.mikepenz.agentbuddy.ui.detail.LicensesWindow
import com.mikepenz.agentbuddy.ui.theme.AgentBuddyTheme
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
 * bootstrap (logging + dataDir + graph + `application { AgentBuddyShell() }`).
 *
 * Wires the application-scoped managers ([com.mikepenz.agentbuddy.app.TrayManager],
 * [com.mikepenz.agentbuddy.app.RiskAnalyzerLifecycle], [ApprovalServerRunner])
 * to the Compose runtime, provides the `LocalViewModelStoreOwner` and
 * `LocalMetroViewModelFactory` composition locals, and renders the main
 * window, port-error window, licenses window, and pop-out detail window.
 *
 * Must be called from inside `androidx.compose.ui.window.application { }` so
 * [exitApplication] is the host's terminator.
 */
@Composable
fun ApplicationScope.AgentBuddyShell(graph: AppGraph, devMode: Boolean, exitApplication: () -> Unit) {
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
    AgentBuddyTray(
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
            graph.databaseStorage.close()
            graph.environment.appScope.cancel()
        }
    }

    if (showPortError) {
        PortInUseWindow(themeMode = stateManager.state.value.settings.themeMode, onExit = exitApplication)
    }

    val windowState = rememberPersistedWindowState(stateManager)
    val appState by stateManager.state.collectAsState()

    if (isVisible) {
        AgentBuddyTheme(themeMode = appState.settings.themeMode) {
            MaterialDecoratedWindow(
                onCloseRequest = { trayManager.hide() },
                title = "Agent Buddy",
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
                            "Agent Buddy",
                            color = AgentBuddyColors.inkSecondary,
                            fontSize = 12.5.sp,
                            fontWeight = FontWeight.SemiBold,
                            letterSpacing = (-0.1).sp,
                        )
                        if (devMode) {
                            Box(
                                modifier = Modifier
                                    .size(3.dp)
                                    .clip(CircleShape)
                                    .background(AgentBuddyColors.inkSubtle),
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
    com.mikepenz.agentbuddy.ui.theme.PreviewScaffold {
        androidx.compose.runtime.CompositionLocalProvider(
            com.mikepenz.agentbuddy.ui.shell.LocalCommandPaletteController provides
                remember { com.mikepenz.agentbuddy.ui.shell.CommandPaletteController() },
        ) {
            Row(modifier = Modifier.fillMaxSize()) {
                com.mikepenz.agentbuddy.ui.shell.AppSidebar(
                    selectedTab = AppTab.Approvals,
                    onTabSelect = {},
                    pendingCount = 3,
                    appVersion = "1.0.0",
                    serverPort = 19532,
                    agentRegistrations = listOf(
                        com.mikepenz.agentbuddy.ui.AgentRegistration("Claude Code", true),
                        com.mikepenz.agentbuddy.ui.AgentRegistration("GitHub Copilot", true),
                    ),
                )
                Box(modifier = Modifier.fillMaxSize()) {
                    com.mikepenz.agentbuddy.ui.approvals.ApprovalsScreen(
                        items = listOf(
                            com.mikepenz.agentbuddy.ui.approvals.ApprovalQueueItem(
                                id = "1",
                                tool = "Bash",
                                source = com.mikepenz.agentbuddy.model.Source.CLAUDE_CODE,
                                summary = "rm -rf node_modules && pnpm install",
                                risk = 3, via = "claude", timestamp = "14:02:11",
                                elapsedSeconds = 18, ttlSeconds = 60, session = "abc",
                                prompt = "", workingDir = "/project",
                                riskAssessment = "Filesystem delete, approved 3x recently.",
                            ),
                            com.mikepenz.agentbuddy.ui.approvals.ApprovalQueueItem(
                                id = "2",
                                tool = "Edit",
                                source = com.mikepenz.agentbuddy.model.Source.COPILOT,
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
    com.mikepenz.agentbuddy.ui.theme.PreviewScaffold(
        themeMode = com.mikepenz.agentbuddy.model.ThemeMode.LIGHT,
    ) {
        androidx.compose.runtime.CompositionLocalProvider(
            com.mikepenz.agentbuddy.ui.shell.LocalCommandPaletteController provides
                remember { com.mikepenz.agentbuddy.ui.shell.CommandPaletteController() },
        ) {
            Row(modifier = Modifier.fillMaxSize()) {
                com.mikepenz.agentbuddy.ui.shell.AppSidebar(
                    selectedTab = AppTab.History,
                    onTabSelect = {},
                    pendingCount = 0,
                    appVersion = "1.0.0",
                    serverPort = 19532,
                    agentRegistrations = listOf(
                        com.mikepenz.agentbuddy.ui.AgentRegistration("Claude Code", true),
                        com.mikepenz.agentbuddy.ui.AgentRegistration("GitHub Copilot", false),
                    ),
                )
                Box(modifier = Modifier.fillMaxSize()) {
                    com.mikepenz.agentbuddy.ui.approvals.ApprovalsScreen(items = emptyList())
                }
            }
        }
    }
}

@Composable
private fun PortInUseWindow(themeMode: com.mikepenz.agentbuddy.model.ThemeMode, onExit: () -> Unit) {
    Window(
        onCloseRequest = onExit,
        title = "Port In Use",
        state = rememberWindowState(
            size = DpSize(400.dp, 200.dp),
            position = WindowPosition(Alignment.Center),
        ),
    ) {
        AgentBuddyTheme(themeMode = themeMode) {
            Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                Column(
                    modifier = Modifier.fillMaxSize().padding(24.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterVertically),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Text(
                        "The server port is already in use. Another instance may be running.",
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    Button(onClick = onExit) { Text("Exit") }
                }
            }
        }
    }
}
