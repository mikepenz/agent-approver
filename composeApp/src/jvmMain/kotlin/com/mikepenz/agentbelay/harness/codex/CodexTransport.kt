package com.mikepenz.agentbelay.harness.codex

import com.mikepenz.agentbelay.harness.HarnessTransport
import com.mikepenz.agentbelay.harness.HookEvent

/**
 * Codex runs command hooks, so [com.mikepenz.agentbelay.hook.CodexBridgeInstaller]
 * installs small shell bridge scripts that POST stdin JSON to these routes.
 * Routes are namespaced with a `-codex` suffix to coexist with the other
 * harnesses on the same Ktor server.
 *
 * PostToolUse is mounted for correlation cleanup/observability, but
 * supportsOutputRedaction stays false until Codex output mutation is wired
 * through.
 */
class CodexTransport : HarnessTransport {
    override fun endpoints(): Map<HookEvent, String> = mapOf(
        HookEvent.PERMISSION_REQUEST to "/approve-codex",
        HookEvent.PRE_TOOL_USE to "/pre-tool-use-codex",
        HookEvent.POST_TOOL_USE to "/post-tool-use-codex",
    )
}
