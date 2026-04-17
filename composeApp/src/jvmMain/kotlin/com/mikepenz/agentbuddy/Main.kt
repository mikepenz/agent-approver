package com.mikepenz.agentbuddy

import androidx.compose.ui.window.application
import com.mikepenz.agentbuddy.di.AppEnvironment
import com.mikepenz.agentbuddy.di.AppGraph
import com.mikepenz.agentbuddy.storage.LegacyDataMigration
import com.mikepenz.agentbuddy.ui.AgentBuddyShell
import com.mikepenz.agentbuddy.ui.theme.configureLogging
import dev.zacsweers.metro.createGraphFactory
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import java.io.File

fun getAppDataDir(): String {
    val osName = System.getProperty("os.name").lowercase()
    val home = System.getProperty("user.home")
    return when {
        osName.contains("mac") -> "$home/Library/Application Support/AgentBuddy"
        osName.contains("win") -> "${System.getenv("APPDATA") ?: "$home/AppData/Roaming"}/AgentBuddy"
        else -> "$home/.local/share/AgentBuddy"
    }
}

private fun getLegacyAppDataDir(): String {
    val osName = System.getProperty("os.name").lowercase()
    val home = System.getProperty("user.home")
    return when {
        osName.contains("mac") -> "$home/Library/Application Support/AgentApprover"
        osName.contains("win") -> "${System.getenv("APPDATA") ?: "$home/AppData/Roaming"}/AgentApprover"
        else -> "$home/.local/share/AgentApprover"
    }
}

fun main(args: Array<String>) {
    // Enable macOS template images so the tray icon adapts to menu bar background colour.
    System.setProperty("apple.awt.enableTemplateImages", "true")
    configureLogging()

    val devMode = "--dev" in args || System.getProperty("agentbuddy.devmode") == "true"

    // One-shot migration from the old "Agent Approver" install layout. Runs
    // before the data dir is created so a clean rename (instead of merge) is
    // possible. Idempotent — no-op on fresh installs and after first migration.
    LegacyDataMigration.run(legacyDataDir = getLegacyAppDataDir(), newDataDir = getAppDataDir())

    val dataDir = getAppDataDir().also { File(it).mkdirs() }

    val appScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    val graph: AppGraph = createGraphFactory<AppGraph.Factory>().create(
        AppEnvironment(dataDir = dataDir, devMode = devMode, appScope = appScope),
    )

    application {
        AgentBuddyShell(graph = graph, devMode = devMode, exitApplication = ::exitApplication)
    }
}
