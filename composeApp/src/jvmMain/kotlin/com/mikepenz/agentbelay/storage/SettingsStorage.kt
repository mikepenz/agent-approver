package com.mikepenz.agentbelay.storage

import co.touchlab.kermit.Logger
import com.mikepenz.agentbelay.model.AppSettings
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.jsonObject
import java.io.File
import java.nio.file.Files
import java.nio.file.StandardCopyOption

class SettingsStorage(private val dataDir: String) {

    private val json = Json {
        ignoreUnknownKeys = true
        prettyPrint = true
        encodeDefaults = true
    }

    private val file get() = File(dataDir, "settings.json")

    fun load(): AppSettings {
        return try {
            val f = file
            if (!f.exists()) return AppSettings()
            val migrated = migrateLegacyAutoRiskFields(json.parseToJsonElement(f.readText()).jsonObject)
            json.decodeFromJsonElement(AppSettings.serializer(), migrated)
        } catch (e: Exception) {
            Logger.w("SettingsStorage") { "Failed to load settings, using defaults: ${e.message}" }
            AppSettings()
        }
    }

    private fun migrateLegacyAutoRiskFields(obj: JsonObject): JsonObject {
        val hasNewApprove = obj.containsKey("autoApproveLevel")
        val hasNewDeny = obj.containsKey("autoDenyLevel")
        if (hasNewApprove && hasNewDeny) return obj

        val patched = obj.toMutableMap()
        if (!hasNewApprove) {
            val legacy = (obj["autoApproveRisk1"] as? JsonPrimitive)?.booleanOrNull ?: false
            patched["autoApproveLevel"] = JsonPrimitive(if (legacy) 1 else 0)
        }
        if (!hasNewDeny) {
            val legacy = (obj["autoDenyRisk5"] as? JsonPrimitive)?.booleanOrNull ?: false
            patched["autoDenyLevel"] = JsonPrimitive(if (legacy) 5 else 0)
        }
        return JsonObject(patched)
    }

    fun save(settings: AppSettings) {
        try {
            val dir = File(dataDir)
            if (!dir.exists()) dir.mkdirs()
            val tmp = File(dataDir, "settings.json.tmp")
            tmp.writeText(json.encodeToString(AppSettings.serializer(), settings))
            Files.move(tmp.toPath(), file.toPath(), StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE)
        } catch (e: Exception) {
            Logger.e("SettingsStorage") { "Failed to save settings: ${e.message}" }
        }
    }
}
