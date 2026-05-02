package com.mikepenz.agentbelay.harness.copilot

import com.mikepenz.agentbelay.harness.HarnessTransport
import com.mikepenz.agentbelay.harness.HookEvent

/**
 * Copilot CLI's hooks fire shell scripts; those scripts `curl` Belay over
 * HTTP. The endpoints below are what the shim scripts target — they are
 * namespaced with a `-copilot` suffix to coexist with Claude Code's
 * routes on the same Ktor server.
 *
 * `POST_TOOL_USE` returns a flat `{modifiedResult, additionalContext}`
 * envelope — see [CopilotAdapter.buildPostToolUseRedactedResponse].
 */
class CopilotTransport : HarnessTransport {
    override fun endpoints(): Map<HookEvent, String> = mapOf(
        HookEvent.PERMISSION_REQUEST to "/approve-copilot",
        HookEvent.PRE_TOOL_USE to "/pre-tool-use-copilot",
        HookEvent.POST_TOOL_USE to "/post-tool-use-copilot",
    )
}
