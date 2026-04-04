package com.mikepenz.agentapprover.hook

import co.touchlab.kermit.Logger
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import java.io.File

private val logger = Logger.withTag("CopilotBridgeInstaller")

private val json = Json {
    ignoreUnknownKeys = true
    prettyPrint = true
}

object CopilotBridgeInstaller {

    private const val SCRIPT_NAME = "copilot-hook.sh"
    private const val DEFAULT_PORT = 19532

    private fun scriptDir(): File {
        val home = System.getProperty("user.home")
        return File(home, ".agent-approver")
    }

    private fun scriptFile(): File = File(scriptDir(), SCRIPT_NAME)

    private val SCRIPT_CONTENT = """
        #!/usr/bin/env bash
        # Agent Approver bridge script for GitHub Copilot CLI
        # Reads preToolUse JSON from stdin, POSTs to Agent Approver, returns response.
        # Fail-open: if server is unreachable, exits 0 so Copilot proceeds normally.

        PORT="${'$'}{AGENT_APPROVER_PORT:-$DEFAULT_PORT}"
        URL="http://localhost:${'$'}PORT/pre-tool-use-copilot"
        INPUT=$(cat)

        RESPONSE=$(echo "${'$'}INPUT" | curl -s --max-time 300 \
            -X POST \
            -H "Content-Type: application/json" \
            -d @- \
            "${'$'}URL" 2>/dev/null)

        if [ ${'$'}? -ne 0 ] || [ -z "${'$'}RESPONSE" ]; then
            # Server unreachable — fail open
            exit 0
        fi

        echo "${'$'}RESPONSE"
    """.trimIndent()

    private const val HOOK_BASH_PATH = "~/.agent-approver/$SCRIPT_NAME"

    fun isInstalled(): Boolean {
        val file = scriptFile()
        return file.exists() && file.canExecute()
    }

    fun install() {
        val dir = scriptDir()
        dir.mkdirs()
        val file = scriptFile()
        file.writeText(SCRIPT_CONTENT)
        file.setExecutable(true)
        logger.i { "Installed bridge script to ${file.absolutePath}" }
    }

    fun uninstall() {
        val file = scriptFile()
        if (file.exists()) {
            file.delete()
            logger.i { "Removed bridge script from ${file.absolutePath}" }
        }
    }

    private const val HOOK_EVENT_KEY = "preToolUse"

    fun isHookRegistered(projectPath: String): Boolean {
        val hooksFile = File(projectPath, ".github/hooks/hooks.json")
        if (!hooksFile.exists()) return false
        return try {
            val root = json.parseToJsonElement(hooksFile.readText()).jsonObject
            val hooks = root["hooks"]?.jsonObject ?: return false
            val preToolUse = hooks[HOOK_EVENT_KEY]?.jsonArray ?: return false
            preToolUse.any { entry ->
                val bash = entry.jsonObject["bash"]?.jsonPrimitive?.content
                val command = entry.jsonObject["command"]?.jsonPrimitive?.content
                bash == HOOK_BASH_PATH || command == HOOK_BASH_PATH
            }
        } catch (e: Exception) {
            logger.w(e) { "Failed to read hooks.json at $projectPath" }
            false
        }
    }

    fun registerHook(projectPath: String) {
        if (isHookRegistered(projectPath)) {
            logger.i { "Hook already registered in $projectPath" }
            return
        }

        val hooksFile = File(projectPath, ".github/hooks/hooks.json")

        val root: JsonObject = if (hooksFile.exists()) {
            try {
                json.parseToJsonElement(hooksFile.readText()).jsonObject
            } catch (e: Exception) {
                logger.w(e) { "Failed to parse hooks.json, starting fresh" }
                JsonObject(emptyMap())
            }
        } else {
            JsonObject(emptyMap())
        }

        val newEntry = buildJsonObject {
            put("type", "command")
            put("command", HOOK_BASH_PATH)
            put("bash", HOOK_BASH_PATH)
            put("timeout", 300)
            put("timeoutSec", 300)
            put("comment", "Agent Approver — approval workflow")
        }

        val existingHooks = root["hooks"]?.jsonObject ?: JsonObject(emptyMap())
        val existing = existingHooks[HOOK_EVENT_KEY]?.jsonArray?.toMutableList() ?: mutableListOf()
        val alreadyPresent = existing.any { entry ->
            val bash = entry.jsonObject["bash"]?.jsonPrimitive?.content
            val command = entry.jsonObject["command"]?.jsonPrimitive?.content
            bash == HOOK_BASH_PATH || command == HOOK_BASH_PATH
        }
        if (!alreadyPresent) existing.add(newEntry)

        val updatedHooks = buildJsonObject {
            existingHooks.forEach { (key, value) ->
                if (key != HOOK_EVENT_KEY) put(key, value)
            }
            put(HOOK_EVENT_KEY, buildJsonArray { existing.forEach { add(it) } })
        }

        val updatedRoot = buildJsonObject {
            root.forEach { (key, value) ->
                if (key != "hooks") put(key, value)
            }
            if (!root.containsKey("version")) put("version", 1)
            put("hooks", updatedHooks)
        }

        hooksFile.parentFile.mkdirs()
        hooksFile.writeText(json.encodeToString(JsonElement.serializer(), updatedRoot))
        logger.i { "Registered hook in $projectPath" }
    }

    fun unregisterHook(projectPath: String) {
        val hooksFile = File(projectPath, ".github/hooks/hooks.json")
        if (!hooksFile.exists()) return

        val root: JsonObject = try {
            json.parseToJsonElement(hooksFile.readText()).jsonObject
        } catch (e: Exception) {
            logger.w(e) { "Failed to parse hooks.json" }
            return
        }

        val existingHooks = root["hooks"]?.jsonObject ?: return

        val updatedHooks = buildJsonObject {
            existingHooks.forEach { (key, value) ->
                if (key == HOOK_EVENT_KEY) {
                    val filtered = value.jsonArray.filter { entry ->
                        val bash = entry.jsonObject["bash"]?.jsonPrimitive?.content
                        val command = entry.jsonObject["command"]?.jsonPrimitive?.content
                        bash != HOOK_BASH_PATH && command != HOOK_BASH_PATH
                    }
                    if (filtered.isNotEmpty()) {
                        put(key, buildJsonArray { filtered.forEach { add(it) } })
                    }
                } else {
                    put(key, value)
                }
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

        hooksFile.writeText(json.encodeToString(JsonElement.serializer(), updatedRoot))
        logger.i { "Unregistered hook from $projectPath" }
    }

    fun hooksJsonSnippet(): String = """
        {
          "version": 1,
          "hooks": {
            "preToolUse": [
              {
                "type": "command",
                "command": "$HOOK_BASH_PATH",
                "bash": "$HOOK_BASH_PATH",
                "timeout": 300,
                "timeoutSec": 300,
                "comment": "Agent Approver — approval workflow"
              }
            ]
          }
        }
    """.trimIndent()
}
