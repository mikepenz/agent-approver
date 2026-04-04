package com.mikepenz.agentapprover.protection.modules

import com.mikepenz.agentapprover.model.HookInput
import com.mikepenz.agentapprover.model.ProtectionHit
import com.mikepenz.agentapprover.model.ProtectionMode
import com.mikepenz.agentapprover.protection.CommandParser
import com.mikepenz.agentapprover.protection.ProtectionModule
import com.mikepenz.agentapprover.protection.ProtectionRule

object DestructiveCommandsModule : ProtectionModule {
    override val id = "destructive_commands"
    override val name = "Destructive Commands"
    override val description = "Blocks dangerous filesystem and git commands that can cause irreversible data loss."
    override val corrective = false
    override val defaultMode = ProtectionMode.ASK_AUTO_BLOCK
    override val applicableTools = setOf("Bash")

    override val rules: List<ProtectionRule> = listOf(
        RmRf,
        FindDelete,
        XargsRm,
        GitResetHard,
        GitCheckoutFiles,
        GitCleanForce,
        GitPushForce,
        GitBranchForceDelete,
        GitStashDrop,
        TruncateDd,
    )

    private fun hit(ruleId: String, message: String) = ProtectionHit(
        moduleId = id,
        ruleId = ruleId,
        message = message,
        mode = defaultMode,
    )

    private object RmRf : ProtectionRule {
        override val id = "rm_rf"
        override val name = "Recursive force remove"
        override val description = "Detects rm -rf and variants that recursively force-delete files."
        private val pattern = Regex(
            """\brm\s+(-[a-zA-Z]*r[a-zA-Z]*f[a-zA-Z]*|-[a-zA-Z]*f[a-zA-Z]*r[a-zA-Z]*|--recursive\s+--force|--force\s+--recursive)\b"""
        )

        override fun evaluate(hookInput: HookInput): ProtectionHit? {
            val cmd = CommandParser.bashCommand(hookInput) ?: return null
            if (!pattern.containsMatchIn(cmd)) return null
            if (cmd.contains("/tmp")) return null
            return hit(id, "Recursive force delete: $cmd")
        }
    }

    private object FindDelete : ProtectionRule {
        override val id = "find_delete"
        override val name = "Find with delete"
        override val description = "Detects find commands that delete matched files."
        private val deletePattern = Regex("""\bfind\b.*\s-delete\b""")
        private val execRmPattern = Regex("""\bfind\b.*-exec\s+rm\b""")

        override fun evaluate(hookInput: HookInput): ProtectionHit? {
            val cmd = CommandParser.bashCommand(hookInput) ?: return null
            if (deletePattern.containsMatchIn(cmd) || execRmPattern.containsMatchIn(cmd)) {
                return hit(id, "Find with destructive action: $cmd")
            }
            return null
        }
    }

    private object XargsRm : ProtectionRule {
        override val id = "xargs_rm"
        override val name = "Piped xargs remove"
        override val description = "Detects xargs piped to rm or unlink."
        private val pattern = Regex("""\bxargs\s+(rm|unlink)\b""")

        override fun evaluate(hookInput: HookInput): ProtectionHit? {
            val cmd = CommandParser.bashCommand(hookInput) ?: return null
            if (pattern.containsMatchIn(cmd)) {
                return hit(id, "Piped xargs delete: $cmd")
            }
            return null
        }
    }

    private object GitResetHard : ProtectionRule {
        override val id = "git_reset_hard"
        override val name = "Git reset --hard"
        override val description = "Detects git reset --hard which discards uncommitted changes."
        private val pattern = Regex("""\bgit\s+reset\s+--hard\b""")

        override fun evaluate(hookInput: HookInput): ProtectionHit? {
            val cmd = CommandParser.bashCommand(hookInput) ?: return null
            if (pattern.containsMatchIn(cmd)) {
                return hit(id, "Git hard reset: $cmd")
            }
            return null
        }
    }

