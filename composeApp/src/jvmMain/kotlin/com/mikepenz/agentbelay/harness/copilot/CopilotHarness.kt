package com.mikepenz.agentbelay.harness.copilot

import com.mikepenz.agentbelay.harness.Harness
import com.mikepenz.agentbelay.harness.HarnessAdapter
import com.mikepenz.agentbelay.harness.HarnessCapabilities
import com.mikepenz.agentbelay.harness.HarnessRegistrar
import com.mikepenz.agentbelay.harness.HarnessTransport
import com.mikepenz.agentbelay.model.Source
import com.mikepenz.agentbelay.server.CopilotAdapter

class CopilotHarness(
    override val adapter: HarnessAdapter = CopilotAdapter(),
    override val registrar: HarnessRegistrar = CopilotRegistrar(),
    override val transport: HarnessTransport = CopilotTransport(),
) : Harness {
    override val source: Source = Source.COPILOT

    override val capabilities: HarnessCapabilities = HarnessCapabilities(
        // Copilot CLI v1.0.22+ honors `modifiedArgs` on permissionRequest allow.
        supportsArgRewriting = true,
        // Copilot has no permission write-through equivalent; trust patterns
        // are managed via Copilot's own rules file, not via the hook envelope.
        supportsAlwaysAllowWriteThrough = false,
        // Copilot's `postToolUse` does not allow modifying the result.
        supportsOutputRedaction = false,
        // No `defer` analogue.
        supportsDefer = false,
        // permissionRequest deny supports `interrupt: true` to abort the call.
        supportsInterruptOnDeny = true,
        // sessionStart `additionalContext` is supported (used by CapabilityEngine).
        supportsAdditionalContextInjection = true,
    )

    /**
     * `report_intent` is Copilot's status-tool: the agent fires it
     * purely to declare what it intends to do next. Auto-allowing
     * spares the user a UI prompt for a non-actionable call.
     */
    override val autoAllowTools: Set<String> = setOf("report_intent")
}
