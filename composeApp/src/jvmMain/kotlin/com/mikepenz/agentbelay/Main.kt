package com.mikepenz.agentbelay

import androidx.compose.ui.window.application
import com.mikepenz.agentbelay.di.AppEnvironment
import com.mikepenz.agentbelay.di.AppGraph
import com.mikepenz.agentbelay.hook.DefaultHookRegistry
import com.mikepenz.agentbelay.storage.IntegrationRefresh
import com.mikepenz.agentbelay.storage.LegacyDataMigration
import com.mikepenz.agentbelay.ui.AgentBelayShell
import com.mikepenz.agentbelay.ui.theme.configureLogging
import dev.zacsweers.metro.createGraphFactory
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import java.io.File

fun getAppDataDir(): String = appDataDirFor("AgentBelay")

private fun getAgentBuddyAppDataDir(): String = appDataDirFor("AgentBuddy")

private fun getAgentApproverAppDataDir(): String = appDataDirFor("AgentApprover")

private fun appDataDirFor(brand: String): String {
    val osName = System.getProperty("os.name").lowercase()
    val home = System.getProperty("user.home")
    return when {
        osName.contains("mac") -> "$home/Library/Application Support/$brand"
        osName.contains("win") -> "${System.getenv("APPDATA") ?: "$home/AppData/Roaming"}/$brand"
        else -> "$home/.local/share/$brand"
    }
}

fun main(args: Array<String>) {
    // Enable macOS template images so the tray icon adapts to menu bar background colour.
    System.setProperty("apple.awt.enableTemplateImages", "true")
    configureLogging()

    val devMode = "--dev" in args || System.getProperty("agentbelay.devmode") == "true"

    // One-shot migrations from previous brands. The app has been renamed twice
    // — Agent Approver → Agent Buddy → Agent Belay — so we run one step per
    // legacy brand in chronological order. Idempotent: each step no-ops on
    // fresh installs and after first migration.
    val migrated = listOf(
        LegacyDataMigration.run(
            LegacyDataMigration.Step(
                legacyDataDir = getAgentApproverAppDataDir(),
                newDataDir = getAgentBuddyAppDataDir(),
                legacyDbFile = "agent-approver.db",
                newDbFile = "agent-buddy.db",
                legacyHookDirName = ".agent-approver",
                newHookDirName = ".agent-buddy",
                legacyCopilotHookFile = "agent-approver.json",
                newCopilotHookFile = "agent-buddy.json",
            ),
        ),
        LegacyDataMigration.run(
            LegacyDataMigration.Step(
                legacyDataDir = getAgentBuddyAppDataDir(),
                newDataDir = getAppDataDir(),
                legacyDbFile = "agent-buddy.db",
                newDbFile = "agent-belay.db",
                legacyHookDirName = ".agent-buddy",
                newHookDirName = ".agent-belay",
                legacyCopilotHookFile = "agent-buddy.json",
                newCopilotHookFile = "agent-belay.json",
            ),
        ),
    ).any { it }

    val dataDir = getAppDataDir().also { File(it).mkdirs() }
    if (migrated) IntegrationRefresh.markPending(dataDir)

    val appScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    val graph: AppGraph = createGraphFactory<AppGraph.Factory>().create(
        AppEnvironment(dataDir = dataDir, devMode = devMode, appScope = appScope),
    )

    // Refresh on-disk bridge scripts and hook configs from the current
    // build's templates for whichever integrations were active before the
    // rename. No-op when the marker is absent (fresh install / already
    // refreshed). Runs synchronously so the registrars finish before the
    // server starts and the UI queries integration state.
    val settings = graph.settingsStorage.load()
    IntegrationRefresh.runIfNeeded(
        dataDir = dataDir,
        port = settings.serverPort,
        copilotFailClosed = settings.copilotFailClosed,
        hookRegistry = DefaultHookRegistry,
    )

    // Fire a silent update check on startup (subject to user preference and
    // a 24h throttle). Result lands in UpdateManager.state and is surfaced by
    // the in-app banner; callers don't await it.
    graph.autoUpdateChecker.runIfDue()

    // Dev-mode UI scaffolding: jvmRun builds aren't installed packages, so
    // `UpdateManager.isSupported` is false and the auto-check skips. Drive a
    // fake `Available` state so the banner is visible for design / QA work.
    // Production builds never enter this branch.
    if (devMode) graph.updateManager.simulateAvailable("dev-${System.currentTimeMillis() / 1000}")

    // Start the Usage tab's session-file scanner. It honours the
    // `usageTrackingEnabled` setting per-pass, so the loop stays running but
    // becomes a no-op when the user disables tracking.
    graph.usageIngestService.start()

    application {
        AgentBelayShell(graph = graph, devMode = devMode, exitApplication = ::exitApplication)
    }
}
