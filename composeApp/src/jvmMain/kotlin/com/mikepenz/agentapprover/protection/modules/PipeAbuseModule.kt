package com.mikepenz.agentapprover.protection.modules

import com.mikepenz.agentapprover.model.HookInput
import com.mikepenz.agentapprover.model.ProtectionHit
import com.mikepenz.agentapprover.model.ProtectionMode
import com.mikepenz.agentapprover.protection.CommandParser
import com.mikepenz.agentapprover.protection.ProtectionModule
import com.mikepenz.agentapprover.protection.ProtectionRule

object PipeAbuseModule : ProtectionModule {
    override val id = "pipe_abuse"
    override val name = "Pipe Abuse"
    override val description = "Detects dangerous pipe patterns such as bulk permission changes and write-then-execute chains."
    override val corrective = false
    override val defaultMode = ProtectionMode.ASK_AUTO_BLOCK
    override val applicableTools = setOf("Bash")

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

    private object BulkPermissionChange : ProtectionRule {
        override val id = "bulk_permission_change"
        override val name = "Bulk permission change via xargs"
        override val description = "Detects xargs chmod or xargs chown for bulk permission changes."
        private val pattern = Regex("""\bxargs\s+(chmod|chown)\b""")

        override fun evaluate(hookInput: HookInput): ProtectionHit? {
            val cmd = CommandParser.bashCommand(hookInput) ?: return null
            if (!pattern.containsMatchIn(cmd)) return null
            return hit(id, "Bulk permission change via xargs: $cmd")
        }
    }

    private object WriteThenExecute : ProtectionRule {
        override val id = "write_then_execute"
        override val name = "Write then execute script"
        override val description = "Detects creating a script file and executing it in the same command chain."
        private val writePattern = Regex("""(tee|cat\s+>)\s+(\S+\.(sh|py|rb|pl|js|bash))""")
        private val chainSeparator = Regex("""&&|\|\||;""")

        override fun evaluate(hookInput: HookInput): ProtectionHit? {
            val cmd = CommandParser.bashCommand(hookInput) ?: return null
            val writeMatch = writePattern.find(cmd) ?: return null
            val filename = writeMatch.groupValues[2]
            // Check if the filename appears after a chain separator
            val afterWrite = cmd.substring(writeMatch.range.last + 1)
            if (!chainSeparator.containsMatchIn(afterWrite)) return null
            // Split on chain separators and check if the filename appears in any subsequent segment
            val segments = chainSeparator.split(afterWrite)
            val filenameBase = filename.substringAfterLast('/')
            for (segment in segments) {
                if (segment.contains(filename) || segment.contains(filenameBase)) {
                    return hit(id, "Write then execute in same chain: $cmd")
                }
            }
            return null
        }
    }
}
