package com.mikepenz.agentapprover.server

import co.touchlab.kermit.Logger
import com.mikepenz.agentapprover.state.AppStateManager
import io.ktor.serialization.kotlinx.json.*
import io.ktor.server.application.*
import io.ktor.server.engine.*
import io.ktor.server.netty.*
import io.ktor.server.plugins.contentnegotiation.*
import io.ktor.server.routing.*
import kotlinx.serialization.json.Json

class ApprovalServer(
    private val stateManager: AppStateManager,
    private val onNewApproval: () -> Unit,
) {
    private val logger = Logger.withTag("ApprovalServer")
    private val adapter = ClaudeCodeAdapter()
    private var server: EmbeddedServer<NettyApplicationEngine, NettyApplicationEngine.Configuration>? = null

    fun start(port: Int) {
        val settings = stateManager.state.value.settings
        val writeTimeoutSeconds = maxOf((settings.defaultTimeoutSeconds + 30) * 1000L, 600_000L) / 1000

        val env = applicationEnvironment()

        server = embeddedServer(
            factory = Netty,
            environment = env,
            configure = {
                connector { this.port = port }
                responseWriteTimeoutSeconds = writeTimeoutSeconds.toInt()
            },
            module = {
                install(ContentNegotiation) {
                    json(Json { ignoreUnknownKeys = true })
                }
                routing {
                    approvalRoute(stateManager, adapter, onNewApproval)
                }
            },
        ).start(wait = false)

        logger.i { "Approval server started on port $port (write timeout: ${writeTimeoutSeconds}s)" }
    }

    fun stop() {
        server?.stop(gracePeriodMillis = 1000, timeoutMillis = 3000)
        server = null
        logger.i { "Approval server stopped" }
    }
}
