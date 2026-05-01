package com.mikepenz.agentbelay.risk

import com.mikepenz.agentbelay.model.HookInput
import com.mikepenz.agentbelay.model.RiskAnalysis

interface RiskAnalyzer {
    suspend fun analyze(hookInput: HookInput): Result<RiskAnalysis>
    fun shutdown() {}

    /**
     * Free-form prompt → text completion path. Used by the Optimization
     * Insights feature to get a tailored suggestion from the same backend
     * the user already configured for risk analysis, without forcing the
     * caller through the [HookInput] shape (which is rigidly typed for
     * tool-call risk classification).
     *
     * Every shipped analyzer overrides this — Claude CLI, Copilot, Ollama,
     * and OpenAI-compat all support the generic prompt path against the
     * same connection / process they use for risk analysis.
     */
    suspend fun analyzeText(systemPrompt: String, userPrompt: String): Result<String>
}
