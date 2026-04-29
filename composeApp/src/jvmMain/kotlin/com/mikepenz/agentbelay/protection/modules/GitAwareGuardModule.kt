package com.mikepenz.agentbelay.protection.modules

import co.touchlab.kermit.Logger
import com.mikepenz.agentbelay.model.HookInput
import com.mikepenz.agentbelay.model.ProtectionHit
import com.mikepenz.agentbelay.model.ProtectionMode
import com.mikepenz.agentbelay.protection.CommandParser
import com.mikepenz.agentbelay.protection.ProtectionModule
import com.mikepenz.agentbelay.protection.ProtectionRule
import java.io.File
import java.nio.file.Paths
import java.util.concurrent.TimeUnit

/**
 * Adds a cwd-context safety check on top of the existing [DestructiveCommandsModule] /
 * [UncommittedFilesModule] rules. This module flags the classic "agent rm -rf's my work
 * before I committed" footgun by looking at the *directory* a destructive operation runs in:
 *
 *  1. `destructive_outside_git` — destructive Bash command in a directory that is not inside
 *     any git repository at all.
 *  2. `write_outside_git` — Write/Edit creating/modifying a file under a cwd that is not in a
 *     git repository (catches agents writing to `/tmp` or `$HOME` without VCS safety).
 *  3. `destructive_in_dirty_repo` — destructive Bash command in a git repo that has any
 *     uncommitted changes (broader than [UncommittedFilesModule.DestructiveOnDirty], which only
 *     fires when the destructive op explicitly targets one of the dirty files).
 *
 * Default mode is [ProtectionMode.ASK] — these are warnings, not hard blocks.
 */
object GitAwareGuardModule : ProtectionModule {
    override val id = "git_aware_guard"
    override val name = "Git-aware Guard"
    override val description =
        "Warns when destructive shell or file operations run in a directory that is not under git " +
            "version control, or in a git repo with uncommitted changes that could be lost."
    override val corrective = false
    override val defaultMode = ProtectionMode.ASK
    override val applicableTools = setOf("Bash", "Write", "Edit")

    override val rules: List<ProtectionRule> = listOf(
        DestructiveOutsideGit,
        WriteOutsideGit,
        DestructiveInDirtyRepo,
    )

    private fun hit(ruleId: String, message: String) = ProtectionHit(
        moduleId = id,
        ruleId = ruleId,
        message = message,
        mode = defaultMode,
    )

    // --- helpers ---------------------------------------------------------------

    /**
     * Walks upward from [path] looking for a `.git` entry. Returns the directory that
     * contains it (the git work-tree root), or null if none is found before reaching the
     * filesystem root. Handles `.git` being a regular file (submodules / linked worktrees).
     */
    internal fun findGitRoot(path: String): File? {
        if (path.isEmpty()) return null
        var dir: File? = try {
            File(path).absoluteFile.canonicalFile
        } catch (_: Exception) {
            File(path).absoluteFile
        }
        while (dir != null) {
            val dotGit = File(dir, ".git")
            if (dotGit.exists()) return dir
            dir = dir.parentFile
        }
        return null
    }

    /**
     * Runs `git status --porcelain` in [gitRoot] and returns true iff there is any output.
     * Fail-open: any error / timeout / missing git binary returns false so we don't spam
     * false positives on systems without git.
     */
    internal fun hasUncommittedChanges(gitRoot: File): Boolean {
        return try {
            val process = ProcessBuilder("git", "status", "--porcelain")
                .directory(gitRoot)
                // Merge stderr into stdout so a chatty stderr can't fill its pipe and
                // block the child before waitFor returns. We drain the combined stream
                // below regardless of exit code.
                .redirectErrorStream(true)
                .start()
            val output = process.inputStream.bufferedReader().use { it.readText() }
            val finished = process.waitFor(3, TimeUnit.SECONDS)
            if (!finished) {
                process.destroyForcibly()
                Logger.w("GitAwareGuardModule") { "git status timed out in ${gitRoot.path}" }
                return false
            }
            if (process.exitValue() != 0) return false
            // With redirectErrorStream(true) stderr ends up in `output` too; porcelain
            // output is line-oriented, so checking for any non-blank line is sufficient.
            output.isNotBlank()
        } catch (e: Exception) {
            Logger.w("GitAwareGuardModule", e) { "Failed to run git status in ${gitRoot.path}" }
            false
        }
    }

