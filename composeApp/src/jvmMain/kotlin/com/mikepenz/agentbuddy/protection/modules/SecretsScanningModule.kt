package com.mikepenz.agentbuddy.protection.modules

import com.mikepenz.agentbuddy.model.HookInput
import com.mikepenz.agentbuddy.model.ProtectionHit
import com.mikepenz.agentbuddy.model.ProtectionMode
import com.mikepenz.agentbuddy.protection.CommandParser
import com.mikepenz.agentbuddy.protection.ProtectionModule
import com.mikepenz.agentbuddy.protection.ProtectionRule
import com.mikepenz.agentbuddy.protection.parser.SimpleCommand
import com.mikepenz.agentbuddy.protection.parser.allPipelines
import com.mikepenz.agentbuddy.protection.parser.allSimpleCommands
import com.mikepenz.agentbuddy.protection.parser.effectiveCommands
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull

/**
 * Detects secrets being exposed before the agent runs them: high-entropy API key strings
 * embedded in Bash commands, exfiltration of credential files via network tools, env var
 * leaks into network egress, and hardcoded secret assignments in Write/Edit content.
 *
 * Hit messages never include the actual matched secret — they describe the category only,
 * since hits are logged and surfaced in the UI.
 */
object SecretsScanningModule : ProtectionModule {
    override val id = "secrets_scanning"
    override val name = "Secrets Scanning"
    override val description =
        "Flags commands and file content that expose API keys, tokens, private keys, " +
            "or exfiltrate credential files."
    override val corrective = false
    override val defaultMode = ProtectionMode.ASK_AUTO_BLOCK
    override val applicableTools = setOf("Bash", "Write", "Edit")

    override val rules: List<ProtectionRule> = listOf(
        ApiKeyLiterals,
        CredentialFileExfiltration,
        EnvVarLeak,
        HardcodedSecretAssignment,
    )

    private fun hit(ruleId: String, message: String) = ProtectionHit(
        moduleId = id,
        ruleId = ruleId,
        message = message,
        mode = defaultMode,
    )

    // -----------------------------------------------------------------------
    // Shared regex catalogue
    // -----------------------------------------------------------------------

    /** Regex set used to detect known token formats. Order matters only for the message label. */
    private val TOKEN_PATTERNS: List<Pair<Regex, String>> = listOf(
        Regex("""AKIA[0-9A-Z]{16}""") to "AWS access key",
        Regex("""ghp_[A-Za-z0-9]{36}""") to "GitHub personal access token",
        Regex("""gho_[A-Za-z0-9]{36}""") to "GitHub OAuth token",
        Regex("""ghs_[A-Za-z0-9]{36}""") to "GitHub server token",
        Regex("""ghu_[A-Za-z0-9]{36}""") to "GitHub user token",
        Regex("""AIza[0-9A-Za-z_\-]{35}""") to "Google API key",
        Regex("""xox[baprs]-[0-9a-zA-Z\-]{10,}""") to "Slack token",
        Regex("""eyJ[A-Za-z0-9_=\-]+\.eyJ[A-Za-z0-9_=\-]+\.[A-Za-z0-9._=\-]+""") to "JWT",
        Regex("""sk-[A-Za-z0-9]{20,}""") to "API secret key (sk-...)",
        Regex("""-----BEGIN (?:RSA |EC |OPENSSH |DSA |PGP )?PRIVATE KEY-----""") to "private key block",
    )

    /** Obvious placeholders that should never trigger a real-secret hit. */
    private val PLACEHOLDER_EXCLUSIONS: List<Regex> = listOf(
        Regex("""(?i)YOUR[_\-]?(API[_\-]?KEY|TOKEN|SECRET|PASSWORD|PASSWD|KEY)"""),
        Regex("""(?i)REPLACE[_\-]?ME"""),
        Regex("""(?i)PLACEHOLDER"""),
        Regex("""(?i)\bexample[_\-]?(value|key|token|secret|password)\b"""),
        Regex("""(?i)\bdummy\b"""),
        Regex("""(?i)\bfake\b"""),
        Regex("""x{6,}"""),
    )

    private fun isPlaceholder(text: String): Boolean =
        PLACEHOLDER_EXCLUSIONS.any { it.containsMatchIn(text) }

    /**
     * Shared catalogue of network/egress tools. Referenced by multiple rules to keep them
     * in sync — adding a tool here benefits every secrets-scanning rule at once.
     */
    private val NETWORK_TOOLS = setOf(
        "curl", "wget", "nc", "ncat", "scp", "rsync", "sftp", "ftp", "http", "httpie",
    )