    private object GitCheckoutFiles : ProtectionRule {
        override val id = "git_checkout_files"
        override val name = "Git checkout files"
        override val description = "Detects git checkout that overwrites working tree files."
        private val branchFlag = Regex("""\bgit\s+checkout\s+-b\b""")
        private val dashDash = Regex("""\bgit\s+checkout\s+--\s""")
        private val dot = Regex("""\bgit\s+checkout\s+\.""")

        override fun evaluate(hookInput: HookInput): ProtectionHit? {
            val cmd = CommandParser.bashCommand(hookInput) ?: return null
            if (branchFlag.containsMatchIn(cmd)) return null
            if (dashDash.containsMatchIn(cmd) || dot.containsMatchIn(cmd)) {
                return hit(id, "Git checkout overwrites files: $cmd")
            }
            return null
        }
    }

    private object GitCleanForce : ProtectionRule {
        override val id = "git_clean_force"
        override val name = "Git clean -f"
        override val description = "Detects git clean -f which removes untracked files."
        private val dryRun = Regex("""\bgit\s+clean\s+-[a-zA-Z]*n""")
        private val force = Regex("""\bgit\s+clean\s+-[a-zA-Z]*f""")

        override fun evaluate(hookInput: HookInput): ProtectionHit? {
            val cmd = CommandParser.bashCommand(hookInput) ?: return null
            if (dryRun.containsMatchIn(cmd)) return null
            if (force.containsMatchIn(cmd)) {
                return hit(id, "Git clean force: $cmd")
            }
            return null
        }
    }

    private object GitPushForce : ProtectionRule {
        override val id = "git_push_force"
        override val name = "Git push --force"
        override val description = "Detects git push --force which overwrites remote history."
        private val forceWithLease = Regex("""\bgit\s+push\b.*--force-with-lease\b""")
        private val forceLong = Regex("""\bgit\s+push\b.*--force\b""")
        private val forceShort = Regex("""\bgit\s+push\b.*\s-f\b""")

        override fun evaluate(hookInput: HookInput): ProtectionHit? {
            val cmd = CommandParser.bashCommand(hookInput) ?: return null
            if (forceWithLease.containsMatchIn(cmd)) return null
            if (forceLong.containsMatchIn(cmd) || forceShort.containsMatchIn(cmd)) {
                return hit(id, "Git force push: $cmd")
            }
            return null
        }
    }

    private object GitBranchForceDelete : ProtectionRule {
        override val id = "git_branch_force_delete"
        override val name = "Git branch -D"
        override val description = "Detects git branch -D which force-deletes a branch."
        private val pattern = Regex("""\bgit\s+branch\s+-D\b""")

        override fun evaluate(hookInput: HookInput): ProtectionHit? {
            val cmd = CommandParser.bashCommand(hookInput) ?: return null
            if (pattern.containsMatchIn(cmd)) {
                return hit(id, "Git force delete branch: $cmd")
            }
            return null
        }
    }

    private object GitStashDrop : ProtectionRule {
        override val id = "git_stash_drop"
        override val name = "Git stash drop/clear"
        override val description = "Detects git stash drop or clear which permanently removes stashed changes."
        private val pattern = Regex("""\bgit\s+stash\s+(drop|clear)\b""")

        override fun evaluate(hookInput: HookInput): ProtectionHit? {
            val cmd = CommandParser.bashCommand(hookInput) ?: return null
            if (pattern.containsMatchIn(cmd)) {
                return hit(id, "Git stash destruction: $cmd")
            }
            return null
        }
    }

    private object TruncateDd : ProtectionRule {
        override val id = "truncate_dd"
        override val name = "Truncate or dd"
        override val description = "Detects truncate or dd of= which can destroy file contents."
        private val truncatePattern = Regex("""\btruncate\b""")
        private val ddPattern = Regex("""\bdd\b.*\bof=""")

        override fun evaluate(hookInput: HookInput): ProtectionHit? {
            val cmd = CommandParser.bashCommand(hookInput) ?: return null
            if (truncatePattern.containsMatchIn(cmd) || ddPattern.containsMatchIn(cmd)) {
                return hit(id, "Destructive file operation: $cmd")
            }
            return null
        }
    }
}
