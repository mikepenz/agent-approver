package com.mikepenz.agentapprover.protection.modules

import com.mikepenz.agentapprover.model.HookInput
import com.mikepenz.agentapprover.model.ProtectionHit
import com.mikepenz.agentapprover.model.ProtectionMode
import com.mikepenz.agentapprover.protection.CommandParser
import com.mikepenz.agentapprover.protection.ProtectionModule
import com.mikepenz.agentapprover.protection.ProtectionRule

object InlineScriptsModule : ProtectionModule {
    override val id = "inline_scripts"
    override val name = "Inline Script Prevention"
    override val description =
        "Prevents inline script creation. Create reusable scripts in scripts/ directory with proper headers and --help instead."
    override val corrective = true
    override val defaultMode = ProtectionMode.AUTO_BLOCK
    override val applicableTools = setOf("Bash")

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

    private object HeredocScript : ProtectionRule {
        override val id = "heredoc_script"
        override val name = "Heredoc to script file"
        override val description = "Detects heredoc redirected to script files (.sh, .py, .rb, .pl, .js, .bash)."
        private val pattern = Regex("""\b(cat|tee)\s+>?\s*\S+\.(sh|py|rb|pl|js|bash)\s*<<""")

        override fun evaluate(hookInput: HookInput): ProtectionHit? {
            val cmd = CommandParser.bashCommand(hookInput) ?: return null
            if (!pattern.containsMatchIn(cmd)) return null
            return hit(
                id,
                "Instead of heredoc scripts, create a proper script file in scripts/ with set -euo pipefail, --help support, and register it in scripts/SCRIPTS.md",
            )
        }
    }

    private object ShebangWrite : ProtectionRule {
        override val id = "shebang_write"
        override val name = "Shebang via echo/printf"
        override val description = "Detects writing shebang lines via echo or printf."
        private val pattern = Regex("""(echo|printf)\s+['"]#!/""")

        override fun evaluate(hookInput: HookInput): ProtectionHit? {
            val cmd = CommandParser.bashCommand(hookInput) ?: return null
            if (!pattern.containsMatchIn(cmd)) return null
            return hit(
                id,
                "Instead of writing shebangs via echo, create a proper script file in scripts/ using the Write tool",
            )
        }
    }

    private object BashCComplex : ProtectionRule {
        override val id = "bash_c_complex"
        override val name = "Complex bash -c"
        override val description = "Detects bash -c with 4 or more chained commands."
        private val bashCPattern = Regex("""\bbash\s+-c\s""")

        override fun evaluate(hookInput: HookInput): ProtectionHit? {
            val cmd = CommandParser.bashCommand(hookInput) ?: return null
            if (!bashCPattern.containsMatchIn(cmd)) return null
            val chainCount = CommandParser.countChainedCommands(cmd)
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
        private val pattern = Regex("""\b(python[23]?|node|ruby)\s+(-c|-e)\s+['"](.{150,})""")

        override fun evaluate(hookInput: HookInput): ProtectionHit? {
            val cmd = CommandParser.bashCommand(hookInput) ?: return null
            if (!pattern.containsMatchIn(cmd)) return null
            return hit(
                id,
                "Long inline interpreter code detected. Create a proper script file in scripts/ instead.",
            )
        }
    }
}