    private fun findToken(text: String): String? {
        for ((regex, label) in TOKEN_PATTERNS) {
            val match = regex.find(text) ?: continue
            // The placeholder check inspects a small window around the match to avoid skipping
            // a real token just because a placeholder word lives elsewhere in the same string.
            val start = (match.range.first - 16).coerceAtLeast(0)
            val end = (match.range.last + 16).coerceAtMost(text.length)
            val window = text.substring(start, end)
            if (isPlaceholder(window)) continue
            return label
        }
        return null
    }

    // -----------------------------------------------------------------------
    // Helpers for Write/Edit content extraction
    // -----------------------------------------------------------------------

    private fun writeContent(hookInput: HookInput): String? {
        val content = hookInput.toolInput["content"] as? JsonPrimitive ?: return null
        return content.contentOrNull
    }

    private fun editNewString(hookInput: HookInput): String? {
        val ns = hookInput.toolInput["new_string"] as? JsonPrimitive ?: return null
        return ns.contentOrNull
    }

    private fun fileContent(hookInput: HookInput): String? = when (hookInput.toolName) {
        "Write" -> writeContent(hookInput)
        "Edit" -> editNewString(hookInput)
        else -> null
    }

    // -----------------------------------------------------------------------
    // Rule 1: high-entropy API keys / tokens embedded in commands
    // -----------------------------------------------------------------------

    private object ApiKeyLiterals : ProtectionRule {
        override val id = "api_key_literal"
        override val name = "API key literal in command"
        override val description =
            "Detects AWS/GitHub/Google/Slack/JWT/private-key tokens embedded in a Bash command."

        override fun evaluate(hookInput: HookInput): ProtectionHit? {
            if (hookInput.toolName != "Bash") return null
            val cmd = CommandParser.bashCommand(hookInput) ?: return null
            val label = findToken(cmd) ?: return null
            return hit(id, "Possible $label embedded in command")
        }
    }

    // -----------------------------------------------------------------------
    // Rule 2: credential file READ + network egress combinations
    // -----------------------------------------------------------------------

    private object CredentialFileExfiltration : ProtectionRule {
        override val id = "credential_exfiltration"
        override val name = "Credential file exfiltration"
        override val description =
            "Detects reads/uploads of .env, ~/.aws/credentials, ~/.ssh/id_*, .netrc, etc. " +
                "via cat, curl, scp, rsync, or similar tools."

        // Path patterns considered credential-bearing for read/upload purposes.
        private val CRED_PATHS = listOf(
            Regex("""(^|/)\.env(\.[A-Za-z0-9_\-]+)?$"""),
            Regex("""\.aws/credentials"""),
            Regex("""\.ssh/id_[A-Za-z0-9_\-]+"""),
            Regex("""(^|/)\.netrc$"""),
            Regex("""(^|/)\.npmrc$"""),
            Regex("""\.docker/config\.json"""),
            Regex("""\.kube/config"""),
        )

        private val CRED_PATH_EXCLUSIONS = listOf(
            Regex("""\.env\.(example|sample|template)$"""),
        )

        // Local read tools that, when piped to a network tool, are still suspicious.
        private val READ_TOOLS = setOf("cat", "tac", "head", "tail", "less", "more", "base64")

        private fun matchesCredPath(path: String): Boolean {
            if (CRED_PATH_EXCLUSIONS.any { it.containsMatchIn(path) }) return false
            return CRED_PATHS.any { it.containsMatchIn(path) }
        }

        private fun literalsOf(sc: SimpleCommand) =
            buildList {
                for (a in sc.args) a.literal?.let { add(it) }
                for (r in sc.redirects) r.target.literal?.let { add(it) }
                for (asg in sc.assignments) asg.value.literal?.let { add(it) }
            }

        private fun referencesCredPath(sc: SimpleCommand): Boolean {
            val literals = literalsOf(sc)
            if (literals.any { matchesCredPath(it) }) return true
            // curl --data-binary @/path/to/.env style: strip leading '@' before matching.
            return literals.map { it.removePrefix("@") }.any { matchesCredPath(it) }
        }

        override fun evaluate(hookInput: HookInput): ProtectionHit? {
            if (hookInput.toolName != "Bash") return null
            val parsed = CommandParser.parsedBash(hookInput) ?: return null

            // 1) Direct network upload of a credential file:
            //    `curl --data-binary @/home/user/.aws/credentials ...`, `scp ~/.ssh/id_rsa host:...`
            for (sc in parsed.allSimpleCommands()) {
                val name = sc.commandName ?: continue
                if (name in NETWORK_TOOLS && referencesCredPath(sc)) {
                    return hit(id, "Credential file referenced by network upload command")
                }
            }

            // 2) True pipeline combining a credential-file read with a network tool:
            //    `cat .env | curl --data-binary @- https://...`
            //    We require both commands to live in the SAME pipeline so that accidental
            //    co-occurrence via `;` / `&&` does not produce a "piped" hit.
            for (pipeline in parsed.allPipelines()) {
                val cmds = pipeline.effectiveCommands()
                if (cmds.size < 2) continue
                var sawCredRead = false
                var sawNetwork = false
                for (cmd in cmds) {
                    val name = cmd.commandName ?: continue
                    if (name in READ_TOOLS && referencesCredPath(cmd)) sawCredRead = true
                    if (name in NETWORK_TOOLS) sawNetwork = true
                }
                if (sawCredRead && sawNetwork) {
                    return hit(id, "Credential file piped into a network tool")
                }
            }

            return null
        }
    }

