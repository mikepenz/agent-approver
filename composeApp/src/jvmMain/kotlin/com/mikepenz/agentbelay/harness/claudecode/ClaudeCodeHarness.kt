package com.mikepenz.agentbelay.harness.claudecode

import com.mikepenz.agentbelay.harness.Harness
import com.mikepenz.agentbelay.harness.HarnessAdapter
import com.mikepenz.agentbelay.harness.HarnessCapabilities
import com.mikepenz.agentbelay.harness.HarnessRegistrar
import com.mikepenz.agentbelay.harness.HarnessTransport
import com.mikepenz.agentbelay.model.ApprovalRequest
import com.mikepenz.agentbelay.model.Source
import com.mikepenz.agentbelay.model.ToolType
import com.mikepenz.agentbelay.server.ClaudeCodeAdapter

/**
 * Composition root for the Claude Code integration. Phase 2 will follow
 * the same pattern (Codex/Cline/Cursor/Gemini/OpenCode each own a
 * sibling `*Harness.kt`).
 */
class ClaudeCodeHarness(
    override val adapter: HarnessAdapter = ClaudeCodeAdapter(),
    override val registrar: HarnessRegistrar = ClaudeCodeRegistrar(),
    override val transport: HarnessTransport = ClaudeCodeTransport(),
) : Harness {
    override val source: Source = Source.CLAUDE_CODE

    override val capabilities: HarnessCapabilities = HarnessCapabilities(
        // PermissionRequest decision.updatedInput, PreToolUse hookSpecificOutput.updatedInput
        supportsArgRewriting = true,
        // PermissionRequest decision.updatedPermissions (write-through to ~/.claude/settings.json)
        supportsAlwaysAllowWriteThrough = true,
        // PostToolUse hookSpecificOutput.updatedToolOutput (v2.1.121+, generalised to all tools)
        supportsOutputRedaction = true,
        // PreToolUse permissionDecision: "defer" (v2.1.89+) — not yet wired in routes
        supportsDefer = false,
        // PermissionRequest decision.interrupt: true halts the agent
        supportsInterruptOnDeny = true,
        // SessionStart / UserPromptSubmit additionalContext (already used by CapabilityEngine)
        supportsAdditionalContextInjection = true,
    )

    /**
     * Plan and AskUserQuestion are agent-paused tools — Claude is
     * waiting for the user to make a structured choice, not just to
     * approve the call. Timing them out resolves the wrong way (denies
     * the agent's plan submission), so we wait forever even when Away
     * Mode is off.
     */
    override fun shouldWaitIndefinitely(request: ApprovalRequest, awayMode: Boolean): Boolean =
        awayMode ||
            request.toolType == ToolType.PLAN ||
            request.toolType == ToolType.ASK_USER_QUESTION
}
