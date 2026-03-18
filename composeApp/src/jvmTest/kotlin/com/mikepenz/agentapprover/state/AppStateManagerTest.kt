package com.mikepenz.agentapprover.state

import com.mikepenz.agentapprover.model.*
import kotlinx.datetime.Clock
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class AppStateManagerTest {
    private fun makeRequest(id: String) = ApprovalRequest(
        id = id, source = Source.CLAUDE_CODE, toolName = "Bash",
        toolType = ToolType.DEFAULT,
        toolInput = JsonObject(mapOf("command" to JsonPrimitive("ls"))),
        sessionId = "s1", cwd = "/tmp",
        timestamp = Clock.System.now(), rawRequestJson = "{}"
    )

    @Test
    fun addPendingApproval() {
        val manager = AppStateManager()
        val request = makeRequest("1")
        manager.addPending(request)
        assertEquals(1, manager.state.value.pendingApprovals.size)
        assertEquals(request, manager.state.value.pendingApprovals.first())
    }

    @Test
    fun resolveApprovalMovesToHistory() {
        val manager = AppStateManager()
        val request = makeRequest("1")
        manager.addPending(request)
        manager.resolve(requestId = "1", decision = Decision.APPROVED, feedback = null, riskAnalysis = null, rawResponseJson = "{}")
        assertTrue(manager.state.value.pendingApprovals.isEmpty())
        assertEquals(1, manager.state.value.history.size)
        assertEquals(Decision.APPROVED, manager.state.value.history.first().decision)
    }

    @Test
    fun removePending() {
        val manager = AppStateManager()
        manager.addPending(makeRequest("1"))
        manager.addPending(makeRequest("2"))
        manager.removePending("1")
        assertEquals(1, manager.state.value.pendingApprovals.size)
        assertEquals("2", manager.state.value.pendingApprovals.first().id)
    }
}
