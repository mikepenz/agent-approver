package com.mikepenz.agentbelay.server

import com.mikepenz.agentbelay.capability.CapabilityEngine
import com.mikepenz.agentbelay.model.CapabilitySettings
import com.mikepenz.agentbelay.model.Decision
import com.mikepenz.agentbelay.model.ProtectionSettings
import com.mikepenz.agentbelay.protection.ProtectionEngine
import com.mikepenz.agentbelay.state.AppStateManager
import java.net.ServerSocket
import java.net.Socket
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Verifies that when an HTTP client (the Claude Code or Copilot CLI bridge)
 * drops the TCP connection while a permission request is still pending,
 * agent-belay detects the disconnect, removes the entry from the pending
 * list, and records it in history as RESOLVED_EXTERNALLY.
 *
 * This validates the [io.ktor.server.http.HttpRequestLifecycle] plugin
 * installation in [ApprovalServer] (Ktor 3.4+). Without that plugin,
 * Ktor on Netty does not propagate client TCP FIN into the suspended
 * handler coroutine, and the catch (CancellationException) blocks in
 * the route handlers never fire.
 */
class DisconnectDetectionTest {

    private lateinit var stateManager: AppStateManager
    private lateinit var server: ApprovalServer
    private var port: Int = 0

    @BeforeTest
    fun setUp() {
        stateManager = AppStateManager()
        val protectionEngine = ProtectionEngine(
            modules = emptyList(),
            settingsProvider = { ProtectionSettings() },
        )
        val capabilityEngine = CapabilityEngine(
            modules = emptyList(),
            settingsProvider = { CapabilitySettings() },
        )
        server = ApprovalServer(
            stateManager = stateManager,
            protectionEngine = protectionEngine,
            capabilityEngine = capabilityEngine,
            databaseStorage = null,
            onNewApproval = {},
        )
        port = freePort()
        server.start(port = port, host = "127.0.0.1")
        // Give Netty a moment to bind
        Thread.sleep(200)
    }

    @AfterTest
    fun tearDown() {
        server.stop()
    }

    @Test
    fun claudeApproveRouteCleansUpOnClientDisconnect() {
        val body = """{"session_id":"s1","cwd":"/tmp","hook_event_name":"PermissionRequest","tool_name":"Bash","tool_input":{"command":"ls"}}"""
        sendPostThenDisconnect("/approve", body)

        waitFor("pending entry to be cleared") {
            stateManager.state.value.pendingApprovals.isEmpty() &&
                stateManager.state.value.history.any { it.decision == Decision.RESOLVED_EXTERNALLY }
        }

        assertTrue(stateManager.state.value.pendingApprovals.isEmpty())
        val historyEntry = stateManager.state.value.history.first()
        assertEquals(Decision.RESOLVED_EXTERNALLY, historyEntry.decision)
        assertEquals("Bash", historyEntry.request.hookInput.toolName)
    }

    @Test
    fun secondRequestAfterDisconnectStillWorks() {
        // Regression: after the HttpRequestLifecycle plugin cancels the first
        // call due to client FIN, a second request arriving on a fresh
        // connection must still be parked normally as pending — not
        // immediately cancelled.
        val body1 = """{"session_id":"s1","cwd":"/tmp","hook_event_name":"PermissionRequest","tool_name":"Bash","tool_input":{"command":"first"}}"""
        sendPostThenDisconnect("/approve", body1)
        waitFor("first request to be cleared") {
            stateManager.state.value.pendingApprovals.isEmpty()
        }

        // Fire a second request and confirm it actually parks as pending
        // for at least 500ms (i.e. is NOT immediately cancelled).
        val body2 = """{"session_id":"s2","cwd":"/tmp","hook_event_name":"PermissionRequest","tool_name":"Bash","tool_input":{"command":"second"}}"""
        val socket = Socket("127.0.0.1", port)
        try {
            val out = socket.getOutputStream()
            val request = buildString {
                append("POST /approve HTTP/1.1\r\n")
                append("Host: localhost:$port\r\n")
                append("Content-Type: application/json\r\n")
                append("Content-Length: ${body2.toByteArray().size}\r\n")
                append("Connection: close\r\n")
                append("\r\n")
                append(body2)
            }
            out.write(request.toByteArray())
            out.flush()

            waitFor("second request to register as pending") {
                stateManager.state.value.pendingApprovals.any { it.hookInput.toolInput["command"]?.toString()?.contains("second") == true }
            }
            // Hold the request open and verify it stays parked (not auto-cancelled)
            Thread.sleep(500)
            assertTrue(
                stateManager.state.value.pendingApprovals.any { it.hookInput.toolInput["command"]?.toString()?.contains("second") == true },
                "second request should still be pending — was prematurely cancelled",
            )
        } finally {
            socket.close()
        }
    }

