package com.mikepenz.agentbelay.ui

import com.mikepenz.agentbelay.di.AppEnvironment
import com.mikepenz.agentbelay.hook.CodexBridge
import com.mikepenz.agentbelay.hook.CopilotBridge
import com.mikepenz.agentbelay.hook.HookRegistry
import com.mikepenz.agentbelay.hook.OpenCodeBridge
import com.mikepenz.agentbelay.hook.PiBridge
import com.mikepenz.agentbelay.hook.RegistrationEvents
import com.mikepenz.agentbelay.model.ApprovalRequest
import com.mikepenz.agentbelay.model.HookInput
import com.mikepenz.agentbelay.model.Source
import com.mikepenz.agentbelay.model.ToolType
import com.mikepenz.agentbelay.state.AppStateManager
import com.mikepenz.agentbelay.update.UpdateManager
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

    private object FakeHookRegistry : HookRegistry {
        override fun isRegistered(port: Int): Boolean = false
        override fun register(port: Int) {}
        override fun unregister(port: Int) {}
        override fun isCapabilityHookRegistered(port: Int): Boolean = false
        override fun registerCapabilityHook(port: Int) {}
        override fun unregisterCapabilityHook(port: Int) {}
        override fun isSessionStartHookRegistered(port: Int): Boolean = false
        override fun registerSessionStartHook(port: Int) {}
        override fun unregisterSessionStartHook(port: Int) {}
    }

    private object FakeCopilotBridge : CopilotBridge {
        override fun isRegistered(port: Int): Boolean = false
        override fun register(port: Int, failClosed: Boolean) {}
        override fun unregister(port: Int) {}
        override fun isCapabilityHookRegistered(port: Int): Boolean = false
        override fun registerCapabilityHook(port: Int, failClosed: Boolean) {}
        override fun unregisterCapabilityHook(port: Int) {}
    }

    private object FakeOpenCodeBridge : OpenCodeBridge {
        override fun isRegistered(port: Int): Boolean = false
        override fun register(port: Int) {}
        override fun unregister(port: Int) {}
        override fun isCapabilityHookRegistered(port: Int): Boolean = false
        override fun registerCapabilityHook(port: Int) {}
        override fun unregisterCapabilityHook(port: Int) {}
    }

    private object FakePiBridge : PiBridge {
        override fun isRegistered(port: Int): Boolean = false
        override fun register(port: Int) {}
        override fun unregister(port: Int) {}
    }

    private class FakeCodexBridge(private val registered: Boolean = false) : CodexBridge {
        override fun isRegistered(port: Int): Boolean = registered
        override fun register(port: Int) {}
        override fun unregister(port: Int) {}
    }

    private fun fakeUpdateManager() = UpdateManager(scope = CoroutineScope(SupervisorJob()))

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
        val vm = AppViewModel(
            state,
            env(devMode = false),
            FakeHookRegistry,
            FakeCopilotBridge,
            FakeOpenCodeBridge,
            FakePiBridge,
            FakeCodexBridge(),
            RegistrationEvents(),
            fakeUpdateManager(),
        )
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
        val vm = AppViewModel(
            AppStateManager(),
            env(),
            FakeHookRegistry,
            FakeCopilotBridge,
            FakeOpenCodeBridge,
            FakePiBridge,
            FakeCodexBridge(),
            RegistrationEvents(),
            fakeUpdateManager(),
        )
        runCurrent()

        assertEquals(0, vm.selectedTab.value)
        vm.selectTab(2)
        assertEquals(2, vm.selectedTab.value)
    }

    @Test
    fun `devMode flag comes from environment`() = runTest {
        val vm = AppViewModel(
            AppStateManager(),
            env(devMode = true),
            FakeHookRegistry,
            FakeCopilotBridge,
            FakeOpenCodeBridge,
            FakePiBridge,
            FakeCodexBridge(),
            RegistrationEvents(),
            fakeUpdateManager(),
        )
        runCurrent()
        assertTrue(vm.tabState.value.devMode)
    }

    @Test
    fun `resolveTab maps indexes correctly in dev and non-dev`() {
        // Non-dev: Approvals, History, Statistics, Usage, Settings
        assertEquals(AppTab.Approvals, resolveTab(0, devMode = false))
        assertEquals(AppTab.History, resolveTab(1, devMode = false))
        assertEquals(AppTab.Statistics, resolveTab(2, devMode = false))
        assertEquals(AppTab.Usage, resolveTab(3, devMode = false))
        assertEquals(AppTab.Settings, resolveTab(4, devMode = false))

        // Dev: Approvals, History, Statistics, Usage, ProtectionLog, Settings
        assertEquals(AppTab.Approvals, resolveTab(0, devMode = true))
        assertEquals(AppTab.History, resolveTab(1, devMode = true))
        assertEquals(AppTab.Statistics, resolveTab(2, devMode = true))
        assertEquals(AppTab.Usage, resolveTab(3, devMode = true))
        assertEquals(AppTab.ProtectionLog, resolveTab(4, devMode = true))
        assertEquals(AppTab.Settings, resolveTab(5, devMode = true))
    }

    @Test
    fun `tabState includes Codex registration for sidebar`() = runTest {
        val vm = AppViewModel(
            AppStateManager(),
            env(),
            FakeHookRegistry,
            FakeCopilotBridge,
            FakeOpenCodeBridge,
            FakePiBridge,
            FakeCodexBridge(registered = true),
            RegistrationEvents(),
            fakeUpdateManager(),
        )
        runCurrent()

        val codex = vm.tabState.value.agentRegistrations.single { it.name == "Codex" }
        assertTrue(codex.registered)
    }
}
