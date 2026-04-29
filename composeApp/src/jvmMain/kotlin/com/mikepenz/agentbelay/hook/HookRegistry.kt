package com.mikepenz.agentbelay.hook

/**
 * Thin testable interface over the [HookRegistrar] singleton object so that
 * [com.mikepenz.agentbelay.ui.settings.SettingsViewModel] can depend on it
 * for unit-testing without touching the user's real `~/.claude/settings.json`.
 *
 * The default production binding is [DefaultHookRegistry], wired in
 * [com.mikepenz.agentbelay.di.AppProviders].
 *
 * Methods on this interface perform synchronous filesystem I/O — callers
 * should invoke them from an IO-friendly dispatcher.
 */
interface HookRegistry {
    fun isRegistered(port: Int): Boolean
    fun register(port: Int)
    fun unregister(port: Int)

    /** True iff the capability `UserPromptSubmit` hook is registered for this port. */
    fun isCapabilityHookRegistered(port: Int): Boolean

    /** Adds our `UserPromptSubmit` hook entry. Idempotent. */
    fun registerCapabilityHook(port: Int)

    /** Removes our `UserPromptSubmit` hook entry. */
    fun unregisterCapabilityHook(port: Int)

    /** True iff the `SessionStart` hook is registered for this port. */
    fun isSessionStartHookRegistered(port: Int): Boolean

    /** Adds our `SessionStart` hook entry. Idempotent. */
    fun registerSessionStartHook(port: Int)

    /** Removes our `SessionStart` hook entry. */
    fun unregisterSessionStartHook(port: Int)
}

/** Production-only delegate to the [HookRegistrar] object. */
object DefaultHookRegistry : HookRegistry {
    override fun isRegistered(port: Int): Boolean = HookRegistrar.isRegistered(port)
    override fun register(port: Int) = HookRegistrar.register(port)
    override fun unregister(port: Int) = HookRegistrar.unregister(port)
    override fun isCapabilityHookRegistered(port: Int): Boolean =
        HookRegistrar.isCapabilityHookRegistered(port)
    override fun registerCapabilityHook(port: Int) = HookRegistrar.registerCapabilityHook(port)
    override fun unregisterCapabilityHook(port: Int) = HookRegistrar.unregisterCapabilityHook(port)
    override fun isSessionStartHookRegistered(port: Int): Boolean =
        HookRegistrar.isSessionStartHookRegistered(port)
    override fun registerSessionStartHook(port: Int) = HookRegistrar.registerSessionStartHook(port)
    override fun unregisterSessionStartHook(port: Int) = HookRegistrar.unregisterSessionStartHook(port)
}
