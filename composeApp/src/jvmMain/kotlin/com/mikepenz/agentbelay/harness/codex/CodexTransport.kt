package com.mikepenz.agentbelay.harness.codex

import com.mikepenz.agentbelay.harness.HarnessTransport
import com.mikepenz.agentbelay.harness.HookEvent

/**
 * Codex's hooks crate POSTs to HTTP endpoints (`type = "http"` in the
 * TOML config). Routes are namespaced with a `-codex` suffix to coexist
 * with the other harnesses on the same Ktor server.
 *
 * `POST_TOOL_USE` is intentionally absent: PostToolUse output redaction
 * for Codex is gated on a future PR that generalises [PostToolUseRoute]
 * (currently Claude-only). The capability flag on [CodexHarness] mirrors
 * this — supportsOutputRedaction stays false until then.
 */
class CodexTransport : HarnessTransport {
    override fun endpoints(): Map<HookEvent, String> = mapOf(
        HookEvent.PERMISSION_REQUEST to "/approve-codex",
        HookEvent.PRE_TOOL_USE to "/pre-tool-use-codex",
    )
}
