package com.mikepenz.agentapprover.hook

import co.touchlab.kermit.Logger
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.encodeToJsonElement
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.put
import java.io.File

private val logger = Logger.withTag("HookRegistrar")

private val json = Json {
    ignoreUnknownKeys = true
    prettyPrint = true
    explicitNulls = false
}

@Serializable
private data class HookEntry(
    val matcher: String = "",
    val hooks: List<HookDef> = emptyList(),
)

@Serializable
private data class HookDef(
    val type: String,
    val url: String,
    val timeout: Int? = null,
)

object HookRegistrar {

    private fun settingsFile(): File {
        val home = System.getProperty("user.home")
        return File(home, ".claude/settings.json")
    }

    private fun hookUrl(port: Int): String = "http://localhost:$port/approve"

    private fun preToolUseUrl(port: Int): String = "http://localhost:$port/pre-tool-use"

    private fun postToolUseUrl(port: Int): String = "http://localhost:$port/post-tool-use"

    private fun userPromptSubmitUrl(port: Int): String = "http://localhost:$port/capability/inject"

    private fun hasHook(hooks: JsonObject, event: String, url: String): Boolean {
        val entries = hooks[event]?.jsonArray ?: return false
        return entries.any { entry ->
            val obj = entry.jsonObject
            val innerHooks = obj["hooks"]?.jsonArray ?: return@any false
            innerHooks.any { h ->
                val hObj = h.jsonObject
                hObj["type"].toString().trim('"') == "http" &&
                    hObj["url"].toString().trim('"') == url
            }
        }
    }

    fun isRegistered(port: Int): Boolean {
        val file = settingsFile()
        if (!file.exists()) return false
        return try {
            val root = json.parseToJsonElement(file.readText()).jsonObject
            val hooks = root["hooks"]?.jsonObject ?: return false
            hasHook(hooks, "PermissionRequest", hookUrl(port)) &&
                hasHook(hooks, "PreToolUse", preToolUseUrl(port)) &&
                hasHook(hooks, "PostToolUse", postToolUseUrl(port))
        } catch (e: Exception) {
            logger.w(e) { "Failed to read settings.json" }
            false
        }
    }

    fun register(port: Int) {
        val file = settingsFile()

        val root: JsonObject = if (file.exists()) {
            try {
                json.parseToJsonElement(file.readText()).jsonObject
            } catch (e: Exception) {
                logger.w(e) { "Failed to parse settings.json, starting fresh" }
                JsonObject(emptyMap())
            }
        } else {
            JsonObject(emptyMap())
        }

        val existingHooks = root["hooks"]?.jsonObject ?: JsonObject(emptyMap())

        val permUrl = hookUrl(port)
        val ptuUrl = preToolUseUrl(port)
        val postUrl = postToolUseUrl(port)

        val hasPermHook = hasHook(existingHooks, "PermissionRequest", permUrl)
        val hasPtuHook = hasHook(existingHooks, "PreToolUse", ptuUrl)
        val hasPostHook = hasHook(existingHooks, "PostToolUse", postUrl)

        if (hasPermHook && hasPtuHook && hasPostHook) {
            logger.i { "Hooks already registered for port $port" }
            return
        }

        // Build PermissionRequest array
        val permList = existingHooks["PermissionRequest"]?.jsonArray?.toMutableList() ?: mutableListOf()
        if (!hasPermHook) {
            permList.add(
                json.encodeToJsonElement(
                    HookEntry(matcher = "", hooks = listOf(HookDef(type = "http", url = permUrl)))
                )
            )
        }

        // Build PreToolUse array
        val ptuList = existingHooks["PreToolUse"]?.jsonArray?.toMutableList() ?: mutableListOf()
        if (!hasPtuHook) {
            ptuList.add(
                json.encodeToJsonElement(
                    HookEntry(matcher = "", hooks = listOf(HookDef(type = "http", url = ptuUrl, timeout = 120)))
                )
            )
        }

        // Build PostToolUse array — secondary correlation channel that lets
        // us clear pending entries whose original PermissionRequest hung
        // (canUseTool race in claude-code).
        val postList = existingHooks["PostToolUse"]?.jsonArray?.toMutableList() ?: mutableListOf()
        if (!hasPostHook) {
            postList.add(
                json.encodeToJsonElement(
                    HookEntry(matcher = "", hooks = listOf(HookDef(type = "http", url = postUrl, timeout = 5)))
                )
            )
        }

        val updatedHooks = buildJsonObject {
            existingHooks.forEach { (key, value) ->
                if (key != "PermissionRequest" && key != "PreToolUse" && key != "PostToolUse") put(key, value)
            }
            put("PermissionRequest", Json.encodeToJsonElement(permList))
            put("PreToolUse", Json.encodeToJsonElement(ptuList))
            put("PostToolUse", Json.encodeToJsonElement(postList))
        }

        val updatedRoot = buildJsonObject {
            root.forEach { (key, value) ->
                if (key != "hooks") put(key, value)
            }
            put("hooks", updatedHooks)
        }

        file.parentFile.mkdirs()
        file.writeText(json.encodeToString(JsonElement.serializer(), updatedRoot))
        logger.i { "Registered hooks for port $port" }
    }

