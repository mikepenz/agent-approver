package com.mikepenz.agentbelay.harness

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
}
