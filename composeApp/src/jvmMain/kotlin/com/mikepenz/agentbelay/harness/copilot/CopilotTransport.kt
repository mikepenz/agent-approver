package com.mikepenz.agentbelay.harness.copilot

import com.mikepenz.agentbelay.harness.HarnessTransport
import com.mikepenz.agentbelay.harness.HookEvent

/**
 * Copilot CLI's hooks fire shell scripts; those scripts `curl` Belay over
 * HTTP. The endpoints below are what the shim scripts target — they are
 * namespaced with a `-copilot` suffix to coexist with Claude Code's
 * routes on the same Ktor server.
 *
 * `POST_TOOL_USE` is intentionally absent: the GitHub docs note that
 * `postToolUse` cannot modify tool output, so Belay does not register a
 * post-tool hook for Copilot today.
 */
class CopilotTransport : HarnessTransport {
    override fun endpoints(): Map<HookEvent, String> = mapOf(
        HookEvent.PERMISSION_REQUEST to "/approve-copilot",
        HookEvent.PRE_TOOL_USE to "/pre-tool-use-copilot",
    )
}
