package com.mikepenz.agentbuddy.protection.modules

import com.mikepenz.agentbuddy.model.HookInput
import com.mikepenz.agentbuddy.model.ProtectionHit
import com.mikepenz.agentbuddy.model.ProtectionMode
import com.mikepenz.agentbuddy.protection.CommandParser
import com.mikepenz.agentbuddy.protection.ProtectionModule
import com.mikepenz.agentbuddy.protection.ProtectionRule
import com.mikepenz.agentbuddy.protection.parser.allSimpleCommands

object SensitiveFilesModule : ProtectionModule {
    override val id = "sensitive_files"
    override val name = "Sensitive File Protection"
    override val description = "Blocks writes to credential files, keys, cloud configs, and other sensitive paths."
    override val corrective = false
    override val defaultMode = ProtectionMode.AUTO_BLOCK
    override val applicableTools = setOf("Bash", "Write", "Edit")

    override val rules: List<ProtectionRule> = listOf(
        EnvFiles,
        CryptoKeys,
        SshDir,
        CloudCreds,
        SecretFiles,
        GpgFiles,
        Keychain,
        AuthConfigs,
        TerraformState,
        KubeConfig,
        DockerConfig,
    )

    private fun hit(ruleId: String, message: String) = ProtectionHit(
        moduleId = id,
        ruleId = ruleId,
        message = message,
        mode = defaultMode,
    )

    /**
     * Collects every literal string from the parsed Bash command that could name a file:
     * positional args, redirect targets, and assignment values. Opaque (variable / substitution)
     * tokens are skipped — if the target is opaque the rule cannot pattern-match on it anyway.
     */
    private fun collectLiterals(hookInput: HookInput): List<String> {
        val parsed = CommandParser.parsedBash(hookInput) ?: return emptyList()
        val out = mutableListOf<String>()
        for (sc in parsed.allSimpleCommands()) {
            for (a in sc.args) a.literal?.let { out.add(it) }
            for (r in sc.redirects) r.target.literal?.let { out.add(it) }
            for (asg in sc.assignments) asg.value.literal?.let { out.add(it) }
        }
        return out
    }

    /**
     * Base class for rules that check file paths against regex patterns. Handles Write/Edit
     * (via file_path) and Bash (via literal tokens extracted from the parsed AST).
     */
    private abstract class SensitiveFileRule : ProtectionRule {
        abstract val patterns: List<Regex>
        open val exclusions: List<Regex> = emptyList()

        private fun matches(path: String): Boolean {
            if (exclusions.any { it.containsMatchIn(path) }) return false
            return patterns.any { it.containsMatchIn(path) }
        }

        override fun evaluate(hookInput: HookInput): ProtectionHit? {
            when (hookInput.toolName) {
                "Write", "Edit" -> {
                    val path = CommandParser.filePath(hookInput) ?: return null
                    if (matches(path)) return hit(id, "Sensitive file access: $path")
                }
                "Bash" -> {
                    val cmd = CommandParser.bashCommand(hookInput) ?: return null
                    for (literal in collectLiterals(hookInput)) {
                        if (matches(literal)) return hit(id, "Sensitive file in command: $cmd")
                    }
                }
            }
            return null
        }
    }

    private object EnvFiles : SensitiveFileRule() {
        override val id = "env_files"
        override val name = "Environment files"
        override val description = "Blocks writes to .env files that may contain secrets."
        override val patterns = listOf(
            // full path ending in .env / .env.X
            Regex("""(^|/)\.env(\.[a-zA-Z0-9_-]+)?$"""),
        )
        override val exclusions = listOf(Regex("""\.env\.(example|sample|template)$"""))
    }

    private object CryptoKeys : SensitiveFileRule() {
        override val id = "crypto_keys"
        override val name = "Cryptographic key files"
        override val description = "Blocks writes to PEM, key, certificate, and PKCS files."
        override val patterns = listOf(Regex("""\.(pem|key|p12|pfx|crt|cer|der)$"""))
    }

    private object SshDir : SensitiveFileRule() {
        override val id = "ssh_dir"
        override val name = "SSH directory"
        override val description = "Blocks writes to files in the .ssh directory."
        override val patterns = listOf(Regex("""\.ssh/"""))
    }

    private object CloudCreds : SensitiveFileRule() {
        override val id = "cloud_creds"
        override val name = "Cloud credentials"
        override val description = "Blocks writes to AWS, GCP, and Azure credential files."
        override val patterns = listOf(
            Regex("""\.aws/credentials"""),
            Regex("""\.gcp/"""),
            Regex("""\.azure/"""),
        )
    }

    private object SecretFiles : SensitiveFileRule() {
        override val id = "secret_files"
        override val name = "Secret files"
        override val description = "Blocks writes to files named secret.*, credentials.*, etc."
        override val patterns = listOf(
            Regex("""(^|/)secret\.[a-zA-Z0-9]+"""),
            Regex("""(^|/)credentials\.[a-zA-Z0-9]+"""),
            Regex("""(^|/)password\.[a-zA-Z0-9]+"""),
            Regex("""(^|/)token\.[a-zA-Z0-9]+"""),
            Regex("""(^|/)apikey\.[a-zA-Z0-9]+"""),
        )
    }

    private object GpgFiles : SensitiveFileRule() {
        override val id = "gpg_files"
        override val name = "GPG/PGP files"
        override val description = "Blocks writes to GPG, PGP, and ASCII-armored key files."
        override val patterns = listOf(Regex("""\.(gpg|pgp|asc)$"""))
    }

    private object Keychain : SensitiveFileRule() {
        override val id = "keychain"
        override val name = "Keychain files"
        override val description = "Blocks writes to keychain and password database files."
        override val patterns = listOf(Regex("""\.(keychain|keychain-db|kdbx|kdb)$"""))
    }

    private object AuthConfigs : SensitiveFileRule() {
        override val id = "auth_configs"
        override val name = "Authentication config files"
        override val description = "Blocks writes to .netrc, .npmrc, .pypirc, .gitcredentials."
        override val patterns = listOf(
            Regex("""(^|/)\.(netrc|npmrc|pypirc|gitcredentials)$"""),
        )
    }

    private object TerraformState : SensitiveFileRule() {
        override val id = "terraform_state"
        override val name = "Terraform state"
        override val description = "Blocks writes to terraform.tfstate which may contain secrets."
        override val patterns = listOf(Regex("""terraform\.tfstate"""))
    }

    private object KubeConfig : SensitiveFileRule() {
        override val id = "kube_config"
        override val name = "Kubernetes config"
        override val description = "Blocks writes to .kube/config which contains cluster credentials."
        override val patterns = listOf(Regex("""\.kube/config"""))
    }

    private object DockerConfig : SensitiveFileRule() {
        override val id = "docker_config"
        override val name = "Docker config"
        override val description = "Blocks writes to .docker/config.json which may contain registry credentials."
        override val patterns = listOf(Regex("""\.docker/config\.json"""))
    }
}
