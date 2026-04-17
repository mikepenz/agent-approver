package com.mikepenz.agentbuddy.protection

import com.mikepenz.agentbuddy.model.HookInput
import com.mikepenz.agentbuddy.model.ProtectionHit
import com.mikepenz.agentbuddy.model.ProtectionMode
import com.mikepenz.agentbuddy.model.ProtectionSettings

class ProtectionEngine(
    val modules: List<ProtectionModule>,
    private val settingsProvider: () -> ProtectionSettings,
) {
    fun evaluate(hookInput: HookInput): List<ProtectionHit> {
        val settings = settingsProvider()
        val hits = mutableListOf<ProtectionHit>()
        for (module in modules) {
            val moduleSettings = settings.modules[module.id]
            val mode = moduleSettings?.mode ?: module.defaultMode
            if (mode == ProtectionMode.DISABLED) continue
            if (hookInput.toolName !in module.applicableTools) continue
            val disabledRules = moduleSettings?.disabledRules ?: emptySet()
            for (rule in module.rules) {
                if (rule.id in disabledRules) continue
                val hit = rule.evaluate(hookInput)
                if (hit != null) {
                    hits.add(hit.copy(mode = mode))
                }
            }
        }
        return hits
    }

    fun highestSeverity(hits: List<ProtectionHit>): ProtectionMode {
        return hits.minByOrNull { it.mode.ordinal }?.mode ?: ProtectionMode.DISABLED
    }

    fun evaluateAll(hookInput: HookInput): List<ProtectionEvaluation> {
        val settings = settingsProvider()
        return modules.map { module ->
            val moduleSettings = settings.modules[module.id]
            val mode = moduleSettings?.mode ?: module.defaultMode
            val enabled = mode != ProtectionMode.DISABLED
            val applicable = hookInput.toolName in module.applicableTools
            val disabledRules = moduleSettings?.disabledRules ?: emptySet()
            val ruleResults = module.rules.map { rule ->
                val ruleEnabled = rule.id !in disabledRules
                val hit = if (enabled && applicable && ruleEnabled) rule.evaluate(hookInput) else null
                RuleEvalResult(
                    ruleId = rule.id,
                    ruleName = rule.name,
                    enabled = ruleEnabled,
                    matched = hit != null,
                    message = hit?.message,
                )
            }
            ProtectionEvaluation(
                moduleId = module.id,
                moduleName = module.name,
                mode = mode,
                applicable = applicable,
                enabled = enabled,
                ruleResults = ruleResults,
            )
        }
    }
}
