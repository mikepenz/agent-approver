package com.mikepenz.agentbelay.usage.pricing

import co.touchlab.kermit.Logger
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.io.File
import java.net.HttpURLConnection
import java.net.URI
import java.net.URL

/**
 * Loads a [PricingTable] from three layers, in order:
 *
 *  1. Disk cache (`<dataDir>/litellm-snapshot.json`) when fresher than [TTL_MILLIS].
 *  2. Networked LiteLLM JSON, written through to the disk cache. 24h TTL.
 *  3. Bundled snapshot (`resources/usage/litellm-snapshot.json`) as the
 *     unconditional fallback. Always available, ships with the build.
 *
 * The networked path is best-effort: any failure (DNS, 5xx, parse error) is
 * swallowed and the next-best layer is used. Cost calculations remain
 * deterministic offline.
 */
class LiteLlmSource(
    private val cacheDir: String,
    private val now: () -> Long = { System.currentTimeMillis() },
    private val httpFetch: (String) -> String? = ::defaultHttpFetch,
) {
    private val logger = Logger.withTag("LiteLlmSource")
    private val json = Json { ignoreUnknownKeys = true; isLenient = true }

    fun load(): PricingTable {
        val cacheFile = File(cacheDir, CACHE_FILE_NAME)
        if (cacheFile.exists() && now() - cacheFile.lastModified() < TTL_MILLIS) {
            runCatching { parse(cacheFile.readText()) }
                .onSuccess { return it }
                .onFailure { logger.w { "Cache read failed: ${it.message}; falling through" } }
        }

        val networked = runCatching { httpFetch(LITELLM_URL) }.getOrNull()
        if (!networked.isNullOrBlank()) {
            runCatching { parse(networked) }
                .onSuccess { table ->
                    runCatching {
                        cacheFile.parentFile?.mkdirs()
                        cacheFile.writeText(networked)
                    }.onFailure { logger.w { "Cache write failed: ${it.message}" } }
                    return table
                }
                .onFailure { logger.w { "Network parse failed: ${it.message}" } }
        }

        // Reuse a stale cache rather than the bundled snapshot if we have one —
        // it's still likely fresher than what shipped with the binary.
        if (cacheFile.exists()) {
            runCatching { parse(cacheFile.readText()) }.getOrNull()?.let { return it }
        }
        return loadBundled()
    }

    fun loadBundled(): PricingTable {
        val stream = LiteLlmSource::class.java.getResourceAsStream("/usage/litellm-snapshot.json")
            ?: error("bundled litellm snapshot missing from resources/usage")
        val text = stream.bufferedReader().use { it.readText() }
        return parse(text)
    }

    /**
     * Parses the LiteLLM JSON into a [PricingTable]. Skips entries that don't
     * declare `input_cost_per_token` (e.g. `_metadata` blocks, embedding-only
     * models). Field names mirror LiteLLM's schema:
     *   - `input_cost_per_token`
     *   - `output_cost_per_token`
     *   - `cache_creation_input_token_cost`
     *   - `cache_read_input_token_cost`
     */
    fun parse(text: String): PricingTable {
        val root = json.parseToJsonElement(text).jsonObject
        val map = mutableMapOf<String, ModelPricing>()
        for ((modelName, element) in root) {
            if (modelName.startsWith("_")) continue
            val obj = element as? JsonObject ?: continue
            val input = obj["input_cost_per_token"]?.jsonPrimitive?.doubleOrNull
                ?: obj["input_cost_per_token_above_200k_tokens"]?.jsonPrimitive?.doubleOrNull
                ?: continue
            val output = obj["output_cost_per_token"]?.jsonPrimitive?.doubleOrNull ?: input * 4
            val cacheWrite = obj["cache_creation_input_token_cost"]?.jsonPrimitive?.doubleOrNull
                ?: input
            val cacheRead = obj["cache_read_input_token_cost"]?.jsonPrimitive?.doubleOrNull
                ?: input * 0.1
            map[modelName.lowercase()] = ModelPricing(
                inputCostPerToken = input,
                outputCostPerToken = output,
                cacheWriteCostPerToken = cacheWrite,
                cacheReadCostPerToken = cacheRead,
            )
        }
        return PricingTable(map)
    }

    companion object {
        const val LITELLM_URL =
            "https://raw.githubusercontent.com/BerriAI/litellm/main/model_prices_and_context_window.json"
        const val CACHE_FILE_NAME = "litellm-snapshot.json"
        const val TTL_MILLIS = 24L * 60L * 60L * 1000L

        private fun defaultHttpFetch(url: String): String? {
            val connection = (URI(url).toURL().openConnection() as HttpURLConnection).apply {
                connectTimeout = 8_000
                readTimeout = 12_000
                requestMethod = "GET"
            }
            return try {
                if (connection.responseCode in 200..299) {
                    connection.inputStream.bufferedReader().use { it.readText() }
                } else null
            } catch (_: Exception) {
                null
            } finally {
                connection.disconnect()
            }
        }
    }
}
