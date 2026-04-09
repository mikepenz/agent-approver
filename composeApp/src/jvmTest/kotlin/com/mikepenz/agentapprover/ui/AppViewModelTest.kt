package com.mikepenz.agentapprover.ui

import com.mikepenz.agentapprover.di.AppEnvironment
import com.mikepenz.agentapprover.model.ApprovalRequest
import com.mikepenz.agentapprover.model.HookInput
import com.mikepenz.agentapprover.model.Source
import com.mikepenz.agentapprover.model.ToolType
import com.mikepenz.agentapprover.state.AppStateManager
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
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class AppViewModelTest {

    private val mainDispatcher = StandardTestDispatcher()

    @BeforeTest
    fun setUp() {
        Dispatchers.setMain(mainDispatcher)
    }

    @AfterTest
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun env(devMode: Boolean = false) = AppEnvironment(
        dataDir = "/tmp/test",
        devMode = devMode,
        appScope = CoroutineScope(SupervisorJob()),
    )

    private fun newRequest(id: String = "r-1") = ApprovalRequest(
        id = id,
        source = Source.CLAUDE_CODE,
        toolType = ToolType.DEFAULT,
        hookInput = HookInput(sessionId = "s", toolName = "Bash"),
        timestamp = Clock.System.now(),
        rawRequestJson = "{}",
    )

    @Test
    fun `tabState reflects pending count and away mode`() = runTest {
        val state = AppStateManager()
        val vm = AppViewModel(state, env(devMode = false))
        runCurrent()

        assertEquals(0, vm.tabState.value.pendingCount)
        assertEquals(false, vm.tabState.value.awayMode)
        assertEquals(false, vm.tabState.value.devMode)

        state.addPending(newRequest("a"))
        state.addPending(newRequest("b"))
        runCurrent()

        assertEquals(2, vm.tabState.value.pendingCount)

        state.updateSettings(state.state.value.settings.copy(awayMode = true))
        runCurrent()

        assertTrue(vm.tabState.value.awayMode)
    }

    @Test
    fun `selectTab updates the selected index`() = runTest {
        val vm = AppViewModel(AppStateManager(), env())
        runCurrent()

        assertEquals(0, vm.selectedTab.value)
        vm.selectTab(2)
        assertEquals(2, vm.selectedTab.value)
    }

    @Test
    fun `devMode flag comes from environment`() = runTest {
        val vm = AppViewModel(AppStateManager(), env(devMode = true))
        runCurrent()
        assertTrue(vm.tabState.value.devMode)
    }

    @Test
    fun `resolveTab maps indexes correctly in dev and non-dev`() {
        // Non-dev: Approvals, History, Settings
        assertEquals(AppTab.Approvals, resolveTab(0, devMode = false))
        assertEquals(AppTab.History, resolveTab(1, devMode = false))
        assertEquals(AppTab.Settings, resolveTab(2, devMode = false))

        // Dev: Approvals, History, ProtectionLog, Settings
        assertEquals(AppTab.Approvals, resolveTab(0, devMode = true))
        assertEquals(AppTab.History, resolveTab(1, devMode = true))
        assertEquals(AppTab.ProtectionLog, resolveTab(2, devMode = true))
        assertEquals(AppTab.Settings, resolveTab(3, devMode = true))
    }
}
