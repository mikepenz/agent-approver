package com.mikepenz.agentbelay.redaction.modules

import com.mikepenz.agentbelay.model.RedactionMode
import com.mikepenz.agentbelay.redaction.RedactionModule
import com.mikepenz.agentbelay.redaction.RedactionRule
import com.mikepenz.agentbelay.redaction.RuleRedactionResult

/**
 * Catches credential-shaped env-var assignments leaking from `cat .env`,
 * `printenv`, `set`, or shell history. Recognises the common naming
 * conventions: `*_TOKEN`, `*_SECRET`, `*_PASSWORD`, `*_KEY`, `*_API_KEY`.
 *
 * Single rule per module to keep the UI simple — within the rule we run
 * a grouped regex over each line and redact only the right-hand value,
 * preserving the variable name so the agent still knows which secret was
 * present without leaking its content.
 */
object EnvVarRedactionModule : RedactionModule {
    override val id: String = "env-vars"
    override val name: String = "Credential Env Vars"
    override val description: String =
        "Redacts the value of environment-variable assignments whose key suggests a credential (TOKEN, SECRET, PASSWORD, KEY, API_KEY)."

    override val applicableTools: Set<String>? = null
    override val defaultMode: RedactionMode = RedactionMode.ENABLED

    private val pattern = Regex(
        // Anchored to a line start (or shell `export ` prefix). Captures
        // the credential-shaped key as group 1, the optional quote as group 2,
        // and the value (excluding the closing quote/eol) as group 3.
        """(?m)^(?:export\s+)?([A-Z][A-Z0-9_]*(?:TOKEN|SECRET|PASSWORD|KEY|API_KEY|PASS|PWD|CREDENTIAL[S]?))=(['"]?)([^\r\n]*?)\2$"""
    )

    override val rules: List<RedactionRule> = listOf(
        object : RedactionRule {
            override val id: String = "credential-assignment"
            override val name: String = "Credential Assignment"
            override val description: String =
                "Replaces the value of `KEY=value` lines where KEY matches a credential pattern."

            override fun redact(input: String, moduleId: String): RuleRedactionResult {
                var count = 0
                val replaced = pattern.replace(input) { match ->
                    val key = match.groupValues[1]
                    val quote = match.groupValues[2]
                    val value = match.groupValues[3]
                    if (value.isBlank()) return@replace match.value
                    count++
                    "$key=$quote[REDACTED:$moduleId/$id]$quote"
                }
                return RuleRedactionResult(output = replaced, count = count)
            }
        }
    )
}
