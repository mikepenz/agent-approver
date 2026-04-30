package com.mikepenz.agentbelay.redaction.modules

import com.mikepenz.agentbelay.model.RedactionMode
import com.mikepenz.agentbelay.redaction.RedactionModule
import com.mikepenz.agentbelay.redaction.RedactionRule
import com.mikepenz.agentbelay.redaction.RegexRedactionRule

/**
 * Catches well-known cloud / SaaS API keys with deterministic prefixes.
 *
 * These patterns are intentionally narrow — they target specific issuer
 * formats (AWS, GitHub, Slack, Stripe, Google) so false positives are
 * rare. A separate, broader entropy-based heuristic could be added later
 * but is out of scope for the initial set.
 */
object ApiKeyRedactionModule : RedactionModule {
    override val id: String = "api-keys"
    override val name: String = "API Keys"
    override val description: String =
        "Detects and redacts well-known API key formats (AWS, GitHub, Slack, Stripe, Google) in tool output."

    override val applicableTools: Set<String>? = null // run on every tool
    override val defaultMode: RedactionMode = RedactionMode.ENABLED

    override val rules: List<RedactionRule> = listOf(
        RegexRedactionRule(
            id = "aws-access-key",
            name = "AWS Access Key ID",
            description = "20-char IAM access key (AKIA…, ASIA…).",
            pattern = Regex("""(?:AKIA|ASIA)[0-9A-Z]{16}"""),
        ),
        RegexRedactionRule(
            id = "github-token",
            name = "GitHub Token",
            description = "Personal access tokens (ghp_, gho_, ghu_, ghs_, ghr_) and fine-grained PATs (github_pat_).",
            pattern = Regex("""\b(?:ghp|gho|ghu|ghs|ghr)_[A-Za-z0-9]{36,}\b|\bgithub_pat_[A-Za-z0-9_]{82,}\b"""),
        ),
        RegexRedactionRule(
            id = "slack-token",
            name = "Slack Token",
            description = "Slack bot/user/app tokens (xoxb-, xoxp-, xoxa-, xoxr-).",
            pattern = Regex("""\bxox[bpars]-[A-Za-z0-9-]{10,}\b"""),
        ),
        RegexRedactionRule(
            id = "stripe-key",
            name = "Stripe Key",
            description = "Stripe live or test keys (sk_live_, sk_test_, rk_live_, rk_test_).",
            pattern = Regex("""\b(?:sk|rk)_(?:live|test)_[A-Za-z0-9]{16,}\b"""),
        ),
        RegexRedactionRule(
            id = "google-api-key",
            name = "Google API Key",
            description = "Google API key (AIza prefix, 39 chars total).",
            pattern = Regex("""\bAIza[0-9A-Za-z_-]{35}\b"""),
        ),
    )
}
