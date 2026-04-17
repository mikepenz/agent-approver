package com.mikepenz.agentbuddy.model

import kotlinx.serialization.Serializable

@Serializable
enum class CompressionIntensity { LITE, FULL, ULTRA }

@Serializable
data class CapabilitySettings(
    val modules: Map<String, CapabilityModuleSettings> = emptyMap(),
)

@Serializable
data class CapabilityModuleSettings(
    val enabled: Boolean = false,
    val intensity: CompressionIntensity? = null,
    val targets: Set<String> = emptySet(),
    val extras: Map<String, String> = emptyMap(),
)
