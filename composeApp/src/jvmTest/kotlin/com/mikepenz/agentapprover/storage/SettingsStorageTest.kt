package com.mikepenz.agentapprover.storage

import com.mikepenz.agentapprover.model.AppSettings
import kotlin.test.Test
import kotlin.test.assertEquals

class SettingsStorageTest {

    @Test
    fun `load returns defaults when file missing`() {
        val dir = "/tmp/test-settings-${System.currentTimeMillis()}"
        val storage = SettingsStorage(dir)
        val settings = storage.load()
        assertEquals(AppSettings(), settings)
    }

    @Test
    fun `save and reload preserves settings`() {
        val dir = "/tmp/test-settings-${System.currentTimeMillis()}"
        val storage = SettingsStorage(dir)
        val custom = AppSettings(
            serverPort = 9999,
            alwaysOnTop = false,
            defaultTimeoutSeconds = 60,
            startOnBoot = true,
            riskAnalysisEnabled = false,
            autoApproveRisk1 = true,
            autoDenyRisk5 = true,
        )
        storage.save(custom)
        val loaded = storage.load()
        assertEquals(custom, loaded)
    }

    @Test
    fun `save and reload preserves prominentAlwaysAllow`() {
        val dir = "/tmp/test-settings-${System.currentTimeMillis()}"
        val storage = SettingsStorage(dir)
        val custom = AppSettings(prominentAlwaysAllow = true)
        storage.save(custom)
        val loaded = storage.load()
        assertEquals(true, loaded.prominentAlwaysAllow)
    }
}
