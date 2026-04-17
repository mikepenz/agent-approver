package com.mikepenz.agentbuddy.server

import com.mikepenz.agentbuddy.capability.CapabilityEngine
import com.mikepenz.agentbuddy.model.AppSettings
import com.mikepenz.agentbuddy.model.CapabilitySettings
import com.mikepenz.agentbuddy.model.Decision
import com.mikepenz.agentbuddy.model.HookInput
import com.mikepenz.agentbuddy.model.ProtectionHit
import com.mikepenz.agentbuddy.model.ProtectionMode
import com.mikepenz.agentbuddy.model.ModuleSettings
import com.mikepenz.agentbuddy.model.ProtectionSettings
import com.mikepenz.agentbuddy.protection.ProtectionEngine
import com.mikepenz.agentbuddy.protection.ProtectionModule
import com.mikepenz.agentbuddy.protection.ProtectionRule
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
 * Tests for pre-tool-use routes with protection engine hits.
 * Uses a fake protection module that always fires to test
 * AUTO_BLOCK, LOG_ONLY, and ASK mode behaviors.
 */
class PreToolUseRouteTest {

    private lateinit var stateManager: AppStateManager
    private lateinit var server: ApprovalServer
    private var port: Int = 0
    private var hitMode: ProtectionMode = ProtectionMode.AUTO_BLOCK

    @BeforeTest
    fun setUp() {
        stateManager = AppStateManager()
    }

    @AfterTest
    fun tearDown() {
        if (::server.isInitialized) server.stop()
    }

    private fun startServer(mode: ProtectionMode) {
        hitMode = mode
        val fakeRule = object : ProtectionRule {
            override val id = "test-rule"
            override val name = "Test Rule"
            override val description = "Always fires"
            override fun evaluate(hookInput: HookInput): ProtectionHit {
                return ProtectionHit(
                    moduleId = "test-module",
                    ruleId = id,
                    message = "Test protection hit",
                    mode = hitMode,
                )
            }
        }
        val fakeModule = object : ProtectionModule {
            override val id = "test-module"
            override val name = "Test Module"
            override val description = "Test"
            override val corrective = false
            override val defaultMode = hitMode
            override val applicableTools = setOf("Bash")
            override val rules = listOf(fakeRule)
        }
        val protectionSettings = ProtectionSettings(
            modules = mapOf("test-module" to ModuleSettings(mode = mode)),
        )
        val protectionEngine = ProtectionEngine(
            modules = listOf(fakeModule),
            settingsProvider = { protectionSettings },
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
        Thread.sleep(200)
    }

    // ---- Claude Code /pre-tool-use ----

    @Test
    fun preToolUseAutoBlockReturnsDeny() {
        startServer(ProtectionMode.AUTO_BLOCK)
        val body = claudeCodeBody("Bash", """{"command":"rm -rf /"}""")
        val response = httpPost("/pre-tool-use", body)
        val json = Json.parseToJsonElement(extractBody(response)).jsonObject
        val output = json["hookSpecificOutput"]?.jsonObject
        assertEquals("deny", output?.get("permissionDecision")?.jsonPrimitive?.content)

        // Should be logged in history
        waitFor("history entry") { stateManager.state.value.history.isNotEmpty() }
        assertEquals(Decision.PROTECTION_BLOCKED, stateManager.state.value.history.first().decision)
    }

    @Test
    fun preToolUseLogOnlyReturnsAllow() {
        startServer(ProtectionMode.LOG_ONLY)
        val body = claudeCodeBody("Bash", """{"command":"echo hi"}""")
        val response = httpPost("/pre-tool-use", body)
        val json = Json.parseToJsonElement(extractBody(response)).jsonObject
        val output = json["hookSpecificOutput"]?.jsonObject
        assertEquals("allow", output?.get("permissionDecision")?.jsonPrimitive?.content)

        // Should be logged in history as PROTECTION_LOGGED
        waitFor("history entry") { stateManager.state.value.history.isNotEmpty() }
        assertEquals(Decision.PROTECTION_LOGGED, stateManager.state.value.history.first().decision)
    }

    @Test
    fun preToolUseAskModeParksRequest() {
        startServer(ProtectionMode.ASK)
        val body = claudeCodeBody("Bash", """{"command":"danger"}""")
        val socket = Socket("127.0.0.1", port)
        try {
            socket.getOutputStream().apply {
                write(buildHttpPost("/pre-tool-use", body).toByteArray())
                flush()
            }
            waitFor("pending request") { stateManager.state.value.pendingApprovals.isNotEmpty() }

            // Resolve as overridden (user approves despite protection hit)
            val requestId = stateManager.state.value.pendingApprovals.first().id
            stateManager.resolve(requestId, Decision.PROTECTION_OVERRIDDEN, "User approved", null, null)

            socket.soTimeout = 5000
            val response = socket.getInputStream().bufferedReader().readText()
            val json = Json.parseToJsonElement(extractBody(response)).jsonObject
            val output = json["hookSpecificOutput"]?.jsonObject
            assertEquals("allow", output?.get("permissionDecision")?.jsonPrimitive?.content)
        } finally {
            socket.close()
        }
    }

    // ---- Copilot /pre-tool-use-copilot ----

    @Test
    fun copilotPreToolUseAutoBlockReturnsDeny() {
        startServer(ProtectionMode.AUTO_BLOCK)
        val body = copilotBody("bash")
        val response = httpPost("/pre-tool-use-copilot", body)
        val json = Json.parseToJsonElement(extractBody(response)).jsonObject
        assertEquals("deny", json["permissionDecision"]?.jsonPrimitive?.content)
    }

    @Test
    fun copilotPreToolUseLogOnlyReturnsAllow() {
        startServer(ProtectionMode.LOG_ONLY)
        val body = copilotBody("bash")
        val response = httpPost("/pre-tool-use-copilot", body)
        val json = Json.parseToJsonElement(extractBody(response)).jsonObject
        assertEquals("allow", json["permissionDecision"]?.jsonPrimitive?.content)
    }

    // ---- Helpers ----

    private fun claudeCodeBody(tool: String, toolInput: String): String =
        """{"session_id":"s1","cwd":"/tmp","hook_event_name":"PreToolUse","tool_name":"$tool","tool_input":$toolInput}"""

    private fun copilotBody(tool: String): String =
        """{"toolName":"$tool","toolArgs":"{}","timestamp":1704614600000,"cwd":"/tmp"}"""

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

    private fun extractBody(response: String): String {
        val idx = response.indexOf("\r\n\r\n")
        if (idx < 0) return response
        val body = response.substring(idx + 4)
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
            pos = dataEnd + 2
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
