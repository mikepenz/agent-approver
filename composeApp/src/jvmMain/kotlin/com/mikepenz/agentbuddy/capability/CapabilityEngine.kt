package com.mikepenz.agentbuddy.capability

import com.mikepenz.agentbuddy.model.CapabilityModuleSettings
import com.mikepenz.agentbuddy.model.CapabilitySettings

class CapabilityEngine(
    val modules: List<CapabilityModule>,
    private val settingsProvider: () -> CapabilitySettings,
) {
    /**
     * Per-module settings as they should be read by callers — the stored
     * settings for the module, or a default `CapabilityModuleSettings()` if
     * the user has never touched this capability.
     */
    fun settingsFor(module: CapabilityModule): CapabilityModuleSettings =
        settingsProvider().modules[module.id] ?: CapabilityModuleSettings()

    /** Returns only modules the user has explicitly enabled. */
    fun enabledModules(): List<CapabilityModule> =
        modules.filter { settingsFor(it).enabled }

    /**
     * Union of all hook events required by enabled modules. Used by
     * HookRegistrar / CopilotBridgeInstaller to decide whether a given event
     * registration is needed at all.
     */
    fun requiredHookEvents(): Set<HookEvent> =
        enabledModules().flatMap { it.requiredHookEvents }.toSet()

    /**
     * Merged context injection for the given [event] across all enabled
     * modules that support [target] and declare [event]. Returns an empty
     * string if nothing applies. Module outputs are joined with a blank line.
     */
    fun injectionFor(event: HookEvent, target: AgentTarget): String {
        val parts = enabledModules()
            .filter { target in it.supportedTargets && event in it.requiredHookEvents }
            .map { it.contextInjection(settingsFor(it), event) }
            .filter { it.isNotBlank() }
        return parts.joinToString(separator = "\n\n")
    }
}
