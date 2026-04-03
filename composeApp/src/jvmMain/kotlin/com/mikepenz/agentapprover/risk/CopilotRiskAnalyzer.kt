package com.mikepenz.agentapprover.risk

import co.touchlab.kermit.Logger
import com.github.copilot.sdk.CopilotClient
import com.github.copilot.sdk.SystemMessageMode
import com.github.copilot.sdk.json.MessageOptions
import com.github.copilot.sdk.json.SessionConfig
import com.github.copilot.sdk.json.SystemMessageConfig
import com.mikepenz.agentapprover.model.HookInput
import com.mikepenz.agentapprover.model.RiskAnalysis
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.future.await
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

class CopilotRiskAnalyzer(
    model: String = "gpt-4.1-mini",
    customSystemPrompt: String = "",
) : RiskAnalyzer {
    private val log = Logger.withTag("CopilotRiskAnalyzer")
    var model: String = model
    var systemPrompt: String = customSystemPrompt.ifBlank {
        RiskMessageBuilder.DEFAULT_SYSTEM_PROMPT + "\n\n" + JSON_FORMAT_INSTRUCTION
    }

    private val json = Json { ignoreUnknownKeys = true }
    private var client: CopilotClient? = null

    fun start() {
        log.i { "Starting CopilotClient" }
        val c = CopilotClient()
        client = c
        c.start()
        log.d { "CopilotClient started" }
    }

    override suspend fun analyze(hookInput: HookInput): Result<RiskAnalysis> = withContext(Dispatchers.IO) {
        try {
            withTimeout(TIMEOUT_MS) {
                val c = client ?: run {
                    log.w { "CopilotClient not started, starting now" }
                    val newClient = CopilotClient()
                    client = newClient
                    newClient.start()
                    newClient
                }

                val userMessage = RiskMessageBuilder.buildUserMessage(hookInput)
                log.i { "Analyzing ${hookInput.toolName} with model=$model" }

                val sessionConfig = SessionConfig()
                    .setModel(model)
                    .setStreaming(false)
                    .setTools(emptyList())
                    .setSystemMessage(
                        SystemMessageConfig()
                            .setMode(SystemMessageMode.REPLACE)
                            .setContent(systemPrompt)
                    )

                val session = c.createSession(sessionConfig).await()
                try {
                    val messageOptions = MessageOptions().setPrompt(userMessage)
                    val event = session.sendAndWait(messageOptions, SEND_TIMEOUT_MS).await()
                    val rawContent = event.data?.content
                        ?: throw RuntimeException("No content in Copilot response")
                    log.d { "Raw response: ${rawContent.take(200)}" }
                    Result.success(parseResult(rawContent))
                } finally {
                    session.close()
                }
            }
        } catch (e: Exception) {
            log.e(e) { "Analysis failed" }
            Result.failure(e)
        }
    }

    private fun parseResult(rawContent: String): RiskAnalysis {
        // Strip possible markdown code fences
        val cleaned = rawContent
            .trim()
            .removePrefix("```json")
            .removePrefix("```")
            .removeSuffix("```")
            .trim()

        val response = json.decodeFromString<RiskResponse>(cleaned)
        val level = response.level.coerceIn(1, 5)
        log.i { "Risk: level=$level (${response.label}) - ${response.explanation}" }
        return RiskAnalysis(
            risk = level,
            label = response.label,
            message = response.explanation,
            source = "copilot",
        )
    }

    override fun shutdown() {
        log.i { "Shutting down CopilotClient" }
        client?.stop()
        client = null
    }

    @Serializable
    private data class RiskResponse(
        val level: Int,
        val label: String = "",
        val explanation: String = "",
    )

    companion object {
        private const val TIMEOUT_MS = 30_000L
        private const val SEND_TIMEOUT_MS = 25_000L

        private const val JSON_FORMAT_INSTRUCTION =
            """Respond ONLY with a JSON object in this exact format (no markdown, no explanation outside JSON):
{"level":<1-5>,"label":"<short label>","explanation":"<under 20 words>"}"""
    }
}
