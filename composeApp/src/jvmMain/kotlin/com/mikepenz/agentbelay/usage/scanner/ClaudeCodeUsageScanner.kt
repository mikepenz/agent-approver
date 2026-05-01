package com.mikepenz.agentbelay.usage.scanner

import com.mikepenz.agentbelay.model.Source
import com.mikepenz.agentbelay.model.UsageRecord
import com.mikepenz.agentbelay.usage.ScanCursor
import com.mikepenz.agentbelay.usage.UsageScanner
import kotlin.time.Instant
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull
import java.io.File

/**
 * Claude Code writes one JSONL file per session under
 * `~/.claude/projects/<project-slug>/<session-id>.jsonl`. Each line is a
 * single event; assistant turns carry `message.usage` with cache-aware token
 * counts. We extract one [UsageRecord] per assistant turn.
 *
 * Schema reference (codeburn `src/providers/claude.ts`):
 *   {
 *     "type": "assistant",
 *     "message": {
 *       "model": "claude-sonnet-4-5-20250929",
 *       "usage": {
 *         "input_tokens": 1234,
 *         "output_tokens": 567,
 *         "cache_creation_input_tokens": 0,
 *         "cache_read_input_tokens": 11200
 *       }
 *     },
 *     "timestamp": "2026-04-30T12:34:56.789Z"
 *   }
 */
class ClaudeCodeUsageScanner(
    private val rootOverride: File? = null,
) : UsageScanner {
    override val source: Source = Source.CLAUDE_CODE

    override fun scan(cursors: Map<String, ScanCursor>): List<UsageRecord> {
        val root = rootOverride ?: File(ScannerSupport.homeDir(), ".claude/projects")
        val files = ScannerSupport.listFiles(root) { it.extension == "jsonl" }
        val out = mutableListOf<UsageRecord>()
        for (file in files) {
            val cursor = cursors[file.absolutePath]
            // mtime guard: if the file hasn't moved past where we last read,
            // skip the whole file. Cheap on no-op invocations.
            if (cursor != null &&
                cursor.lastOffset >= file.length() &&
                cursor.lastMtimeMillis >= file.lastModified()
            ) continue
            for ((offset, line) in ScannerSupport.readNewLines(file, cursor)) {
                if (line.isBlank()) continue
                runCatching { parseLine(file, offset, line) }
                    .getOrNull()
                    ?.let(out::add)
            }
        }
        return out
    }

    private fun parseLine(file: File, offset: Long, line: String): UsageRecord? {
        val root = ScannerSupport.json.parseToJsonElement(line).jsonObject
        if ((root["type"]?.jsonPrimitive?.contentOrNull) != "assistant") return null
        val message = root["message"] as? JsonObject ?: return null
        val usage = message["usage"] as? JsonObject ?: return null

        val inputTokens = usage["input_tokens"]?.jsonPrimitive?.longOrNull ?: 0L
        val outputTokens = usage["output_tokens"]?.jsonPrimitive?.longOrNull ?: 0L
        val cacheCreate = usage["cache_creation_input_tokens"]?.jsonPrimitive?.longOrNull ?: 0L
        val cacheRead = usage["cache_read_input_tokens"]?.jsonPrimitive?.longOrNull ?: 0L
        if (inputTokens + outputTokens + cacheCreate + cacheRead == 0L) return null

        val model = message["model"]?.jsonPrimitive?.contentOrNull
        val sessionId = root["sessionId"]?.jsonPrimitive?.contentOrNull
            ?: file.nameWithoutExtension
        val tsString = root["timestamp"]?.jsonPrimitive?.contentOrNull
        val ts = tsString?.let { runCatching { Instant.parse(it) }.getOrNull() }
            ?: Instant.fromEpochMilliseconds(file.lastModified())

        val dedupKey = "claude:${file.absolutePath}:${ts.toEpochMilliseconds()}:$inputTokens:$outputTokens:$cacheRead"
        return UsageRecord(
            harness = source,
            sessionId = sessionId,
            timestamp = ts,
            model = model,
            inputTokens = inputTokens,
            outputTokens = outputTokens,
            cacheReadTokens = cacheRead,
            cacheWriteTokens = cacheCreate,
            reasoningTokens = 0L,
            sourceFile = file.absolutePath,
            sourceOffset = offset,
            dedupKey = dedupKey,
        )
    }
}
