package com.mikepenz.agentbelay.harness.codex

import com.mikepenz.agentbelay.harness.Harness
import com.mikepenz.agentbelay.harness.HarnessAdapter
import com.mikepenz.agentbelay.harness.HarnessCapabilities
import com.mikepenz.agentbelay.harness.HarnessRegistrar
import com.mikepenz.agentbelay.harness.HarnessTransport
import com.mikepenz.agentbelay.model.Source

/**
 * Composition root for the OpenAI Codex CLI integration. Codex's hooks
 * crate is deliberately Claude-Code-compatible (matching event names,
 * matcher group config, `apply_patch` ↔ `Write`/`Edit` aliasing), so
 * this harness reuses Claude's envelope shape almost verbatim. The
 * delta lives in the registrar (TOML config under `~/.codex/config.toml`
 * via a managed-block read-modify-write) and the route namespace.
 *
 * Shipped as **experimental** — see Settings → Integrations. The
 * adapter is unverified against a live Codex install. PostToolUse events
 * are received for cleanup/observability, but output redaction stays off
 * until Codex output mutation is wired through.
 */
class CodexHarness(
    override val adapter: HarnessAdapter = CodexAdapter(),
    override val registrar: HarnessRegistrar = CodexRegistrar(),
    override val transport: HarnessTransport = CodexTransport(),
) : Harness {
    override val source: Source = Source.CODEX

    override val capabilities: HarnessCapabilities = HarnessCapabilities(
        // Codex's PermissionRequest mirrors Claude's `updatedInput`.
        supportsArgRewriting = true,
        // Codex doesn't surface a write-through "always-allow" persistence
        // primitive in its hooks crate today.
        supportsAlwaysAllowWriteThrough = false,
        // PostToolUse is mounted, but redaction output mutation is not.
        // Flip to true once the Codex output schema is wired through.
        supportsOutputRedaction = false,
        // No `defer` analogue.
        supportsDefer = false,
        // Deny on PreToolUse halts the tool call.
        supportsInterruptOnDeny = true,
        // SessionStart context injection unverified — leave off for v1.
        supportsAdditionalContextInjection = false,
    )
}
