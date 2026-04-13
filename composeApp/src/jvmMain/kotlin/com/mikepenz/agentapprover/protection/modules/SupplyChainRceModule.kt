package com.mikepenz.agentapprover.protection.modules

import com.mikepenz.agentapprover.model.HookInput
import com.mikepenz.agentapprover.model.ProtectionHit
import com.mikepenz.agentapprover.model.ProtectionMode
import com.mikepenz.agentapprover.protection.CommandParser
import com.mikepenz.agentapprover.protection.ProtectionModule
import com.mikepenz.agentapprover.protection.ProtectionRule
import com.mikepenz.agentapprover.protection.parser.OpKind
import com.mikepenz.agentapprover.protection.parser.ParsedCommand
import com.mikepenz.agentapprover.protection.parser.allPipelines
import com.mikepenz.agentapprover.protection.parser.allSimpleCommands
import com.mikepenz.agentapprover.protection.parser.effectiveCommands
import com.mikepenz.agentapprover.protection.parser.parseShellCommand

object SupplyChainRceModule : ProtectionModule {
    override val id = "supply_chain_rce"
    override val name = "Supply-Chain / RCE Prevention"
    override val description = "Blocks commands that fetch and execute remote code, escalate privileges via pipes, or write to system paths."
    override val corrective = false
    override val defaultMode = ProtectionMode.AUTO_BLOCK
    override val applicableTools = setOf("Bash")

    private val fetchCommands = setOf("curl", "wget")
    private val interpreters = setOf("bash", "sh", "zsh", "dash", "python", "python2", "python3", "node", "ruby", "perl")
    private val systemRoots = listOf("/etc/", "/usr/", "/bin/", "/sbin/", "/lib/", "/boot/")

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

    private fun parsedOrNull(hookInput: HookInput): ParsedCommand? = CommandParser.parsedBash(hookInput)

    private object CurlPipeExec : ProtectionRule {
        override val id = "curl_pipe_exec"
        override val name = "Curl/wget pipe to interpreter"
        override val description = "Detects curl|bash, wget|sh, curl|python, and similar remote code execution patterns."

        override fun evaluate(hookInput: HookInput): ProtectionHit? {
            val cmd = CommandParser.bashCommand(hookInput) ?: return null
            val parsed = parsedOrNull(hookInput) ?: return null
            val match = parsed.allPipelines().any { p ->
                // Unwrap sudo/doas so `curl ... | sudo bash` exposes `bash` as the interpreter.
                val cmds = p.effectiveCommands()
                val fetchIdx = cmds.indexOfFirst { it.commandName in fetchCommands }
                if (fetchIdx < 0) return@any false
                cmds.subList(fetchIdx + 1, cmds.size).any { it.commandName in interpreters }
            }
            return if (match) hit(id, "Remote code execution via pipe: $cmd") else null
        }
    }

    private object Base64Exec : ProtectionRule {
        override val id = "base64_exec"
        override val name = "Base64/openssl decode pipe to interpreter"
        override val description = "Detects base64 -d | bash, openssl enc -d | bash, and similar obfuscated execution patterns."

        override fun evaluate(hookInput: HookInput): ProtectionHit? {
            val cmd = CommandParser.bashCommand(hookInput) ?: return null
            val parsed = parsedOrNull(hookInput) ?: return null
            val match = parsed.allPipelines().any { p ->
                val cmds = p.effectiveCommands()
                val decodeIdx = cmds.indexOfFirst { sc ->
                    when (sc.commandName) {
                        "base64" -> sc.hasFlag(short = 'd') || sc.hasLongFlag("--decode")
                        "openssl" -> sc.hasLiteralArg("enc") && (sc.hasFlag(short = 'd') || sc.hasLongFlag("--decrypt"))
                        else -> false
                    }
                }
                if (decodeIdx < 0) return@any false
                cmds.subList(decodeIdx + 1, cmds.size).any { it.commandName in interpreters }
            }
            return if (match) hit(id, "Obfuscated code execution: $cmd") else null
        }
    }

