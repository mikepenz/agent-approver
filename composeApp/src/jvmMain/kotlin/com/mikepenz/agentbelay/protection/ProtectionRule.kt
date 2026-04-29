package com.mikepenz.agentbelay.protection

import com.mikepenz.agentbelay.model.HookInput
import com.mikepenz.agentbelay.model.ProtectionHit

interface ProtectionRule {
    val id: String
    val name: String
    val description: String
    /** The message the AI receives when this rule fires. Shown in UI for corrective modules. */
    val correctiveHint: String get() = ""
    fun evaluate(hookInput: HookInput): ProtectionHit?
}
