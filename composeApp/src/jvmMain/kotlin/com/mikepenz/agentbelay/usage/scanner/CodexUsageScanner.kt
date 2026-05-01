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
 * Codex (OpenAI's) writes JSONL "rollouts" under
 * `~/.codex/sessions/<YYYY>/<MM>/<DD>/rollout-*.jsonl`. Token usage rides on
 * `event_msg` records of `type: "token_count"`. The interesting payload is
 * `payload.info.last_token_usage` (per-turn) — `total_token_usage` is
 * cumulative and would double-count.
 *
 * OpenAI semantics put cached tokens *inside* the input total; we normalize to
 * Anthropic-style by subtracting cached from input and routing them to
 * [UsageRecord.cacheReadTokens].
 */
class CodexUsageScanner(
    private val rootOverride: File? = null,
) : UsageScanner {
    override val source: Source = Source.CODEX

    override fun scan(cursors: Map<String, ScanCursor>): List<UsageRecord> {
        val root = rootOverride ?: File(ScannerSupport.homeDir(), ".codex/sessions")
        val files = ScannerSupport.listFiles(root) {
            it.extension == "jsonl" && it.name.startsWith("rollout-")
        }
        val out = mutableListOf<UsageRecord>()
        for (file in files) {
            val cursor = cursors[file.absolutePath]
            if (cursor != null &&
                cursor.lastOffset >= file.length() &&
                cursor.lastMtimeMillis >= file.lastModified()
            ) continue
            // The rollout files often only carry the model name once at the
            // top — remember the most recent one we've seen for this file so
            // later turn entries inherit it.
            var lastModel: String? = null
            var lastSession: String? = null
            for ((offset, line) in ScannerSupport.readNewLines(file, cursor)) {
                if (line.isBlank()) continue
                runCatching {
                    val obj = ScannerSupport.json.parseToJsonElement(line).jsonObject
                    extractModel(obj)?.let { lastModel = it }
                    extractSession(obj)?.let { lastSession = it }
                    parseTokenCount(file, offset, obj, lastModel, lastSession)
                }.getOrNull()?.let(out::add)
            }
        }
        return out
    }

    private fun extractModel(obj: JsonObject): String? {
        val payload = obj["payload"] as? JsonObject
        payload?.get("model")?.jsonPrimitive?.contentOrNull?.let { return it }
        val info = payload?.get("info") as? JsonObject
        info?.get("model")?.jsonPrimitive?.contentOrNull?.let { return it }
        return null
    }

    private fun extractSession(obj: JsonObject): String? {
        val payload = obj["payload"] as? JsonObject
        payload?.get("session_id")?.jsonPrimitive?.contentOrNull?.let { return it }
        payload?.get("conversation_id")?.jsonPrimitive?.contentOrNull?.let { return it }
        return null
    }

    private fun parseTokenCount(
        file: File,
        offset: Long,
        obj: JsonObject,
        modelHint: String?,
        sessionHint: String?,
    ): UsageRecord? {
        val type = obj["type"]?.jsonPrimitive?.contentOrNull ?: return null
        if (type != "event_msg" && type != "token_count") return null
        val payload = obj["payload"] as? JsonObject ?: return null
        val payloadType = payload["type"]?.jsonPrimitive?.contentOrNull
        if (payloadType != "token_count" && type == "event_msg") return null
        val info = payload["info"] as? JsonObject ?: payload
        val turn = (info["last_token_usage"] as? JsonObject) ?: (info["total_token_usage"] as? JsonObject) ?: return null

        val rawInput = turn["input_tokens"]?.jsonPrimitive?.longOrNull ?: 0L
        val cached = turn["cached_input_tokens"]?.jsonPrimitive?.longOrNull ?: 0L
        val output = turn["output_tokens"]?.jsonPrimitive?.longOrNull ?: 0L
        val reasoning = turn["reasoning_output_tokens"]?.jsonPrimitive?.longOrNull ?: 0L
        if (rawInput + cached + output + reasoning == 0L) return null

        // Anthropic-style normalization.
        val uncachedInput = (rawInput - cached).coerceAtLeast(0)

        val tsString = obj["timestamp"]?.jsonPrimitive?.contentOrNull
        val ts = tsString?.let { runCatching { Instant.parse(it) }.getOrNull() }
            ?: Instant.fromEpochMilliseconds(file.lastModified())
        val sessionId = sessionHint ?: file.nameWithoutExtension
        val dedupKey = "codex:${file.absolutePath}:${ts.toEpochMilliseconds()}:$uncachedInput:$output:$cached"
        return UsageRecord(
            harness = source,
            sessionId = sessionId,
            timestamp = ts,
            model = modelHint,
            inputTokens = uncachedInput,
            outputTokens = output,
            cacheReadTokens = cached,
            cacheWriteTokens = 0L,
            reasoningTokens = reasoning,
            sourceFile = file.absolutePath,
            sourceOffset = offset,
            dedupKey = dedupKey,
        )
    }
}
