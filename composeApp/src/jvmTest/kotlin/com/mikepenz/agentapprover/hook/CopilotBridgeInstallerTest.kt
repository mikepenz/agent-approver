package com.mikepenz.agentapprover.hook

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.io.File
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Tests the user-scoped behavior of [CopilotBridgeInstaller].
 *
 * `user.home` is redirected to a per-test temp directory so the tests are
 * hermetic and don't touch the host filesystem.
 */
class CopilotBridgeInstallerTest {

    private lateinit var fakeHome: File
    private lateinit var originalHome: String
    private val port = 19532

    private val json = Json { ignoreUnknownKeys = true }

    @BeforeTest
    fun setUp() {
        originalHome = System.getProperty("user.home")
        fakeHome = File(System.getProperty("java.io.tmpdir"), "copilot-bridge-test-${System.nanoTime()}")
        fakeHome.mkdirs()
        System.setProperty("user.home", fakeHome.absolutePath)
    }

    @AfterTest
    fun tearDown() {
        System.setProperty("user.home", originalHome)
        fakeHome.deleteRecursively()
    }

    private fun preToolUseScript() = File(fakeHome, ".agent-approver/copilot-hook.sh")
    private fun permissionRequestScript() = File(fakeHome, ".agent-approver/copilot-approve.sh")
    private fun hookFile() = File(fakeHome, ".copilot/hooks/agent-approver.json")

    // ----- register / unregister / isRegistered -----

    @Test
    fun `register writes both bridge scripts and hook file`() {
        assertFalse(CopilotBridgeInstaller.isRegistered(port))

        CopilotBridgeInstaller.register(port)

        assertTrue(preToolUseScript().exists())
        assertTrue(preToolUseScript().canExecute())
        assertTrue(permissionRequestScript().exists())
        assertTrue(permissionRequestScript().canExecute())
        assertTrue(hookFile().exists())
        assertTrue(CopilotBridgeInstaller.isRegistered(port))
    }

    @Test
    fun `each script targets its own endpoint path with the registered port`() {
        CopilotBridgeInstaller.register(port)

        val pre = preToolUseScript().readText()
        val perm = permissionRequestScript().readText()

        assertTrue("/pre-tool-use-copilot" in pre)
        assertTrue("localhost:$port" in pre)
        assertFalse("/approve-copilot" in pre)

        assertTrue("/approve-copilot" in perm)
        assertTrue("localhost:$port" in perm)
        assertFalse("/pre-tool-use-copilot" in perm)
    }

    @Test
    fun `register baked port matches the requested port`() {
        CopilotBridgeInstaller.register(20001)

        assertTrue("localhost:20001" in preToolUseScript().readText())
        assertTrue("localhost:20001" in permissionRequestScript().readText())
    }

    @Test
    fun `hook file contains both preToolUse and permissionRequest entries`() {
        CopilotBridgeInstaller.register(port)

        val root = json.parseToJsonElement(hookFile().readText()).jsonObject
        val hooks = root["hooks"]!!.jsonObject

        val preEntries = hooks["preToolUse"]!!.jsonArray
        assertEquals(1, preEntries.size)
        val preEntry = preEntries[0].jsonObject
        assertEquals("command", preEntry["type"]!!.jsonPrimitive.content)
        assertTrue(preEntry["bash"]!!.jsonPrimitive.content.endsWith("/.agent-approver/copilot-hook.sh"))
        assertEquals(300, preEntry["timeoutSec"]!!.jsonPrimitive.content.toInt())

        val permEntries = hooks["permissionRequest"]!!.jsonArray
        assertEquals(1, permEntries.size)
        val permEntry = permEntries[0].jsonObject
        assertEquals("command", permEntry["type"]!!.jsonPrimitive.content)
        assertTrue(permEntry["bash"]!!.jsonPrimitive.content.endsWith("/.agent-approver/copilot-approve.sh"))
        assertEquals(300, permEntry["timeoutSec"]!!.jsonPrimitive.content.toInt())
    }

