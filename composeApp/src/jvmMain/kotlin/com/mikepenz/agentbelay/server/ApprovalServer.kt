package com.mikepenz.agentbelay.server

import co.touchlab.kermit.Logger
import com.mikepenz.agentbelay.capability.CapabilityEngine
import com.mikepenz.agentbelay.harness.Harness
import com.mikepenz.agentbelay.harness.HarnessRouteDeps
import com.mikepenz.agentbelay.harness.HookEvent
import com.mikepenz.agentbelay.harness.claudecode.ClaudeCodeAdapter
import com.mikepenz.agentbelay.harness.claudecode.ClaudeCodeHarness
import com.mikepenz.agentbelay.harness.codex.CodexHarness
import com.mikepenz.agentbelay.harness.copilot.CopilotHarness
import com.mikepenz.agentbelay.harness.opencode.OpenCodeHarness
import com.mikepenz.agentbelay.harness.pi.PiHarness
import com.mikepenz.agentbelay.protection.ProtectionEngine
import com.mikepenz.agentbelay.redaction.RedactionEngine
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
    private val redactionEngine: RedactionEngine,
    private val databaseStorage: DatabaseStorage?,
    private val onNewApproval: () -> Unit,
) {
    private val logger = Logger.withTag("ApprovalServer")

    /**
     * Live harness list. Adding a new harness is exactly one entry here
     * — the [harnessApprovalRoute] / [harnessPreToolUseRoute] generic
     * handlers in [HarnessRoutes] consume the [Harness] interface
     * directly, so no per-harness route files are needed.
     */
    private val harnesses: List<Harness> = listOf(
        ClaudeCodeHarness(),
        CopilotHarness(),
        OpenCodeHarness(),
        PiHarness(),
        CodexHarness(),
    )

    private val claudeCode = harnesses.first { it is ClaudeCodeHarness }
    private val claudeAdapter = claudeCode.adapter as ClaudeCodeAdapter
    private val codex = harnesses.first { it is CodexHarness }

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
                    // the agent's TUI. Available since Ktor 3.4.0.
                    install(HttpRequestLifecycle) {
                        cancelCallOnClose = true
                    }

                    // Per-harness routes — each harness installs its own
                    // route graph through [Harness.installRoutes]. The
                    // default implementation calls the generic
                    // [harnessApprovalRoute] + [harnessPreToolUseRoute]
                    // handlers; harnesses with non-standard protocols
                    // (e.g. an MCP elicitation event, websocket transport,
                    // or a third response branch like Gemini's
                    // `decision: "ask"`) can override to install bespoke
                    // routes alongside or instead of the generic ones.
                    val routeDeps = HarnessRouteDeps(
                        stateManager = stateManager,
                        protectionEngine = protectionEngine,
                        onNewApproval = onNewApproval,
                    )
                    for (harness in harnesses) {
                        harness.installRoutes(this, routeDeps)
                    }

                    // PostToolUse is Claude Code-only today (Copilot's
                    // postToolUse cannot modify output and OpenCode has
                    // no equivalent event). Wire the Claude harness
                    // directly until/unless another harness gains the
                    // capability.
                    postToolUseRoute(
                        stateManager = stateManager,
                        adapter = claudeAdapter,
                        redactionEngine = redactionEngine,
                        supportsOutputRedaction = claudeCode.capabilities.supportsOutputRedaction,
                    )
                    codex.transport.endpoints()[HookEvent.POST_TOOL_USE]?.let { path ->
                        postToolUseRoute(
                            stateManager = stateManager,
                            adapter = codex.adapter,
                            redactionEngine = redactionEngine,
                            supportsOutputRedaction = codex.capabilities.supportsOutputRedaction,
                            path = path,
                        )
                    }
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
