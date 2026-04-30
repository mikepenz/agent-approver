package com.mikepenz.agentbelay.redaction.modules

import com.mikepenz.agentbelay.model.RedactionMode
import com.mikepenz.agentbelay.redaction.RedactionModule
import com.mikepenz.agentbelay.redaction.RedactionRule
import com.mikepenz.agentbelay.redaction.RegexRedactionRule

/**
 * Catches JWTs with a base64url-encoded header that decodes to JSON
 * starting with `{`. The `ey` prefix is the base64url encoding of the
 * `{"…` literal that opens every JWT header — extremely high-signal
 * marker, very few false positives in real-world output.
 */
object JwtRedactionModule : RedactionModule {
    override val id: String = "jwt"
    override val name: String = "JWT Tokens"
    override val description: String =
        "Redacts JSON Web Tokens (three base64url-encoded segments separated by dots, header beginning `ey`)."

    override val applicableTools: Set<String>? = null
    override val defaultMode: RedactionMode = RedactionMode.ENABLED

    override val rules: List<RedactionRule> = listOf(
        RegexRedactionRule(
            id = "compact-jwt",
            name = "Compact-Serialized JWT",
            description = "Three base64url segments (header.payload.signature), header starting with `ey`.",
            pattern = Regex("""\bey[A-Za-z0-9_-]{8,}\.ey[A-Za-z0-9_-]{8,}\.[A-Za-z0-9_-]{8,}\b"""),
        ),
    )
}
