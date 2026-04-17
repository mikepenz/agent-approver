package com.mikepenz.agentbuddy.storage

import com.mikepenz.agentbuddy.model.AppSettings
import kotlin.test.Test
import kotlin.test.assertEquals

class SettingsStorageTest {

    @Test
    fun `load returns defaults when file missing`() {
        val dir = "/tmp/test-settings-${java.util.UUID.randomUUID()}"
        val storage = SettingsStorage(dir)
        val settings = storage.load()
        assertEquals(AppSettings(), settings)
    }

    @Test
    fun `save and reload preserves settings`() {
        val dir = "/tmp/test-settings-${java.util.UUID.randomUUID()}"
        val storage = SettingsStorage(dir)
        val custom = AppSettings(
            serverPort = 9999,
            alwaysOnTop = false,
            defaultTimeoutSeconds = 60,
            startOnBoot = true,
            riskAnalysisEnabled = false,
            autoApproveLevel = 2,
            autoDenyLevel = 4,
        )
        storage.save(custom)
        val loaded = storage.load()
        assertEquals(custom, loaded)
    }

    @Test
    fun `save and reload preserves prominentAlwaysAllow`() {
        val dir = "/tmp/test-settings-${java.util.UUID.randomUUID()}"
        val storage = SettingsStorage(dir)
        val custom = AppSettings(prominentAlwaysAllow = true)
        storage.save(custom)
        val loaded = storage.load()
        assertEquals(true, loaded.prominentAlwaysAllow)
    }

    @Test
    fun `serverHost defaults to loopback`() {
        assertEquals("127.0.0.1", AppSettings().serverHost)
    }

    @Test
    fun `save and reload preserves serverHost`() {
        val dir = "/tmp/test-settings-${java.util.UUID.randomUUID()}"
        val storage = SettingsStorage(dir)
        val custom = AppSettings(serverHost = "0.0.0.0")
        storage.save(custom)
        val loaded = storage.load()
        assertEquals("0.0.0.0", loaded.serverHost)
    }

    @Test
    fun `verboseLogging defaults to false`() {
        assertEquals(false, AppSettings().verboseLogging)
    }

    @Test
    fun `load defaults verboseLogging when missing from legacy file`() {
        val dir = java.io.File(
            System.getProperty("java.io.tmpdir"),
            "test-settings-${java.util.UUID.randomUUID()}"
        ).absolutePath
        java.io.File(dir).mkdirs()
        // Legacy settings file without verboseLogging — must still deserialize.
        java.io.File(dir, "settings.json").writeText(
            """{"serverPort":19532,"serverHost":"127.0.0.1","themeMode":"SYSTEM"}"""
        )
        val storage = SettingsStorage(dir)
        val loaded = storage.load()
        assertEquals(false, loaded.verboseLogging)
        assertEquals(19532, loaded.serverPort)
    }

    @Test
    fun `save and reload preserves verboseLogging`() {
        val dir = java.io.File(
            System.getProperty("java.io.tmpdir"),
            "test-settings-${java.util.UUID.randomUUID()}"
        ).absolutePath
        val storage = SettingsStorage(dir)
        storage.save(AppSettings(verboseLogging = true))
        assertEquals(true, storage.load().verboseLogging)
    }

    @Test
    fun `legacy autoApproveRisk1 true migrates to autoApproveLevel 1`() {
        val dir = java.io.File(
            System.getProperty("java.io.tmpdir"),
            "test-settings-${java.util.UUID.randomUUID()}"
        ).absolutePath
        java.io.File(dir).mkdirs()
        java.io.File(dir, "settings.json").writeText(
            """{"autoApproveRisk1":true,"autoDenyRisk5":true}"""
        )
        val loaded = SettingsStorage(dir).load()
        assertEquals(1, loaded.autoApproveLevel)
        assertEquals(5, loaded.autoDenyLevel)
    }

    @Test
    fun `legacy auto risk booleans false migrate to level 0`() {
        val dir = java.io.File(
            System.getProperty("java.io.tmpdir"),
            "test-settings-${java.util.UUID.randomUUID()}"
        ).absolutePath
        java.io.File(dir).mkdirs()
        java.io.File(dir, "settings.json").writeText(
            """{"autoApproveRisk1":false,"autoDenyRisk5":false}"""
        )
        val loaded = SettingsStorage(dir).load()
        assertEquals(0, loaded.autoApproveLevel)
        assertEquals(0, loaded.autoDenyLevel)
    }

    @Test
    fun `new autoApproveLevel takes precedence over legacy boolean`() {
        val dir = java.io.File(
            System.getProperty("java.io.tmpdir"),
            "test-settings-${java.util.UUID.randomUUID()}"
        ).absolutePath
        java.io.File(dir).mkdirs()
        java.io.File(dir, "settings.json").writeText(
            """{"autoApproveRisk1":true,"autoApproveLevel":3,"autoDenyRisk5":true,"autoDenyLevel":4}"""
        )
        val loaded = SettingsStorage(dir).load()
        assertEquals(3, loaded.autoApproveLevel)
        assertEquals(4, loaded.autoDenyLevel)
    }

    @Test
    fun `load defaults serverHost when missing from legacy file`() {
        val dir = "/tmp/test-settings-${java.util.UUID.randomUUID()}"
        java.io.File(dir).mkdirs()
        // Legacy settings file without the serverHost field.
        java.io.File(dir, "settings.json").writeText("""{"serverPort":19532}""")
        val storage = SettingsStorage(dir)
        val loaded = storage.load()
        assertEquals("127.0.0.1", loaded.serverHost)
    }
}
