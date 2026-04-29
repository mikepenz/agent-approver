package com.mikepenz.agentbelay.util

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

/**
 * Null-safe JsonElement accessors.
 *
 * Every direct call to `JsonElement.jsonPrimitive` / `.jsonArray` / `.jsonObject`
 * throws `IllegalArgumentException` when the element is a different shape than
 * expected (e.g. "Element class kotlinx.serialization.json.JsonArray is not a
 * JsonPrimitive"). `toolInput` maps come straight from agent payloads and can
 * contain any shape, so UI and summary code must never use those throwing
 * accessors directly.
 */

fun JsonElement?.asStringOrNull(): String? {
    if (this == null || this is JsonNull) return null
    val p = this as? JsonPrimitive ?: return null
    return p.content
}

fun JsonElement?.asArrayOrNull(): JsonArray? = this as? JsonArray

fun JsonElement?.asObjectOrNull(): JsonObject? = this as? JsonObject

fun Map<String, JsonElement>.string(key: String): String? = this[key].asStringOrNull()

fun Map<String, JsonElement>.array(key: String): JsonArray? = this[key].asArrayOrNull()

fun Map<String, JsonElement>.obj(key: String): JsonObject? = this[key].asObjectOrNull()

fun JsonArray.strings(): List<String> = mapNotNull { it.asStringOrNull() }

fun JsonElement?.asIntOrNull(): Int? {
    val p = this as? JsonPrimitive ?: return null
    return p.content.toIntOrNull()
}

fun JsonElement?.asBooleanOrNull(): Boolean? {
    val p = this as? JsonPrimitive ?: return null
    return when (p.content.lowercase()) {
        "true" -> true
        "false" -> false
        else -> null
    }
}
