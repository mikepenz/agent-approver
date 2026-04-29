package com.mikepenz.agentbelay.ui.history

import com.mikepenz.agentbelay.di.AppEnvironment
import com.mikepenz.agentbelay.model.ApprovalRequest
import com.mikepenz.agentbelay.model.ApprovalResult
import com.mikepenz.agentbelay.model.Decision
import com.mikepenz.agentbelay.model.HookInput
import com.mikepenz.agentbelay.model.Source
import com.mikepenz.agentbelay.model.ToolType
import com.mikepenz.agentbelay.state.AppStateManager
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
