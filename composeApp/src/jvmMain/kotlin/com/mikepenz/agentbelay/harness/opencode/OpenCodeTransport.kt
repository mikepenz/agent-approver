package com.mikepenz.agentbelay.harness.opencode

import com.mikepenz.agentbelay.harness.HarnessTransport
import com.mikepenz.agentbelay.harness.HookEvent

/**
 * OpenCode's plugin runs inside the OpenCode process and POSTs to Belay's
 * Ktor server. Endpoints are namespaced with an `-opencode` suffix to
 * coexist with the other harnesses' routes on the same server.
 *
 * `POST_TOOL_USE` is intentionally absent: OpenCode's plugin pipeline does
 * not expose a result-mutation hook, so Belay does not register a
 * post-tool route for OpenCode today.
 */
class OpenCodeTransport : HarnessTransport {
    override fun endpoints(): Map<HookEvent, String> = mapOf(
        HookEvent.PERMISSION_REQUEST to "/approve-opencode",
        HookEvent.PRE_TOOL_USE to "/pre-tool-use-opencode",
    )
}
