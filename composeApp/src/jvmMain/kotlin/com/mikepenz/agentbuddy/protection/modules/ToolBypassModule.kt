package com.mikepenz.agentbuddy.protection.modules

import com.mikepenz.agentbuddy.model.HookInput
import com.mikepenz.agentbuddy.model.ProtectionHit
import com.mikepenz.agentbuddy.model.ProtectionMode
import com.mikepenz.agentbuddy.protection.CommandParser
import com.mikepenz.agentbuddy.protection.ProtectionModule
import com.mikepenz.agentbuddy.protection.ProtectionRule
import com.mikepenz.agentbuddy.protection.parser.OpKind
import com.mikepenz.agentbuddy.protection.parser.SimpleCommand
import com.mikepenz.agentbuddy.protection.parser.allSimpleCommands

object ToolBypassModule : ProtectionModule {
    override val id = "tool_bypass"
    override val name = "Tool-Switching Bypass Detection"
    override val description = "Detects when Bash is used to write files, bypassing Write/Edit tool controls."
    override val corrective = false
    override val defaultMode = ProtectionMode.ASK
    override val applicableTools = setOf("Bash")

    override val rules: List<ProtectionRule> = listOf(
        SedInline,
        PerlInline,
        PythonFileWrite,
        EchoRedirect,
        TeeWrite,
        BashHeredoc,
    )

    private fun hit(ruleId: String, message: String) = ProtectionHit(
        moduleId = id,
        ruleId = ruleId,
        message = message,
        mode = defaultMode,
    )

    private fun commands(hookInput: HookInput): Sequence<SimpleCommand> =
        CommandParser.parsedBash(hookInput)?.allSimpleCommands() ?: emptySequence()

    private object SedInline : ProtectionRule {
        override val id = "sed_inline"
        override val name = "sed -i in-place editing"
        override val description = "Detects sed -i in-place file editing. Use the Edit/Write tool instead."

        override fun evaluate(hookInput: HookInput): ProtectionHit? {
            val match = commands(hookInput).any { sc ->
                sc.commandName == "sed" && (sc.hasFlag(short = 'i') || sc.hasLongFlag("--in-place"))
            }
            return if (match) hit(id, "sed -i in-place editing detected. Use the Edit/Write tool instead.") else null
        }
    }

    private object PerlInline : ProtectionRule {
        override val id = "perl_inline"
        override val name = "perl -i in-place editing"
        override val description = "Detects perl -i or perl -pi -e in-place file editing. Use the Edit/Write tool instead."

        override fun evaluate(hookInput: HookInput): ProtectionHit? {
            val match = commands(hookInput).any { sc ->
                sc.commandName == "perl" && sc.hasFlag(short = 'i')
            }
            return if (match) hit(id, "perl in-place editing detected. Use the Edit/Write tool instead.") else null
        }
    }

    private object PythonFileWrite : ProtectionRule {
        override val id = "python_file_write"
        override val name = "python -c file write"
        override val description = "Detects python -c with open() and write(). Use the Edit/Write tool instead."

        override fun evaluate(hookInput: HookInput): ProtectionHit? {
            val match = commands(hookInput).any { sc ->
                val name = sc.commandName ?: return@any false
                if (name != "python" && name != "python2" && name != "python3") return@any false
                val idx = sc.args.indexOfFirst { it.literal == "-c" }
                if (idx < 0 || idx + 1 >= sc.args.size) return@any false
                val body = sc.args[idx + 1].literal ?: return@any false
                body.contains("open(") && body.contains("write")
            }
            return if (match) hit(id, "python -c file write detected. Use the Edit/Write tool instead.") else null
        }
    }

    private object EchoRedirect : ProtectionRule {
        override val id = "echo_redirect"
        override val name = "echo/printf redirect to file"
        override val description = "Detects echo or printf redirected to a file. Use the Edit/Write tool instead."

        override fun evaluate(hookInput: HookInput): ProtectionHit? {
            val match = commands(hookInput).any { sc ->
                val name = sc.commandName ?: return@any false
                if (name != "echo" && name != "printf") return@any false
                sc.redirects.any { r ->
                    when (r.op) {
                        OpKind.REDIR_OUT, OpKind.REDIR_APPEND, OpKind.REDIR_FORCE_OUT,
                        OpKind.REDIR_ALL_OUT, OpKind.REDIR_ALL_APPEND -> {
                            val target = r.target.literal
                            target != null && target != "/dev/null"
                        }
                        else -> false
                    }
                }
            }
            return if (match) hit(id, "echo/printf redirect to file detected. Use the Edit/Write tool instead.") else null
        }
    }

    private object TeeWrite : ProtectionRule {
        override val id = "tee_write"
        override val name = "tee to file"
        override val description = "Detects tee writing to a file. Use the Edit/Write tool instead."

        override fun evaluate(hookInput: HookInput): ProtectionHit? {
            val match = commands(hookInput).any { sc ->
                if (sc.commandName != "tee") return@any false
                // Any positional (non-flag) arg that isn't /dev/null.
                sc.args.any { a ->
                    val lit = a.literal ?: return@any false
                    !lit.startsWith("-") && lit != "/dev/null"
                }
            }
            return if (match) hit(id, "tee to file detected. Use the Edit/Write tool instead.") else null
        }
    }

    private object BashHeredoc : ProtectionRule {
        override val id = "bash_heredoc"
        override val name = "heredoc file write"
        override val description = "Detects cat or tee with heredoc writing to a file. Use the Edit/Write tool instead."

        override fun evaluate(hookInput: HookInput): ProtectionHit? {
            val match = commands(hookInput).any { sc ->
                if (sc.commandName != "cat" && sc.commandName != "tee") return@any false
                val hasHeredoc = sc.redirects.any { it.op == OpKind.REDIR_HEREDOC }
                if (!hasHeredoc) return@any false
                // For cat: it must also have a file redirect target or be piped to a file.
                // For tee: the positional arg is the target.
                if (sc.commandName == "tee") {
                    sc.args.any { a -> a.literal?.startsWith("-") != true }
                } else {
                    sc.redirects.any { r ->
                        (r.op == OpKind.REDIR_OUT || r.op == OpKind.REDIR_APPEND) && r.target.literal != null
                    }
                }
            }
            return if (match) hit(id, "heredoc file write detected. Use the Edit/Write tool instead.") else null
        }
    }
}
