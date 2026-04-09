package com.mikepenz.agentapprover.hook

/**
 * Thin interface over the [CopilotBridgeInstaller] singleton object so that
 * [com.mikepenz.agentapprover.ui.settings.SettingsViewModel] can depend on it
 * for unit-testing without touching the host filesystem.
 *
 * The default production binding is [DefaultCopilotBridge], wired in
 * [com.mikepenz.agentapprover.di.AppProviders].
 */
interface CopilotBridge {
    fun isInstalled(): Boolean
    fun install()
    fun uninstall()
    fun isHookRegistered(projectPath: String): Boolean
    fun registerHook(projectPath: String)
    fun unregisterHook(projectPath: String)
}

/** Production-only delegate to the [CopilotBridgeInstaller] object. */
object DefaultCopilotBridge : CopilotBridge {
    override fun isInstalled(): Boolean = CopilotBridgeInstaller.isInstalled()
    override fun install() = CopilotBridgeInstaller.install()
    override fun uninstall() = CopilotBridgeInstaller.uninstall()
    override fun isHookRegistered(projectPath: String): Boolean = CopilotBridgeInstaller.isHookRegistered(projectPath)
    override fun registerHook(projectPath: String) = CopilotBridgeInstaller.registerHook(projectPath)
    override fun unregisterHook(projectPath: String) = CopilotBridgeInstaller.unregisterHook(projectPath)
}
