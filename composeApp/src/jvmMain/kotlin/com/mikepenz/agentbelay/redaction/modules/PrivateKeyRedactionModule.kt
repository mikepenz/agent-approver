package com.mikepenz.agentbelay.redaction.modules

import com.mikepenz.agentbelay.model.RedactionMode
import com.mikepenz.agentbelay.redaction.RedactionModule
import com.mikepenz.agentbelay.redaction.RedactionRule
import com.mikepenz.agentbelay.redaction.RegexRedactionRule

/**
 * Strips PEM-format private-key blocks (RSA / OPENSSH / EC / DSA / generic).
 * Catches the entire `-----BEGIN ... PRIVATE KEY-----` … `-----END ... PRIVATE KEY-----`
 * span including the base64 body and trailing armor.
 */
object PrivateKeyRedactionModule : RedactionModule {
    override val id: String = "private-keys"
    override val name: String = "Private Keys"
    override val description: String =
        "Redacts entire PEM-format private key blocks (RSA, OpenSSH, EC, DSA, generic)."

    override val applicableTools: Set<String>? = null
    override val defaultMode: RedactionMode = RedactionMode.ENABLED

    override val rules: List<RedactionRule> = listOf(
        RegexRedactionRule(
            id = "pem-block",
            name = "PEM Private Key Block",
            description = "Matches `-----BEGIN ... PRIVATE KEY-----` to `-----END ... PRIVATE KEY-----`.",
            // [\s\S]*? — non-greedy "any char including newline" since
            // Kotlin Regex doesn't expose the DOTALL flag via the literal.
            pattern = Regex("""-----BEGIN (?:[A-Z]+ )?PRIVATE KEY-----[\s\S]*?-----END (?:[A-Z]+ )?PRIVATE KEY-----"""),
        ),
    )
}
