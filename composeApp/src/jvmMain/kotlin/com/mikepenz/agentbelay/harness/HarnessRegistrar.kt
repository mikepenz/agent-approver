package com.mikepenz.agentbelay.harness

/**
 * Installs and removes a harness's hook configuration on the user's
 * machine. Implementations wrap the file-IO logic specific to that
 * harness (settings.json mutation, bridge-script writes, TOML edits,
 * plugin installs) — see [com.mikepenz.agentbelay.hook.HookRegistrar]
 * (Claude Code) and [com.mikepenz.agentbelay.hook.CopilotBridgeInstaller]
 * (Copilot CLI) for the existing implementations this interface unifies.
 *
 * All methods perform synchronous filesystem I/O; callers must invoke
 * them from an IO-friendly dispatcher.
 */
interface HarnessRegistrar {
    /** Display name surfaced in the Settings → Integrations panel. */
    val displayName: String

    /** True iff the integration is fully installed for [port]. */
    fun isRegistered(port: Int): Boolean

    /** Idempotently install all required artifacts for [port]. */
    fun register(port: Int)

    /** Remove all artifacts that match [port]. */
    fun unregister(port: Int)

    /**
     * Artifacts this registrar would install for [port], for UI listing
     * and uninstall verification. Pure description — does not touch the
     * filesystem. Default empty for harnesses that have nothing extra
     * beyond the hooked settings file (which the registrar already
     * manages internally).
     */
    fun describeArtifacts(port: Int): List<OutboardArtifact> = emptyList()
}
