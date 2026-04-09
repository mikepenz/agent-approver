package com.mikepenz.agentapprover.hook

/**
 * Thin testable interface over the [HookRegistrar] singleton object so that
 * [com.mikepenz.agentapprover.ui.settings.SettingsViewModel] can depend on it
 * for unit-testing without touching the user's real `~/.claude/settings.json`.
 *
 * The default production binding is [DefaultHookRegistry], wired in
 * [com.mikepenz.agentapprover.di.AppProviders].
 *
 * Methods on this interface perform synchronous filesystem I/O — callers
 * should invoke them from an IO-friendly dispatcher.
 */
interface HookRegistry {
    fun isRegistered(port: Int): Boolean
    fun register(port: Int)
    fun unregister(port: Int)
}

/** Production-only delegate to the [HookRegistrar] object. */
object DefaultHookRegistry : HookRegistry {
    override fun isRegistered(port: Int): Boolean = HookRegistrar.isRegistered(port)
    override fun register(port: Int) = HookRegistrar.register(port)
    override fun unregister(port: Int) = HookRegistrar.unregister(port)
}
