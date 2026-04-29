package com.mikepenz.agentbelay.model

import kotlinx.serialization.Serializable

@Serializable
data class PermissionSuggestion(
    val type: String,
    val tool: String? = null,
    val prefix: String? = null,
    val rules: List<RuleEntry>? = null,
    val behavior: String? = null,
    val destination: String? = null,
    val directories: List<String>? = null,
    val mode: String? = null,
)

@Serializable
data class RuleEntry(
    val toolName: String = "",
    val ruleContent: String = "",
)
