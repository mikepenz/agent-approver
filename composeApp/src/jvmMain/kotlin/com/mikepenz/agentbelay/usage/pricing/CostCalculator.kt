package com.mikepenz.agentbelay.usage.pricing

/**
 * Computes USD cost for a single assistant turn given the per-model tier.
 *
 * Reasoning tokens are billed as output tokens (matches OpenAI's billing model
 * for o-series / Codex). Cache-write costs use the dedicated tier when set;
 * otherwise they fall back to standard input cost.
 */
object CostCalculator {
    fun cost(
        pricing: ModelPricing,
        inputTokens: Long,
        outputTokens: Long,
        cacheReadTokens: Long = 0L,
        cacheWriteTokens: Long = 0L,
        reasoningTokens: Long = 0L,
    ): Double {
        val input = inputTokens.coerceAtLeast(0).toDouble() * pricing.inputCostPerToken
        val output = (outputTokens.coerceAtLeast(0) + reasoningTokens.coerceAtLeast(0))
            .toDouble() * pricing.outputCostPerToken
        val cacheRead = cacheReadTokens.coerceAtLeast(0).toDouble() * pricing.cacheReadCostPerToken
        val cacheWrite = cacheWriteTokens.coerceAtLeast(0).toDouble() * pricing.cacheWriteCostPerToken
        return input + output + cacheRead + cacheWrite
    }
}
