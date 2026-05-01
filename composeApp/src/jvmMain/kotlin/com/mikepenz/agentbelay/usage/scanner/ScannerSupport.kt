package com.mikepenz.agentbelay.usage.scanner

import com.mikepenz.agentbelay.usage.ScanCursor
import java.io.File
import kotlinx.serialization.json.Json

/**
 * Shared utilities for JSONL-style scanners. Holds a permissive [Json] config
 * (every harness writes slightly differently — unknown keys, boxed numbers,
 * etc.) and a single helper for the standard "stream new lines past the
 * cursor" pattern.
 */
internal object ScannerSupport {
    val json: Json = Json {
        ignoreUnknownKeys = true
        isLenient = true
        coerceInputValues = true
    }

    /**
     * Resolve the user's home directory respecting the conventional override
     * we use for tests (`USER_HOME` env var). Falls back to the JVM default.
     */
    fun homeDir(): File {
        System.getenv("AGENT_BELAY_HOME_OVERRIDE")?.let { return File(it) }
        return File(System.getProperty("user.home"))
    }

    /**
     * Walk every regular file under [root] matching [predicate]. Skips dot
     * directories so we don't crawl `.git` etc. Returns an empty list if the
     * root doesn't exist.
     */
    fun listFiles(root: File, predicate: (File) -> Boolean): List<File> {
        if (!root.exists() || !root.isDirectory) return emptyList()
        val out = mutableListOf<File>()
        root.walkTopDown()
            .onEnter { dir -> !dir.name.startsWith(".") }
            .filter { it.isFile && predicate(it) }
            .forEach(out::add)
        return out
    }

    /**
     * Read each line of [file] strictly after [cursor.lastOffset]. Yields
     * `(byteOffsetAtLineStart, lineText)` so the caller can record an
     * incremental cursor — surprisingly important for JSONL files that
     * harnesses append to during a live session.
     */
    fun readNewLines(file: File, cursor: ScanCursor?): Sequence<Pair<Long, String>> = sequence {
        if (!file.exists() || !file.isFile) return@sequence
        val start = cursor?.lastOffset?.takeIf { it <= file.length() } ?: 0L
        if (start >= file.length()) return@sequence
        file.inputStream().use { input ->
            input.skip(start)
            val reader = input.bufferedReader()
            var offset = start
            while (true) {
                val line = reader.readLine() ?: break
                val lineLen = line.toByteArray().size + 1L // +1 for newline
                yield(offset to line)
                offset += lineLen
            }
        }
    }
}
