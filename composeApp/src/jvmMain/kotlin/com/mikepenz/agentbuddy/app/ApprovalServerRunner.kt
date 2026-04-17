package com.mikepenz.agentbuddy.app

import com.mikepenz.agentbuddy.capability.CapabilityEngine
import com.mikepenz.agentbuddy.di.AppScope
import com.mikepenz.agentbuddy.protection.ProtectionEngine
import com.mikepenz.agentbuddy.server.ApprovalServer
import com.mikepenz.agentbuddy.state.AppStateManager
import com.mikepenz.agentbuddy.storage.DatabaseStorage
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import java.net.BindException

/**
 * App-scoped wrapper around [ApprovalServer] that classifies the inevitable
 * `BindException` (port already in use, typically because another instance of
 * Agent Buddy is already running) as a typed [StartResult.PortInUse] so the
 * shell can show its "Port In Use" window without re-implementing exception
 * unwrapping at every call site.
 *
 * The [ApprovalServer] itself is constructed lazily inside [start] because it
 * takes an `onNewApproval` callback that needs to know about the [TrayManager]
 * — which is wired up by the shell composable, not by the DI graph.
 */
@SingleIn(AppScope::class)
@Inject
class ApprovalServerRunner(
    private val stateManager: AppStateManager,
    private val protectionEngine: ProtectionEngine,
    private val capabilityEngine: CapabilityEngine,
    private val databaseStorage: DatabaseStorage,
) {
    private var server: ApprovalServer? = null

    sealed interface StartResult {
        data object Ok : StartResult
        data object PortInUse : StartResult
    }

    /**
     * Construct the server and start it on the configured port. [onNewApproval]
     * is invoked from the server thread when a new approval arrives — typically
     * used to bring the main window forward.
     */
    fun start(onNewApproval: () -> Unit): StartResult {
        val newServer = ApprovalServer(
            stateManager = stateManager,
            protectionEngine = protectionEngine,
            capabilityEngine = capabilityEngine,
            databaseStorage = databaseStorage,
            onNewApproval = { onNewApproval() },
        )
        server = newServer
        return try {
            val settings = stateManager.state.value.settings
            newServer.start(port = settings.serverPort, host = settings.serverHost)
            StartResult.Ok
        } catch (e: BindException) {
            StartResult.PortInUse
        } catch (e: Exception) {
            if (e.cause is BindException) StartResult.PortInUse else throw e
        }
    }

    fun stop() {
        server?.stop()
        server = null
    }
}
