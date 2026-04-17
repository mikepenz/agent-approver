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
import com.mikepenz.agentbuddy.protection.parser.chainedCommandCount
import com.mikepenz.agentbuddy.protection.parser.parseShellCommand

object InlineScriptsModule : ProtectionModule {
    override val id = "inline_scripts"
    override val name = "Inline Script Prevention"
    override val description =
        "Prevents inline script creation. Create reusable scripts in scripts/ directory with proper headers and --help instead."
    override val corrective = true
    override val defaultMode = ProtectionMode.AUTO_BLOCK
    override val applicableTools = setOf("Bash")

    private val scriptExtensions = setOf("sh", "py", "rb", "pl", "js", "bash")
    private val shellInterpreters = setOf("bash", "sh", "zsh", "dash")

    override val rules: List<ProtectionRule> = listOf(
        HeredocScript,
        ShebangWrite,
        BashCComplex,
        InterpreterLong,
    )

    private fun hit(ruleId: String, message: String) = ProtectionHit(
        moduleId = id,
        ruleId = ruleId,
        message = message,
        mode = defaultMode,
    )

    private fun commands(hookInput: HookInput): Sequence<SimpleCommand> =
        CommandParser.parsedBash(hookInput)?.allSimpleCommands() ?: emptySequence()

    private fun isScriptFilename(lit: String?): Boolean {
        if (lit == null) return false
        val ext = lit.substringAfterLast('.', "").lowercase()
        return ext in scriptExtensions
    }

    private object HeredocScript : ProtectionRule {
        override val id = "heredoc_script"
        override val name = "Heredoc to script file"
        override val description = "Detects heredoc redirected to script files (.sh, .py, .rb, .pl, .js, .bash)."
        override val correctiveHint =
            "Instead of heredoc scripts, create a proper script file in scripts/ with set -euo pipefail, --help support, and register it in scripts/SCRIPTS.md"

        override fun evaluate(hookInput: HookInput): ProtectionHit? {
            val match = commands(hookInput).firstOrNull { sc ->
                if (sc.commandName != "cat" && sc.commandName != "tee") return@firstOrNull false
                val hasHeredoc = sc.redirects.any { it.op == OpKind.REDIR_HEREDOC }
                if (!hasHeredoc) return@firstOrNull false
                // Script filename can be either a redirect target or a positional arg.
                val redirTarget = sc.redirects.firstOrNull { it.op == OpKind.REDIR_OUT || it.op == OpKind.REDIR_APPEND }
                if (isScriptFilename(redirTarget?.target?.literal)) return@firstOrNull true
                sc.args.any { isScriptFilename(it.literal) }
            } ?: return null
            return hit(id, correctiveHint)
        }
    }

    private object ShebangWrite : ProtectionRule {
        override val id = "shebang_write"
        override val name = "Shebang via echo/printf"
        override val description = "Detects writing shebang lines via echo or printf."
        override val correctiveHint =
            "Instead of writing shebangs via echo, create a proper script file in scripts/ using the Write tool"

        override fun evaluate(hookInput: HookInput): ProtectionHit? {
            val match = commands(hookInput).firstOrNull { sc ->
                val name = sc.commandName ?: return@firstOrNull false
                if (name != "echo" && name != "printf") return@firstOrNull false
                sc.args.any { it.literal?.startsWith("#!/") == true }
            } ?: return null
            return hit(id, correctiveHint)
        }
    }

    private object BashCComplex : ProtectionRule {
        override val id = "bash_c_complex"
        override val name = "Complex bash -c"
        override val description = "Detects bash -c with 4 or more chained commands."
        override val correctiveHint =
            "Complex bash -c with multiple chained commands. Create a proper script in scripts/ instead."

        override fun evaluate(hookInput: HookInput): ProtectionHit? {
            val bashC = commands(hookInput).firstOrNull { sc ->
                sc.commandName in shellInterpreters && sc.hasLiteralArg("-c")
            } ?: return null
            val idx = bashC.args.indexOfFirst { it.literal == "-c" }
            if (idx < 0 || idx + 1 >= bashC.args.size) return null
            val body = bashC.args[idx + 1].literal ?: return null
            val chainCount = parseShellCommand(body).chainedCommandCount()
            if (chainCount < 4) return null
            return hit(
                id,
                "Complex bash -c with $chainCount chained commands. Create a proper script in scripts/ instead.",
            )
        }
    }

    private object InterpreterLong : ProtectionRule {
        override val id = "interpreter_long"
        override val name = "Long inline interpreter code"
        override val description = "Detects python -c, node -e, or ruby -e with more than 150 characters of inline code."
        override val correctiveHint =
            "Long inline interpreter code detected. Create a proper script file in scripts/ instead."

        private val interpreters = mapOf(
            "python" to "-c",
            "python2" to "-c",
            "python3" to "-c",
            "node" to "-e",
            "ruby" to "-e",
        )

        override fun evaluate(hookInput: HookInput): ProtectionHit? {
            val match = commands(hookInput).firstOrNull { sc ->
                val name = sc.commandName ?: return@firstOrNull false
                val flag = interpreters[name] ?: return@firstOrNull false
                val idx = sc.args.indexOfFirst { it.literal == flag }
                if (idx < 0 || idx + 1 >= sc.args.size) return@firstOrNull false
                val body = sc.args[idx + 1].literal ?: return@firstOrNull false
                body.length >= 150
            } ?: return null
            return hit(id, correctiveHint)
        }
    }
}
