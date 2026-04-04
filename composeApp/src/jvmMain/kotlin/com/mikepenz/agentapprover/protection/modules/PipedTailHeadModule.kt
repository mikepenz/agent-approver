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
        "Detects tail or head receiving piped input. Write output to a temp file first, then use tail/head on that file."
    override val corrective = true
    override val defaultMode = ProtectionMode.AUTO_BLOCK
    override val applicableTools = setOf("Bash")

    override val rules: List<ProtectionRule> = listOf(
        PipedTail,
        PipedHead,
    )

    private fun hit(ruleId: String, message: String) = ProtectionHit(
        moduleId = id,
        ruleId = ruleId,
        message = message,
        mode = defaultMode,
    )

    private object PipedTail : ProtectionRule {
        override val id = "piped_tail"
        override val name = "tail on piped input"
        override val description = "Detects tail receiving output via a pipe instead of operating on a file."
        override val correctiveHint =
            "Instead of piping to tail, redirect output to a temp file first: `command > /tmp/out.txt && tail -n 20 /tmp/out.txt`"
        private val pattern = Regex("""\|\s*tail\b""")

        override fun evaluate(hookInput: HookInput): ProtectionHit? {
            val cmd = CommandParser.bashCommand(hookInput) ?: return null
            if (!pattern.containsMatchIn(cmd)) return null
            return hit(
                id,
                "tail on piped input detected. Redirect output to a temp file first: `command > /tmp/out.txt && tail -n 20 /tmp/out.txt`",
            )
        }
    }

    private object PipedHead : ProtectionRule {
        override val id = "piped_head"
        override val name = "head on piped input"
        override val description = "Detects head receiving output via a pipe instead of operating on a file."
        override val correctiveHint =
            "Instead of piping to head, redirect output to a temp file first: `command > /tmp/out.txt && head -n 20 /tmp/out.txt`"
        private val pattern = Regex("""\|\s*head\b""")

        override fun evaluate(hookInput: HookInput): ProtectionHit? {
            val cmd = CommandParser.bashCommand(hookInput) ?: return null
            if (!pattern.containsMatchIn(cmd)) return null
            return hit(
                id,
                "head on piped input detected. Redirect output to a temp file first: `command > /tmp/out.txt && head -n 20 /tmp/out.txt`",
            )
        }
    }
}
