package com.mikepenz.agentbelay.model

/**
 * Buckets used by the Statistics tab to group raw [Decision] values by who/what
 * resolved the request. The grouping intentionally separates manual user actions
 * from risk-analyzer auto-actions and from protection-engine auto-actions so the
 * UI can show "X% of approvals were manual vs Y% auto" without re-deriving it.
 */
enum class DecisionGroup {
    MANUAL_APPROVE,
    RISK_APPROVE,
    MANUAL_DENY,
    RISK_DENY,
    PROTECTION_BLOCK,
    PROTECTION_LOG,
    TIMEOUT,
    /**
     * Resolved outside of agent-belay — either the harness's TUI made the
     * decision before our user did (FIN detected on the in-flight hook
     * connection) or a Claude Code PostToolUse event arrived for a tool whose
     * permission request was still parked because of the upstream canUseTool
     * race. Distinct from [OTHER] so the Statistics tab can surface how often
     * we're being bypassed.
     */
    EXTERNAL,
    OTHER,
}

fun Decision.group(): DecisionGroup = when (this) {
    Decision.APPROVED, Decision.ALWAYS_ALLOWED, Decision.PROTECTION_OVERRIDDEN -> DecisionGroup.MANUAL_APPROVE
    Decision.AUTO_APPROVED -> DecisionGroup.RISK_APPROVE
    Decision.DENIED -> DecisionGroup.MANUAL_DENY
    Decision.AUTO_DENIED -> DecisionGroup.RISK_DENY
    Decision.PROTECTION_BLOCKED -> DecisionGroup.PROTECTION_BLOCK
    Decision.PROTECTION_LOGGED -> DecisionGroup.PROTECTION_LOG
    Decision.TIMEOUT -> DecisionGroup.TIMEOUT
    Decision.RESOLVED_EXTERNALLY -> DecisionGroup.EXTERNAL
    Decision.CANCELLED_BY_CLIENT -> DecisionGroup.OTHER
}

/** True for groups where time-to-decision is meaningful (a human or analyzer deliberated). */
val DecisionGroup.hasMeaningfulLatency: Boolean
    get() = this == DecisionGroup.MANUAL_APPROVE ||
        this == DecisionGroup.RISK_APPROVE ||
        this == DecisionGroup.MANUAL_DENY ||
        this == DecisionGroup.RISK_DENY

val DecisionGroup.isApproval: Boolean
    get() = this == DecisionGroup.MANUAL_APPROVE ||
        this == DecisionGroup.RISK_APPROVE

val DecisionGroup.isDenial: Boolean
    get() = this == DecisionGroup.MANUAL_DENY ||
        this == DecisionGroup.RISK_DENY ||
        this == DecisionGroup.PROTECTION_BLOCK
