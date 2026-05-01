package com.mikepenz.agentbelay.usage

import com.mikepenz.agentbelay.usage.pricing.CostCalculator
import com.mikepenz.agentbelay.usage.pricing.LiteLlmSource
import java.io.File
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class CostCalculatorTest {

    @Test
    fun computes_cost_for_claude_sonnet_45_against_bundled_snapshot() {
        val table = LiteLlmSource(File(System.getProperty("java.io.tmpdir")).absolutePath).loadBundled()
        val tier = table.lookup("claude-sonnet-4-5")
        assertNotNull(tier, "bundled snapshot must contain claude-sonnet-4-5")

        // 1M input tokens, 100k output, 500k cache-read.
        val cost = CostCalculator.cost(
            pricing = tier,
            inputTokens = 1_000_000,
            outputTokens = 100_000,
            cacheReadTokens = 500_000,
        )
        // Expected: 1M * 3e-6 + 100k * 1.5e-5 + 500k * 3e-7 = 3.00 + 1.50 + 0.15 = 4.65
        assertTrue(abs(cost - 4.65) < 0.01, "cost=$cost expected ≈ 4.65")
    }

    @Test
    fun pricing_lookup_handles_dated_model_suffix() {
        val table = LiteLlmSource(File(System.getProperty("java.io.tmpdir")).absolutePath).loadBundled()
        val tier = table.lookup("claude-sonnet-4-5-20250929")
        assertNotNull(tier, "longest-prefix lookup must find claude-sonnet-4-5")
    }

    @Test
    fun parser_skips_metadata_blocks() {
        val source = LiteLlmSource(File(System.getProperty("java.io.tmpdir")).absolutePath)
        val parsed = source.parse(
            """{"_metadata":{"a":"b"},"foo-model":{"input_cost_per_token":1e-6,"output_cost_per_token":2e-6}}"""
        )
        assertNotNull(parsed.lookup("foo-model"))
        assertEquals(1, parsed.size)
    }
}
