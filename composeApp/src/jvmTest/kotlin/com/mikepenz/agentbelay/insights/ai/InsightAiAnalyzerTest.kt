package com.mikepenz.agentbelay.insights.ai

import com.mikepenz.agentbelay.insights.EstimatedSavings
import com.mikepenz.agentbelay.insights.Insight
import com.mikepenz.agentbelay.insights.InsightFixtures
import com.mikepenz.agentbelay.insights.InsightKind
import com.mikepenz.agentbelay.insights.InsightSeverity
import com.mikepenz.agentbelay.model.HookInput
import com.mikepenz.agentbelay.model.RiskAnalysis
import com.mikepenz.agentbelay.model.Source
import com.mikepenz.agentbelay.risk.ActiveRiskAnalyzerHolder
import com.mikepenz.agentbelay.risk.RiskAnalyzer
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class InsightAiAnalyzerTest {

    /**
     * Captures the (systemPrompt, userPrompt) pair so the test can assert on
     * what the adapter sent. Returns a fixed canned response.
     */
    private class StubAnalyzer(private val canned: String) : RiskAnalyzer {
        var lastSystemPrompt: String? = null
        var lastUserPrompt: String? = null
        override suspend fun analyze(hookInput: HookInput): Result<RiskAnalysis> =
            error("not used in this test")
        override suspend fun analyzeText(systemPrompt: String, userPrompt: String): Result<String> {
            lastSystemPrompt = systemPrompt
            lastUserPrompt = userPrompt
            return Result.success(canned)
        }
    }

    private fun sampleInsight() = Insight(
        kind = InsightKind.COLD_CACHE_THRASH,
        severity = InsightSeverity.WARNING,
        title = "Prompt cache is missing — only 12% hit",
        description = "Healthy sessions cache 70-90%. Yours is 12%.",
        evidence = listOf("cache_read=1234", "input=8000"),
        savings = EstimatedSavings(tokens = 50_000, usd = 1.50),
        harness = Source.CLAUDE_CODE,
        sessionId = "sess-1",
        aiEligible = true,
    )

    private fun sampleSession() = InsightFixtures.session(
        turns = listOf(
            InsightFixtures.turn(input = 4_000, cacheRead = 200, output = 100, cost = 0.03),
            InsightFixtures.turn(input = 4_000, cacheRead = 300, output = 100, cost = 0.03),
        ),
    )

    @Test
    fun elevate_sends_system_and_user_prompt_and_parses_strict_response() = runBlocking {
        val stub = StubAnalyzer(
            """
            TITLE: Trim the volatile date stamp from CLAUDE.md
            BODY: Your CLAUDE.md starts with a "Today is …" line that invalidates the cache prefix on every turn. Move it under "## Context" or remove it; cache-read should jump back to ~80%.
            ACTION: rg -n '^Today is' ~/.claude/CLAUDE.md && remove that line
            """.trimIndent()
        )
        val holder = ActiveRiskAnalyzerHolder().also { it.set(stub) }
        val analyzer = InsightAiAnalyzer(holder)

        val result = analyzer.elevate(sampleInsight(), sampleSession()).getOrThrow()

        assertEquals("Trim the volatile date stamp from CLAUDE.md", result.title)
        assertTrue(result.body.contains("CLAUDE.md"))
        assertNotNull(result.action)
        assertTrue(result.action!!.startsWith("rg "))

        // Sent prompts:
        assertNotNull(stub.lastSystemPrompt)
        assertTrue(stub.lastSystemPrompt!!.contains("TITLE:"), "system prompt must mention the response format")
        assertTrue(stub.lastUserPrompt!!.contains("INSIGHT:"))
        assertTrue(stub.lastUserPrompt!!.contains("Prompt cache is missing"))
        // Bound on prompt size — we want the request small.
        assertTrue(stub.lastUserPrompt!!.length < 2_000, "user prompt should stay under 2k chars; was ${stub.lastUserPrompt!!.length}")
    }

    @Test
    fun elevate_falls_back_when_response_is_unstructured() = runBlocking {
        val stub = StubAnalyzer("Just a freeform paragraph the model wrote with no labels.")
        val holder = ActiveRiskAnalyzerHolder().also { it.set(stub) }
        val analyzer = InsightAiAnalyzer(holder)

        val result = analyzer.elevate(sampleInsight(), sampleSession()).getOrThrow()
        assertEquals("AI suggestion", result.title)
        assertTrue(result.body.contains("freeform paragraph"))
    }

    @Test
    fun elevate_fails_when_no_analyzer_is_set() = runBlocking {
        val analyzer = InsightAiAnalyzer(ActiveRiskAnalyzerHolder())
        val result = analyzer.elevate(sampleInsight(), sampleSession())
        assertTrue(result.isFailure)
    }

    @Test
    fun elevate_handles_action_none_as_null() = runBlocking {
        val stub = StubAnalyzer(
            """
            TITLE: Neat
            BODY: Looks fine to me.
            ACTION: none
            """.trimIndent()
        )
        val analyzer = InsightAiAnalyzer(ActiveRiskAnalyzerHolder().also { it.set(stub) })
        val result = analyzer.elevate(sampleInsight(), sampleSession()).getOrThrow()
        assertEquals(null, result.action)
    }
}
