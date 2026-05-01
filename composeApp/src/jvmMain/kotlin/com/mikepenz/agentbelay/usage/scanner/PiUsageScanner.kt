package com.mikepenz.agentbelay.usage.scanner

import com.mikepenz.agentbelay.model.Source
import com.mikepenz.agentbelay.model.UsageRecord
import com.mikepenz.agentbelay.usage.ScanCursor
import com.mikepenz.agentbelay.usage.UsageScanner
import kotlinx.datetime.Instant
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull
import java.io.File

/**
 * Pi (badlogic/pi-mono) writes append-only conversation logs at
 * `~/.pi/agent/sessions/.../log.jsonl`. Token usage is exposed via the
 * `get_session_stats` RPC; on disk each assistant message carries a `usage`
 * object with `input`, `output`, `cacheRead`, `cacheWrite` token counts.
 */
class PiUsageScanner(
    private val rootOverride: File? = null,
) : UsageScanner {
    override val source: Source = Source.PI

    override fun scan(cursors: Map<String, ScanCursor>): List<UsageRecord> {
        val root = rootOverride ?: File(ScannerSupport.homeDir(), ".pi/agent/sessions")
        val files = ScannerSupport.listFiles(root) { it.name == "log.jsonl" }
        val out = mutableListOf<UsageRecord>()
        for (file in files) {
            val cursor = cursors[file.absolutePath]
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
        val obj = ScannerSupport.json.parseToJsonElement(line).jsonObject
        val role = obj["role"]?.jsonPrimitive?.contentOrNull
            ?: obj["type"]?.jsonPrimitive?.contentOrNull
        if (role != null && !role.contains("assistant", ignoreCase = true)) return null

        val usage = (obj["usage"] as? JsonObject)
            ?: ((obj["message"] as? JsonObject)?.get("usage") as? JsonObject)
            ?: return null

        val input = usage["input"]?.jsonPrimitive?.longOrNull
            ?: usage["input_tokens"]?.jsonPrimitive?.longOrNull ?: 0L
        val output = usage["output"]?.jsonPrimitive?.longOrNull
            ?: usage["output_tokens"]?.jsonPrimitive?.longOrNull ?: 0L
        val cacheRead = usage["cacheRead"]?.jsonPrimitive?.longOrNull
            ?: usage["cache_read"]?.jsonPrimitive?.longOrNull ?: 0L
        val cacheWrite = usage["cacheWrite"]?.jsonPrimitive?.longOrNull
            ?: usage["cache_write"]?.jsonPrimitive?.longOrNull ?: 0L
        if (input + output + cacheRead + cacheWrite == 0L) return null

        val model = obj["model"]?.jsonPrimitive?.contentOrNull
            ?: (obj["message"] as? JsonObject)?.get("model")?.jsonPrimitive?.contentOrNull
        val sessionId = obj["sessionId"]?.jsonPrimitive?.contentOrNull
            ?: file.parentFile?.name
            ?: file.nameWithoutExtension
        val msgId = obj["id"]?.jsonPrimitive?.contentOrNull
        val tsString = obj["timestamp"]?.jsonPrimitive?.contentOrNull
            ?: obj["ts"]?.jsonPrimitive?.contentOrNull
        val ts = tsString?.let { runCatching { Instant.parse(it) }.getOrNull() }
            ?: Instant.fromEpochMilliseconds(file.lastModified())
        val dedupKey = "pi:${file.absolutePath}:${msgId ?: "$offset:${ts.toEpochMilliseconds()}"}"
        return UsageRecord(
            harness = source,
            sessionId = sessionId,
            timestamp = ts,
            model = model,
            inputTokens = input,
            outputTokens = output,
            cacheReadTokens = cacheRead,
            cacheWriteTokens = cacheWrite,
            sourceFile = file.absolutePath,
            sourceOffset = offset,
            dedupKey = dedupKey,
        )
    }
}
