package com.mikepenz.agentbelay.hook

import co.touchlab.kermit.Logger
import java.io.File
import java.io.RandomAccessFile
import java.nio.channels.FileLock
import java.nio.file.Files
import java.nio.file.StandardCopyOption

private val logger = Logger.withTag("CodexBridgeInstaller")

/**
 * Installs Agent Belay as a hook source for OpenAI's Codex CLI.
 *
 * Codex stores configuration in `~/.codex/config.toml`. The hooks crate
 * reads `[[hooks.<event>]]` array-of-table entries with `type = "http"`
 * and `url = "..."` fields — deliberately Claude-Code-shaped, per
 * `codex-rs/hooks/`.
 *
 * Rather than pull in a TOML parser, we bracket our hook entries inside
 * a managed block:
 *
 * ```
 * # >>> agent-belay >>>
 * # Managed by Agent Belay — do not edit. Re-register in Agent Belay
 * # to update the port; unregister to remove.
 * [[hooks.PermissionRequest]]
 * type = "http"
 * url  = "http://localhost:<port>/approve-codex"
 * …
 * # <<< agent-belay <<<
 * ```
 *
 * `register` reads the file, replaces any existing block (or appends a
 * new one if absent), and atomically writes the result back.
 * `unregister` strips the block. User content outside the markers is
 * preserved untouched, so coexistence with hand-edited keys is safe.
 *
 * If a user manually moves or breaks the markers, we treat the file as
 * not-registered and a fresh `register` will append a new block — never
 * mutating user lines.
 */
object CodexBridgeInstaller {

    private const val BEGIN_MARKER = "# >>> agent-belay >>>"
    private const val END_MARKER = "# <<< agent-belay <<<"

    private fun configFile(): File {
        val home = System.getProperty("user.home")
        return File(home, ".codex/config.toml")
    }

    private fun permissionRequestUrl(port: Int): String = "http://localhost:$port/approve-codex"
    private fun preToolUseUrl(port: Int): String = "http://localhost:$port/pre-tool-use-codex"

    fun isRegistered(port: Int): Boolean {
        val file = configFile()
        if (!file.exists()) return false
        return try {
            val text = file.readText()
            val block = extractManagedBlock(text) ?: return false
            block.contains(permissionRequestUrl(port)) && block.contains(preToolUseUrl(port))
        } catch (e: Exception) {
            logger.w(e) { "Failed to read ${file.absolutePath}" }
            false
        }
    }

    fun register(port: Int) {
        val file = configFile()
        file.parentFile.mkdirs()

        withFileLock(file) {
            val original = if (file.exists()) file.readText() else ""
            val withoutBlock = stripManagedBlock(original)
            val block = buildManagedBlock(port)
            val updated = if (withoutBlock.isEmpty()) {
                block + "\n"
            } else if (withoutBlock.endsWith("\n")) {
                withoutBlock + "\n" + block + "\n"
            } else {
                withoutBlock + "\n\n" + block + "\n"
            }
            atomicWrite(file, updated)
            logger.i { "Registered Codex hooks for port $port" }
        }
    }

    fun unregister(@Suppress("UNUSED_PARAMETER") port: Int) {
        val file = configFile()
        if (!file.exists()) return

        withFileLock(file) {
            val original = file.readText()
            val stripped = stripManagedBlock(original)
            if (stripped == original) return@withFileLock
            // If the file is now empty / whitespace-only, delete it to avoid
            // leaving an orphaned config file we created.
            if (stripped.isBlank()) {
                file.delete()
                logger.i { "Removed empty ${file.absolutePath} after unregistering Codex hooks" }
            } else {
                atomicWrite(file, stripped)
                logger.i { "Unregistered Codex hooks from ${file.absolutePath}" }
            }
        }
    }

    internal fun buildManagedBlock(port: Int): String = """
        |$BEGIN_MARKER
        |# Managed by Agent Belay — do not edit. Re-register in Agent Belay
        |# to update the port; unregister to remove.
        |[[hooks.PermissionRequest]]
        |matcher = ""
        |type    = "http"
        |url     = "${permissionRequestUrl(port)}"
        |
        |[[hooks.PreToolUse]]
        |matcher = ""
        |type    = "http"
        |url     = "${preToolUseUrl(port)}"
        |timeout = 120
        |$END_MARKER
    """.trimMargin()

    /** Returns the contents between the markers (exclusive), or null if not found. */
    internal fun extractManagedBlock(text: String): String? {
        val begin = text.indexOf(BEGIN_MARKER)
        if (begin < 0) return null
        val end = text.indexOf(END_MARKER, startIndex = begin + BEGIN_MARKER.length)
        if (end < 0) return null
        return text.substring(begin + BEGIN_MARKER.length, end)
    }

    /** Removes the managed block (and one trailing newline) from [text]. */
    internal fun stripManagedBlock(text: String): String {
        val begin = text.indexOf(BEGIN_MARKER)
        if (begin < 0) return text
        val endStart = text.indexOf(END_MARKER, startIndex = begin + BEGIN_MARKER.length)
        if (endStart < 0) return text
        var endExclusive = endStart + END_MARKER.length
        if (endExclusive < text.length && text[endExclusive] == '\n') endExclusive++
        // Also collapse a leading blank line we might have inserted.
        var beginInclusive = begin
        if (beginInclusive > 0 && text[beginInclusive - 1] == '\n') {
            // peek one further back to drop the separator we added
            if (beginInclusive >= 2 && text[beginInclusive - 2] == '\n') beginInclusive--
        }
        return text.substring(0, beginInclusive) + text.substring(endExclusive)
    }

    private fun atomicWrite(target: File, content: String) {
        val tmp = File(target.parentFile, "${target.name}.tmp")
        tmp.writeText(content)
        Files.move(tmp.toPath(), target.toPath(), StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE)
    }

    private inline fun withFileLock(file: File, block: () -> Unit) {
        val lockFile = File(file.parentFile, "${file.name}.lock")
        lockFile.parentFile.mkdirs()
        var raf: RandomAccessFile? = null
        var lock: FileLock? = null
        try {
            raf = RandomAccessFile(lockFile, "rw")
            lock = try {
                raf.channel.lock()
            } catch (e: Exception) {
                logger.w(e) { "Could not acquire file lock on ${lockFile.absolutePath} — proceeding unlocked" }
                null
            }
            block()
        } finally {
            lock?.release()
            raf?.close()
        }
    }
}