    @Test
    fun postToolUseClearsStalePendingEntry() {
        // Simulate the canUseTool race in claude-code: a PermissionRequest
        // arrives, the user approves in the TUI, claude-code runs the tool
        // (firing PostToolUse) but never closes our hook connection. The
        // PostToolUse event must clear the stale pending entry.
        val approveBody = """{"session_id":"sess-xyz","cwd":"/tmp","hook_event_name":"PermissionRequest","tool_name":"Bash","tool_input":{"command":"echo hi"}}"""
        val socket = Socket("127.0.0.1", port)
        try {
            val out = socket.getOutputStream()
            out.write(buildHttpPost("/approve", approveBody).toByteArray())
            out.flush()

            waitFor("PermissionRequest to register") {
                stateManager.state.value.pendingApprovals.isNotEmpty()
            }

            // Fire the PostToolUse event with matching correlation key on a
            // separate connection (the original connection deliberately
            // stays open — this is the bug we're working around).
            val postBody = """{"session_id":"sess-xyz","cwd":"/tmp","hook_event_name":"PostToolUse","tool_name":"Bash","tool_input":{"command":"echo hi"},"tool_response":{"output":"hi"}}"""
            val response = httpPost("/post-tool-use", postBody)
            assertTrue(response.startsWith("HTTP/1.1 200"), "Expected 200 OK from /post-tool-use, got: ${response.lineSequence().firstOrNull()}")

            waitFor("stale pending entry to be cleared by PostToolUse") {
                stateManager.state.value.pendingApprovals.isEmpty() &&
                    stateManager.state.value.history.any { it.decision == Decision.RESOLVED_EXTERNALLY }
            }

            assertEquals(Decision.RESOLVED_EXTERNALLY, stateManager.state.value.history.first().decision)
        } finally {
            socket.close()
        }
    }

    @Test
    fun postToolUseWithoutMatchingPendingIsNoOp() {
        // PostToolUse for a tool we never saw a PermissionRequest for must
        // not throw, must not affect history, must return 200.
        val postBody = """{"session_id":"sess-x","cwd":"/tmp","hook_event_name":"PostToolUse","tool_name":"Bash","tool_input":{"command":"foo"},"tool_response":{"output":"ok"}}"""
        val response = httpPost("/post-tool-use", postBody)
        assertTrue(response.startsWith("HTTP/1.1 200"))
        assertTrue(stateManager.state.value.pendingApprovals.isEmpty())
        assertTrue(stateManager.state.value.history.isEmpty())
    }

    @Test
    fun copilotApproveRouteCleansUpOnClientDisconnect() {
        val body = """{"toolName":"bash","toolArgs":"{\"command\":\"ls\"}","timestamp":1704614600000,"cwd":"/tmp"}"""
        sendPostThenDisconnect("/approve-copilot", body)

        waitFor("pending entry to be cleared") {
            stateManager.state.value.pendingApprovals.isEmpty() &&
                stateManager.state.value.history.any { it.decision == Decision.RESOLVED_EXTERNALLY }
        }

        assertTrue(stateManager.state.value.pendingApprovals.isEmpty())
        val historyEntry = stateManager.state.value.history.first()
        assertEquals(Decision.RESOLVED_EXTERNALLY, historyEntry.decision)
        assertEquals("Bash", historyEntry.request.hookInput.toolName)
    }

    /**
     * Opens a raw TCP socket, writes a complete HTTP/1.1 POST request,
     * waits long enough for the server to register the request as pending,
     * then closes the socket without reading the response — simulating the
     * harness deciding internally and dropping the connection.
     */
    private fun sendPostThenDisconnect(path: String, body: String) {
        Socket("127.0.0.1", port).use { socket ->
            val out = socket.getOutputStream()
            out.write(buildHttpPost(path, body).toByteArray())
            out.flush()

            // Give the server time to parse the request and register it as pending.
            waitFor("request to register as pending") {
                stateManager.state.value.pendingApprovals.isNotEmpty()
            }
            assertEquals(1, stateManager.state.value.pendingApprovals.size)
        }
        // Socket.close() at end of `use` block sends FIN — server should detect.
    }

    /** Builds a complete HTTP/1.1 POST request with the given path and body. */
    private fun buildHttpPost(path: String, body: String): String = buildString {
        append("POST $path HTTP/1.1\r\n")
        append("Host: localhost:$port\r\n")
        append("Content-Type: application/json\r\n")
        append("Content-Length: ${body.toByteArray().size}\r\n")
        append("Connection: close\r\n")
        append("\r\n")
        append(body)
    }

    /**
     * Sends a POST and reads the full HTTP response (status line + headers
     * + body) until the server closes the connection. Used for fire-and-
     * forget endpoints like /post-tool-use that respond and exit.
     */
    private fun httpPost(path: String, body: String): String {
        Socket("127.0.0.1", port).use { socket ->
            socket.getOutputStream().apply {
                write(buildHttpPost(path, body).toByteArray())
                flush()
            }
            return socket.getInputStream().bufferedReader().readText()
        }
    }

    private fun waitFor(description: String, timeoutMs: Long = 3_000, predicate: () -> Boolean) {
        val deadline = System.currentTimeMillis() + timeoutMs
        while (System.currentTimeMillis() < deadline) {
            if (predicate()) return
            Thread.sleep(20)
        }
        error("Timed out waiting for $description")
    }

    private fun freePort(): Int = ServerSocket(0).use { it.localPort }
}
