package com.mikepenz.agentapprover.ui.settings

import com.mikepenz.agentapprover.hook.CopilotBridge
import com.mikepenz.agentapprover.model.ModuleSettings
import com.mikepenz.agentapprover.model.ProtectionMode
import com.mikepenz.agentapprover.model.ProtectionSettings
import com.mikepenz.agentapprover.protection.ProtectionEngine
import com.mikepenz.agentapprover.risk.CopilotInitState
import com.mikepenz.agentapprover.risk.CopilotStateHolder
import com.mikepenz.agentapprover.state.AppStateManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class SettingsViewModelTest {

    private val mainDispatcher = StandardTestDispatcher()

    @BeforeTest
    fun setUp() {
        Dispatchers.setMain(mainDispatcher)
    }

    @AfterTest
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private class FakeCopilotBridge(
        var installed: Boolean = false,
        val registeredHooks: MutableSet<String> = mutableSetOf(),
    ) : CopilotBridge {
        var installCalls = 0
        var uninstallCalls = 0

        override fun isInstalled(): Boolean = installed
        override fun install() {
            installCalls++
            installed = true
        }
        override fun uninstall() {
            uninstallCalls++
            installed = false
        }
        override fun isHookRegistered(projectPath: String): Boolean = projectPath in registeredHooks
        override fun registerHook(projectPath: String) { registeredHooks.add(projectPath) }
        override fun unregisterHook(projectPath: String) { registeredHooks.remove(projectPath) }
    }

    private fun newVm(
        bridge: FakeCopilotBridge = FakeCopilotBridge(),
        copilotState: CopilotStateHolder = CopilotStateHolder(),
    ): Pair<SettingsViewModel, AppStateManager> {
        val state = AppStateManager()
        val engine = ProtectionEngine(modules = emptyList(), settingsProvider = { ProtectionSettings() })
        val vm = SettingsViewModel(
            stateManager = state,
            copilotBridge = bridge,
            copilotStateHolder = copilotState,
            protectionEngine = engine,
        )
        return vm to state
    }

    @Test
    fun `installCopilot delegates to bridge and updates uiState`() = runTest {
        val bridge = FakeCopilotBridge(installed = false)
        val (vm, _) = newVm(bridge = bridge)
        runCurrent()

        assertFalse(vm.uiState.value.isCopilotInstalled)
        vm.installCopilot()
        runCurrent()

        assertEquals(1, bridge.installCalls)
        assertTrue(vm.uiState.value.isCopilotInstalled)

        vm.uninstallCopilot()
        runCurrent()
        assertEquals(1, bridge.uninstallCalls)
        assertFalse(vm.uiState.value.isCopilotInstalled)
    }

    @Test
    fun `register and unregister copilot hook flow through bridge`() {
        val bridge = FakeCopilotBridge()
        val (vm, _) = newVm(bridge = bridge)

        vm.registerCopilotHook("/path/a")
        vm.registerCopilotHook("/path/b")
        assertTrue(vm.isCopilotHookRegistered("/path/a"))
        assertTrue(vm.isCopilotHookRegistered("/path/b"))

        vm.unregisterCopilotHook("/path/a")
        assertFalse(vm.isCopilotHookRegistered("/path/a"))
        assertTrue(vm.isCopilotHookRegistered("/path/b"))
    }

    @Test
    fun `updateSettings persists through state manager`() = runTest {
        val (vm, state) = newVm()
        runCurrent()

        vm.updateSettings(state.state.value.settings.copy(awayMode = true))
        runCurrent()

        assertTrue(state.state.value.settings.awayMode)
        assertTrue(vm.uiState.value.settings.awayMode)
    }

    @Test
    fun `updateProtectionSettings replaces protection settings on the snapshot`() = runTest {
        val (vm, state) = newVm()
        runCurrent()

        val newProtection = ProtectionSettings(
            modules = mapOf("destructive-commands" to ModuleSettings(mode = ProtectionMode.DISABLED)),
        )
        vm.updateProtectionSettings(newProtection)
        runCurrent()

        assertEquals(newProtection, state.state.value.settings.protectionSettings)
    }

    @Test
    fun `uiState exposes copilot models and init state from holder`() = runTest {
        val holder = CopilotStateHolder()
        val (vm, _) = newVm(copilotState = holder)
        runCurrent()

        assertTrue(vm.uiState.value.copilotModels.isEmpty())
        assertEquals(CopilotInitState.IDLE, vm.uiState.value.copilotInitState)

        holder.setModels(listOf("gpt-4" to "GPT-4", "gpt-5" to "GPT-5"))
        holder.setInitState(CopilotInitState.READY)
        runCurrent()

        assertEquals(2, vm.uiState.value.copilotModels.size)
        assertEquals(CopilotInitState.READY, vm.uiState.value.copilotInitState)
    }

    @Test
    fun `clearHistory delegates to state manager`() = runTest {
        val (vm, state) = newVm()
        runCurrent()
        vm.clearHistory()
        runCurrent()
        assertTrue(state.state.value.history.isEmpty())
    }
}