    // -----------------------------------------------------------------------
    // Rule 3: env-var leaks into commands / network egress
    // -----------------------------------------------------------------------

    private object EnvVarLeak : ProtectionRule {
        override val id = "env_var_leak"
        override val name = "Environment variable leak"
        override val description =
            "Detects commands that print environment variables (printenv/env) to stdout " +
                "or pipe them to a network tool, plus echo of $-prefixed secret variables."

        // Variables whose name strongly suggests a secret.
        private val SECRET_VAR_NAME = Regex(
            """\$\{?([A-Z0-9_]*(?:SECRET|TOKEN|KEY|PASSWORD|PASSWD|PWD|API[_-]?KEY|CREDENTIAL)[A-Z0-9_]*)\}?"""
        )

        override fun evaluate(hookInput: HookInput): ProtectionHit? {
            if (hookInput.toolName != "Bash") return null
            val cmd = CommandParser.bashCommand(hookInput) ?: return null
            val parsed = CommandParser.parsedBash(hookInput) ?: return null

            // 1) env/printenv piped directly into a network tool within the SAME pipeline.
            //    Unrelated chains like `env; curl https://...` are intentionally ignored here
            //    to avoid false positives when a user just happens to run both in sequence.
            for (pipeline in parsed.allPipelines()) {
                val cmds = pipeline.effectiveCommands()
                if (cmds.size < 2) continue
                var sawEnvDump = false
                var sawNetwork = false
                for (c in cmds) {
                    val name = c.commandName ?: continue
                    if (name == "printenv" || name == "env") sawEnvDump = true
                    if (name in NETWORK_TOOLS) sawNetwork = true
                }
                if (sawEnvDump && sawNetwork) {
                    return hit(id, "Environment dump piped into a network tool")
                }
            }

            // 2) echo $SECRET_TOKEN-style leak of a secret-named environment variable.
            var hasEchoOfSecretVar = false
            for (sc in parsed.allSimpleCommands()) {
                val name = sc.commandName ?: continue
                if (name == "echo") {
                    for (a in sc.args) {
                        val raw = a.literal ?: continue
                        if (SECRET_VAR_NAME.containsMatchIn(raw)) {
                            hasEchoOfSecretVar = true
                        }
                    }
                }
            }

            // Even if literal extraction missed something (quoted "$TOKEN" tokens, etc.), the
            // raw command string is still a useful signal for echo $SECRET-style leaks.
            if (!hasEchoOfSecretVar && SECRET_VAR_NAME.containsMatchIn(cmd) &&
                Regex("""\becho\b""").containsMatchIn(cmd)
            ) {
                hasEchoOfSecretVar = true
            }

            if (hasEchoOfSecretVar) {
                return hit(id, "Echo of secret-named environment variable")
            }
            return null
        }
    }

    // -----------------------------------------------------------------------
    // Rule 4: hardcoded secret assignments in Write/Edit content
    // -----------------------------------------------------------------------

    private object HardcodedSecretAssignment : ProtectionRule {
        override val id = "hardcoded_secret"
        override val name = "Hardcoded secret in file content"
        override val description =
            "Scans Write/Edit content for token literals and password=/api_key=/secret= assignments."

        private val ASSIGNMENT_PATTERNS: List<Pair<Regex, String>> = listOf(
            Regex("""(?i)password\s*[:=]\s*["'][^"']{6,}["']""") to "password assignment",
            Regex("""(?i)api[_\-]?key\s*[:=]\s*["'][^"']+["']""") to "api_key assignment",
            Regex("""(?i)secret\s*[:=]\s*["'][^"']+["']""") to "secret assignment",
        )

        override fun evaluate(hookInput: HookInput): ProtectionHit? {
            if (hookInput.toolName != "Write" && hookInput.toolName != "Edit") return null
            val content = fileContent(hookInput) ?: return null

            findToken(content)?.let { label ->
                return hit(id, "Possible $label in file content")
            }

            for ((regex, label) in ASSIGNMENT_PATTERNS) {
                val match = regex.find(content) ?: continue
                val start = (match.range.first - 16).coerceAtLeast(0)
                val end = (match.range.last + 16).coerceAtMost(content.length)
                if (isPlaceholder(content.substring(start, end))) continue
                return hit(id, "Possible hardcoded $label in file content")
            }
            return null
        }
    }
}
