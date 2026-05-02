package com.mikepenz.agentbelay.hook

import com.mikepenz.agentbelay.testutil.withTempHome
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Coverage for [CopilotBridgeInstaller]'s capability-hook entry points
 * (`registerCapabilityHook` / `unregisterCapabilityHook` /
 * `isCapabilityHookRegistered`), which the existing
 * [CopilotBridgeInstallerTest] does not exercise. The capability-hook
 * surface coexists with the approval surface in a single
 * `~/.copilot/hooks/agent-belay.json` file, so the cases below focus on
 * coexistence semantics: each entry is installed and removed independently
 * without disturbing the other.
 *
 * Uses the [withTempHome] helper to redirect filesystem writes — the
 * existing test class hand-rolls its own `BeforeTest`/`AfterTest`; new
 * tests should prefer the helper for brevity.
 */
class CopilotCapabilityHookInstallerTest {

    private val port = 19532

    @Test
    fun `registerCapabilityHook installs only the capability shim and sessionStart entry`() = withTempHome { home ->
        CopilotBridgeInstaller.registerCapabilityHook(port, failClosed = false)

        val belay = home.resolve(".agent-belay").toFile()
        assertTrue(belay.resolve("copilot-capability.sh").exists())
        assertTrue(belay.resolve("copilot-capability.sh").canExecute())
        assertFalse(belay.resolve("copilot-hook.sh").exists(), "approval shim must not be installed")
        assertFalse(belay.resolve("copilot-approve.sh").exists())

        val hooks = readHooks(home)
        assertEquals(setOf("sessionStart"), hooks.keys, "only sessionStart should be present")
        assertTrue(CopilotBridgeInstaller.isCapabilityHookRegistered(port))
        assertFalse(CopilotBridgeInstaller.isRegistered(port))
    }

    @Test
    fun `register-then-registerCapability surfaces all four entries`() = withTempHome { home ->
        CopilotBridgeInstaller.register(port, failClosed = false)
        CopilotBridgeInstaller.registerCapabilityHook(port, failClosed = false)

        val hooks = readHooks(home)
        assertEquals(
            setOf("preToolUse", "permissionRequest", "postToolUse", "sessionStart"),
            hooks.keys,
            "all four event keys must coexist",
        )
        assertTrue(CopilotBridgeInstaller.isRegistered(port))
        assertTrue(CopilotBridgeInstaller.isCapabilityHookRegistered(port))
    }

    @Test
    fun `unregisterCapabilityHook leaves the approval entries intact`() = withTempHome { home ->
        CopilotBridgeInstaller.register(port, failClosed = false)
        CopilotBridgeInstaller.registerCapabilityHook(port, failClosed = false)
        CopilotBridgeInstaller.unregisterCapabilityHook(port)

        val belay = home.resolve(".agent-belay").toFile()
        assertFalse(belay.resolve("copilot-capability.sh").exists(), "capability shim removed")
        assertTrue(belay.resolve("copilot-hook.sh").exists(), "approval shim survives")

        val hooks = readHooks(home)
        assertEquals(setOf("preToolUse", "permissionRequest", "postToolUse"), hooks.keys)
        assertFalse(CopilotBridgeInstaller.isCapabilityHookRegistered(port))
        assertTrue(CopilotBridgeInstaller.isRegistered(port))
    }

    @Test
    fun `unregister leaves the capability entry intact when its shim is still installed`() = withTempHome { home ->
        CopilotBridgeInstaller.register(port, failClosed = false)
        CopilotBridgeInstaller.registerCapabilityHook(port, failClosed = false)
        CopilotBridgeInstaller.unregister(port)

        val belay = home.resolve(".agent-belay").toFile()
        assertFalse(belay.resolve("copilot-hook.sh").exists())
        assertTrue(belay.resolve("copilot-capability.sh").exists(), "capability shim must survive an approval-only unregister")

        val hooks = readHooks(home)
        assertEquals(setOf("sessionStart"), hooks.keys)
        assertTrue(CopilotBridgeInstaller.isCapabilityHookRegistered(port))
        assertFalse(CopilotBridgeInstaller.isRegistered(port))
    }

    @Test
    fun `unregister + unregisterCapabilityHook leaves no Belay artefacts`() = withTempHome { home ->
        CopilotBridgeInstaller.register(port, failClosed = false)
        CopilotBridgeInstaller.registerCapabilityHook(port, failClosed = false)
        CopilotBridgeInstaller.unregister(port)
        CopilotBridgeInstaller.unregisterCapabilityHook(port)

        val belay = home.resolve(".agent-belay").toFile()
        assertTrue(belay.listFiles().isNullOrEmpty(), "no shims should remain")
        val hookFile = home.resolve(".copilot/hooks/agent-belay.json").toFile()
        assertFalse(hookFile.exists(), "empty hook file must be deleted")
    }

    @Test
    fun `registerCapabilityHook is idempotent`() = withTempHome { home ->
        CopilotBridgeInstaller.registerCapabilityHook(port, failClosed = false)
        val first = home.resolve(".copilot/hooks/agent-belay.json").toFile().readText()

        CopilotBridgeInstaller.registerCapabilityHook(port, failClosed = false)
        val second = home.resolve(".copilot/hooks/agent-belay.json").toFile().readText()

        assertEquals(first, second)
    }

    private fun readHooks(home: java.nio.file.Path) =
        Json.parseToJsonElement(home.resolve(".copilot/hooks/agent-belay.json").toFile().readText())
            .jsonObject["hooks"]!!.jsonObject
}
