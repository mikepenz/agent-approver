package com.mikepenz.agentbuddy.ui.history

import com.mikepenz.agentbuddy.di.AppEnvironment
import com.mikepenz.agentbuddy.model.ApprovalRequest
import com.mikepenz.agentbuddy.model.ApprovalResult
import com.mikepenz.agentbuddy.model.Decision
import com.mikepenz.agentbuddy.model.HookInput
import com.mikepenz.agentbuddy.model.Source
import com.mikepenz.agentbuddy.model.ToolType
import com.mikepenz.agentbuddy.state.AppStateManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlinx.datetime.Clock
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class HistoryViewModelTest {

    private val mainDispatcher = StandardTestDispatcher()

    @BeforeTest
    fun setUp() {
        Dispatchers.setMain(mainDispatcher)
    }

    @AfterTest
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun env(devMode: Boolean = true) = AppEnvironment(
        dataDir = "/tmp/test",
        devMode = devMode,
        appScope = CoroutineScope(SupervisorJob()),
    )

    private fun result(
        id: String,
        toolName: String = "Bash",
        source: Source = Source.CLAUDE_CODE,
    ) = ApprovalResult(
        request = ApprovalRequest(
            id = id,
            source = source,
            toolType = ToolType.DEFAULT,
            hookInput = HookInput(sessionId = "s", toolName = toolName),
            timestamp = Clock.System.now(),
            rawRequestJson = "{}",
        ),
        decision = Decision.APPROVED,
        feedback = null,
        riskAnalysis = null,
        rawResponseJson = null,
        decidedAt = Clock.System.now(),
    )

    @Test
    fun `filterHistory returns all entries when source filter is null`() {
        val items = listOf(
            result("a", source = Source.CLAUDE_CODE),
            result("b", source = Source.COPILOT),
        )
        val filtered = filterHistory(items, query = "", typeFilter = "all", sourceFilter = null)
        assertEquals(2, filtered.size)
    }

    @Test
    fun `filterHistory keeps only matching source`() {
        val items = listOf(
            result("a", source = Source.CLAUDE_CODE),
            result("b", source = Source.COPILOT),
            result("c", source = Source.COPILOT),
        )
        val claude = filterHistory(items, "", "all", Source.CLAUDE_CODE)
        assertEquals(listOf("a"), claude.map { it.request.id })

        val copilot = filterHistory(items, "", "all", Source.COPILOT)
        assertEquals(listOf("b", "c"), copilot.map { it.request.id })
    }

    @Test
    fun `filterHistory combines source filter with text query`() {
        val items = listOf(
            result("a", toolName = "Bash", source = Source.CLAUDE_CODE),
            result("b", toolName = "Grep", source = Source.COPILOT),
            result("c", toolName = "Bash", source = Source.COPILOT),
        )
        val filtered = filterHistory(items, query = "bash", typeFilter = "all", sourceFilter = Source.COPILOT)
        assertEquals(listOf("c"), filtered.map { it.request.id })
    }

    @Test
    fun `replay clones the request with a new id and adds to pending`() = runTest {
        val state = AppStateManager()
        state.addToHistory(result("orig-1", toolName = "Grep"))
        val vm = HistoryViewModel(state, env())
        runCurrent()

        val original = state.state.value.history.first()
        vm.replay(original)
        runCurrent()

        val pending = state.state.value.pendingApprovals
        assertEquals(1, pending.size)
        val replayed = pending.first()
        assertNotEquals(original.request.id, replayed.id)
        assertEquals("Grep", replayed.hookInput.toolName)
    }

    @Test
    fun `history StateFlow reflects state manager`() = runTest {
        val state = AppStateManager()
        val vm = HistoryViewModel(state, env())
        runCurrent()
        assertTrue(vm.history.value.isEmpty())

        state.addToHistory(result("h-1"))
        runCurrent()
        assertEquals(1, vm.history.value.size)
    }

    @Test
    fun `devMode comes from environment`() = runTest {
        val vmDev = HistoryViewModel(AppStateManager(), env(devMode = true))
        val vmProd = HistoryViewModel(AppStateManager(), env(devMode = false))
        assertTrue(vmDev.devMode)
        assertEquals(false, vmProd.devMode)
    }
}
