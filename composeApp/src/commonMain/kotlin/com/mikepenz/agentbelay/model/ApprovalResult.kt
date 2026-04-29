package com.mikepenz.agentbelay.model

import kotlinx.datetime.Instant
import kotlinx.serialization.Serializable

@Serializable
data class ApprovalResult(
    val request: ApprovalRequest,
    val decision: Decision,
    val feedback: String? = null,
    val riskAnalysis: RiskAnalysis? = null,
    val rawResponseJson: String? = null,
    val decidedAt: Instant,
    val protectionModule: String? = null,
    val protectionRule: String? = null,
    val protectionDetail: String? = null,
)

@Serializable
enum class Decision {
    APPROVED, DENIED, TIMEOUT, AUTO_APPROVED, AUTO_DENIED, ALWAYS_ALLOWED, CANCELLED_BY_CLIENT, RESOLVED_EXTERNALLY, PROTECTION_BLOCKED, PROTECTION_LOGGED, PROTECTION_OVERRIDDEN
}

@Serializable
data class RiskAnalysis(
    val risk: Int,
    val label: String = "",
    val message: String,
    val source: String = "",
    /**
     * Raw text returned by the validation LLM (Ollama JSON envelope, Claude CLI
     * stdout, Copilot stdout). Useful for diagnosing why the parsed result is
     * wrong — e.g. the model emitted prose instead of structured JSON. Null when
     * the analyzer doesn't capture it (legacy entries) or when capture fails.
     * Truncated at the analyzer to keep storage and UI responsive.
     */
    val rawResponse: String? = null,
)
