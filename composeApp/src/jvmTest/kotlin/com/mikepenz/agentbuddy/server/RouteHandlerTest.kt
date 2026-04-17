package com.mikepenz.agentbuddy.server

import com.mikepenz.agentbuddy.capability.CapabilityEngine
import com.mikepenz.agentbuddy.model.AppSettings
import com.mikepenz.agentbuddy.model.CapabilitySettings
import com.mikepenz.agentbuddy.model.Decision
import com.mikepenz.agentbuddy.model.ProtectionSettings
import com.mikepenz.agentbuddy.protection.ProtectionEngine
import com.mikepenz.agentbuddy.state.AppStateManager
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.net.ServerSocket
import java.net.Socket
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Unit tests for all HTTP route handlers. Uses a real embedded Ktor server
 * with raw TCP sockets for full-path integration without a dedicated HTTP
 * client dependency.
 */
class RouteHandlerTest {

    private lateinit var stateManager: AppStateManager
    private lateinit var server: ApprovalServer
    private var port: Int = 0
    private var newApprovalCount = 0

    @BeforeTest
    fun setUp() {
        stateManager = AppStateManager()
        newApprovalCount = 0
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
            onNewApproval = { newApprovalCount++ },
        )
        port = freePort()
        server.start(port = port, host = "127.0.0.1")
        Thread.sleep(200)
    }

    @AfterTest
    fun tearDown() {
        server.stop()
    }

    // ---- /approve route tests ----

    @Test
    fun approveRouteReturnsAllowOnApproval() {
        val body = claudeCodeBody("Bash", """{"command":"ls"}""")
        val response = httpPostWithResolve("/approve", body) { requestId ->
            stateManager.resolve(requestId, Decision.APPROVED, "ok", null, null)
        }
        val json = Json.parseToJsonElement(extractBody(response)).jsonObject
        val decision = json["hookSpecificOutput"]?.jsonObject?.get("decision")?.jsonObject
        assertEquals("allow", decision?.get("behavior")?.jsonPrimitive?.content)
    }

    @Test
    fun approveRouteReturnsDenyOnDenial() {
        val body = claudeCodeBody("Bash", """{"command":"rm -rf /"}""")
        val response = httpPostWithResolve("/approve", body) { requestId ->
            stateManager.resolve(requestId, Decision.DENIED, "Dangerous command", null, null)
        }
        val json = Json.parseToJsonElement(extractBody(response)).jsonObject
        val decision = json["hookSpecificOutput"]?.jsonObject?.get("decision")?.jsonObject
        assertEquals("deny", decision?.get("behavior")?.jsonPrimitive?.content)
        assertEquals("Dangerous command", decision?.get("message")?.jsonPrimitive?.content)
    }

    @Test
    fun approveRouteReturnsAlwaysAllowWithPermissions() {
        val body = claudeCodeBody("Bash", """{"command":"ls"}""")
        val response = httpPostWithResolve("/approve", body) { requestId ->
            stateManager.resolve(requestId, Decision.ALWAYS_ALLOWED, "Always allowed", null, null)
        }
        val json = Json.parseToJsonElement(extractBody(response)).jsonObject
        val decision = json["hookSpecificOutput"]?.jsonObject?.get("decision")?.jsonObject
        assertEquals("allow", decision?.get("behavior")?.jsonPrimitive?.content)
    }

    @Test
    fun approveRouteReturnsBadRequestForInvalidJson() {
        val response = httpPost("/approve", "not json at all")
        assertTrue(response.contains("400"), "Expected 400 status, got: ${response.lineSequence().firstOrNull()}")
    }

    @Test
    fun approveRouteTimesOutWithDeny() {
        // Set a very short timeout
        stateManager.updateSettings(AppSettings(defaultTimeoutSeconds = 1))

        val response = httpPost("/approve", claudeCodeBody("Bash", """{"command":"ls"}"""))
        val json = Json.parseToJsonElement(extractBody(response)).jsonObject
        val decision = json["hookSpecificOutput"]?.jsonObject?.get("decision")?.jsonObject
        assertEquals("deny", decision?.get("behavior")?.jsonPrimitive?.content)

        // Should be recorded as TIMEOUT in history
        waitFor("timeout recorded") { stateManager.state.value.history.any { it.decision == Decision.TIMEOUT } }
    }

    @Test
    fun approveRouteInvokesOnNewApprovalCallback() {
        val body = claudeCodeBody("Bash", """{"command":"ls"}""")
        httpPostWithResolve("/approve", body) { requestId ->
            stateManager.resolve(requestId, Decision.APPROVED, null, null, null)
        }
        assertTrue(newApprovalCount > 0, "onNewApproval should have been called")
    }

    // ---- /approve-copilot route tests ----

    @Test
    fun copilotRouteAutoAllowsReportIntent() {
        val body = copilotBody("report_intent")
        val response = httpPost("/approve-copilot", body)
        val json = Json.parseToJsonElement(extractBody(response)).jsonObject
        assertEquals("allow", json["behavior"]?.jsonPrimitive?.content)
        // Should NOT register as pending
        assertTrue(stateManager.state.value.pendingApprovals.isEmpty())
    }

    @Test
    fun copilotRouteReturnsAllowOnApproval() {
        val body = copilotBody("bash")
        val response = httpPostWithResolve("/approve-copilot", body) { requestId ->
            stateManager.resolve(requestId, Decision.APPROVED, null, null, null)
        }
        val json = Json.parseToJsonElement(extractBody(response)).jsonObject
        assertEquals("allow", json["behavior"]?.jsonPrimitive?.content)
    }

    @Test
    fun copilotRouteReturnsDenyOnDenial() {
        val body = copilotBody("bash")
        val response = httpPostWithResolve("/approve-copilot", body) { requestId ->
            stateManager.resolve(requestId, Decision.DENIED, "Blocked", null, null)
        }
        val json = Json.parseToJsonElement(extractBody(response)).jsonObject
        assertEquals("deny", json["behavior"]?.jsonPrimitive?.content)
        assertEquals("Blocked", json["message"]?.jsonPrimitive?.content)
    }

    @Test
    fun copilotRouteReturnsBadRequestForInvalidJson() {
        val response = httpPost("/approve-copilot", "broken")
        assertTrue(response.contains("400"), "Expected 400 status")
    }

    // ---- /pre-tool-use route tests ----

    @Test
    fun preToolUseRouteAllowsWhenNoProtectionHits() {
        val body = claudeCodeBody("Bash", """{"command":"echo hello"}""")
        val response = httpPost("/pre-tool-use", body)
        assertTrue(extractBody(response) == "{}")
    }

    @Test
    fun preToolUseRouteReturnsEmptyForInvalidJson() {
        val response = httpPost("/pre-tool-use", "garbage")
        assertTrue(extractBody(response) == "{}")
    }

    // ---- /post-tool-use route tests ----

    @Test
    fun postToolUseRouteAlwaysReturns200() {
        val body = """{"session_id":"s1","cwd":"/tmp","hook_event_name":"PostToolUse","tool_name":"Bash","tool_input":{"command":"ls"},"tool_response":{"output":"file.txt"}}"""
        val response = httpPost("/post-tool-use", body)
        assertTrue(response.startsWith("HTTP/1.1 200"), "Expected 200")
    }

    @Test
    fun postToolUseRouteReturns200ForInvalidJson() {
        val response = httpPost("/post-tool-use", "bad json")
        assertTrue(response.startsWith("HTTP/1.1 200"), "Expected 200 even for bad JSON")
    }

    // ---- /capability/inject route tests ----

    @Test
    fun capabilityRouteReturnsEmptyWhenNoModules() {
        val response = httpPost("/capability/inject", "{}")
        assertTrue(extractBody(response) == "{}")
    }

    // ---- /capability/inject-copilot route tests ----

    @Test
    fun capabilityCopilotRouteReturnsEmptyWhenNoModules() {
        val response = httpPost("/capability/inject-copilot", "{}")
        assertTrue(extractBody(response) == "{}")
    }

    // ---- Server shutdown tests ----

    @Test
    fun serverStopResolvesAllPendingRequests() {
        val body = claudeCodeBody("Bash", """{"command":"ls"}""")
        val socket = Socket("127.0.0.1", port)
        try {
            socket.getOutputStream().apply {
                write(buildHttpPost("/approve", body).toByteArray())
                flush()
            }
            waitFor("request pending") { stateManager.state.value.pendingApprovals.isNotEmpty() }

            // Stop server — should resolve all pending
            server.stop()

            waitFor("pending cleared") { stateManager.state.value.pendingApprovals.isEmpty() }
            assertTrue(
                stateManager.state.value.history.any { it.decision == Decision.RESOLVED_EXTERNALLY },
                "Should have RESOLVED_EXTERNALLY entry in history"
            )
        } finally {
            socket.close()
        }
    }

    // ---- Helpers ----

    private fun claudeCodeBody(tool: String, toolInput: String): String =
        """{"session_id":"s1","cwd":"/tmp","hook_event_name":"PermissionRequest","tool_name":"$tool","tool_input":$toolInput}"""

    private fun copilotBody(tool: String): String =
        """{"toolName":"$tool","toolArgs":"{}","timestamp":1704614600000,"cwd":"/tmp"}"""

    /**
     * Sends a POST, waits for the request to register as pending, resolves
     * it via [resolver], and returns the full HTTP response.
     */
    private fun httpPostWithResolve(path: String, body: String, resolver: (String) -> Unit): String {
        val socket = Socket("127.0.0.1", port)
        return try {
            socket.soTimeout = 5000
            socket.getOutputStream().apply {
                write(buildHttpPost(path, body).toByteArray())
                flush()
            }
            waitFor("request pending") { stateManager.state.value.pendingApprovals.isNotEmpty() }
            val requestId = stateManager.state.value.pendingApprovals.first().id
            resolver(requestId)
            socket.getInputStream().bufferedReader().readText()
        } finally {
            socket.close()
        }
    }

    private fun httpPost(path: String, body: String): String {
        Socket("127.0.0.1", port).use { socket ->
            socket.soTimeout = 10000
            socket.getOutputStream().apply {
                write(buildHttpPost(path, body).toByteArray())
                flush()
            }
            return socket.getInputStream().bufferedReader().readText()
        }
    }

    private fun buildHttpPost(path: String, body: String): String = buildString {
        append("POST $path HTTP/1.1\r\n")
        append("Host: localhost:$port\r\n")
        append("Content-Type: application/json\r\n")
        append("Content-Length: ${body.toByteArray().size}\r\n")
        append("Connection: close\r\n")
        append("\r\n")
        append(body)
    }

    /** Extracts the HTTP response body (after the blank line). */
    private fun extractBody(response: String): String {
        val idx = response.indexOf("\r\n\r\n")
        if (idx < 0) return response
        val body = response.substring(idx + 4)
        // Handle chunked transfer encoding: strip chunk size lines
        if (response.contains("Transfer-Encoding: chunked", ignoreCase = true)) {
            return parseChunked(body)
        }
        return body.trim()
    }

    private fun parseChunked(raw: String): String {
        val sb = StringBuilder()
        var pos = 0
        while (pos < raw.length) {
            val lineEnd = raw.indexOf("\r\n", pos)
            if (lineEnd < 0) break
            val sizeLine = raw.substring(pos, lineEnd).trim()
            val chunkSize = try { sizeLine.toInt(16) } catch (_: NumberFormatException) { break }
            if (chunkSize == 0) break
            val dataStart = lineEnd + 2
            val dataEnd = (dataStart + chunkSize).coerceAtMost(raw.length)
            sb.append(raw.substring(dataStart, dataEnd))
            pos = dataEnd + 2 // skip trailing \r\n after chunk data
        }
        return sb.toString()
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
