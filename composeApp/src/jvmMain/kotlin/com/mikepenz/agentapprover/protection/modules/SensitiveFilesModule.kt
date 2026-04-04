package com.mikepenz.agentapprover.protection.modules

import com.mikepenz.agentapprover.model.HookInput
import com.mikepenz.agentapprover.model.ProtectionHit
import com.mikepenz.agentapprover.model.ProtectionMode
import com.mikepenz.agentapprover.protection.CommandParser
import com.mikepenz.agentapprover.protection.ProtectionModule
import com.mikepenz.agentapprover.protection.ProtectionRule

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
     * Base class for rules that check file paths against regex patterns.
     * Handles Write/Edit (via file_path) and Bash (via extractPaths + inline patterns).
     */
    private abstract class SensitiveFileRule : ProtectionRule {
        abstract val patterns: List<Regex>
        open val exclusions: List<Regex> get() = emptyList()
        open val inlinePatterns: List<Regex> get() = emptyList()

        private fun matches(path: String): Boolean {
            if (exclusions.any { it.containsMatchIn(path) }) return false
            return patterns.any { it.containsMatchIn(path) }
        }

        override fun evaluate(hookInput: HookInput): ProtectionHit? {
            val toolName = hookInput.toolName
            if (toolName !in applicableTools) return null

            when (toolName) {
                "Write", "Edit" -> {
                    val path = CommandParser.filePath(hookInput) ?: return null
                    if (matches(path)) {
                        return hit(id, "Sensitive file access: $path")
                    }
                }
                "Bash" -> {
                    val cmd = CommandParser.bashCommand(hookInput) ?: return null
                    val paths = CommandParser.extractPaths(cmd)
                    for (path in paths) {
                        if (matches(path)) {
                            return hit(id, "Sensitive file in command: $cmd")
                        }
                    }
                    // Check inline patterns (bare filenames like .env, .npmrc)
                    for (pattern in inlinePatterns) {
                        if (pattern.containsMatchIn(cmd)) {
                            // Verify exclusions don't apply to the matched text
                            val match = pattern.find(cmd)?.value ?: continue
                            if (exclusions.any { it.containsMatchIn(match) }) continue
                            return hit(id, "Sensitive file in command: $cmd")
                        }
                    }
                    // Also check bare patterns against the full command
                    for (pattern in patterns) {
                        if (pattern.containsMatchIn(cmd)) {
                            val match = pattern.find(cmd)?.value ?: continue
                            if (exclusions.any { it.containsMatchIn(match) }) continue
                            return hit(id, "Sensitive file in command: $cmd")
                        }
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
        override val patterns = listOf(Regex("""\.env(\.[a-zA-Z0-9_-]+)?$"""))
        override val exclusions = listOf(Regex("""\.env\.(example|sample|template)$"""))
        override val inlinePatterns = listOf(Regex("""\b\.env(\.[a-zA-Z0-9_-]+)?\b"""))
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
        override val inlinePatterns = listOf(
            Regex("""\.(netrc|npmrc|pypirc|gitcredentials)\b"""),
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
