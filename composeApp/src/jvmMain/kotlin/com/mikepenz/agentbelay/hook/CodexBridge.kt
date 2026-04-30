package com.mikepenz.agentbelay.hook

/**
 * Thin interface over the [CodexBridgeInstaller] singleton object so that
 * [com.mikepenz.agentbelay.ui.settings.SettingsViewModel] can depend on it
 * for unit-testing without touching the host filesystem. Mirrors the
 * [CopilotBridge] / [OpenCodeBridge] shape.
 */
interface CodexBridge {
    fun isRegistered(port: Int): Boolean
    fun register(port: Int)
    fun unregister(port: Int)
}

/** Production-only delegate to the [CodexBridgeInstaller] object. */
object DefaultCodexBridge : CodexBridge {
    override fun isRegistered(port: Int): Boolean = CodexBridgeInstaller.isRegistered(port)
    override fun register(port: Int) = CodexBridgeInstaller.register(port)
    override fun unregister(port: Int) = CodexBridgeInstaller.unregister(port)
}
