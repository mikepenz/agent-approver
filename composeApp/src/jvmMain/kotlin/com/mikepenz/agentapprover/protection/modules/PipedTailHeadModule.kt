package com.mikepenz.agentapprover.protection.modules

import com.mikepenz.agentapprover.model.HookInput
import com.mikepenz.agentapprover.model.ProtectionHit
import com.mikepenz.agentapprover.model.ProtectionMode
import com.mikepenz.agentapprover.protection.CommandParser
import com.mikepenz.agentapprover.protection.ProtectionModule
import com.mikepenz.agentapprover.protection.ProtectionRule

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

    /** Splits on single pipe `|` but not logical OR `||` or pipe-stderr `|&`. */
    private val singlePipePattern = Regex("""(?<!\|)\|(?!\||&)""")

    /** Matches command chain separators: `;`, `&&`, `||`. */
    private val chainSeparator = Regex(""";|&&|\|\|""")

    /** Grep-family commands. If any appears in the pipeline before head/tail, the pipeline is allowed. */
    private val grepCommands = setOf("grep", "egrep", "fgrep", "rg", "ag")

    /** Extracts the command name from a pipeline segment, skipping env assignments. */
    private fun segmentCommand(segment: String): String? {
        val trimmed = segment.trim()
        if (trimmed.isEmpty()) return null
        val tokens = trimmed.split(Regex("""\s+"""))
        val cmdToken = tokens.firstOrNull { !it.contains('=') } ?: return null
        return cmdToken.substringAfterLast('/')
    }

    /**
     * Returns true if every command before the final tail/head is fast, OR if the segment
     * immediately before the tail/head is a grep-family command (grep limits its own output,
     * so tail/head directly after grep is fine regardless of upstream cost).
     */
    private fun allPipeSegmentsFast(fullCmd: String, pipeMatch: MatchResult): Boolean {
        val beforeFinalPipe = fullCmd.substring(0, pipeMatch.range.first)
        // Isolate the command chain segment containing this pipe by finding the last chain separator
        val lastSep = chainSeparator.findAll(beforeFinalPipe).lastOrNull()
        val pipelineStr = if (lastSep != null) {
            beforeFinalPipe.substring(lastSep.range.last + 1)
        } else {
            beforeFinalPipe
        }
        val segments = singlePipePattern.split(pipelineStr)
        val commands = segments.map { segmentCommand(it) }
        if (commands.any { it == null }) return false
        if (commands.lastOrNull() in grepCommands) return true
        return commands.all { it in fastCommands }
    }

    private fun hit(ruleId: String, message: String) = ProtectionHit(
        moduleId = id,
        ruleId = ruleId,
        message = message,
        mode = defaultMode,
    )

    private object PipedTail : ProtectionRule {
        override val id = "piped_tail"
        override val name = "tail on piped input"
        override val description = "Detects tail receiving output from slow/expensive commands via a pipe."
        override val correctiveHint =
            "Instead of piping to tail, use a temp file: `_out=\$(mktemp) && command > \$_out 2>&1 && tail -n 20 \$_out`"
        private val pattern = Regex("""\|\s*tail\b""")

        override fun evaluate(hookInput: HookInput): ProtectionHit? {
            val cmd = CommandParser.bashCommand(hookInput) ?: return null
            val hasNonFastPipeline = pattern.findAll(cmd).any { match ->
                !allPipeSegmentsFast(cmd, match)
            }
            if (!hasNonFastPipeline) return null
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
        private val pattern = Regex("""\|\s*head\b""")

        override fun evaluate(hookInput: HookInput): ProtectionHit? {
            val cmd = CommandParser.bashCommand(hookInput) ?: return null
            val hasNonFastPipeline = pattern.findAll(cmd).any { match ->
                !allPipeSegmentsFast(cmd, match)
            }
            if (!hasNonFastPipeline) return null
            return hit(
                id,
                "head on piped input detected. Use a temp file: `_out=\$(mktemp) && command > \$_out 2>&1 && head -n 20 \$_out`",
            )
        }
    }
}
