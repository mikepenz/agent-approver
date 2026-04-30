package com.mikepenz.agentbelay.harness

import com.mikepenz.agentbelay.model.ApprovalRequest
import com.mikepenz.agentbelay.model.Source

/**
 * Composes the per-axis abstractions ([HarnessRegistrar],
 * [HarnessTransport], [HarnessAdapter]) plus capability flags into a
 * single descriptor for one AI coding agent.
 *
 * Adding a new harness means creating four implementations (one per
 * axis) and tying them together here — the existing wiring in
 * `ApprovalServer`, route handlers, and Settings UI consume only this
 * interface so they need no changes.
 */
interface Harness {
    /**
     * Persisted identity. Reused as the [com.mikepenz.agentbelay.model.ApprovalRequest.source]
     * value, so its values are part of the on-disk history schema and
     * cannot be removed (only added) without breaking compatibility.
     */
    val source: Source

    val capabilities: HarnessCapabilities
    val registrar: HarnessRegistrar
    val transport: HarnessTransport
    val adapter: HarnessAdapter

    /**
     * Tools this harness fires that should be auto-allowed without ever
     * surfacing in the approval UI — typically non-actionable status /
     * intent tools the agent reports for telemetry only. Defaults to
     * empty; override per-harness when needed (Copilot CLI's
     * `report_intent` is the canonical example).
     */
    val autoAllowTools: Set<String> get() = emptySet()

    /**
     * Whether the approval HTTP handler should wait indefinitely for a
     * decision instead of falling back to [com.mikepenz.agentbelay.model.AppSettings.defaultTimeoutSeconds].
     *
     * Default: only when the user has Away Mode on. Claude Code overrides
     * to also wait forever on `Plan` / `AskUserQuestion` tool calls
     * because the agent itself is paused waiting for a structured user
     * input — a timeout there would resolve the wrong way.
     */
    fun shouldWaitIndefinitely(request: ApprovalRequest, awayMode: Boolean): Boolean = awayMode
}
