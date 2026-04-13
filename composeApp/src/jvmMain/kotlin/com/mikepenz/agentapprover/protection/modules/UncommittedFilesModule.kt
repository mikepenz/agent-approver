package com.mikepenz.agentapprover.protection.modules

import co.touchlab.kermit.Logger
import com.mikepenz.agentapprover.model.HookInput
import com.mikepenz.agentapprover.model.ProtectionHit
import com.mikepenz.agentapprover.model.ProtectionMode
import com.mikepenz.agentapprover.protection.CommandParser
import com.mikepenz.agentapprover.protection.ProtectionModule
import com.mikepenz.agentapprover.protection.ProtectionRule
import com.mikepenz.agentapprover.protection.parser.OpKind
import com.mikepenz.agentapprover.protection.parser.allSimpleCommands
import java.io.File
import java.util.concurrent.TimeUnit

object UncommittedFilesModule : ProtectionModule {
    override val id = "uncommitted_files"
    override val name = "Uncommitted Files"
    override val description = "Warns when destructive operations target files with uncommitted git changes."
    override val corrective = false
    override val defaultMode = ProtectionMode.ASK
    override val applicableTools = setOf("Bash")

    private val destructiveCommands = setOf("rm", "mv", "unlink", "truncate")

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

        override fun evaluate(hookInput: HookInput): ProtectionHit? {
            val parsed = CommandParser.parsedBash(hookInput) ?: return null
            val cwd = hookInput.cwd.ifEmpty { return null }

            // Collect candidate file paths from any simple command that performs a destructive op.
            val candidates = mutableListOf<String>()
            var anyDestructive = false
            for (sc in parsed.allSimpleCommands()) {
                val name = sc.commandName
                var destructive = false
                if (name in destructiveCommands) {
                    destructive = true
                    sc.args.forEach { a -> a.literal?.let { if (!it.startsWith("-")) candidates.add(it) } }
                }
                if (name == "sed" && (sc.hasFlag(short = 'i') || sc.hasLongFlag("--in-place"))) {
                    destructive = true
                    // `-e <script>` / `-f <script-file>` take an operand that is NOT the target
                    // file sed writes to. Skip that operand so we don't flag the script file.
                    var skipNext = false
                    for (a in sc.args) {
                        val lit = a.literal ?: continue
                        if (skipNext) { skipNext = false; continue }
                        when {
                            lit == "-e" || lit == "-f" || lit == "--expression" || lit == "--file" -> skipNext = true
                            lit.startsWith("-e") || lit.startsWith("-f") ||
                                lit.startsWith("--expression=") || lit.startsWith("--file=") -> { /* inline option */ }
                            !lit.startsWith("-") -> candidates.add(lit)
                        }
                    }
                }
                if (name == "perl" && sc.hasFlag(short = 'i')) {
                    destructive = true
                    sc.args.forEach { a -> a.literal?.let { if (!it.startsWith("-")) candidates.add(it) } }
                }
                // Any overwrite redirect (>, >>, >|, &>, &>>) treats its target as a destructive write.
                for (r in sc.redirects) {
                    when (r.op) {
                        OpKind.REDIR_OUT, OpKind.REDIR_APPEND, OpKind.REDIR_FORCE_OUT,
                        OpKind.REDIR_ALL_OUT, OpKind.REDIR_ALL_APPEND -> {
                            destructive = true
                            r.target.literal?.let { candidates.add(it) }
                        }
                        else -> {}
                    }
                }
                if (destructive) anyDestructive = true
            }
            if (!anyDestructive || candidates.isEmpty()) return null

            val dirtyFiles = getDirtyFiles(cwd) ?: return null
            if (dirtyFiles.isEmpty()) return null

            val matching = candidates.any { path ->
                val resolved = if (path.startsWith("/")) path else "$cwd/$path"
                val normalized = File(resolved).normalize().path
                dirtyFiles.any { dirty ->
                    val dirtyNormalized = File("$cwd/$dirty").normalize().path
                    normalized == dirtyNormalized || dirty == path
                }
            }
            if (!matching) return null
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
