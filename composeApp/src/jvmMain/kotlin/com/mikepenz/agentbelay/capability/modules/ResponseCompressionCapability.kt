package com.mikepenz.agentbelay.capability.modules

import com.mikepenz.agentbelay.capability.AgentTarget
import com.mikepenz.agentbelay.capability.CapabilityModule
import com.mikepenz.agentbelay.capability.HookEvent
import com.mikepenz.agentbelay.model.CapabilityModuleSettings
import com.mikepenz.agentbelay.model.CompressionIntensity

/**
 * Built-in token compression capability. On every user prompt, injects a
 * terse-response instruction into the agent's context. Three intensity
 * presets (Lite / Full / Ultra) control how aggressively the model should
 * compress its prose — code, paths, commands, and URLs are always preserved.
 */
object ResponseCompressionCapability : CapabilityModule {
    override val id = "response-compression"
    override val name = "Response Compression"
    override val description =
        "Cut output tokens by instructing the agent to respond tersely. Code, commands, paths, and URLs are preserved verbatim."

    override val supportedTargets: Set<AgentTarget> =
        setOf(AgentTarget.CLAUDE_CODE, AgentTarget.COPILOT_CLI)

    override val requiredHookEvents: Set<HookEvent> = setOf(HookEvent.USER_PROMPT_SUBMIT)

    override fun contextInjection(settings: CapabilityModuleSettings, event: HookEvent): String {
        if (event != HookEvent.USER_PROMPT_SUBMIT) return ""
        val intensity = settings.intensity ?: CompressionIntensity.FULL
        return buildInstruction(intensity)
    }

    fun buildInstruction(intensity: CompressionIntensity): String = buildString {
        append("[Response Compression — ")
        append(intensity.name.lowercase())
        append("] ")
        append(
            when (intensity) {
                CompressionIntensity.LITE -> LITE_BODY
                CompressionIntensity.FULL -> FULL_BODY
                CompressionIntensity.ULTRA -> ULTRA_BODY
            }
        )
        append("\n")
        append(GOLDEN_RULE)
    }

    private const val LITE_BODY =
        "Write professionally but cut filler. No hedging, no pleasantries, no restating the question. " +
            "Keep full sentences and normal grammar."

    private const val FULL_BODY =
        "Respond in fragments. Drop articles and filler. Use the pattern `[thing] [action] [reason].` " +
            "Stay dense: one idea, one line. No preamble, no summary, no self-reference."

    private const val ULTRA_BODY =
        "Telegraphic mode. Abbreviate aggressively. One idea per line, minimum words, no articles, " +
            "no conjunctions unless essential. Think commit-message length for every answer."

    internal const val GOLDEN_RULE =
        "Never compress code blocks, file paths, shell commands, URLs, version numbers, or error messages — " +
            "reproduce them exactly as you would normally."
}