    private object EvalPipe : ProtectionRule {
        override val id = "eval_pipe"
        override val name = "Pipe to eval"
        override val description = "Detects piping output to eval which can execute arbitrary code."

        override fun evaluate(hookInput: HookInput): ProtectionHit? {
            val cmd = CommandParser.bashCommand(hookInput) ?: return null
            val parsed = parsedOrNull(hookInput) ?: return null
            val match = parsed.allPipelines().any { p ->
                val cmds = p.effectiveCommands()
                if (cmds.size < 2) return@any false
                cmds.drop(1).any { it.commandName == "eval" }
            }
            return if (match) hit(id, "Pipe to eval: $cmd") else null
        }
    }

    private object FetchSubshell : ProtectionRule {
        override val id = "fetch_subshell"
        override val name = "Fetch in subshell"
        override val description = "Detects \$(curl ...) or backtick wget patterns that execute fetched content."

        override fun evaluate(hookInput: HookInput): ProtectionHit? {
            val cmd = CommandParser.bashCommand(hookInput) ?: return null
            val parsed = parsedOrNull(hookInput) ?: return null
            // For every word with substitutions, parse each substitution body and check for a
            // curl/wget simple command within. This catches $(curl ...) and backticks regardless
            // of nesting depth.
            val match = parsed.allSimpleCommands().any { sc ->
                val words = listOfNotNull(sc.name) + sc.args + sc.redirects.map { it.target } +
                    sc.assignments.map { it.value }
                words.any { w ->
                    w.substitutions.any { body ->
                        parseShellCommand(body).allSimpleCommands().any { inner ->
                            inner.commandName in fetchCommands
                        }
                    }
                }
            }
            return if (match) hit(id, "Fetch in subshell: $cmd") else null
        }
    }

    private object PrivilegePipe : ProtectionRule {
        override val id = "privilege_pipe"
        override val name = "Pipe to sudo/su"
        override val description = "Detects piping to sudo or su which can escalate privileges with untrusted input."

        override fun evaluate(hookInput: HookInput): ProtectionHit? {
            val cmd = CommandParser.bashCommand(hookInput) ?: return null
            val parsed = parsedOrNull(hookInput) ?: return null
            val match = parsed.allPipelines().any { p ->
                val cmds = p.commands
                if (cmds.size < 2) return@any false
                cmds.drop(1).any { it.commandName == "sudo" || it.commandName == "su" || it.commandName == "doas" }
            }
            return if (match) hit(id, "Privilege escalation via pipe: $cmd") else null
        }
    }

    private object SystemPathWrite : ProtectionRule {
        override val id = "system_path_write"
        override val name = "Write to system path"
        override val description = "Detects redirects or tee to system directories like /etc, /usr, /bin, /sbin, /lib, /boot."

        override fun evaluate(hookInput: HookInput): ProtectionHit? {
            val cmd = CommandParser.bashCommand(hookInput) ?: return null
            val parsed = parsedOrNull(hookInput) ?: return null
            val match = parsed.allSimpleCommands().any { sc ->
                // 1. Any output redirect (>, >>, &>, &>>, >|) whose target is under a system root.
                val writeOps = setOf(
                    OpKind.REDIR_OUT, OpKind.REDIR_APPEND, OpKind.REDIR_FORCE_OUT,
                    OpKind.REDIR_ALL_OUT, OpKind.REDIR_ALL_APPEND,
                )
                val hasBadRedirect = sc.redirects.any { r ->
                    r.op in writeOps && r.target.literal?.let { isSystemPath(it) } == true
                }
                if (hasBadRedirect) return@any true
                // 2. tee command with a system-path argument (including append mode -a).
                if (sc.commandName == "tee") {
                    sc.args.any { a ->
                        val lit = a.literal ?: return@any false
                        isSystemPath(lit)
                    }
                } else false
            }
            return if (match) hit(id, "Write to system path: $cmd") else null
        }

        private fun isSystemPath(p: String): Boolean =
            systemRoots.any { root -> p.startsWith(root) }
    }
}
