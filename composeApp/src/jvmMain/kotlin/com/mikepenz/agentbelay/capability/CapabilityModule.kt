package com.mikepenz.agentbelay.capability

import com.mikepenz.agentbelay.model.CapabilityModuleSettings

/**
 * Supported hook events a capability can plug into. These are a superset of
 * events across Claude Code and Copilot CLI — each capability declares which
 * ones it needs, and the [CapabilityEngine] / hook registrars translate them
 * to the per-agent native event names.
 */
enum class HookEvent {
    /** Fires on every user prompt. Lets the capability inject per-turn context. */
    USER_PROMPT_SUBMIT,

    /** Fires once at session start. Injects session-level context / instructions. */
    SESSION_START,
}

/** Agent targets a capability can be installed into. */
enum class AgentTarget { CLAUDE_CODE, COPILOT_CLI }

/**
 * A pluggable built-in enhancement that delivers its behaviour by injecting
 * text into an agent session via hooks. Mirrors the shape of
 * `com.mikepenz.agentbelay.protection.ProtectionModule` so the Settings UI
 * and DI wiring stay analogous.
 */
interface CapabilityModule {
    val id: String
    val name: String
    val description: String
    val supportedTargets: Set<AgentTarget>
    val requiredHookEvents: Set<HookEvent>

    /**
     * Returns the context text to inject for the given event and per-module
     * settings. An empty string means "no injection for this call".
     */
    fun contextInjection(settings: CapabilityModuleSettings, event: HookEvent): String
}
