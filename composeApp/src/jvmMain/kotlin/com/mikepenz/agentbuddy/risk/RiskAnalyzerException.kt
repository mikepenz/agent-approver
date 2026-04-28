package com.mikepenz.agentbuddy.risk

/**
 * Thrown by analyzers when a request reaches the model but the response cannot
 * be parsed (or the daemon returned a non-success status). Carries the raw
 * payload so [com.mikepenz.agentbuddy.ui.approvals.ApprovalsViewModel] can
 * attach it to the synthetic error [com.mikepenz.agentbuddy.model.RiskAnalysis]
 * persisted to history — that's the case where seeing the raw model output is
 * most valuable, since the parser said it was malformed.
 *
 * Connection-level failures (daemon unreachable, socket timeout) intentionally
 * do not use this type — there is no payload to carry, and the message alone
 * already explains the failure.
 */
class RiskAnalyzerException(
    message: String,
    val rawResponse: String? = null,
    cause: Throwable? = null,
) : RuntimeException(message, cause)
