package com.mikepenz.agentapprover.ui.settings

import com.mikepenz.agentapprover.capability.CapabilityEngine
import com.mikepenz.agentapprover.capability.modules.ResponseCompressionCapability
import com.mikepenz.agentapprover.capability.modules.SocraticThinkingCapability
import com.mikepenz.agentapprover.hook.CopilotBridge
import com.mikepenz.agentapprover.hook.HookRegistry
import com.mikepenz.agentapprover.model.CapabilityModuleSettings
import com.mikepenz.agentapprover.model.CapabilitySettings
import com.mikepenz.agentapprover.model.ModuleSettings
import com.mikepenz.agentapprover.model.ProtectionMode
import com.mikepenz.agentapprover.model.ProtectionSettings
import com.mikepenz.agentapprover.protection.ProtectionEngine
import com.mikepenz.agentapprover.risk.CopilotInitState
import com.mikepenz.agentapprover.risk.CopilotStateHolder
import com.mikepenz.agentapprover.risk.OllamaStateHolder
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
        val registeredPorts: MutableSet<Int> = mutableSetOf(),
    ) : CopilotBridge {
        var registerCalls = 0
        var unregisterCalls = 0
        var isRegisteredCalls = 0
        var lastRegisterFailClosed: Boolean? = null

        override fun isRegistered(port: Int): Boolean {
            isRegisteredCalls++
            return port in registeredPorts
        }
        override fun register(port: Int, failClosed: Boolean) {
            registerCalls++
            lastRegisterFailClosed = failClosed
            registeredPorts.add(port)
        }
        override fun unregister(port: Int) {
            unregisterCalls++
            registeredPorts.remove(port)
        }

        val capabilityHookPorts: MutableSet<Int> = mutableSetOf()
        var lastCapabilityFailClosed: Boolean? = null
        override fun isCapabilityHookRegistered(port: Int): Boolean = port in capabilityHookPorts
        override fun registerCapabilityHook(port: Int, failClosed: Boolean) {
            lastCapabilityFailClosed = failClosed
            capabilityHookPorts.add(port)
        }
        override fun unregisterCapabilityHook(port: Int) { capabilityHookPorts.remove(port) }
    }

    private class FakeHookRegistry(
        val registeredPorts: MutableSet<Int> = mutableSetOf(),
    ) : HookRegistry {
        var registerCalls = 0
        var unregisterCalls = 0
        var isRegisteredCalls = 0

        override fun isRegistered(port: Int): Boolean {
            isRegisteredCalls++
            return port in registeredPorts
        }
        override fun register(port: Int) {
            registerCalls++
            registeredPorts.add(port)
        }
        override fun unregister(port: Int) {
            unregisterCalls++
            registeredPorts.remove(port)
        }

        val capabilityHookPorts: MutableSet<Int> = mutableSetOf()
        override fun isCapabilityHookRegistered(port: Int): Boolean = port in capabilityHookPorts
        override fun registerCapabilityHook(port: Int) { capabilityHookPorts.add(port) }
        override fun unregisterCapabilityHook(port: Int) { capabilityHookPorts.remove(port) }

        val sessionStartHookPorts: MutableSet<Int> = mutableSetOf()
        override fun isSessionStartHookRegistered(port: Int): Boolean = port in sessionStartHookPorts
        override fun registerSessionStartHook(port: Int) { sessionStartHookPorts.add(port) }
        override fun unregisterSessionStartHook(port: Int) { sessionStartHookPorts.remove(port) }
    }

    private fun newVm(
        bridge: FakeCopilotBridge = FakeCopilotBridge(),
        registry: FakeHookRegistry = FakeHookRegistry(),
        copilotState: CopilotStateHolder = CopilotStateHolder(),
        ollamaState: OllamaStateHolder = OllamaStateHolder(),
    ): Triple<SettingsViewModel, AppStateManager, FakeHookRegistry> {
        val state = AppStateManager()
        val engine = ProtectionEngine(modules = emptyList(), settingsProvider = { ProtectionSettings() })
        val capEngine = CapabilityEngine(modules = listOf(ResponseCompressionCapability, SocraticThinkingCapability), settingsProvider = { state.state.value.settings.capabilitySettings })
        val vm = SettingsViewModel(
            stateManager = state,
            copilotBridge = bridge,
            copilotStateHolder = copilotState,
            ollamaStateHolder = ollamaState,
            protectionEngine = engine,
            capabilityEngine = capEngine,
            hookRegistry = registry,
            ioDispatcher = mainDispatcher, // run "IO" on the test dispatcher so runCurrent advances it
        )
        return Triple(vm, state, registry)
    }

    @Test
    fun `registerCopilot delegates to bridge and updates uiState`() = runTest {
        val bridge = FakeCopilotBridge()
        val (vm, _, _) = newVm(bridge = bridge)
        runCurrent()

        assertFalse(vm.uiState.value.isCopilotRegistered)

        vm.registerCopilot()
        runCurrent()
        assertEquals(1, bridge.registerCalls)
        assertEquals(false, bridge.lastRegisterFailClosed)
        assertTrue(vm.uiState.value.isCopilotRegistered)

        vm.unregisterCopilot()
        runCurrent()
        assertEquals(1, bridge.unregisterCalls)
        assertFalse(vm.uiState.value.isCopilotRegistered)
    }

    @Test
    fun `registerCopilot forwards copilotFailClosed from current settings`() = runTest {
        val bridge = FakeCopilotBridge()
        val (vm, state, _) = newVm(bridge = bridge)
        runCurrent()

        state.updateSettings(state.state.value.settings.copy(copilotFailClosed = true))
        runCurrent()

        vm.registerCopilot()
        runCurrent()

        assertEquals(true, bridge.lastRegisterFailClosed)
    }

    @Test
    fun `toggling copilotFailClosed re-registers when currently registered`() = runTest {
        val bridge = FakeCopilotBridge(registeredPorts = mutableSetOf(19532))
        val (_, state, _) = newVm(bridge = bridge)
        runCurrent()
        // The startup flag-watcher drops its initial emission, so no re-register
        // should have fired yet.
        assertEquals(0, bridge.registerCalls)

        state.updateSettings(state.state.value.settings.copy(copilotFailClosed = true))
        runCurrent()

        assertEquals(1, bridge.registerCalls)
        assertEquals(true, bridge.lastRegisterFailClosed)
    }

    @Test
    fun `toggling copilotFailClosed does nothing when Copilot is not registered`() = runTest {
        val bridge = FakeCopilotBridge()
        val (_, state, _) = newVm(bridge = bridge)
        runCurrent()

        state.updateSettings(state.state.value.settings.copy(copilotFailClosed = true))
        runCurrent()

        assertEquals(0, bridge.registerCalls)
    }

    @Test
    fun `toggling copilotFailClosed re-registers capability hook when only capability is installed`() = runTest {
        // Capability hooks can be installed independently of the main
        // approval hooks (via reconcileCapabilityHooks). In that state the
        // main register() path is not exercised, so the watcher must fall
        // back to registerCapabilityHook() to re-bake the capability script.
        val bridge = FakeCopilotBridge()
        val (_, state, _) = newVm(bridge = bridge)
        runCurrent() // drain startup reconciles

        // Simulate the user having enabled a capability previously so the
        // capability hook is live on disk, but main approval is not.
        bridge.capabilityHookPorts.add(state.state.value.settings.serverPort)
        bridge.lastCapabilityFailClosed = null

        state.updateSettings(state.state.value.settings.copy(copilotFailClosed = true))
        runCurrent()

        // Main register() is NOT called because main isn't registered.
        assertEquals(0, bridge.registerCalls)
        // Capability hook IS re-registered with the new flag.
        assertEquals(true, bridge.lastCapabilityFailClosed)
    }

    @Test
    fun `isCopilotRegistered is initially false then populated from bridge on IO`() = runTest {
        val bridge = FakeCopilotBridge(registeredPorts = mutableSetOf(19532))
        val (vm, _, _) = newVm(bridge = bridge)

        // Before any coroutines run, the seeded value is false (safe default).
        assertFalse(vm.uiState.value.isCopilotRegistered)

        runCurrent()
        assertTrue(vm.uiState.value.isCopilotRegistered)
    }

    @Test
    fun `port change re-polls copilot registration`() = runTest {
        val bridge = FakeCopilotBridge()
        val (vm, state, _) = newVm(bridge = bridge)
        runCurrent()
        val initialIsRegisteredCalls = bridge.isRegisteredCalls

        // Simulate the user (or the hook flow) marking the new port as registered
        bridge.registeredPorts.add(20000)
        state.updateSettings(state.state.value.settings.copy(serverPort = 20000))
        runCurrent()

        assertTrue(vm.uiState.value.isCopilotRegistered)
        assertTrue(bridge.isRegisteredCalls > initialIsRegisteredCalls)
    }

    @Test
    fun `rapid updateSettings calls land in launch order`() = runTest {
        val (vm, state, _) = newVm()
        runCurrent()

        val s0 = state.state.value.settings
        // Three rapid updates — the limitedParallelism(1) write dispatcher
        // must drain them FIFO so the final state matches the last call.
        vm.updateSettings(s0.copy(serverPort = 19001))
        vm.updateSettings(s0.copy(serverPort = 19002))
        vm.updateSettings(s0.copy(serverPort = 19003))
        runCurrent()

        assertEquals(19003, state.state.value.settings.serverPort)
    }

    @Test
    fun `updateSettings persists through state manager`() = runTest {
        val (vm, state, _) = newVm()
        runCurrent()

        vm.updateSettings(state.state.value.settings.copy(awayMode = true))
        runCurrent()

        assertTrue(state.state.value.settings.awayMode)
        assertTrue(vm.uiState.value.settings.awayMode)
    }

    @Test
    fun `updateProtectionSettings replaces protection settings on the snapshot`() = runTest {
        val (vm, state, _) = newVm()
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
        val (vm, _, _) = newVm(copilotState = holder)
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
        val (vm, state, _) = newVm()
        runCurrent()
        vm.clearHistory()
        runCurrent()
        assertTrue(state.state.value.history.isEmpty())
    }

    @Test
    fun `isHookRegistered is initially false then populated from registry on IO`() = runTest {
        val registry = FakeHookRegistry(registeredPorts = mutableSetOf(19532))
        val (vm, _, _) = newVm(registry = registry)

        // Before any coroutines run, the seeded value is false (safe default).
        assertFalse(vm.uiState.value.isHookRegistered)

        // Once the init coroutine runs on the (test) IO dispatcher, the real
        // value is published.
        runCurrent()
        assertTrue(vm.uiState.value.isHookRegistered)
        // The port-flow collector owns the initial poll; only one read at startup.
        assertEquals(1, registry.isRegisteredCalls)
    }

    @Test
    fun `registerHook calls registry on IO dispatcher and updates state`() = runTest {
        val registry = FakeHookRegistry()
        val (vm, _, _) = newVm(registry = registry)
        runCurrent()
        assertFalse(vm.uiState.value.isHookRegistered)

        vm.registerHook()
        runCurrent()

        assertEquals(1, registry.registerCalls)
        assertTrue(vm.uiState.value.isHookRegistered)

        vm.unregisterHook()
        runCurrent()

        assertEquals(1, registry.unregisterCalls)
        assertFalse(vm.uiState.value.isHookRegistered)
    }

    @Test
    fun `port change re-polls hook registration`() = runTest {
        val registry = FakeHookRegistry()
        val (vm, state, _) = newVm(registry = registry)
        runCurrent()
        val initialIsRegisteredCalls = registry.isRegisteredCalls

        // Simulate the user (or the hook flow) marking the new port as registered
        registry.registeredPorts.add(20000)
        state.updateSettings(state.state.value.settings.copy(serverPort = 20000))
        runCurrent()

        assertTrue(vm.uiState.value.isHookRegistered)
        assertTrue(registry.isRegisteredCalls > initialIsRegisteredCalls)
    }

    @Test
    fun `enabling a capability registers capability hooks on both agents`() = runTest {
        val registry = FakeHookRegistry()
        val bridge = FakeCopilotBridge()
        val (vm, state, _) = newVm(bridge = bridge, registry = registry)
        runCurrent()

        // Startup reconcile with no enabled capabilities must unregister.
        assertFalse(registry.capabilityHookPorts.contains(state.state.value.settings.serverPort))
        assertFalse(bridge.capabilityHookPorts.contains(state.state.value.settings.serverPort))

        vm.updateCapabilitySettings(
            CapabilitySettings(
                modules = mapOf("response-compression" to CapabilityModuleSettings(enabled = true)),
            ),
        )
        runCurrent()

        val port = state.state.value.settings.serverPort
        assertTrue(registry.capabilityHookPorts.contains(port))
        assertTrue(bridge.capabilityHookPorts.contains(port))
    }

    @Test
    fun `disabling the last capability unregisters capability hooks on both agents`() = runTest {
        val registry = FakeHookRegistry()
        val bridge = FakeCopilotBridge()
        val (vm, state, _) = newVm(bridge = bridge, registry = registry)
        runCurrent()

        vm.updateCapabilitySettings(
            CapabilitySettings(
                modules = mapOf("response-compression" to CapabilityModuleSettings(enabled = true)),
            ),
        )
        runCurrent()
        val port = state.state.value.settings.serverPort
        assertTrue(registry.capabilityHookPorts.contains(port))

        vm.updateCapabilitySettings(
            CapabilitySettings(
                modules = mapOf("response-compression" to CapabilityModuleSettings(enabled = false)),
            ),
        )
        runCurrent()

        assertFalse(registry.capabilityHookPorts.contains(port))
        assertFalse(bridge.capabilityHookPorts.contains(port))
    }

    @Test
    fun `port change re-registers capability hooks at the new port`() = runTest {
        val registry = FakeHookRegistry()
        val bridge = FakeCopilotBridge()
        val (vm, state, _) = newVm(bridge = bridge, registry = registry)
        runCurrent()

        vm.updateCapabilitySettings(
            CapabilitySettings(
                modules = mapOf("response-compression" to CapabilityModuleSettings(enabled = true)),
            ),
        )
        runCurrent()
        val oldPort = state.state.value.settings.serverPort
        assertTrue(registry.capabilityHookPorts.contains(oldPort))

        state.updateSettings(state.state.value.settings.copy(serverPort = 20000))
        runCurrent()

        // Capability hook should now be wired to the new port. Our fake
        // set-add semantics mean the old port lingers, but the new port must
        // be present — that's the bit the reconcile flow guarantees.
        assertTrue(registry.capabilityHookPorts.contains(20000))
        assertTrue(bridge.capabilityHookPorts.contains(20000))
    }
}
