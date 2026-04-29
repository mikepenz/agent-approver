package com.mikepenz.agentbelay.state

import com.mikepenz.agentbelay.model.*
import com.mikepenz.agentbelay.storage.DatabaseStorage
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.runBlocking
import kotlinx.datetime.Clock
import kotlinx.serialization.json.JsonPrimitive
import java.io.File
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class AppStateManagerTest {
    private fun makeRequest(id: String) = ApprovalRequest(
        id = id, source = Source.CLAUDE_CODE,
        toolType = ToolType.DEFAULT,
        hookInput = HookInput(
            sessionId = "s1",
            toolName = "Bash",
            toolInput = mapOf("command" to JsonPrimitive("ls")),
            cwd = "/tmp",
        ),
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

    @Test
    fun resolveByCorrelationKeyMatchesAndResolves() {
        val manager = AppStateManager()
        val request = makeRequest("1") // session=s1, tool=Bash, input={command=ls}
        manager.addPending(request)

        val matched = manager.resolveByCorrelationKey(
            sessionId = "s1",
            toolName = "Bash",
            toolInput = mapOf("command" to JsonPrimitive("ls")),
        )

        assertTrue(matched)
        assertTrue(manager.state.value.pendingApprovals.isEmpty())
        assertEquals(Decision.RESOLVED_EXTERNALLY, manager.state.value.history.first().decision)
    }

    @Test
    fun resolveByCorrelationKeyReturnsFalseWhenNoMatch() {
        val manager = AppStateManager()
        manager.addPending(makeRequest("1"))

        val matchedDifferentSession = manager.resolveByCorrelationKey(
            sessionId = "other",
            toolName = "Bash",
            toolInput = mapOf("command" to JsonPrimitive("ls")),
        )
        val matchedDifferentTool = manager.resolveByCorrelationKey(
            sessionId = "s1",
            toolName = "Edit",
            toolInput = mapOf("command" to JsonPrimitive("ls")),
        )
        val matchedDifferentInput = manager.resolveByCorrelationKey(
            sessionId = "s1",
            toolName = "Bash",
            toolInput = mapOf("command" to JsonPrimitive("rm -rf /")),
        )

        assertFalse(matchedDifferentSession)
        assertFalse(matchedDifferentTool)
        assertFalse(matchedDifferentInput)
        assertEquals(1, manager.state.value.pendingApprovals.size)
        assertTrue(manager.state.value.history.isEmpty())
    }

    @Test
    fun resolveCompletesDeferredEvenWhenDbInsertThrows() = runBlocking {
        // A throwing DatabaseStorage simulates a JDBC failure during resolve.
        // The HTTP route on the other side is awaiting the CompletableDeferred,
        // so a DB outage must NOT translate into a hung request.
        val tempDir = File(System.getProperty("java.io.tmpdir"), "asm-test-${System.currentTimeMillis()}").also { it.mkdirs() }
        val throwingDb = object : DatabaseStorage(tempDir.absolutePath) {
            override fun insert(result: ApprovalResult) {
                throw RuntimeException("simulated jdbc failure")
            }
        }
        try {
            val manager = AppStateManager(databaseStorage = throwingDb)
            val request = makeRequest("crash-1")
            val deferred = CompletableDeferred<ApprovalResult>()
            manager.addPending(request, deferred)

            manager.resolve(
                requestId = "crash-1",
                decision = Decision.APPROVED,
                feedback = null,
                riskAnalysis = null,
                rawResponseJson = null,
            )

            // Deferred must be completed even though insert threw.
            assertTrue(deferred.isCompleted)
            assertEquals(Decision.APPROVED, deferred.await().decision)
            // State was still updated.
            assertTrue(manager.state.value.pendingApprovals.isEmpty())
            assertEquals(1, manager.state.value.history.size)
        } finally {
            throwingDb.close()
            tempDir.deleteRecursively()
        }
    }

    @Test
    fun concurrentResolveDoesNotDoubleInsertOrDoubleComplete() = runBlocking {
        // Two callers race to resolve the same request — the loser must see
        // it gone and bail without inserting again or trying to complete a
        // deferred that no longer exists.
        var insertCalls = 0
        val tempDir = File(System.getProperty("java.io.tmpdir"), "asm-test-${System.currentTimeMillis()}").also { it.mkdirs() }
        val countingDb = object : DatabaseStorage(tempDir.absolutePath) {
            override fun insert(result: ApprovalResult) {
                insertCalls++
                super.insert(result)
            }
        }
        try {
            val manager = AppStateManager(databaseStorage = countingDb)
            val deferred = CompletableDeferred<ApprovalResult>()
            manager.addPending(makeRequest("race-1"), deferred)

            manager.resolve("race-1", Decision.APPROVED, "first", null, null)
            // Second resolve of the same id — should silently bail.
            manager.resolve("race-1", Decision.DENIED, "second", null, null)

            assertEquals(1, insertCalls)
            assertEquals(1, manager.state.value.history.size)
            assertEquals(Decision.APPROVED, manager.state.value.history.first().decision)
            // Deferred resolved exactly once with the winning decision.
            assertTrue(deferred.isCompleted)
            assertEquals(Decision.APPROVED, deferred.await().decision)
        } finally {
            countingDb.close()
            tempDir.deleteRecursively()
        }
    }

    @Test
    fun losingResolverDoesNotLeakUpdatedInput() = runBlocking {
        // The losing concurrent resolver previously wrote to
        // pendingUpdatedInputs before the find() check, leaking a stale
        // entry that getAndClearUpdatedInput would later pick up.
        val tempDir = File(System.getProperty("java.io.tmpdir"), "asm-test-${System.currentTimeMillis()}").also { it.mkdirs() }
        val db = DatabaseStorage(tempDir.absolutePath)
        try {
            val manager = AppStateManager(databaseStorage = db)
            manager.addPending(makeRequest("leak-1"))

            manager.resolve("leak-1", Decision.APPROVED, "first", null, null)
            // Loser passes updatedInput — must not leave a leftover entry.
            manager.resolve(
                requestId = "leak-1",
                decision = Decision.DENIED,
                feedback = "second",
                riskAnalysis = null,
                rawResponseJson = null,
                updatedInput = mapOf("ghost" to JsonPrimitive("orphan")),
            )

            // No leaked input from the losing call.
            assertEquals(null, manager.getAndClearUpdatedInput("leak-1"))
        } finally {
            db.close()
            tempDir.deleteRecursively()
        }
    }
}
