package com.mikepenz.agentapprover.capability.modules

import com.mikepenz.agentapprover.capability.AgentTarget
import com.mikepenz.agentapprover.capability.CapabilityModule
import com.mikepenz.agentapprover.capability.HookEvent
import com.mikepenz.agentapprover.model.CapabilityModuleSettings

/**
 * Built-in Socratic Thinking capability. Injected once at session start, it
 * forces the model to follow a 3-phase process: ask clarifying questions,
 * surface assumptions, and only then provide an answer — reducing guesswork
 * and improving response accuracy.
 */
object SocraticThinkingCapability : CapabilityModule {
    override val id = "socratic-thinking"
    override val name = "Socratic Thinking"
    override val description =
        "Inject a Socratic reasoning prompt at session start. " +
            "Forces the model to ask clarifying questions and surface assumptions before answering."

    override val supportedTargets: Set<AgentTarget> =
        setOf(AgentTarget.CLAUDE_CODE, AgentTarget.COPILOT_CLI)

    override val requiredHookEvents: Set<HookEvent> = setOf(HookEvent.SESSION_START)

    override fun contextInjection(settings: CapabilityModuleSettings, event: HookEvent): String {
        if (event != HookEvent.SESSION_START) return ""
        return PROMPT
    }

    internal const val PROMPT =
        "[Socratic Thinking] You are a Socratic analyst. Your first job is to remove ambiguity, not to answer.\n" +
            "\n" +
            "Phase 1 — Questions only:\n" +
            "Before providing any answer or recommendation, ask 2-4 clarifying questions. " +
            "Each question must be tied to a concrete decision the answer depends on " +
            "(scope, constraints, trade-offs, audience, risk tolerance). " +
            "Do not provide suggestions yet.\n" +
            "\n" +
            "Phase 2 — Assumptions check:\n" +
            "After the user responds, restate the problem in your own words and list the assumptions " +
            "you are making — only those supported by the user's replies. " +
            "If gaps remain, ask focused follow-up questions.\n" +
            "\n" +
            "Phase 3 — Answer:\n" +
            "Only when the problem is fully specified, provide your answer. " +
            "Include a brief \"why this is the right framing\" explanation " +
            "and one alternative framing that could change the recommendation.\n" +
            "\n" +
            "If the user explicitly asks you to skip questioning " +
            "(e.g. \"just answer\", \"skip to the answer\"), " +
            "proceed directly to Phase 3 with the information available."
}
