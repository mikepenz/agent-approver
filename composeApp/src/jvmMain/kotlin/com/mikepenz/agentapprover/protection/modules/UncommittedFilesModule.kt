package com.mikepenz.agentapprover.protection.modules

import co.touchlab.kermit.Logger
import com.mikepenz.agentapprover.model.HookInput
import com.mikepenz.agentapprover.model.ProtectionHit
import com.mikepenz.agentapprover.model.ProtectionMode
import com.mikepenz.agentapprover.protection.CommandParser
import com.mikepenz.agentapprover.protection.ProtectionModule
import com.mikepenz.agentapprover.protection.ProtectionRule
import java.io.File
import java.util.concurrent.TimeUnit

object UncommittedFilesModule : ProtectionModule {
    override val id = "uncommitted_files"
    override val name = "Uncommitted Files"
    override val description = "Warns when destructive operations target files with uncommitted git changes."
    override val corrective = false
    override val defaultMode = ProtectionMode.ASK
    override val applicableTools = setOf("Bash")

    override val rules: List<ProtectionRule> = listOf(
        DestructiveOnDirty,
    )

    private fun hit(ruleId: String, message: String) = ProtectionHit(
        moduleId = id,
        ruleId = ruleId,
        message = message,
        mode = defaultMode,
    )

    private object DestructiveOnDirty : ProtectionRule {
        override val id = "destructive_on_dirty"
        override val name = "Destructive operation on dirty files"
        override val description =
            "Detects destructive operations (rm, mv, unlink, truncate, redirect, sed -i, perl -i) targeting files with uncommitted git changes."
        private val destructivePattern = Regex("""\b(rm|mv|unlink|truncate)\b|>\s|sed\s+-i|perl\s+-i""")

        override fun evaluate(hookInput: HookInput): ProtectionHit? {
            val cmd = CommandParser.bashCommand(hookInput) ?: return null
            if (!destructivePattern.containsMatchIn(cmd)) return null

            val paths = CommandParser.extractPaths(cmd)
            if (paths.isEmpty()) return null

            val cwd = hookInput.cwd.ifEmpty { return null }
            val dirtyFiles = getDirtyFiles(cwd) ?: return null
            if (dirtyFiles.isEmpty()) return null

            val matchingFiles = paths.filter { path ->
                val resolved = if (path.startsWith("/")) path else "$cwd/$path"
                val normalized = File(resolved).normalize().path
                dirtyFiles.any { dirty ->
                    val dirtyResolved = "$cwd/$dirty"
                    val dirtyNormalized = File(dirtyResolved).normalize().path
                    normalized == dirtyNormalized || dirty == path
                }
            }

            if (matchingFiles.isEmpty()) return null
            return hit(
                id,
                "Blocked: destructive operation on file with uncommitted changes. Use git stash first or use the Edit tool.",
            )
        }

        private fun getDirtyFiles(cwd: String): List<String>? {
            return try {
                val process = ProcessBuilder("git", "status", "--porcelain")
                    .directory(File(cwd))
                    .redirectErrorStream(true)
                    .start()
                val completed = process.waitFor(5, TimeUnit.SECONDS)
                if (!completed) {
                    process.destroyForcibly()
                    Logger.w("UncommittedFilesModule") { "git status timed out in $cwd" }
                    return null
                }
                if (process.exitValue() != 0) return null
                process.inputStream.bufferedReader().readLines()
                    .filter { it.length > 3 }
                    .map { it.substring(3) }
            } catch (e: Exception) {
                Logger.w("UncommittedFilesModule", e) { "Failed to run git status in $cwd" }
                null
            }
        }
    }
}