    /**
     * Lightweight destructive-command detector. Intentionally not a full parser — we want to
     * catch common footguns without duplicating the more rigorous detection in
     * [DestructiveCommandsModule]. False positives are OK because the mode is ASK, not BLOCK.
     */
    private val destructivePatterns: List<Regex> = listOf(
        Regex("""(^|[\s;&|`(])rm\s+(-[a-zA-Z]*r[a-zA-Z]*f|-[a-zA-Z]*f[a-zA-Z]*r|-rf|-fr|--recursive)"""),
        Regex("""(^|[\s;&|`(])rm\s+-r(\s|$)"""),
        Regex("""(^|[\s;&|`(])git\s+clean\s+.*-[a-zA-Z]*f"""),
        Regex("""(^|[\s;&|`(])git\s+reset\s+.*--hard"""),
        Regex("""(^|[\s;&|`(])git\s+checkout\s+(\.|\-\-)"""),
        Regex("""(^|[\s;&|`(])find\s+[^|;&]*-delete"""),
        Regex("""(^|[\s;&|`(])find\s+[^|;&]*-exec\s+rm"""),
        Regex("""(^|[\s;&|`(])xargs\s+[^|;&]*\brm\b"""),
        Regex("""(^|[\s;&|`(])(truncate|shred|dd)\s+"""),
        // single `>` redirect (overwrite), but not `>>`. Tolerate spaces.
        Regex("""[^>]>(?!>)\s*[^\s|&;]+"""),
        Regex("""(^|[\s;&|`(])mv\s+[^|;&]*\s+\S+"""),
    )

    private fun isDestructiveCommand(command: String): Boolean {
        val c = command.trim()
        if (c.isEmpty()) return false
        return destructivePatterns.any { it.containsMatchIn(c) }
    }

    private fun truncate(s: String, max: Int = 80): String =
        if (s.length <= max) s else s.substring(0, max - 1) + "\u2026"

    // --- rules -----------------------------------------------------------------

    private object DestructiveOutsideGit : ProtectionRule {
        override val id = "destructive_outside_git"
        override val name = "Destructive command outside git"
        override val description =
            "Warns when a destructive shell command runs in a directory that is not inside a git repository."

        override fun evaluate(hookInput: HookInput): ProtectionHit? {
            if (hookInput.toolName != "Bash") return null
            val command = CommandParser.bashCommand(hookInput) ?: return null
            if (!isDestructiveCommand(command)) return null
            val cwd = hookInput.cwd.ifEmpty { return null }
            if (findGitRoot(cwd) != null) return null
            return hit(
                id,
                "Destructive command in non-git directory: $cwd (cmd: ${truncate(command)})",
            )
        }
    }

    private object WriteOutsideGit : ProtectionRule {
        override val id = "write_outside_git"
        override val name = "Write/Edit outside git"
        override val description =
            "Warns when Write or Edit creates/modifies a file in a directory that is not inside a git repository."

        override fun evaluate(hookInput: HookInput): ProtectionHit? {
            if (hookInput.toolName != "Write" && hookInput.toolName != "Edit") return null
            val filePath = CommandParser.filePath(hookInput) ?: return null
            val cwd = hookInput.cwd.ifEmpty { return null }
            // If the cwd is in a git repo, defer to other modules.
            if (findGitRoot(cwd) != null) return null
            // Only warn when the target lives under (or is) the cwd — don't double-warn for
            // arbitrary absolute paths handled elsewhere.
            val targetUnderCwd = try {
                // Use NIO Path so this is correct on Windows (C:\...) as well as POSIX.
                val rawTarget = Paths.get(filePath)
                val cwdPath = Paths.get(cwd).toAbsolutePath().normalize()
                val targetPath = (if (rawTarget.isAbsolute) rawTarget else cwdPath.resolve(rawTarget))
                    .toAbsolutePath()
                    .normalize()
                targetPath == cwdPath || targetPath.startsWith(cwdPath)
            } catch (_: Exception) {
                false
            }
            if (!targetUnderCwd) return null
            return hit(id, "Write/Edit in non-git directory: ${truncate(filePath)}")
        }
    }

    /**
     * Note on overlap with [UncommittedFilesModule.DestructiveOnDirty]: that rule only fires
     * when a destructive op explicitly *names* a dirty file (it intersects parsed argument
     * paths with `git status --porcelain` output). This rule is broader: any destructive
     * command at all in a repo with uncommitted changes (e.g. `rm -rf build/` while
     * `src/Main.kt` is dirty). The rules are complementary, not duplicative.
     */
    private object DestructiveInDirtyRepo : ProtectionRule {
        override val id = "destructive_in_dirty_repo"
        override val name = "Destructive command in dirty repo"
        override val description =
            "Warns when a destructive shell command runs in a git repo that has uncommitted changes."

        override fun evaluate(hookInput: HookInput): ProtectionHit? {
            if (hookInput.toolName != "Bash") return null
            val command = CommandParser.bashCommand(hookInput) ?: return null
            if (!isDestructiveCommand(command)) return null
            val cwd = hookInput.cwd.ifEmpty { return null }
            val gitRoot = findGitRoot(cwd) ?: return null
            if (!hasUncommittedChanges(gitRoot)) return null
            return hit(
                id,
                "Destructive command with uncommitted changes: ${gitRoot.path} (cmd: ${truncate(command)})",
            )
        }
    }
}
