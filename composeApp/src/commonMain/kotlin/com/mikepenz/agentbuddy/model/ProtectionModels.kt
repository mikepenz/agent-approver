package com.mikepenz.agentbuddy.model

import kotlinx.serialization.Serializable

@Serializable
enum class ProtectionMode {
    AUTO_BLOCK,
    ASK_AUTO_BLOCK,
    ASK,
    LOG_ONLY,
    DISABLED,
}

@Serializable
data class ProtectionSettings(
    val modules: Map<String, ModuleSettings> = emptyMap(),
)

@Serializable
data class ModuleSettings(
    val mode: ProtectionMode? = null,
    val disabledRules: Set<String> = emptySet(),
)

data class ProtectionHit(
    val moduleId: String,
    val ruleId: String,
    val message: String,
    val mode: ProtectionMode,
)