    /**
     * Returns true iff a `UserPromptSubmit` hook entry pointing at our
     * capability injection endpoint already exists in the user's settings
     * file. Used by the Capabilities settings UI to show the right state.
     */
    fun isCapabilityHookRegistered(port: Int): Boolean {
        val file = settingsFile()
        if (!file.exists()) return false
        return try {
            val root = json.parseToJsonElement(file.readText()).jsonObject
            val hooks = root["hooks"]?.jsonObject ?: return false
            hasHook(hooks, "UserPromptSubmit", userPromptSubmitUrl(port))
        } catch (e: Exception) {
            logger.w(e) { "Failed to read settings.json" }
            false
        }
    }

    /**
     * Adds a `UserPromptSubmit` hook entry pointing at the capability
     * injection endpoint. Idempotent — safe to call on every toggle. Leaves
     * all other hook events (PermissionRequest / PreToolUse / PostToolUse)
     * untouched.
     */
    fun registerCapabilityHook(port: Int) {
        val file = settingsFile()

        val root: JsonObject = if (file.exists()) {
            try {
                json.parseToJsonElement(file.readText()).jsonObject
            } catch (e: Exception) {
                logger.w(e) { "Failed to parse settings.json, starting fresh" }
                JsonObject(emptyMap())
            }
        } else {
            JsonObject(emptyMap())
        }

        val existingHooks = root["hooks"]?.jsonObject ?: JsonObject(emptyMap())
        val upsUrl = userPromptSubmitUrl(port)
        if (hasHook(existingHooks, "UserPromptSubmit", upsUrl)) {
            logger.i { "Capability hook already registered for port $port" }
            return
        }

        val upsList = existingHooks["UserPromptSubmit"]?.jsonArray?.toMutableList() ?: mutableListOf()
        upsList.add(
            json.encodeToJsonElement(
                HookEntry(matcher = "", hooks = listOf(HookDef(type = "http", url = upsUrl, timeout = 10)))
            )
        )

        val updatedHooks = buildJsonObject {
            existingHooks.forEach { (key, value) ->
                if (key != "UserPromptSubmit") put(key, value)
            }
            put("UserPromptSubmit", Json.encodeToJsonElement(upsList))
        }

        val updatedRoot = buildJsonObject {
            root.forEach { (key, value) ->
                if (key != "hooks") put(key, value)
            }
            put("hooks", updatedHooks)
        }

        file.parentFile.mkdirs()
        file.writeText(json.encodeToString(JsonElement.serializer(), updatedRoot))
        logger.i { "Registered capability hook for port $port" }
    }

