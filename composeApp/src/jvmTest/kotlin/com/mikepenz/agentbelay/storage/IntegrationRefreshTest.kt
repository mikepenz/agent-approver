package com.mikepenz.agentbelay.storage

import com.mikepenz.agentbelay.hook.HookRegistry
import java.io.File
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class IntegrationRefreshTest {

    private lateinit var dataDir: File

    @BeforeTest
    fun setUp() {
        dataDir = File(System.getProperty("java.io.tmpdir"), "refresh-test-${System.nanoTime()}")
        dataDir.mkdirs()
    }

    @AfterTest
    fun tearDown() {
        dataDir.deleteRecursively()
    }

    @Test
    fun `noMarker_isNoOp`() {
        val hooks = RecordingHookRegistry()
        val copilot = RecordingCopilot()

        IntegrationRefresh.runIfNeeded(dataDir.absolutePath, 19532, false, hooks, copilot)

        assertEquals(0, hooks.registerCalls)
        assertEquals(0, hooks.registerCapabilityCalls)
        assertEquals(0, hooks.registerSessionStartCalls)
        assertEquals(0, copilot.registerCalls)
        assertEquals(0, copilot.registerCapabilityCalls)
    }

    @Test
    fun `markerPresent_refreshesOnlyRegisteredIntegrations`() {
        IntegrationRefresh.markPending(dataDir.absolutePath)
        // Migration left only Claude main hooks + Copilot main hooks active.
        val hooks = RecordingHookRegistry(
            mainRegistered = true,
            capabilityRegistered = false,
            sessionStartRegistered = false,
        )
        val copilot = RecordingCopilot(
            mainRegistered = true,
            capabilityRegistered = false,
        )

        IntegrationRefresh.runIfNeeded(dataDir.absolutePath, 19532, false, hooks, copilot)

        assertEquals(1, hooks.registerCalls)
        assertEquals(0, hooks.registerCapabilityCalls)
        assertEquals(0, hooks.registerSessionStartCalls)
        assertEquals(1, copilot.registerCalls)
        assertEquals(0, copilot.registerCapabilityCalls)
        assertFalse(File(dataDir, IntegrationRefresh.MARKER_NAME).exists(), "marker must be cleared on success")
    }

    @Test
    fun `markerPresent_allActive_refreshesEverything`() {
        IntegrationRefresh.markPending(dataDir.absolutePath)
        val hooks = RecordingHookRegistry(
            mainRegistered = true,
            capabilityRegistered = true,
            sessionStartRegistered = true,
        )
        val copilot = RecordingCopilot(mainRegistered = true, capabilityRegistered = true)

        IntegrationRefresh.runIfNeeded(dataDir.absolutePath, 19532, true, hooks, copilot)

        assertEquals(1, hooks.registerCalls)
        assertEquals(1, hooks.registerCapabilityCalls)
        assertEquals(1, hooks.registerSessionStartCalls)
        assertEquals(1, copilot.registerCalls)
        assertEquals(1, copilot.registerCapabilityCalls)
        assertEquals(true, copilot.lastFailClosed)
        assertFalse(File(dataDir, IntegrationRefresh.MARKER_NAME).exists())
    }

    @Test
    fun `partialFailure_keepsMarkerForRetry`() {
        IntegrationRefresh.markPending(dataDir.absolutePath)
        val hooks = RecordingHookRegistry(
            mainRegistered = true,
            registerThrows = RuntimeException("boom"),
        )
        val copilot = RecordingCopilot(mainRegistered = true)

        IntegrationRefresh.runIfNeeded(dataDir.absolutePath, 19532, false, hooks, copilot)

        // Copilot still gets refreshed even though hooks.register threw.
        assertEquals(1, copilot.registerCalls)
        // Marker remains so next launch retries.
        assertTrue(File(dataDir, IntegrationRefresh.MARKER_NAME).exists())
    }

    private class RecordingHookRegistry(
        var mainRegistered: Boolean = false,
        var capabilityRegistered: Boolean = false,
        var sessionStartRegistered: Boolean = false,
        var registerThrows: Throwable? = null,
    ) : HookRegistry {
        var registerCalls = 0
        var registerCapabilityCalls = 0
        var registerSessionStartCalls = 0

        override fun isRegistered(port: Int) = mainRegistered
        override fun register(port: Int) {
            registerCalls++
            registerThrows?.let { throw it }
        }
        override fun unregister(port: Int) = error("not used")
        override fun isCapabilityHookRegistered(port: Int) = capabilityRegistered
        override fun registerCapabilityHook(port: Int) { registerCapabilityCalls++ }
        override fun unregisterCapabilityHook(port: Int) = error("not used")
        override fun isSessionStartHookRegistered(port: Int) = sessionStartRegistered
        override fun registerSessionStartHook(port: Int) { registerSessionStartCalls++ }
        override fun unregisterSessionStartHook(port: Int) = error("not used")
    }

    private class RecordingCopilot(
        var mainRegistered: Boolean = false,
        var capabilityRegistered: Boolean = false,
    ) : IntegrationRefresh.CopilotInstaller {
        var registerCalls = 0
        var registerCapabilityCalls = 0
        var lastFailClosed: Boolean? = null

        override fun isRegistered(port: Int) = mainRegistered
        override fun register(port: Int, failClosed: Boolean) {
            registerCalls++
            lastFailClosed = failClosed
        }
        override fun isCapabilityHookRegistered(port: Int) = capabilityRegistered
        override fun registerCapabilityHook(port: Int, failClosed: Boolean) {
            registerCapabilityCalls++
            lastFailClosed = failClosed
        }
    }
}
