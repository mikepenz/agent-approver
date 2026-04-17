package com.mikepenz.agentbuddy.storage

import co.touchlab.kermit.Logger
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.io.File
import java.nio.file.Files
import java.nio.file.StandardCopyOption

/**
 * One-shot migration from the old "Agent Approver" install layout to the new
 * "Agent Buddy" layout. Runs on every startup before the app opens its
 * database or installs hooks. Idempotent: a no-op when the legacy paths are
 * absent (fresh install, or migration already completed).
 *
 * Each step is wrapped in try/catch so a single failure (corrupt Claude
 * settings.json, locked keyring, etc.) can never abort app startup — we log
 * and move on.
 */
object LegacyDataMigration {

    private val logger = Logger.withTag("LegacyDataMigration")

    private val json = Json {
        ignoreUnknownKeys = true
        prettyPrint = true
        explicitNulls = false
    }

    private const val LEGACY_DB_FILE = "agent-approver.db"
    private const val NEW_DB_FILE = "agent-buddy.db"
    private const val LEGACY_HOOK_DIR_NAME = ".agent-approver"
    private const val NEW_HOOK_DIR_NAME = ".agent-buddy"
    private const val LEGACY_COPILOT_HOOK_FILE = "agent-approver.json"
    private const val NEW_COPILOT_HOOK_FILE = "agent-buddy.json"

    fun run(legacyDataDir: String, newDataDir: String) {
        val home = System.getProperty("user.home") ?: return
        val didAny = listOf(
            safe { migrateDataDir(legacyDataDir, newDataDir) },
            safe { migrateHookBridgeDir(home) },
            safe { migrateCopilotHookFile(home) },
            safe { migrateClaudeSettingsSessionStartPaths(home) },
        ).any { it }
        if (didAny) logger.i { "Migrated legacy Agent Approver state to Agent Buddy" }
    }

    /**
     * If the legacy app data dir exists and the new one does not, rename the
     * legacy dir in place. Also renames the legacy SQLite file inside.
     */
    internal fun migrateDataDir(legacyDataDir: String, newDataDir: String): Boolean {
        val legacy = File(legacyDataDir)
        val target = File(newDataDir)
        if (!legacy.isDirectory) return false
        if (target.exists()) return false

        target.parentFile?.mkdirs()
        Files.move(legacy.toPath(), target.toPath(), StandardCopyOption.ATOMIC_MOVE)
        logger.i { "Renamed data dir ${legacy.absolutePath} -> ${target.absolutePath}" }

        val legacyDb = File(target, LEGACY_DB_FILE)
        val newDb = File(target, NEW_DB_FILE)
        if (legacyDb.exists() && !newDb.exists()) {
            Files.move(legacyDb.toPath(), newDb.toPath(), StandardCopyOption.ATOMIC_MOVE)
            logger.i { "Renamed $LEGACY_DB_FILE -> $NEW_DB_FILE" }
        }
        return true
    }

    /**
     * Renames `~/.agent-approver/` (hook bridge scripts + logs) to
     * `~/.agent-buddy/`. If both exist, the new wins and the legacy is left
     * untouched so the user can manually inspect.
     */
    internal fun migrateHookBridgeDir(home: String): Boolean {
        val legacy = File(home, LEGACY_HOOK_DIR_NAME)
        val target = File(home, NEW_HOOK_DIR_NAME)
        if (!legacy.isDirectory) return false
        if (target.exists()) return false

        Files.move(legacy.toPath(), target.toPath(), StandardCopyOption.ATOMIC_MOVE)
        logger.i { "Renamed hook bridge dir ${legacy.absolutePath} -> ${target.absolutePath}" }

        // Rewrite absolute paths baked into moved bridge scripts so they no
        // longer point at the legacy dir.
        rewriteBridgeScripts(target, legacy.absolutePath, target.absolutePath)
        return true
    }

