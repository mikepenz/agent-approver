package com.mikepenz.agentbelay.redaction

import com.mikepenz.agentbelay.model.RedactionHit
import com.mikepenz.agentbelay.model.RedactionMode
import com.mikepenz.agentbelay.model.RedactionSettings
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject

/**
 * Per-tool redaction over PostToolUse output. Mirrors `ProtectionEngine`'s
 * shape: a list of modules, settings come from a thunk so live UI toggles
 * apply on the next call without restart, the engine is harness-agnostic
 * and operates on canonical tool names + JSON shapes only.
 *
 * Result semantics:
 *
 *  - **No applicable rules / no hits**: [Result.redactedOutput] is null,
 *    [Result.hits] is empty. Callers pass the original output through.
 *  - **Hits found, at least one in [RedactionMode.ENABLED]**:
 *    `redactedOutput` is the rebuilt response object with redactions
 *    applied; `hits` lists every match (including LOG_ONLY hits, so the
 *    history pane shows a complete picture).
 *  - **Hits found, all in LOG_ONLY**: `redactedOutput` is null but `hits`
 *    is populated — caller passes original through but records the hits.
 */
class RedactionEngine(
    val modules: List<RedactionModule>,
    private val settingsProvider: () -> RedactionSettings,
) {
    data class Result(
        /**
         * The rebuilt tool response with redactions applied, or null if
         * the engine did not modify the output (no hits, all LOG_ONLY,
         * or unsupported tool).
         */
        val redactedOutput: JsonObject?,
        val hits: List<RedactionHit>,
    ) {
        companion object {
            val Empty = Result(redactedOutput = null, hits = emptyList())
        }
    }

    fun scan(toolName: String, toolResponse: JsonElement?): Result {
        if (toolResponse == null) return Result.Empty
        if (toolName !in ToolOutputShape.supportedTools) return Result.Empty

        val settings = settingsProvider()
        if (!settings.enabled) return Result.Empty

        val extraction = ToolOutputShape.extract(toolName, toolResponse)
        if (extraction.regions.isEmpty()) return Result.Empty

        val hits = mutableListOf<RedactionHit>()
        // Track the modified text per field; start with the originals.
        val workingText = extraction.regions.associate { it.field to it.text }.toMutableMap()
        // Track whether we should rebuild at all (any ENABLED hit).
        var anyEnabledHit = false

        for (module in modules) {
            val moduleSettings = settings.modules[module.id]
            val mode = moduleSettings?.mode ?: module.defaultMode
            if (mode == RedactionMode.DISABLED) continue
            if (module.applicableTools != null && toolName !in module.applicableTools!!) continue

            val disabledRules = moduleSettings?.disabledRules ?: emptySet()
            for (rule in module.rules) {
                if (rule.id in disabledRules) continue

                for ((field, currentText) in workingText.toMap()) {
                    val ruleResult = rule.redact(currentText, module.id)
                    if (ruleResult.count == 0) continue

                    hits += RedactionHit(
                        moduleId = module.id,
                        ruleId = rule.id,
                        field = field,
                        count = ruleResult.count,
                    )

                    if (mode == RedactionMode.ENABLED) {
                        workingText[field] = ruleResult.output
                        anyEnabledHit = true
                    }
                    // LOG_ONLY: record hit, do not mutate the working text.
                }
            }
        }

        if (hits.isEmpty()) return Result.Empty
        val redacted = if (anyEnabledHit) {
            ToolOutputShape.rebuild(toolName, toolResponse, workingText)
        } else {
            null
        }
        return Result(redactedOutput = redacted, hits = hits)
    }
}
