package com.mikepenz.agentbelay.server

import com.mikepenz.agentbelay.capability.CapabilityEngine
import com.mikepenz.agentbelay.model.AppSettings
import com.mikepenz.agentbelay.model.CapabilitySettings
import com.mikepenz.agentbelay.model.Decision
import com.mikepenz.agentbelay.model.HookInput
import com.mikepenz.agentbelay.model.ProtectionHit
import com.mikepenz.agentbelay.model.ProtectionMode
import com.mikepenz.agentbelay.model.ModuleSettings
import com.mikepenz.agentbelay.model.ProtectionSettings
import com.mikepenz.agentbelay.protection.ProtectionEngine
import com.mikepenz.agentbelay.protection.ProtectionModule
import com.mikepenz.agentbelay.protection.ProtectionRule
import com.mikepenz.agentbelay.state.AppStateManager
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
            redactionEngine = com.mikepenz.agentbelay.redaction.RedactionEngine(
                modules = emptyList(),
                settingsProvider = { com.mikepenz.agentbelay.model.RedactionSettings(enabled = false) },
            ),
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

    @Test
    fun dynamicSettingsAreRespectedByProtectionEngine() {
        // Build the engine with a mutable settings holder so we can swap settings
        // after the server starts — simulating the user toggling a module in the UI.
        var currentSettings = ProtectionSettings(
            modules = mapOf("test-module" to ModuleSettings(mode = ProtectionMode.AUTO_BLOCK))
        )
        val fakeRule = object : ProtectionRule {
            override val id = "test-rule"
            override val name = "Test Rule"
            override val description = "Always fires"
            override fun evaluate(hookInput: HookInput) = ProtectionHit(
                moduleId = "test-module", ruleId = id, message = "hit", mode = ProtectionMode.AUTO_BLOCK
            )
        }
        val fakeModule = object : ProtectionModule {
            override val id = "test-module"
            override val name = "Test Module"
            override val description = "Test"
            override val corrective = false
            override val defaultMode = ProtectionMode.AUTO_BLOCK
            override val applicableTools = setOf("Bash")
            override val rules = listOf(fakeRule)
        }
        val protectionEngine = ProtectionEngine(listOf(fakeModule)) { currentSettings }
        val capabilityEngine = CapabilityEngine(emptyList()) { CapabilitySettings() }
        server = ApprovalServer(
            stateManager = stateManager,
            protectionEngine = protectionEngine,
            capabilityEngine = capabilityEngine,
            redactionEngine = com.mikepenz.agentbelay.redaction.RedactionEngine(
                modules = emptyList(),
                settingsProvider = { com.mikepenz.agentbelay.model.RedactionSettings(enabled = false) },
            ),
            databaseStorage = null,
            onNewApproval = {},
        )
        port = freePort()
        server.start(port = port, host = "127.0.0.1")
        Thread.sleep(200)

        // First request: AUTO_BLOCK → deny
        val body = claudeCodeBody("Bash", """{"command":"echo hi"}""")
        val resp1 = httpPost("/pre-tool-use", body)
        val out1 = Json.parseToJsonElement(extractBody(resp1)).jsonObject["hookSpecificOutput"]?.jsonObject
        assertEquals("deny", out1?.get("permissionDecision")?.jsonPrimitive?.content)

        // Simulate user changing module to LOG_ONLY in settings
        currentSettings = ProtectionSettings(
            modules = mapOf("test-module" to ModuleSettings(mode = ProtectionMode.LOG_ONLY))
        )

        // Second request: same command, now LOG_ONLY → allow
        val resp2 = httpPost("/pre-tool-use", body)
        val out2 = Json.parseToJsonElement(extractBody(resp2)).jsonObject["hookSpecificOutput"]?.jsonObject
        assertEquals("allow", out2?.get("permissionDecision")?.jsonPrimitive?.content)
    }

    @Test
    fun multiModuleMixedModesBlocksWhenOneModuleIsAutoBlock() {
        // Reproduces the real-world scenario: user sets one module to LOG_ONLY
        // but a second module (AUTO_BLOCK default) also fires on the same command.
        // The overall decision must still be "deny" because highestSeverity = AUTO_BLOCK.
        val logOnlyRule = object : ProtectionRule {
            override val id = "log-rule"
            override val name = "Log Rule"
            override val description = "Always fires, LOG_ONLY"
            override fun evaluate(hookInput: HookInput) = ProtectionHit(
                moduleId = "log-module", ruleId = id, message = "log hit", mode = ProtectionMode.LOG_ONLY
            )
        }
        val blockRule = object : ProtectionRule {
            override val id = "block-rule"
            override val name = "Block Rule"
            override val description = "Always fires, AUTO_BLOCK"
            override fun evaluate(hookInput: HookInput) = ProtectionHit(
                moduleId = "block-module", ruleId = id, message = "block hit", mode = ProtectionMode.AUTO_BLOCK
            )
        }
        val logModule = object : ProtectionModule {
            override val id = "log-module"
            override val name = "Log Module"
            override val description = "Log"
            override val corrective = false
            override val defaultMode = ProtectionMode.LOG_ONLY
            override val applicableTools = setOf("Bash")
            override val rules = listOf(logOnlyRule)
        }
        val blockModule = object : ProtectionModule {
            override val id = "block-module"
            override val name = "Block Module"
            override val description = "Block"
            override val corrective = false
            override val defaultMode = ProtectionMode.AUTO_BLOCK
            override val applicableTools = setOf("Bash")
            override val rules = listOf(blockRule)
        }
        val settings = ProtectionSettings(
            modules = mapOf("log-module" to ModuleSettings(mode = ProtectionMode.LOG_ONLY))
            // block-module has no entry → uses AUTO_BLOCK default
        )
        val protectionEngine = ProtectionEngine(listOf(logModule, blockModule)) { settings }
        val capabilityEngine = CapabilityEngine(emptyList()) { CapabilitySettings() }
        val redactionEngine = com.mikepenz.agentbelay.redaction.RedactionEngine(
            modules = emptyList(),
            settingsProvider = { com.mikepenz.agentbelay.model.RedactionSettings(enabled = false) },
        )
        server = ApprovalServer(
            stateManager = stateManager,
            protectionEngine = protectionEngine,
            capabilityEngine = capabilityEngine,
            redactionEngine = redactionEngine,
            databaseStorage = null,
            onNewApproval = {},
        )
        port = freePort()
        server.start(port = port, host = "127.0.0.1")
        Thread.sleep(200)

        val body = claudeCodeBody("Bash", """{"command":"echo hi"}""")
        val response = httpPost("/pre-tool-use", body)
        val output = Json.parseToJsonElement(extractBody(response)).jsonObject["hookSpecificOutput"]?.jsonObject
        assertEquals("deny", output?.get("permissionDecision")?.jsonPrimitive?.content,
            "AUTO_BLOCK from the second module should win over LOG_ONLY from the first")

        waitFor("history entry") { stateManager.state.value.history.isNotEmpty() }
        val historyEntry = stateManager.state.value.history.first()
        assertEquals(Decision.PROTECTION_BLOCKED, historyEntry.decision)
        // The history record must attribute the block to the AUTO_BLOCK module, not the LOG_ONLY one.
        assertEquals("block-module", historyEntry.protectionModule,
            "primaryHit should be the strictest hit (AUTO_BLOCK), not the first-registered (LOG_ONLY)")
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