    private fun rewriteBridgeScripts(dir: File, oldPath: String, newPath: String) {
        val files = dir.listFiles { f -> f.isFile && f.name.endsWith(".sh") } ?: return
        for (f in files) {
            val content = f.readText()
            if (!content.contains(oldPath)) continue
            f.writeText(content.replace(oldPath, newPath))
        }
    }

    /**
     * Moves `~/.copilot/hooks/agent-approver.json` to `agent-buddy.json`,
     * rewriting any bash paths that still reference the legacy hook dir.
     */
    internal fun migrateCopilotHookFile(home: String): Boolean {
        val dir = File(home, ".copilot/hooks")
        val legacy = File(dir, LEGACY_COPILOT_HOOK_FILE)
        val target = File(dir, NEW_COPILOT_HOOK_FILE)
        if (!legacy.isFile) return false
        if (target.exists()) {
            // New file already in place; drop the legacy to avoid double
            // registration.
            legacy.delete()
            logger.i { "Removed redundant legacy Copilot hook file ${legacy.absolutePath}" }
            return true
        }

        val rewritten = legacy.readText()
            .replace("/$LEGACY_HOOK_DIR_NAME/", "/$NEW_HOOK_DIR_NAME/")
        target.writeText(rewritten)
        legacy.delete()
        logger.i { "Migrated Copilot hook file -> ${target.absolutePath}" }
        return true
    }

    /**
     * Claude's `~/.claude/settings.json` stores `SessionStart` command hooks
     * with absolute paths that may point at the legacy bridge dir. Rewrite
     * those paths so the hook continues to fire after the rebrand. HTTP hook
     * URLs contain no brand and are left untouched.
     */
    internal fun migrateClaudeSettingsSessionStartPaths(home: String): Boolean {
        val file = File(home, ".claude/settings.json")
        if (!file.isFile) return false

        val root: JsonObject = try {
            json.parseToJsonElement(file.readText()).jsonObject
        } catch (e: Exception) {
            logger.w(e) { "Could not parse ${file.absolutePath} during migration — skipping" }
            return false
        }

        val hooks = root["hooks"]?.jsonObject ?: return false
        val sessionStart = hooks["SessionStart"]?.jsonArray ?: return false

        var mutated = false
        val rewrittenEntries = sessionStart.map { entry ->
            val obj = entry.jsonObject
            val inner = obj["hooks"]?.jsonArray ?: return@map entry
            val newInner = inner.map { h ->
                val hObj = h.jsonObject
                val type = hObj["type"]?.jsonPrimitive?.content
                val cmd = hObj["command"]?.jsonPrimitive?.content
                if (type == "command" && cmd != null && cmd.contains("/$LEGACY_HOOK_DIR_NAME/")) {
                    mutated = true
                    val updated = cmd.replace("/$LEGACY_HOOK_DIR_NAME/", "/$NEW_HOOK_DIR_NAME/")
                    buildJsonObject {
                        hObj.forEach { (k, v) -> if (k != "command") put(k, v) }
                        put("command", JsonPrimitive(updated))
                    }
                } else h
            }
            buildJsonObject {
                obj.forEach { (k, v) -> if (k != "hooks") put(k, v) }
                put("hooks", JsonArray(newInner))
            }
        }

        if (!mutated) return false

        val updatedHooks = buildJsonObject {
            hooks.forEach { (k, v) -> if (k != "SessionStart") put(k, v) }
            put("SessionStart", JsonArray(rewrittenEntries))
        }
        val updatedRoot = buildJsonObject {
            root.forEach { (k, v) -> if (k != "hooks") put(k, v) }
            put("hooks", updatedHooks)
        }

        val tmp = File(file.parentFile, "${file.name}.tmp")
        tmp.writeText(json.encodeToString(JsonElement.serializer(), updatedRoot))
        Files.move(tmp.toPath(), file.toPath(), StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE)
        logger.i { "Rewrote Claude SessionStart command paths in ${file.absolutePath}" }
        return true
    }

    private inline fun safe(block: () -> Boolean): Boolean = try {
        block()
    } catch (e: Throwable) {
        logger.w(e) { "Migration step failed: ${e.message}" }
        false
    }
}
