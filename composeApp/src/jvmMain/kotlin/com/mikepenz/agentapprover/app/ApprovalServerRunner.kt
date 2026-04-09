package com.mikepenz.agentapprover.app

import com.mikepenz.agentapprover.di.AppScope
import com.mikepenz.agentapprover.protection.ProtectionEngine
import com.mikepenz.agentapprover.server.ApprovalServer
import com.mikepenz.agentapprover.state.AppStateManager
import com.mikepenz.agentapprover.storage.DatabaseStorage
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import java.net.BindException

/**
 * App-scoped wrapper around [ApprovalServer] that classifies the inevitable
 * `BindException` (port already in use, typically because another instance of
 * Agent Approver is already running) as a typed [StartResult.PortInUse] so the
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
            databaseStorage = databaseStorage,
            onNewApproval = { onNewApproval() },
        )
        server = newServer
        return try {
            newServer.start(stateManager.state.value.settings.serverPort)
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
