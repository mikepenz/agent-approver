package com.mikepenz.agentbuddy.protection.modules

import com.mikepenz.agentbuddy.model.HookInput
import com.mikepenz.agentbuddy.model.ProtectionHit
import com.mikepenz.agentbuddy.model.ProtectionMode
import com.mikepenz.agentbuddy.protection.CommandParser
import com.mikepenz.agentbuddy.protection.ProtectionModule
import com.mikepenz.agentbuddy.protection.ProtectionRule
import com.mikepenz.agentbuddy.protection.parser.ParsedCommand
import com.mikepenz.agentbuddy.protection.parser.SimpleCommand
import com.mikepenz.agentbuddy.protection.parser.allSimpleCommands

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

    /** Parse helper — returns the parsed command or null if not a Bash invocation. */
    private fun parse(hookInput: HookInput): ParsedCommand? = CommandParser.parsedBash(hookInput)

    /** All simple commands reachable from [hookInput], flattened (recursing into subshells / bash -c bodies). */
    private fun commands(hookInput: HookInput): Sequence<SimpleCommand> =
        parse(hookInput)?.allSimpleCommands() ?: emptySequence()

    private object RmRf : ProtectionRule {
        override val id = "rm_rf"
        override val name = "Recursive force remove"
        override val description = "Detects rm -rf and variants that recursively force-delete files."

        override fun evaluate(hookInput: HookInput): ProtectionHit? {
            val cmd = CommandParser.bashCommand(hookInput) ?: return null
            val offending = commands(hookInput).firstOrNull { sc ->
                if (sc.commandName != "rm") return@firstOrNull false
                val hasR = sc.hasFlag(short = 'r') || sc.hasLongFlag("--recursive")
                val hasF = sc.hasFlag(short = 'f') || sc.hasLongFlag("--force")
                if (!(hasR && hasF)) return@firstOrNull false
                // /tmp whitelist: if every non-flag literal arg resolves under /tmp and none is opaque, allow.
                val targets = sc.args.filter { it.literal?.startsWith("-") != true }
                val allLiteral = targets.all { it.literal != null }
                val allTmp = allLiteral && targets.isNotEmpty() &&
                    targets.all { it.literal!!.startsWith("/tmp") || it.literal!!.startsWith("tmp/") }
                !allTmp
            } ?: return null
            return hit(id, "Recursive force delete: $cmd")
        }
    }

    private object FindDelete : ProtectionRule {
        override val id = "find_delete"
        override val name = "Find with delete"
        override val description = "Detects find commands that delete matched files."

        override fun evaluate(hookInput: HookInput): ProtectionHit? {
            val cmd = CommandParser.bashCommand(hookInput) ?: return null
            val match = commands(hookInput).firstOrNull { sc ->
                if (sc.commandName != "find") return@firstOrNull false
                if (sc.hasLiteralArg("-delete")) return@firstOrNull true
                // -exec rm ...
                val execIdx = sc.args.indexOfFirst { it.literal == "-exec" }
                if (execIdx >= 0 && execIdx + 1 < sc.args.size) {
                    val next = sc.args[execIdx + 1].literal
                    if (next != null && (next == "rm" || next.endsWith("/rm"))) return@firstOrNull true
                }
                false
            }
            return if (match != null) hit(id, "Find with destructive action: $cmd") else null
        }
    }

    private object XargsRm : ProtectionRule {
        override val id = "xargs_rm"
        override val name = "Piped xargs remove"
        override val description = "Detects xargs piped to rm or unlink."

        override fun evaluate(hookInput: HookInput): ProtectionHit? {
            val cmd = CommandParser.bashCommand(hookInput) ?: return null
            val match = commands(hookInput).firstOrNull { sc ->
                if (sc.commandName != "xargs") return@firstOrNull false
                // xargs invokes the first non-flag positional arg as a command.
                val invokedCmd = sc.args.firstOrNull { lit ->
                    val l = lit.literal ?: return@firstOrNull false
                    !l.startsWith("-")
                }?.literal?.substringAfterLast('/')
                invokedCmd == "rm" || invokedCmd == "unlink"
            }
            return if (match != null) hit(id, "Piped xargs delete: $cmd") else null
        }
    }

    private object GitResetHard : ProtectionRule {
        override val id = "git_reset_hard"
        override val name = "Git reset --hard"
        override val description = "Detects git reset --hard which discards uncommitted changes."

        override fun evaluate(hookInput: HookInput): ProtectionHit? {
            val cmd = CommandParser.bashCommand(hookInput) ?: return null
            val match = commands(hookInput).firstOrNull { sc ->
                sc.commandName == "git" &&
                    sc.hasLiteralArg("reset") &&
                    sc.hasLongFlag("--hard")
            }
            return if (match != null) hit(id, "Git hard reset: $cmd") else null
        }
    }

    private object GitCheckoutFiles : ProtectionRule {
        override val id = "git_checkout_files"
        override val name = "Git checkout files"
        override val description = "Detects git checkout that overwrites working tree files."

        override fun evaluate(hookInput: HookInput): ProtectionHit? {
            val cmd = CommandParser.bashCommand(hookInput) ?: return null
            val match = commands(hookInput).firstOrNull { sc ->
                if (sc.commandName != "git") return@firstOrNull false
                if (!sc.hasLiteralArg("checkout")) return@firstOrNull false
                if (sc.hasFlag(short = 'b') || sc.hasLongFlag("--branch")) return@firstOrNull false
                // checkout . or checkout -- <file>
                val hasDot = sc.hasLiteralArg(".")
                val hasDashDash = sc.hasLiteralArg("--")
                hasDot || hasDashDash
            }
            return if (match != null) hit(id, "Git checkout overwrites files: $cmd") else null
        }
    }

    private object GitCleanForce : ProtectionRule {
        override val id = "git_clean_force"
        override val name = "Git clean -f"
        override val description = "Detects git clean -f which removes untracked files."

        override fun evaluate(hookInput: HookInput): ProtectionHit? {
            val cmd = CommandParser.bashCommand(hookInput) ?: return null
            val match = commands(hookInput).firstOrNull { sc ->
                if (sc.commandName != "git" || !sc.hasLiteralArg("clean")) return@firstOrNull false
                // Dry-run -n overrides force.
                if (sc.hasFlag(short = 'n') || sc.hasLongFlag("--dry-run")) return@firstOrNull false
                sc.hasFlag(short = 'f') || sc.hasLongFlag("--force")
            }
            return if (match != null) hit(id, "Git clean force: $cmd") else null
        }
    }

    private object GitPushForce : ProtectionRule {
        override val id = "git_push_force"
        override val name = "Git push --force"
        override val description = "Detects git push --force which overwrites remote history."

        override fun evaluate(hookInput: HookInput): ProtectionHit? {
            val cmd = CommandParser.bashCommand(hookInput) ?: return null
            val match = commands(hookInput).firstOrNull { sc ->
                if (sc.commandName != "git" || !sc.hasLiteralArg("push")) return@firstOrNull false
                if (sc.hasLongFlag("--force-with-lease")) return@firstOrNull false
                sc.hasLongFlag("--force") || sc.hasFlag(short = 'f')
            }
            return if (match != null) hit(id, "Git force push: $cmd") else null
        }
    }

    private object GitBranchForceDelete : ProtectionRule {
        override val id = "git_branch_force_delete"
        override val name = "Git branch -D"
        override val description = "Detects git branch -D which force-deletes a branch."

        override fun evaluate(hookInput: HookInput): ProtectionHit? {
            val cmd = CommandParser.bashCommand(hookInput) ?: return null
            val match = commands(hookInput).firstOrNull { sc ->
                sc.commandName == "git" && sc.hasLiteralArg("branch") && sc.hasLiteralArg("-D")
            }
            return if (match != null) hit(id, "Git force delete branch: $cmd") else null
        }
    }

    private object GitStashDrop : ProtectionRule {
        override val id = "git_stash_drop"
        override val name = "Git stash drop/clear"
        override val description = "Detects git stash drop or clear which permanently removes stashed changes."

        override fun evaluate(hookInput: HookInput): ProtectionHit? {
            val cmd = CommandParser.bashCommand(hookInput) ?: return null
            val match = commands(hookInput).firstOrNull { sc ->
                sc.commandName == "git" && sc.hasLiteralArg("stash") &&
                    (sc.hasLiteralArg("drop") || sc.hasLiteralArg("clear"))
            }
            return if (match != null) hit(id, "Git stash destruction: $cmd") else null
        }
    }

    private object TruncateDd : ProtectionRule {
        override val id = "truncate_dd"
        override val name = "Truncate or dd"
        override val description = "Detects truncate or dd of= which can destroy file contents."

        override fun evaluate(hookInput: HookInput): ProtectionHit? {
            val cmd = CommandParser.bashCommand(hookInput) ?: return null
            val match = commands(hookInput).firstOrNull { sc ->
                when (sc.commandName) {
                    "truncate" -> true
                    "dd" -> sc.args.any { a -> a.literal?.startsWith("of=") == true }
                    else -> false
                }
            }
            return if (match != null) hit(id, "Destructive file operation: $cmd") else null
        }
    }
}
