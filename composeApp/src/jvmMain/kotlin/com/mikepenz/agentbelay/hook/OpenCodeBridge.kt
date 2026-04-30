package com.mikepenz.agentbelay.hook

/**
 * Thin interface over the [OpenCodeBridgeInstaller] singleton object so that
 * [com.mikepenz.agentbelay.ui.settings.SettingsViewModel] can depend on it
 * for unit-testing without touching the host filesystem.
 *
 * Mirrors [CopilotBridge] in shape: a single global registration call
 * keyed on the server port. The port is baked into the generated TypeScript
 * plugin at install time.
 */
interface OpenCodeBridge {
    fun isRegistered(port: Int): Boolean
    fun register(port: Int)
    fun unregister(port: Int)

    /** True iff the plugin contains the capability injection handler. */
    fun isCapabilityHookRegistered(port: Int): Boolean
    fun registerCapabilityHook(port: Int)
    fun unregisterCapabilityHook(port: Int)
}

/** Production-only delegate to the [OpenCodeBridgeInstaller] object. */
object DefaultOpenCodeBridge : OpenCodeBridge {
    override fun isRegistered(port: Int): Boolean = OpenCodeBridgeInstaller.isRegistered(port)
    override fun register(port: Int) = OpenCodeBridgeInstaller.register(port)
    override fun unregister(port: Int) = OpenCodeBridgeInstaller.unregister(port)
    override fun isCapabilityHookRegistered(port: Int): Boolean =
        OpenCodeBridgeInstaller.isCapabilityHookRegistered(port)
    override fun registerCapabilityHook(port: Int) =
        OpenCodeBridgeInstaller.registerCapabilityHook(port)
    override fun unregisterCapabilityHook(port: Int) =
        OpenCodeBridgeInstaller.unregisterCapabilityHook(port)
}
