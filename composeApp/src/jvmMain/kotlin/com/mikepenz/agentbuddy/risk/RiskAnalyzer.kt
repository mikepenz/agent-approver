package com.mikepenz.agentbuddy.risk

import com.mikepenz.agentbuddy.model.HookInput
import com.mikepenz.agentbuddy.model.RiskAnalysis

interface RiskAnalyzer {
    suspend fun analyze(hookInput: HookInput): Result<RiskAnalysis>
    fun shutdown() {}
}
