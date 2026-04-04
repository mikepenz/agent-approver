package com.mikepenz.agentapprover.protection.modules

import com.mikepenz.agentapprover.model.HookInput
import com.mikepenz.agentapprover.model.ProtectionHit
import com.mikepenz.agentapprover.model.ProtectionMode
import com.mikepenz.agentapprover.protection.CommandParser
import com.mikepenz.agentapprover.protection.ProtectionModule
import com.mikepenz.agentapprover.protection.ProtectionRule

object SupplyChainRceModule : ProtectionModule {
    override val id = "supply_chain_rce"
    override val name = "Supply-Chain / RCE Prevention"
    override val description = "Blocks commands that fetch and execute remote code, escalate privileges via pipes, or write to system paths."
    override val corrective = false
    override val defaultMode = ProtectionMode.AUTO_BLOCK
    override val applicableTools = setOf("Bash")

    override val rules: List<ProtectionRule> = listOf(
        CurlPipeExec,
        Base64Exec,
        EvalPipe,
        FetchSubshell,
        PrivilegePipe,
        SystemPathWrite,
    )

    private fun hit(ruleId: String, message: String) = ProtectionHit(
        moduleId = id,
        ruleId = ruleId,
        message = message,
        mode = defaultMode,
    )

    private object CurlPipeExec : ProtectionRule {
        override val id = "curl_pipe_exec"
        override val name = "Curl/wget pipe to interpreter"
        override val description = "Detects curl|bash, wget|sh, curl|python, and similar remote code execution patterns."
        private val pattern = Regex(
            """\b(curl|wget)\b.*\|\s*(bash|sh|zsh|python[23]?|node|ruby|perl)\b"""
        )

        override fun evaluate(hookInput: HookInput): ProtectionHit? {
            val cmd = CommandParser.bashCommand(hookInput) ?: return null
            if (!pattern.containsMatchIn(cmd)) return null
            return hit(id, "Remote code execution via pipe: $cmd")
        }
    }

    private object Base64Exec : ProtectionRule {
        override val id = "base64_exec"
        override val name = "Base64/openssl decode pipe to interpreter"
        override val description = "Detects base64 -d | bash, openssl enc -d | bash, and similar obfuscated execution patterns."
        private val pattern = Regex(
            """\b(base64\s+-d|openssl\s+enc\s+-d)\b.*\|\s*(bash|sh|python[23]?|node|ruby|perl)\b"""
        )

        override fun evaluate(hookInput: HookInput): ProtectionHit? {
            val cmd = CommandParser.bashCommand(hookInput) ?: return null
            if (!pattern.containsMatchIn(cmd)) return null
            return hit(id, "Obfuscated code execution: $cmd")
        }
    }

    private object EvalPipe : ProtectionRule {
        override val id = "eval_pipe"
        override val name = "Pipe to eval"
        override val description = "Detects piping output to eval which can execute arbitrary code."
        private val pattern = Regex("""\|\s*eval\b""")

        override fun evaluate(hookInput: HookInput): ProtectionHit? {
            val cmd = CommandParser.bashCommand(hookInput) ?: return null
            if (!pattern.containsMatchIn(cmd)) return null
            return hit(id, "Pipe to eval: $cmd")
        }
    }

    private object FetchSubshell : ProtectionRule {
        override val id = "fetch_subshell"
        override val name = "Fetch in subshell"
        override val description = "Detects \$(curl ...) or backtick wget patterns that execute fetched content."
        private val dollarParen = Regex("""\$\(\s*(curl|wget)\b""")
        private val backtick = Regex("""`\s*(curl|wget)\b""")

        override fun evaluate(hookInput: HookInput): ProtectionHit? {
            val cmd = CommandParser.bashCommand(hookInput) ?: return null
            if (dollarParen.containsMatchIn(cmd) || backtick.containsMatchIn(cmd)) {
                return hit(id, "Fetch in subshell: $cmd")
            }
            return null
        }
    }

    private object PrivilegePipe : ProtectionRule {
        override val id = "privilege_pipe"
        override val name = "Pipe to sudo/su"
        override val description = "Detects piping to sudo or su which can escalate privileges with untrusted input."
        private val pattern = Regex("""\|\s*(sudo|su)\b""")

        override fun evaluate(hookInput: HookInput): ProtectionHit? {
            val cmd = CommandParser.bashCommand(hookInput) ?: return null
            if (!pattern.containsMatchIn(cmd)) return null
            return hit(id, "Privilege escalation via pipe: $cmd")
        }
    }

    private object SystemPathWrite : ProtectionRule {
        override val id = "system_path_write"
        override val name = "Write to system path"
        override val description = "Detects redirects or tee to system directories like /etc, /usr, /bin, /sbin, /lib, /boot."
        private val redirectPattern = Regex(""">\s*/(etc|usr|bin|sbin|lib|boot)/""")
        private val teePattern = Regex("""\btee\s+/(etc|usr|bin|sbin|lib|boot)/""")

        override fun evaluate(hookInput: HookInput): ProtectionHit? {
            val cmd = CommandParser.bashCommand(hookInput) ?: return null
            if (redirectPattern.containsMatchIn(cmd) || teePattern.containsMatchIn(cmd)) {
                return hit(id, "Write to system path: $cmd")
            }
            return null
        }
    }
}
