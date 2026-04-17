package com.mikepenz.agentbuddy.protection.modules

import com.mikepenz.agentbuddy.model.HookInput
import com.mikepenz.agentbuddy.model.ProtectionHit
import com.mikepenz.agentbuddy.model.ProtectionMode
import com.mikepenz.agentbuddy.protection.CommandParser
import com.mikepenz.agentbuddy.protection.ProtectionModule
import com.mikepenz.agentbuddy.protection.ProtectionRule
import com.mikepenz.agentbuddy.protection.parser.Pipeline
import com.mikepenz.agentbuddy.protection.parser.allPipelines
import com.mikepenz.agentbuddy.protection.parser.effectiveCommands

object PipedTailHeadModule : ProtectionModule {
    override val id = "piped_tail_head"
    override val name = "Piped tail/head"
    override val description =
        "Detects tail or head receiving piped output from slow/expensive commands. Fast file-reading commands (grep, cat, etc.) are allowed."
    override val corrective = true
    override val defaultMode = ProtectionMode.AUTO_BLOCK
    override val applicableTools = setOf("Bash")

    override val rules: List<ProtectionRule> = listOf(
        PipedTail,
        PipedHead,
    )

    /** Commands that read/transform files quickly and don't need the temp-file workaround. */
    private val fastCommands = setOf(
        "cat", "grep", "egrep", "fgrep", "rg", "ag",
        "awk", "sed", "cut", "tr", "sort", "uniq", "wc",
        "head", "tail", "tee",
        "find", "ls", "diff", "comm",
        "echo", "printf",
        "xargs", "jq", "yq",
    )

    /** Grep-family commands. If any appears in the pipeline before head/tail the pipeline is allowed. */
    private val grepCommands = setOf("grep", "egrep", "fgrep", "rg", "ag")

    private fun hit(ruleId: String, message: String) = ProtectionHit(
        moduleId = id,
        ruleId = ruleId,
        message = message,
        mode = defaultMode,
    )

    /**
     * True iff the pipeline is allowed: every upstream command is in the fast-list, OR the
     * command immediately before the target is a grep-family command (which limits output
     * regardless of upstream cost). Opaque command names (variables, substitutions) are treated
     * as non-fast — fail closed. Commands are unwrapped through `sudo`/`doas`.
     */
    private fun isAllowedPipeline(p: Pipeline, target: String): Boolean {
        val commands = p.effectiveCommands()
        val tailIdx = commands.indexOfLast { it.commandName == target }
        if (tailIdx <= 0) return true
        val upstream = commands.subList(0, tailIdx)
        if (upstream.lastOrNull()?.commandName in grepCommands) return true
        return upstream.all { it.commandName in fastCommands }
    }

    private fun offendingPipeline(hookInput: HookInput, target: String): Boolean {
        val parsed = CommandParser.parsedBash(hookInput) ?: return false
        return parsed.allPipelines().any { p ->
            val cmds = p.effectiveCommands()
            val tailIdx = cmds.indexOfLast { it.commandName == target }
            if (tailIdx <= 0) false else !isAllowedPipeline(p, target)
        }
    }

    private object PipedTail : ProtectionRule {
        override val id = "piped_tail"
        override val name = "tail on piped input"
        override val description = "Detects tail receiving output from slow/expensive commands via a pipe."
        override val correctiveHint =
            "Instead of piping to tail, use a temp file: `_out=\$(mktemp) && command > \$_out 2>&1 && tail -n 20 \$_out`"

        override fun evaluate(hookInput: HookInput): ProtectionHit? {
            if (!offendingPipeline(hookInput, "tail")) return null
            return hit(
                id,
                "tail on piped input detected. Use a temp file: `_out=\$(mktemp) && command > \$_out 2>&1 && tail -n 20 \$_out`",
            )
        }
    }

    private object PipedHead : ProtectionRule {
        override val id = "piped_head"
        override val name = "head on piped input"
        override val description = "Detects head receiving output from slow/expensive commands via a pipe."
        override val correctiveHint =
            "Instead of piping to head, use a temp file: `_out=\$(mktemp) && command > \$_out 2>&1 && head -n 20 \$_out`"

        override fun evaluate(hookInput: HookInput): ProtectionHit? {
            if (!offendingPipeline(hookInput, "head")) return null
            return hit(
                id,
                "head on piped input detected. Use a temp file: `_out=\$(mktemp) && command > \$_out 2>&1 && head -n 20 \$_out`",
            )
        }
    }
}
