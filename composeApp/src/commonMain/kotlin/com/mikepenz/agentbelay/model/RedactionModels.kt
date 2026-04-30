package com.mikepenz.agentbelay.model

import kotlinx.serialization.Serializable

/**
 * How a redaction module should behave when it matches sensitive content.
 *
 * Distinct from [ProtectionMode]: redaction is non-interactive (it runs
 * in PostToolUse where the agent has already executed and is waiting for
 * the result), so there is no `ASK` value — the user can only choose
 * whether the engine rewrites the output or just records the hit.
 */
@Serializable
enum class RedactionMode {
    /** Replace the matched span with a `[REDACTED:<module-id>]` marker before the agent reads it. */
    ENABLED,

    /** Detect + record the hit but pass the unredacted output through. Useful for tuning rules. */
    LOG_ONLY,

    /** Module is inactive; rules are not evaluated. */
    DISABLED,
}

/** Per-module configuration. Mirrors [ModuleSettings] but uses [RedactionMode]. */
@Serializable
data class RedactionModuleSettings(
    val mode: RedactionMode? = null,
    val disabledRules: Set<String> = emptySet(),
)

/** Top-level redaction configuration on [AppSettings]. */
@Serializable
data class RedactionSettings(
    /** Master switch — when false, the engine does not run regardless of per-module mode. */
    val enabled: Boolean = true,
    val modules: Map<String, RedactionModuleSettings> = emptyMap(),
)

/**
 * One redacted span recorded for history / UI display. Stored on
 * [ApprovalResult.redactionHits] alongside the (optional) modified
 * response. Sample text is intentionally short and never includes the
 * redacted secret itself — only the module/rule that fired and where in
 * the output it was found.
 */
@Serializable
data class RedactionHit(
    val moduleId: String,
    val ruleId: String,
    /** Where in the tool's output shape the match landed (e.g. `stdout`, `stderr`, `content`). */
    val field: String,
    /** How many times the rule matched in this field. */
    val count: Int,
)
