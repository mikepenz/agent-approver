package com.mikepenz.agentbelay.risk

import com.mikepenz.agentbelay.model.HookInput
import com.mikepenz.agentbelay.model.RiskAnalysis

interface RiskAnalyzer {
    suspend fun analyze(hookInput: HookInput): Result<RiskAnalysis>
    fun shutdown() {}
}
