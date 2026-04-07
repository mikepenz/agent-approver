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
    private val FAST_COMMANDS = setOf(
        "cat", "grep", "egrep", "fgrep", "rg", "ag",
        "awk", "sed", "cut", "tr", "sort", "uniq", "wc",
        "head", "tail", "tee",
        "find", "ls", "diff", "comm",
        "echo", "printf",
        "xargs", "jq", "yq",
    )

    /**
     * Extracts the base command name (without path) from the first segment of a pipeline.
     * For example, from `grep foo bar.log | tail -5` extracts `grep`.
     */
    private fun firstPipelineCommand(fullCmd: String, pipeMatch: MatchResult): String? {
        val beforePipe = fullCmd.substring(0, pipeMatch.range.first).trimEnd()
        // Walk backwards to find the start of the current pipeline segment
        // (could be preceded by ; && || or start of string)
        val segmentStart = maxOf(
            beforePipe.lastIndexOf(';'),
            beforePipe.lastIndexOf("&&").let { if (it >= 0) it + 1 else -1 },
            beforePipe.lastIndexOf("||").let { if (it >= 0) it + 1 else -1 },
        ) + 1
        val segment = beforePipe.substring(segmentStart).trimStart()
        // The first token is the command (strip any leading env assignments like VAR=val)
        val tokens = segment.split(Regex("""\s+"""))
        val cmdToken = tokens.firstOrNull { !it.contains('=') } ?: return null
        // Strip path: /usr/bin/grep -> grep
        return cmdToken.substringAfterLast('/')
    }

    /** Returns true if every command in the pipeline (before tail/head) is a fast command. */
    private fun allPipeSegmentsFast(fullCmd: String, pipeMatch: MatchResult): Boolean {
        val beforeFinalPipe = fullCmd.substring(0, pipeMatch.range.first)
        // Split by pipes to get all segments
        val segments = beforeFinalPipe.split('|')
        return segments.all { segment ->
            val trimmed = segment.trim()
            val tokens = trimmed.split(Regex("""\s+"""))
            val cmdToken = tokens.firstOrNull { !it.contains('=') } ?: return@all false
            val cmd = cmdToken.substringAfterLast('/')
            cmd in FAST_COMMANDS
        }
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
            val match = pattern.find(cmd) ?: return null
            if (allPipeSegmentsFast(cmd, match)) return null
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
            val match = pattern.find(cmd) ?: return null
            if (allPipeSegmentsFast(cmd, match)) return null
            return hit(
                id,
                "head on piped input detected. Use a temp file: `_out=\$(mktemp) && command > \$_out 2>&1 && head -n 20 \$_out`",
            )
        }
    }
}
