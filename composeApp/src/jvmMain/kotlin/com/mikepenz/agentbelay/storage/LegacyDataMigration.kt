package com.mikepenz.agentbelay.storage

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
 * One-shot migration from a previous brand's install layout to the current
 * "Agent Belay" layout. Runs on every startup before the app opens its
 * database or installs hooks. Idempotent: a no-op when the legacy paths are
 * absent (fresh install, or migration already completed).
 *
 * The app has been renamed twice — Agent Approver → Agent Buddy → Agent Belay
 * — so [Main] invokes [run] once per legacy step in chronological order.
 *
 * Each step is wrapped in try/catch so a single failure (corrupt Claude
 * settings.json, locked keyring, etc.) can never abort app startup — we log
 * and move on.
 */
object LegacyDataMigration {

    /**
     * Filenames and bridge-dir names that change when the app is renamed.
     * `home`-relative paths (data dir, hidden hook dir) are rooted by [run]'s
     * arguments rather than the user's `$HOME` so tests can fake it.
     */
    data class Step(
        val legacyDataDir: String,
        val newDataDir: String,
        val legacyDbFile: String,
        val newDbFile: String,
        val legacyHookDirName: String,
        val newHookDirName: String,
        val legacyCopilotHookFile: String,
        val newCopilotHookFile: String,
    )

    private val logger = Logger.withTag("LegacyDataMigration")

    private val json = Json {
        ignoreUnknownKeys = true
        prettyPrint = true
        explicitNulls = false
    }

    /**
     * Returns `true` iff at least one step actually moved files. Callers use
     * this signal to schedule an [IntegrationRefresh] pass that re-runs the
     * registrars against the migrated paths so the bridge scripts come from
     * the current build's templates rather than the stale rewritten copies.
     */
    fun run(step: Step): Boolean {
        val home = System.getProperty("user.home") ?: return false
        val didAny = listOf(
            safe { migrateDataDir(step) },
            safe { migrateHookBridgeDir(home, step) },
            safe { migrateCopilotHookFile(home, step) },
            safe { migrateClaudeSettingsSessionStartPaths(home, step) },
        ).any { it }
        if (didAny) {
            logger.i { "Migrated legacy data (${step.legacyHookDirName} -> ${step.newHookDirName})" }
        }
        return didAny
    }

    /**
     * If the legacy app data dir exists and the new one does not, rename the
     * legacy dir in place. Also renames the legacy SQLite file inside.
     */
    internal fun migrateDataDir(step: Step): Boolean {
        val legacy = File(step.legacyDataDir)
        val target = File(step.newDataDir)
        if (!legacy.isDirectory) return false
        if (target.exists()) return false

        target.parentFile?.mkdirs()
        Files.move(legacy.toPath(), target.toPath(), StandardCopyOption.ATOMIC_MOVE)
        logger.i { "Renamed data dir ${legacy.absolutePath} -> ${target.absolutePath}" }

        val legacyDb = File(target, step.legacyDbFile)
        val newDb = File(target, step.newDbFile)
        if (legacyDb.exists() && !newDb.exists()) {
            Files.move(legacyDb.toPath(), newDb.toPath(), StandardCopyOption.ATOMIC_MOVE)
            logger.i { "Renamed ${step.legacyDbFile} -> ${step.newDbFile}" }
        }
        return true
    }

    /**
     * Renames the legacy hook bridge dir (e.g. `~/.agent-buddy/`) to the new
     * one (e.g. `~/.agent-belay/`). If both exist, the new wins and the
     * legacy is left untouched so the user can manually inspect.
     */
    internal fun migrateHookBridgeDir(home: String, step: Step): Boolean {
        val legacy = File(home, step.legacyHookDirName)
        val target = File(home, step.newHookDirName)
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
     * Moves the legacy Copilot hook file (e.g. `agent-buddy.json`) to the new
     * filename (e.g. `agent-belay.json`), rewriting any bash paths that still
     * reference the legacy hook dir.
     */
    internal fun migrateCopilotHookFile(home: String, step: Step): Boolean {
        val dir = File(home, ".copilot/hooks")
        val legacy = File(dir, step.legacyCopilotHookFile)
        val target = File(dir, step.newCopilotHookFile)
        if (!legacy.isFile) return false
        if (target.exists()) {
            // New file already in place; drop the legacy to avoid double
            // registration.
            legacy.delete()
            logger.i { "Removed redundant legacy Copilot hook file ${legacy.absolutePath}" }
            return true
        }

        val rewritten = legacy.readText()
            .replace("/${step.legacyHookDirName}/", "/${step.newHookDirName}/")
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
    internal fun migrateClaudeSettingsSessionStartPaths(home: String, step: Step): Boolean {
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
                if (type == "command" && cmd != null && cmd.contains("/${step.legacyHookDirName}/")) {
                    mutated = true
                    val updated = cmd.replace("/${step.legacyHookDirName}/", "/${step.newHookDirName}/")
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