    @Test
    fun `unregister removes both scripts and the hook file`() {
        CopilotBridgeInstaller.register(port)
        assertTrue(CopilotBridgeInstaller.isRegistered(port))

        CopilotBridgeInstaller.unregister(port)

        assertFalse(preToolUseScript().exists())
        assertFalse(permissionRequestScript().exists())
        assertFalse(hookFile().exists())
        assertFalse(CopilotBridgeInstaller.isRegistered(port))
    }

    @Test
    fun `isRegistered returns false when only one script exists`() {
        CopilotBridgeInstaller.register(port)
        assertTrue(CopilotBridgeInstaller.isRegistered(port))

        permissionRequestScript().delete()
        assertFalse(CopilotBridgeInstaller.isRegistered(port))
    }

    @Test
    fun `isRegistered returns false when hook file is missing`() {
        CopilotBridgeInstaller.register(port)
        assertTrue(CopilotBridgeInstaller.isRegistered(port))

        hookFile().delete()
        assertFalse(CopilotBridgeInstaller.isRegistered(port))
    }

    @Test
    fun `register is idempotent and refreshes the port`() {
        CopilotBridgeInstaller.register(19000)
        CopilotBridgeInstaller.register(19001)

        assertTrue(CopilotBridgeInstaller.isRegistered(19001))
        assertTrue("localhost:19001" in preToolUseScript().readText())
        // Hook file still has exactly one entry per kind.
        val root = json.parseToJsonElement(hookFile().readText()).jsonObject
        val hooks = root["hooks"]!!.jsonObject
        assertEquals(1, hooks["preToolUse"]!!.jsonArray.size)
        assertEquals(1, hooks["permissionRequest"]!!.jsonArray.size)
    }

    @Test
    fun `isRegistered returns false on a clean install`() {
        assertFalse(CopilotBridgeInstaller.isRegistered(port))
    }

    @Test
    fun `default register bakes fail-open behaviour into both scripts`() {
        CopilotBridgeInstaller.register(port)

        listOf(preToolUseScript(), permissionRequestScript()).forEach { script ->
            val body = script.readText()
            assertTrue("exit 0" in body, "expected fail-open exit 0 in ${script.name}: $body")
            assertFalse("exit 1" in body, "unexpected exit 1 in fail-open script ${script.name}")
            assertTrue("Fail-open" in body)
        }
    }

    @Test
    fun `register with failClosed bakes exit 1 into both scripts`() {
        CopilotBridgeInstaller.register(port, failClosed = true)

        listOf(preToolUseScript(), permissionRequestScript()).forEach { script ->
            val body = script.readText()
            assertTrue("exit 1" in body, "expected fail-closed exit 1 in ${script.name}: $body")
            assertTrue("Fail-closed" in body)
            assertTrue("fail-closed" in body) // user-visible stderr message
        }
    }

    @Test
    fun `bridge scripts start with the shebang at byte 0 in both modes`() {
        // Regression: an earlier attempt interpolated a pre-trimIndent()'d
        // failure branch into a trimIndent()'d outer template, which made
        // the min-indent detection collapse to 0 and left every line —
        // including `#!/usr/bin/env bash` — indented by 12 spaces. A leading
        // space in front of the shebang defeats kernel shebang lookup when
        // Copilot CLI execs the script directly.
        listOf(false, true).forEach { failClosed ->
            CopilotBridgeInstaller.register(port, failClosed = failClosed)
            listOf(preToolUseScript(), permissionRequestScript()).forEach { script ->
                val body = script.readText()
                assertTrue(
                    body.startsWith("#!/usr/bin/env bash"),
                    "script ${script.name} (failClosed=$failClosed) must start with shebang at byte 0, " +
                        "got: ${body.take(40)}",
                )
            }
        }
    }

    @Test
    fun `re-registering with a different failClosed flag overwrites the scripts`() {
        CopilotBridgeInstaller.register(port, failClosed = false)
        assertTrue("exit 0" in preToolUseScript().readText())

        CopilotBridgeInstaller.register(port, failClosed = true)
        val body = preToolUseScript().readText()
        assertTrue("exit 1" in body)
        assertFalse("# Server unreachable — fail open" in body)
    }
}
