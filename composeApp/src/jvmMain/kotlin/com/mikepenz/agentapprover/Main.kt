package com.mikepenz.agentapprover

import androidx.compose.ui.window.application
import com.mikepenz.agentapprover.di.AppEnvironment
import com.mikepenz.agentapprover.di.AppGraph
import com.mikepenz.agentapprover.ui.AgentApproverShell
import com.mikepenz.agentapprover.ui.theme.configureLogging
import dev.zacsweers.metro.createGraphFactory
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import java.io.File

fun getAppDataDir(): String {
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

    val devMode = "--dev" in args || System.getProperty("agentapprover.devmode") == "true"
    val dataDir = getAppDataDir().also { File(it).mkdirs() }

    val appScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    val graph: AppGraph = createGraphFactory<AppGraph.Factory>().create(
        AppEnvironment(dataDir = dataDir, devMode = devMode, appScope = appScope),
    )

    application {
        AgentApproverShell(graph = graph, devMode = devMode, exitApplication = ::exitApplication)
    }
}
