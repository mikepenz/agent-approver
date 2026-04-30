package com.mikepenz.agentbelay.redaction

import com.mikepenz.agentbelay.model.RedactionMode

/**
 * A bundle of related [RedactionRule]s with a shared default mode and a
 * tool-applicability filter. Mirrors `ProtectionModule`'s structure so
 * the Settings UI can render both engines through one component family.
 */
interface RedactionModule {
    val id: String
    val name: String
    val description: String

    /**
     * Tool names this module evaluates against. Standard Belay-canonical
     * names: "Bash", "Read", "WebFetch". A module that should run on every
     * tool returns `null`.
     */
    val applicableTools: Set<String>?

    val defaultMode: RedactionMode

    val rules: List<RedactionRule>
}
