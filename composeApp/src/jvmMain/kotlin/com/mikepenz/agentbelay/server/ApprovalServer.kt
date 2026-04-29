package com.mikepenz.agentbelay.server

import co.touchlab.kermit.Logger
import com.mikepenz.agentbelay.capability.CapabilityEngine
import com.mikepenz.agentbelay.protection.ProtectionEngine
import com.mikepenz.agentbelay.state.AppStateManager
import com.mikepenz.agentbelay.storage.DatabaseStorage
import io.ktor.serialization.kotlinx.json.*
import io.ktor.server.application.*
import io.ktor.server.engine.*
import io.ktor.server.http.HttpRequestLifecycle
import io.ktor.server.netty.*
import io.ktor.server.plugins.contentnegotiation.*
import io.ktor.server.routing.*
import kotlinx.serialization.json.Json

class ApprovalServer(
    private val stateManager: AppStateManager,
    private val protectionEngine: ProtectionEngine,
    private val capabilityEngine: CapabilityEngine,
    private val databaseStorage: DatabaseStorage?,
    private val onNewApproval: () -> Unit,
) {
    private val logger = Logger.withTag("ApprovalServer")
    private val adapter = ClaudeCodeAdapter()
    private val copilotAdapter = CopilotAdapter()
    private var server: EmbeddedServer<NettyApplicationEngine, NettyApplicationEngine.Configuration>? = null

    fun start(port: Int, host: String) {
        val env = applicationEnvironment()

        server = embeddedServer(
            factory = Netty,
            environment = env,
            configure = {
                connector {
                    this.port = port
                    this.host = host
                }
                responseWriteTimeoutSeconds = 0
            },
            module = {
                install(ContentNegotiation) {
                    json(Json { ignoreUnknownKeys = true })
                }
                routing {
                    // HttpRequestLifecycle is a route-scoped plugin: install it
                    // inside routing so it properly applies per-call. Propagates
                    // client TCP FIN into call.coroutineContext so suspended
                    // handlers (waiting on CompletableDeferred) get a
                    // CancellationException when the harness drops the
                    // connection — e.g. user approved/denied directly inside
                    // Claude Code or Copilot CLI's TUI. Available since Ktor 3.4.0.
                    install(HttpRequestLifecycle) {
                        cancelCallOnClose = true
                    }
                    approvalRoute(stateManager, adapter, onNewApproval)
                    copilotApprovalRoute(stateManager, copilotAdapter, onNewApproval)
                    copilotPreToolUseRoute(stateManager, copilotAdapter, protectionEngine, onNewApproval)
                    preToolUseRoute(stateManager, adapter, protectionEngine, onNewApproval)
                    postToolUseRoute(stateManager)
                    capabilityRoute(capabilityEngine)
                }
            },
        ).start(wait = false)

        logger.i { "Approval server started on $host:$port (write timeout: disabled)" }
    }

    fun stop() {
        // Resolve all pending approvals before stopping so that waiting
        // HTTP handlers receive a response instead of hanging indefinitely.
        stateManager.resolveAllPending()
        server?.stop(gracePeriodMillis = 1000, timeoutMillis = 3000)
        server = null
        logger.i { "Approval server stopped" }
    }
}
