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
 * GitHub Copilot CLI writes per-workspace state under
 * `~/.copilot/history/<workspace>/session-state/events.jsonl`. The legacy
 * format only records `outputTokens` on `assistant.message` events, so input
 * cost is unavailable — we record output tokens with input=0 and let the cost
 * calculator surface output-only spend.
 */
class CopilotUsageScanner(
    private val rootOverride: File? = null,
) : UsageScanner {
    override val source: Source = Source.COPILOT

    override fun scan(cursors: Map<String, ScanCursor>): List<UsageRecord> {
        val root = rootOverride ?: File(ScannerSupport.homeDir(), ".copilot/history")
        val files = ScannerSupport.listFiles(root) {
            it.name == "events.jsonl"
        }
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
        val type = obj["type"]?.jsonPrimitive?.contentOrNull
            ?: obj["event"]?.jsonPrimitive?.contentOrNull
            ?: return null
        if (!type.contains("assistant", ignoreCase = true)) return null
        val message = obj["message"] as? JsonObject ?: obj["data"] as? JsonObject ?: obj
        val outputTokens = message["outputTokens"]?.jsonPrimitive?.longOrNull
            ?: (message["usage"] as? JsonObject)?.get("output_tokens")?.jsonPrimitive?.longOrNull
            ?: 0L
        val inputTokens = message["inputTokens"]?.jsonPrimitive?.longOrNull
            ?: (message["usage"] as? JsonObject)?.get("input_tokens")?.jsonPrimitive?.longOrNull
            ?: 0L
        if (inputTokens + outputTokens == 0L) return null

        val model = message["model"]?.jsonPrimitive?.contentOrNull
            ?: obj["model"]?.jsonPrimitive?.contentOrNull
        val messageId = message["messageId"]?.jsonPrimitive?.contentOrNull
            ?: obj["id"]?.jsonPrimitive?.contentOrNull
        val sessionId = obj["sessionId"]?.jsonPrimitive?.contentOrNull
            ?: file.parentFile?.parentFile?.name
            ?: file.nameWithoutExtension
        val tsString = obj["timestamp"]?.jsonPrimitive?.contentOrNull
        val ts = tsString?.let { runCatching { Instant.parse(it) }.getOrNull() }
            ?: Instant.fromEpochMilliseconds(file.lastModified())
        val dedupKey =
            "copilot:${file.absolutePath}:${messageId ?: ts.toEpochMilliseconds()}:$inputTokens:$outputTokens"
        return UsageRecord(
            harness = source,
            sessionId = sessionId,
            timestamp = ts,
            model = model,
            inputTokens = inputTokens,
            outputTokens = outputTokens,
            sourceFile = file.absolutePath,
            sourceOffset = offset,
            dedupKey = dedupKey,
        )
    }
}
