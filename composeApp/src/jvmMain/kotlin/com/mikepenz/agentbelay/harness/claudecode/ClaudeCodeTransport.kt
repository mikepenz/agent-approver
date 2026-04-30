package com.mikepenz.agentbelay.harness.claudecode

import com.mikepenz.agentbelay.harness.HarnessTransport
import com.mikepenz.agentbelay.harness.HookEvent

/**
 * Claude Code talks to Belay over direct HTTP — the harness's `type: "http"`
 * hook entries POST straight to these endpoints with no shim. Endpoint paths
 * match the legacy routing in `ApprovalServer` for backward compatibility.
 */
class ClaudeCodeTransport : HarnessTransport {
    override fun endpoints(): Map<HookEvent, String> = mapOf(
        HookEvent.PERMISSION_REQUEST to "/approve",
        HookEvent.PRE_TOOL_USE to "/pre-tool-use",
        HookEvent.POST_TOOL_USE to "/post-tool-use",
    )
}
