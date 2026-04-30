package com.mikepenz.agentbelay.harness.opencode

import com.mikepenz.agentbelay.harness.Harness
import com.mikepenz.agentbelay.harness.HarnessAdapter
import com.mikepenz.agentbelay.harness.HarnessCapabilities
import com.mikepenz.agentbelay.harness.HarnessRegistrar
import com.mikepenz.agentbelay.harness.HarnessTransport
import com.mikepenz.agentbelay.model.Source
import com.mikepenz.agentbelay.server.OpenCodeAdapter

/**
 * Composition root for the OpenCode integration. OpenCode is the first
 * harness Belay reaches via an in-process plugin (a TypeScript file under
 * `~/.config/opencode/plugin/`) rather than direct HTTP or shim scripts —
 * the registrar emits that file and OpenCode's plugin runtime loads it on
 * startup. The plugin then `fetch`es Belay's HTTP server, so the data path
 * still terminates at Ktor like the other harnesses.
 */
class OpenCodeHarness(
    override val adapter: HarnessAdapter = OpenCodeAdapter(),
    override val registrar: HarnessRegistrar = OpenCodeRegistrar(),
    override val transport: HarnessTransport = OpenCodeTransport(),
) : Harness {
    override val source: Source = Source.OPENCODE

    override val capabilities: HarnessCapabilities = HarnessCapabilities(
        // The plugin's `tool.execute.before` hook only inspects `behavior`
        // — there is no documented arg-rewriting analogue today.
        supportsArgRewriting = false,
        // No write-through equivalent in OpenCode's plugin envelope.
        supportsAlwaysAllowWriteThrough = false,
        // OpenCode's plugin pipeline does not surface a result-mutation
        // hook, so post-tool redaction can't be applied here.
        supportsOutputRedaction = false,
        // No `defer` analogue.
        supportsDefer = false,
        // The plugin throws on deny, which aborts the tool call.
        supportsInterruptOnDeny = true,
        // Capability injection runs via `session.created` / `session.updated`
        // events fetching `/capability/inject-opencode`.
        supportsAdditionalContextInjection = true,
    )
}
