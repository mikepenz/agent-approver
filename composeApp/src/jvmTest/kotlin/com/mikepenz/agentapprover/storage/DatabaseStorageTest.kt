package com.mikepenz.agentapprover.storage

import com.mikepenz.agentapprover.model.*
import kotlinx.datetime.Clock
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonPrimitive
import java.io.File
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class DatabaseStorageTest {

    private lateinit var tempDir: File
    private lateinit var storage: DatabaseStorage

    @BeforeTest
    fun setUp() {
        tempDir = File(System.getProperty("java.io.tmpdir"), "db-test-${System.currentTimeMillis()}")
        tempDir.mkdirs()
        storage = DatabaseStorage(tempDir.absolutePath)
    }

    @AfterTest
    fun tearDown() {
        storage.close()
        tempDir.deleteRecursively()
    }

    private fun makeApprovalResult(
        id: String = "req-1",
        source: Source = Source.CLAUDE_CODE,
        toolType: ToolType = ToolType.DEFAULT,
        toolName: String = "Bash",
        sessionId: String = "session-1",
        cwd: String = "/tmp",
        decision: Decision = Decision.APPROVED,
        feedback: String? = null,
        riskAnalysis: RiskAnalysis? = null,
        rawResponseJson: String? = null,
        protectionModule: String? = null,
        protectionRule: String? = null,
        protectionDetail: String? = null,
        toolInput: Map<String, JsonElement> = emptyMap(),
    ): ApprovalResult = ApprovalResult(
        request = ApprovalRequest(
            id = id,
            source = source,
            toolType = toolType,
            hookInput = HookInput(
                sessionId = sessionId,
                toolName = toolName,
                toolInput = toolInput,
                cwd = cwd,
            ),
            timestamp = Clock.System.now(),
            rawRequestJson = """{"tool":"$toolName"}""",
        ),
        decision = decision,
        feedback = feedback,
        riskAnalysis = riskAnalysis,
        rawResponseJson = rawResponseJson,
        decidedAt = Clock.System.now(),
        protectionModule = protectionModule,
        protectionRule = protectionRule,
        protectionDetail = protectionDetail,
    )

    @Test
    fun insertAndLoadApproval() {
        val result = makeApprovalResult(
            id = "test-1",
            source = Source.COPILOT,
            toolType = ToolType.ASK_USER_QUESTION,
            toolName = "ReadFile",
            sessionId = "sess-42",
            cwd = "/home/user",
            decision = Decision.DENIED,
            feedback = "Looks dangerous",
            riskAnalysis = RiskAnalysis(risk = 3, label = "medium", message = "Moderate risk", source = "ai"),
            rawResponseJson = """{"allowed":false}""",
        )

        storage.insert(result)
        val loaded = storage.loadAll()

        assertEquals(1, loaded.size)
        val r = loaded.first()
        assertEquals("test-1", r.request.id)
        assertEquals(Source.COPILOT, r.request.source)
        assertEquals(ToolType.ASK_USER_QUESTION, r.request.toolType)
        assertEquals("ReadFile", r.request.hookInput.toolName)
        assertEquals("sess-42", r.request.hookInput.sessionId)
        assertEquals("/home/user", r.request.hookInput.cwd)
        assertEquals(Decision.DENIED, r.decision)
        assertEquals("Looks dangerous", r.feedback)
        val risk = assertNotNull(r.riskAnalysis)
        assertEquals(3, risk.risk)
        assertEquals("medium", risk.label)
        assertEquals("Moderate risk", risk.message)
        assertEquals("ai", risk.source)
        assertEquals("""{"allowed":false}""", r.rawResponseJson)
        assertNull(r.protectionModule)
        assertEquals(result.request.rawRequestJson, r.request.rawRequestJson)
    }

    @Test
    fun insertAndLoadProtectionHit() {
        val result = makeApprovalResult(
            id = "prot-1",
            decision = Decision.PROTECTION_BLOCKED,
            protectionModule = "destructive-commands",
            protectionRule = "rm-rf",
            protectionDetail = "Blocked rm -rf /",
        )

        storage.insert(result)
        val loaded = storage.loadAll()

        assertEquals(1, loaded.size)
        val r = loaded.first()
        assertEquals("prot-1", r.request.id)
        assertEquals(Decision.PROTECTION_BLOCKED, r.decision)
        assertEquals("destructive-commands", r.protectionModule)
        assertEquals("rm-rf", r.protectionRule)
        assertEquals("Blocked rm -rf /", r.protectionDetail)
    }

    @Test
    fun loadByTypeFilter() {
        val approval = makeApprovalResult(id = "a-1", decision = Decision.APPROVED)
        val protection = makeApprovalResult(
            id = "p-1",
            decision = Decision.PROTECTION_BLOCKED,
            protectionModule = "sensitive-files",
            protectionRule = "env-file",
            protectionDetail = "Blocked .env access",
        )
        val approval2 = makeApprovalResult(id = "a-2", decision = Decision.DENIED)

        storage.insert(approval)
        storage.insert(protection)
        storage.insert(approval2)

        val approvals = storage.loadByType("approval")
        assertEquals(2, approvals.size)
        assertTrue(approvals.all { it.protectionModule == null })

        val protections = storage.loadByType("protection")
        assertEquals(1, protections.size)
        assertEquals("p-1", protections.first().request.id)
    }

    @Test
    fun prunesOldEntries() {
        storage.close()
        storage = DatabaseStorage(tempDir.absolutePath, maxEntries = 5)

        for (i in 1..8) {
            val result = makeApprovalResult(id = "prune-$i")
            storage.insert(result)
        }

        assertEquals(5, storage.count())
        val loaded = storage.loadAll()
        assertEquals(5, loaded.size)
        // Should keep the newest 5 (ids 4..8), returned newest-first
        assertEquals("prune-8", loaded.first().request.id)
        assertEquals("prune-4", loaded.last().request.id)
    }

    @Test
    fun updateRawResponse() {
        val result = makeApprovalResult(id = "upd-1", rawResponseJson = null)
        storage.insert(result)

        assertNull(storage.loadAll().first().rawResponseJson)

        storage.updateRawResponse("upd-1", """{"result":"ok"}""")

        val loaded = storage.loadAll()
        assertEquals("""{"result":"ok"}""", loaded.first().rawResponseJson)
    }

    @Test
    fun clearAll() {
        storage.insert(makeApprovalResult(id = "c-1"))
        storage.insert(makeApprovalResult(id = "c-2"))
        assertEquals(2, storage.count())

        storage.clearAll()

        assertEquals(0, storage.count())
        assertTrue(storage.loadAll().isEmpty())
    }

    @Test
    fun count() {
        storage.insert(makeApprovalResult(id = "cnt-1"))
        storage.insert(makeApprovalResult(id = "cnt-2"))
        assertEquals(2, storage.count())
    }

    @Test
    fun toolInputRoundTrip() {
        val grepInput = mapOf<String, JsonElement>(
            "pattern" to JsonPrimitive("NATIVE_LIB_VERSION\\s*="),
            "path" to JsonPrimitive("/tmp/project"),
            "glob" to JsonPrimitive("*.kt"),
        )
        storage.insert(
            makeApprovalResult(
                id = "grep-1",
                toolName = "Grep",
                toolInput = grepInput,
            ),
        )

        val loaded = storage.loadAll().single()
        assertEquals(grepInput, loaded.request.hookInput.toolInput)
    }

    @Test
    fun toolInputRoundTripFromCopilotAdapter() {
        // Simulate the exact Copilot flow: parse camelCase permissionRequest,
        // create ApprovalResult, store in DB, load back, verify command is present.
        val adapter = com.mikepenz.agentapprover.server.CopilotAdapter()
        val rawJson = """
            {
                "hookName": "permissionRequest",
                "sessionId": "dae8d1b3-a912-450d-8379-69ed2f9259e1",
                "timestamp": 1775825147685,
                "cwd": "/home/user/projects/example-app",
                "toolName": "bash",
                "toolInput": {
                    "command": "find /home/user/projects/example-app -type f -name '*.kt' | head -20"
                },
                "permissionSuggestions": []
            }
        """.trimIndent()

        val request = adapter.parse(rawJson)!!
        assertEquals("Bash", request.hookInput.toolName)
        assertEquals(
            JsonPrimitive("find /home/user/projects/example-app -type f -name '*.kt' | head -20"),
            request.hookInput.toolInput["command"],
        )

        val result = ApprovalResult(
            request = request,
            decision = Decision.AUTO_APPROVED,
            feedback = "Auto-approved: risk level 1",
            riskAnalysis = RiskAnalysis(risk = 1, label = "Safe", message = "Read-only"),
            rawResponseJson = """{"behavior":"allow"}""",
            decidedAt = kotlinx.datetime.Clock.System.now(),
        )

        storage.insert(result)

        val loaded = storage.loadAll().single()
        assertEquals("Bash", loaded.request.hookInput.toolName)
        val loadedCommand = loaded.request.hookInput.toolInput["command"]
        assertNotNull(loadedCommand, "tool_input_json should contain 'command' key after DB round-trip")
        assertEquals(
            "find /home/user/projects/example-app -type f -name '*.kt' | head -20",
            loadedCommand.jsonPrimitive.content,
        )
    }

    @Test
    fun toolInputRoundTripFromClaudeCodeAdapter() {
        // Simulate the Claude Code flow
        val adapter = com.mikepenz.agentapprover.server.ClaudeCodeAdapter()
        val rawJson = """
            {
                "session_id": "28777844-8497-45d3-99be-e50c85cb7e97",
                "cwd": "/home/user/projects/example-app",
                "tool_name": "Bash",
                "tool_input": {
                    "command": "ls ~",
                    "description": "List home directory"
                },
                "hook_event_name": "PermissionRequest",
                "permission_mode": "default"
            }
        """.trimIndent()

        val request = adapter.parse(rawJson)!!
        assertEquals("Bash", request.hookInput.toolName)
        assertEquals(JsonPrimitive("ls ~"), request.hookInput.toolInput["command"])

        val result = ApprovalResult(
            request = request,
            decision = Decision.AUTO_APPROVED,
            feedback = "Auto-approved: risk level 1",
            riskAnalysis = null,
            rawResponseJson = null,
            decidedAt = kotlinx.datetime.Clock.System.now(),
        )

        storage.insert(result)

        val loaded = storage.loadAll().single()
        assertEquals("Bash", loaded.request.hookInput.toolName)
        val loadedCommand = loaded.request.hookInput.toolInput["command"]
        assertNotNull(loadedCommand, "tool_input_json should contain 'command' key after DB round-trip")
        assertEquals("ls ~", loadedCommand.jsonPrimitive.content)
        assertEquals("List home directory", loaded.request.hookInput.toolInput["description"]?.jsonPrimitive?.content)
    }

    @Test
    fun backfillPopulatesToolInputFromRawRequestJson() {
        // Simulate a row that was inserted before tool_input_json existed:
        // tool_input_json is '{}' but raw_request_json has the full payload.
        val rawJson = """{"session_id":"s1","tool_name":"Bash","tool_input":{"command":"npm test","description":"Run tests"},"cwd":"/tmp"}"""
        val insertSql = """
            INSERT INTO history (
                id, type, source, tool_name, tool_type, session_id, cwd,
                decision, feedback,
                raw_request_json, raw_response_json,
                requested_at, decided_at, tool_input_json
            ) VALUES (?, 'approval', 'CLAUDE_CODE', 'Bash', 'DEFAULT', 's1', '/tmp',
                      'APPROVED', null, ?, null, ?, ?, '{}')
        """.trimIndent()

        // Insert directly via SQL to simulate a pre-migration row
        val conn = java.sql.DriverManager.getConnection(
            "jdbc:sqlite:${java.io.File(tempDir, "agent-approver.db").absolutePath}"
        )
        conn.prepareStatement(insertSql).use { ps ->
            ps.setString(1, "backfill-1")
            ps.setString(2, rawJson)
            ps.setString(3, kotlinx.datetime.Clock.System.now().toString())
            ps.setString(4, kotlinx.datetime.Clock.System.now().toString())
            ps.executeUpdate()
        }
        conn.close()

        // Re-open storage — triggers backfill in createSchema()
        storage.close()
        storage = DatabaseStorage(tempDir.absolutePath)

        val loaded = storage.loadAll().single()
        assertEquals("Bash", loaded.request.hookInput.toolName)
        val cmd = loaded.request.hookInput.toolInput["command"]
        assertNotNull(cmd, "backfill should have populated tool_input_json from raw_request_json")
        assertEquals("npm test", cmd.jsonPrimitive.content)
    }
}
