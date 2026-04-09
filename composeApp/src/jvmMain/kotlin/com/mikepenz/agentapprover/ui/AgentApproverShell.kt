package com.mikepenz.agentapprover.ui

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
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.WindowPosition
import androidx.compose.ui.window.rememberWindowState
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.ViewModelStoreOwner
import androidx.lifecycle.viewmodel.compose.LocalViewModelStoreOwner
import com.mikepenz.agentapprover.app.ApprovalServerRunner
import com.mikepenz.agentapprover.app.rememberPersistedWindowState
import com.mikepenz.agentapprover.di.AppGraph
import kotlinx.coroutines.cancel
import com.mikepenz.agentapprover.ui.approvals.ApprovalsViewModel
import com.mikepenz.agentapprover.ui.detail.ContentDetailWindow
import com.mikepenz.agentapprover.ui.detail.LicensesWindow
import com.mikepenz.agentapprover.ui.theme.AgentApproverTheme
import dev.zacsweers.metrox.viewmodel.LocalMetroViewModelFactory
import io.github.kdroidfilter.nucleus.window.material.MaterialDecoratedWindow
import io.github.kdroidfilter.nucleus.window.material.MaterialTitleBar
import java.awt.Desktop
import java.awt.desktop.AppReopenedListener

/**
 * Top-level composable hosting everything inside the desktop `application { }`
 * block. Pulled out of `Main.kt` in Phase 4 so `Main.kt` becomes a thin
 * bootstrap (logging + dataDir + graph + `application { AgentApproverShell() }`).
 *
 * Wires the application-scoped managers ([com.mikepenz.agentapprover.app.TrayManager],
 * [com.mikepenz.agentapprover.app.RiskAnalyzerLifecycle], [ApprovalServerRunner])
 * to the Compose runtime, provides the `LocalViewModelStoreOwner` and
 * `LocalMetroViewModelFactory` composition locals, and renders the main
 * window, port-error window, licenses window, and pop-out detail window.
 *
 * Must be called from inside `androidx.compose.ui.window.application { }` so
 * [exitApplication] is the host's terminator.
 */
@Composable
fun AgentApproverShell(graph: AppGraph, devMode: Boolean, exitApplication: () -> Unit) {
    val stateManager = graph.stateManager
    val trayManager = graph.trayManager
    val isVisible by trayManager.visible.collectAsState()
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

    // Tray icon + badge + notifications. install() returns an AutoCloseable
    // that removes the icon and cancels the observer jobs.
    DisposableEffect(Unit) {
        val installation = trayManager.install(onQuit = exitApplication)
        onDispose { installation.close() }
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
        AgentApproverTheme(themeMode = appState.settings.themeMode) {
            MaterialDecoratedWindow(
                onCloseRequest = { trayManager.hide() },
                title = "Agent Approver",
                state = windowState,
            ) {
                LaunchedEffect(appState.settings.alwaysOnTop) {
                    window.isAlwaysOnTop = appState.settings.alwaysOnTop
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
                    CompositionLocalProvider(
                        LocalViewModelStoreOwner provides viewModelStoreOwner,
                        LocalMetroViewModelFactory provides graph.metroViewModelFactory,
                    ) {
                        App(
                            onPopOut = { title, content -> popOutState = title to content },
                            onShowLicenses = { showLicenses = true },
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

@Composable
private fun PortInUseWindow(themeMode: com.mikepenz.agentapprover.model.ThemeMode, onExit: () -> Unit) {
    Window(
        onCloseRequest = onExit,
        title = "Port In Use",
        state = rememberWindowState(
            size = DpSize(400.dp, 200.dp),
            position = WindowPosition(Alignment.Center),
        ),
    ) {
        AgentApproverTheme(themeMode = themeMode) {
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
