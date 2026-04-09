package com.mikepenz.agentapprover.hook

/**
 * Thin interface over the [CopilotBridgeInstaller] singleton object so that
 * [com.mikepenz.agentapprover.ui.settings.SettingsViewModel] can depend on it
 * for unit-testing without touching the host filesystem.
 *
 * Mirrors [HookRegistry] in shape: a single user-scoped registration call
 * keyed only on the server port. Both the `preToolUse` and `permissionRequest`
 * Copilot hooks are written together — there is no per-project setup. The
 * `permissionRequest` event requires Copilot CLI ≥ v1.0.16; user-scoped hook
 * loading itself requires v0.0.422.
 *
 * The default production binding is [DefaultCopilotBridge], wired in
 * [com.mikepenz.agentapprover.di.AppProviders].
 */
interface CopilotBridge {
    fun isRegistered(port: Int): Boolean
    fun register(port: Int)
    fun unregister(port: Int)
}

/** Production-only delegate to the [CopilotBridgeInstaller] object. */
object DefaultCopilotBridge : CopilotBridge {
    override fun isRegistered(port: Int): Boolean = CopilotBridgeInstaller.isRegistered(port)
    override fun register(port: Int) = CopilotBridgeInstaller.register(port)
    override fun unregister(port: Int) = CopilotBridgeInstaller.unregister(port)
}
