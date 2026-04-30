package com.mikepenz.agentbelay.harness

/**
 * Per-harness feature flags. Drives both runtime branching (e.g. should
 * PostToolUseRoute run the redaction engine?) and UI presentation (e.g.
 * should the "Always Allow" button be shown when this harness is active?).
 *
 * Flags reflect what the harness's hook protocol supports today, not what
 * Belay's UI exposes — UI gating is layered on top of these.
 */
data class HarnessCapabilities(
    /** PermissionRequest / preToolUse can return rewritten tool args (Claude `updatedInput`, Copilot `modifiedArgs`). */
    val supportsArgRewriting: Boolean,
    /** PermissionRequest can return `updatedPermissions` for write-through to harness's own settings. */
    val supportsAlwaysAllowWriteThrough: Boolean,
    /** PostToolUse can return `updatedToolOutput` to redact tool output before the agent reads it. */
    val supportsOutputRedaction: Boolean,
    /** PreToolUse `permissionDecision` accepts `defer` (Claude Code v2.1.89+). */
    val supportsDefer: Boolean,
    /** Deny response can interrupt the agent (Copilot `interrupt: true`, Claude `interrupt: true`). */
    val supportsInterruptOnDeny: Boolean,
    /** SessionStart / UserPromptSubmit hooks can inject `additionalContext` into the agent's conversation. */
    val supportsAdditionalContextInjection: Boolean,
)
