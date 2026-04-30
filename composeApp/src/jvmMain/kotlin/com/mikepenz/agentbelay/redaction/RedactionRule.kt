package com.mikepenz.agentbelay.redaction

/**
 * One pattern within a [RedactionModule]. Pure-text rules — operates on
 * already-extracted output strings (Bash stdout/stderr, Read content,
 * WebFetch body), not on the tool's full JSON payload. The shape mapping
 * is done in [ToolOutputShape] before rules run.
 *
 * Returns the redacted string plus a count of how many spans were
 * replaced. A count of 0 means the rule did not fire — callers use this
 * to decide whether to record a [com.mikepenz.agentbelay.model.RedactionHit].
 */
interface RedactionRule {
    val id: String
    val name: String
    val description: String

    /**
     * Apply the rule to [input]. Implementations replace each matched
     * span with `[REDACTED:<moduleId>/<ruleId>]` and return the result.
     */
    fun redact(input: String, moduleId: String): RuleRedactionResult
}

data class RuleRedactionResult(
    val output: String,
    val count: Int,
)
