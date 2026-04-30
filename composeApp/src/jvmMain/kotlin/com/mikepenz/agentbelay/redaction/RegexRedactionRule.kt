package com.mikepenz.agentbelay.redaction

/**
 * Convenience [RedactionRule] backed by a single [Regex]. Replaces every
 * match with `[REDACTED:<moduleId>/<id>]` and returns the match count.
 *
 * Most secret-detection rules are single-pattern (AWS access key,
 * GitHub token format, JWT shape). Multi-pattern modules can compose
 * several `RegexRedactionRule` instances.
 */
class RegexRedactionRule(
    override val id: String,
    override val name: String,
    override val description: String,
    private val pattern: Regex,
) : RedactionRule {
    override fun redact(input: String, moduleId: String): RuleRedactionResult {
        var count = 0
        val replaced = pattern.replace(input) {
            count++
            "[REDACTED:$moduleId/$id]"
        }
        return RuleRedactionResult(output = replaced, count = count)
    }
}
