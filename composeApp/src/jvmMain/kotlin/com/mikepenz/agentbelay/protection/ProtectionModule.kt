package com.mikepenz.agentbelay.protection

import com.mikepenz.agentbelay.model.ProtectionMode

interface ProtectionModule {
    val id: String
    val name: String
    val description: String
    val corrective: Boolean
    val defaultMode: ProtectionMode
    val applicableTools: Set<String>
    val rules: List<ProtectionRule>
}
