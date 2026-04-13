package com.mikepenz.agentapprover.protection.modules

import com.mikepenz.agentapprover.model.HookInput
import com.mikepenz.agentapprover.model.ProtectionHit
import com.mikepenz.agentapprover.model.ProtectionMode
import com.mikepenz.agentapprover.protection.CommandParser
import com.mikepenz.agentapprover.protection.ProtectionModule
import com.mikepenz.agentapprover.protection.ProtectionRule
import com.mikepenz.agentapprover.protection.parser.OpKind
import com.mikepenz.agentapprover.protection.parser.SimpleCommand
import com.mikepenz.agentapprover.protection.parser.allSimpleCommands

object PipeAbuseModule : ProtectionModule {
    override val id = "pipe_abuse"
    override val name = "Pipe Abuse"
    override val description = "Detects dangerous pipe patterns such as bulk permission changes and write-then-execute chains."
    override val corrective = false
    override val defaultMode = ProtectionMode.ASK_AUTO_BLOCK
    override val applicableTools = setOf("Bash")

    private val scriptExtensions = setOf("sh", "py", "rb", "pl", "js", "bash")

    override val rules: List<ProtectionRule> = listOf(
        BulkPermissionChange,
        WriteThenExecute,
    )

    private fun hit(ruleId: String, message: String) = ProtectionHit(
        moduleId = id,
        ruleId = ruleId,
        message = message,
        mode = defaultMode,
    )

    private fun commands(hookInput: HookInput): Sequence<SimpleCommand> =
        CommandParser.parsedBash(hookInput)?.allSimpleCommands() ?: emptySequence()

    private object BulkPermissionChange : ProtectionRule {
        override val id = "bulk_permission_change"
        override val name = "Bulk permission change via xargs"
        override val description = "Detects xargs chmod or xargs chown for bulk permission changes."

        override fun evaluate(hookInput: HookInput): ProtectionHit? {
            val cmd = CommandParser.bashCommand(hookInput) ?: return null
            val match = commands(hookInput).firstOrNull { sc ->
                if (sc.commandName != "xargs") return@firstOrNull false
                val invoked = sc.args.firstOrNull { a ->
                    val lit = a.literal ?: return@firstOrNull false
                    !lit.startsWith("-")
                }?.literal?.substringAfterLast('/')
                invoked == "chmod" || invoked == "chown"
            }
            return if (match != null) hit(id, "Bulk permission change via xargs: $cmd") else null
        }
    }

    private object WriteThenExecute : ProtectionRule {
        override val id = "write_then_execute"
        override val name = "Write then execute script"
        override val description = "Detects creating a script file and executing it in the same command chain."

        override fun evaluate(hookInput: HookInput): ProtectionHit? {
            val cmd = CommandParser.bashCommand(hookInput) ?: return null
            val parsed = CommandParser.parsedBash(hookInput) ?: return null

            // Materialize the command stream once so we can check temporal order by index.
            val commands = parsed.allSimpleCommands().toList()
            for ((writerIdx, writer) in commands.withIndex()) {
                val writtenFile = writerScriptTarget(writer) ?: continue
                val writtenBase = writtenFile.substringAfterLast('/')
                // Only commands *after* the writer count as execution.
                val executed = commands.subList(writerIdx + 1, commands.size).any { sc ->
                    val nameBase = sc.commandName
                    if (nameBase != null && nameBase == writtenBase) return@any true
                    sc.args.any { a ->
                        val lit = a.literal ?: return@any false
                        lit == writtenFile || lit.substringAfterLast('/') == writtenBase
                    }
                }
                if (executed) return hit(id, "Write then execute in same chain: $cmd")
            }
            return null
        }

        /** Returns the script filename written by [sc] via `cat > script.sh` / `tee script.sh`, or null. */
        private fun writerScriptTarget(sc: SimpleCommand): String? {
            return when (sc.commandName) {
                "cat" -> {
                    val target = sc.redirects.firstOrNull {
                        it.op == OpKind.REDIR_OUT || it.op == OpKind.REDIR_APPEND
                    }?.target?.literal
                    target?.takeIf { isScript(it) }
                }
                "tee" -> sc.args
                    .mapNotNull { it.literal }
                    .firstOrNull { !it.startsWith("-") && isScript(it) }
                else -> null
            }
        }

        private fun isScript(lit: String?): Boolean {
            if (lit == null) return false
            val ext = lit.substringAfterLast('.', "").lowercase()
            return ext in scriptExtensions
        }
    }
}