    /**
     * Removes our capability hook def from any `UserPromptSubmit` entry.
     * Operates at the inner hook-def level, not the entry level: if a user
     * has merged our hook alongside another tool's into a single entry, the
     * other tool's defs are preserved and only the entry is dropped when no
     * hooks remain. Other hook events (PermissionRequest / PreToolUse / …)
     * and top-level keys (env, etc.) are left alone.
     */
    fun unregisterCapabilityHook(port: Int) {
        val file = settingsFile()
        if (!file.exists()) return

        val root: JsonObject = try {
            json.parseToJsonElement(file.readText()).jsonObject
        } catch (e: Exception) {
            logger.w(e) { "Failed to parse settings.json" }
            return
        }

        val existingHooks = root["hooks"]?.jsonObject ?: return
        val upsUrl = userPromptSubmitUrl(port)
        val filtered = stripMatchingHookDefs(existingHooks["UserPromptSubmit"]?.jsonArray, upsUrl) ?: return

        val updatedHooks = buildJsonObject {
            existingHooks.forEach { (key, value) ->
                if (key != "UserPromptSubmit") put(key, value)
            }
            if (filtered.isNotEmpty()) {
                put("UserPromptSubmit", Json.encodeToJsonElement(filtered))
            }
        }

        val updatedRoot = buildJsonObject {
            root.forEach { (key, value) ->
                if (key != "hooks") put(key, value)
            }
            if (updatedHooks.isNotEmpty()) {
                put("hooks", updatedHooks)
            }
        }

        file.writeText(json.encodeToString(JsonElement.serializer(), updatedRoot))
        logger.i { "Unregistered capability hook for port $port" }
    }

    fun unregister(port: Int) {
        val file = settingsFile()
        if (!file.exists()) return

        val root: JsonObject = try {
            json.parseToJsonElement(file.readText()).jsonObject
        } catch (e: Exception) {
            logger.w(e) { "Failed to parse settings.json" }
            return
        }

        val existingHooks = root["hooks"]?.jsonObject ?: return

        fun filterHooks(event: String, url: String): List<JsonElement> =
            stripMatchingHookDefs(existingHooks[event]?.jsonArray, url) ?: emptyList()

        val filteredPerm = filterHooks("PermissionRequest", hookUrl(port))
        val filteredPtu = filterHooks("PreToolUse", preToolUseUrl(port))
        val filteredPost = filterHooks("PostToolUse", postToolUseUrl(port))
        val filteredUps = filterHooks("UserPromptSubmit", userPromptSubmitUrl(port))

        val updatedHooks = buildJsonObject {
            existingHooks.forEach { (key, value) ->
                if (key != "PermissionRequest" && key != "PreToolUse" && key != "PostToolUse" && key != "UserPromptSubmit") put(key, value)
            }
            if (filteredPerm.isNotEmpty()) {
                put("PermissionRequest", Json.encodeToJsonElement(filteredPerm))
            }
            if (filteredPtu.isNotEmpty()) {
                put("PreToolUse", Json.encodeToJsonElement(filteredPtu))
            }
            if (filteredPost.isNotEmpty()) {
                put("PostToolUse", Json.encodeToJsonElement(filteredPost))
            }
            if (filteredUps.isNotEmpty()) {
                put("UserPromptSubmit", Json.encodeToJsonElement(filteredUps))
            }
        }

        val updatedRoot = buildJsonObject {
            root.forEach { (key, value) ->
                if (key != "hooks") put(key, value)
            }
            if (updatedHooks.isNotEmpty()) {
                put("hooks", updatedHooks)
            }
        }

        file.writeText(json.encodeToString(JsonElement.serializer(), updatedRoot))
        logger.i { "Unregistered hooks for port $port" }
    }

    /**
     * For each entry in [entries], drops any inner hook def whose `type` is
     * `http` and `url` equals [url], preserving unrelated hook defs inside
     * the same entry. Entries that end up empty after the filter are
     * dropped entirely. Returns `null` when the caller's event key is
     * absent from the settings file (so the caller can short-circuit).
     */
    private fun stripMatchingHookDefs(entries: JsonArray?, url: String): List<JsonElement>? {
        if (entries == null) return null
        return entries.mapNotNull { entry ->
            val entryObject = entry.jsonObject
            val innerHooks = entryObject["hooks"]?.jsonArray ?: return@mapNotNull entry
            val remainingHooks = innerHooks.filterNot { h ->
                val hObj = h.jsonObject
                hObj["type"].toString().trim('"') == "http" &&
                    hObj["url"].toString().trim('"') == url
            }
            when {
                remainingHooks.isEmpty() -> null
                remainingHooks.size == innerHooks.size -> entry
                else -> buildJsonObject {
                    entryObject.forEach { (key, value) ->
                        if (key != "hooks") put(key, value)
                    }
                    put("hooks", Json.encodeToJsonElement(remainingHooks))
                }
            }
        }
    }
}
