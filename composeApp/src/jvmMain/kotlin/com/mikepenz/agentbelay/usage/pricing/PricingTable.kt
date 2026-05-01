package com.mikepenz.agentbelay.usage.pricing

/**
 * Per-model cost tier in USD per token. All four fields are USD/token (not
 * USD/Mtok) so cost = tokens × tier directly. Cache-tier costs default to the
 * matching input cost when a model entry omits them — accurate for providers
 * that don't distinguish prompt-cache pricing.
 */
data class ModelPricing(
    val inputCostPerToken: Double,
    val outputCostPerToken: Double,
    val cacheWriteCostPerToken: Double,
    val cacheReadCostPerToken: Double,
)

/**
 * Lookup for [ModelPricing] keyed by (lowercase) model name.
 *
 * Mirrors codeburn's design: a single source of truth, refreshed periodically
 * from LiteLLM's `model_prices_and_context_window.json`. The bundled snapshot
 * in `resources/usage/litellm-snapshot.json` keeps us functional offline; a
 * runtime fetch overlays fresher data when available.
 */
class PricingTable(private val byModel: Map<String, ModelPricing>) {

    fun lookup(model: String?): ModelPricing? {
        if (model.isNullOrBlank()) return null
        val key = model.lowercase()
        byModel[key]?.let { return it }
        // Try canonical fallbacks (strip vendor prefix, version suffix).
        val stripped = key.substringAfter('/').substringBefore('@')
        byModel[stripped]?.let { return it }
        // Loose prefix match — pick the longest matching key. Useful for date-
        // suffixed model variants ("claude-sonnet-4-5-20250929" matches
        // "claude-sonnet-4-5").
        return byModel.entries
            .filter { (k, _) -> stripped.startsWith(k) || key.startsWith(k) }
            .maxByOrNull { (k, _) -> k.length }
            ?.value
    }

    val size: Int get() = byModel.size

    companion object {
        val EMPTY = PricingTable(emptyMap())
    }
}
