package com.mikepenz.agentbelay.redaction

import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.boolean
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/**
 * Extracts redactable text from a tool's response payload, then rebuilds
 * the response with redacted text in the same shape. Claude Code rejects
 * `updatedToolOutput` if the schema does not match the original — this
 * mapper exists to enforce that contract.
 *
 * Supported tools (Belay-canonical names):
 *
 *  - **Bash** — `{stdout, stderr, interrupted, isImage}`. stdout + stderr
 *    are redactable strings; the boolean flags pass through unchanged.
 *  - **Read** — single string body (the file contents). Some Claude Code
 *    versions wrap it as `{type, file: {content}}`; both shapes handled.
 *  - **WebFetch** — string body (the fetched-and-summarised content).
 *
 * Tools not listed here return [Extraction.empty] and the route passes
 * the original output through unmodified — never risk corrupting a
 * shape we don't understand.
 */
internal object ToolOutputShape {

    /**
     * Set of Belay-canonical tool names this mapper knows how to redact.
     * Used by callers to skip the redaction pass entirely for unknown tools.
     */
    val supportedTools: Set<String> = setOf("Bash", "Read", "WebFetch")

    /**
     * One redactable region from a tool response — a key (the response
     * field name, used as `RedactionHit.field`) and the original text.
     */
    data class Region(val field: String, val text: String)

    data class Extraction(val regions: List<Region>) {
        companion object {
            val empty = Extraction(emptyList())
        }
    }

    fun extract(toolName: String, toolResponse: JsonElement?): Extraction {
        if (toolResponse !is JsonObject) return Extraction.empty
        return when (toolName) {
            "Bash" -> Extraction(
                listOfNotNull(
                    toolResponse.string("stdout")?.let { Region("stdout", it) },
                    toolResponse.string("stderr")?.let { Region("stderr", it) },
                )
            )
            "Read" -> {
                // Two known shapes:
                //   1. {"content": "..."} (older Claude Code response shape)
                //   2. {"type": "text", "file": {"content": "..."}} (newer wrapper)
                val direct = toolResponse.string("content")
                val nested = (toolResponse["file"] as? JsonObject)?.string("content")
                val content = direct ?: nested
                if (content != null) Extraction(listOf(Region("content", content))) else Extraction.empty
            }
            "WebFetch" -> {
                val content = toolResponse.string("content") ?: toolResponse.string("body")
                if (content != null) Extraction(listOf(Region("content", content))) else Extraction.empty
            }
            else -> Extraction.empty
        }
    }

    /**
     * Reconstructs the tool's response object with [redacted] regions
     * replacing the originals. The map's keys must match the [Region.field]
     * values returned by [extract].
     */
    fun rebuild(toolName: String, original: JsonElement, redacted: Map<String, String>): JsonObject {
        val obj = original as? JsonObject ?: return buildJsonObject {}
        return when (toolName) {
            "Bash" -> buildJsonObject {
                obj.forEach { (key, value) ->
                    when (key) {
                        "stdout", "stderr" -> {
                            val next = redacted[key]
                            put(key, if (next != null) JsonPrimitive(next) else value)
                        }
                        else -> put(key, value)
                    }
                }
            }
            "Read" -> buildJsonObject {
                obj.forEach { (key, value) ->
                    when {
                        key == "content" && redacted.containsKey("content") ->
                            put(key, JsonPrimitive(redacted["content"]!!))
                        key == "file" && value is JsonObject && redacted.containsKey("content") ->
                            put(key, buildJsonObject {
                                value.forEach { (innerKey, innerValue) ->
                                    if (innerKey == "content") put(innerKey, JsonPrimitive(redacted["content"]!!))
                                    else put(innerKey, innerValue)
                                }
                            })
                        else -> put(key, value)
                    }
                }
            }
            "WebFetch" -> buildJsonObject {
                obj.forEach { (key, value) ->
                    when (key) {
                        "content", "body" -> {
                            val next = redacted[key]
                            put(key, if (next != null) JsonPrimitive(next) else value)
                        }
                        else -> put(key, value)
                    }
                }
            }
            else -> obj
        }
    }

    private fun JsonObject.string(key: String): String? =
        (this[key] as? JsonPrimitive)?.contentOrNull
}
