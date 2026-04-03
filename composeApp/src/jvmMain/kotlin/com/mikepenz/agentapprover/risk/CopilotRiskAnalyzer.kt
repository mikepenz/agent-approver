package com.mikepenz.agentapprover.risk

import co.touchlab.kermit.Logger
import com.github.copilot.sdk.CopilotClient
import com.github.copilot.sdk.SystemMessageMode
import com.github.copilot.sdk.json.CopilotClientOptions
import com.github.copilot.sdk.json.MessageOptions
import com.github.copilot.sdk.json.ModelInfo
import com.github.copilot.sdk.json.PermissionHandler
import com.github.copilot.sdk.json.SessionConfig
import com.github.copilot.sdk.json.SystemMessageConfig
import java.io.File
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
    var systemPrompt: String = customSystemPrompt.ifBlank { RiskMessageBuilder.DEFAULT_SYSTEM_PROMPT }
        set(value) {
            field = value
        }

    /** The effective system prompt sent to Copilot, always with JSON format instruction appended. */
    private val effectiveSystemPrompt: String
        get() = systemPrompt + "\n\n" + JSON_FORMAT_INSTRUCTION

    private val json = Json { ignoreUnknownKeys = true }
    private var client: CopilotClient? = null

    fun start() {
        log.i { "Starting CopilotClient" }
        val options = CopilotClientOptions()
            .setLogLevel("error")
        // Resolve copilot CLI path — packaged apps don't inherit the user's shell PATH
        val cliPath = findCopilotCli()
        if (cliPath != null) {
            log.i { "Found Copilot CLI at: $cliPath" }
            options.setCliPath(cliPath)
        } else {
            log.w { "Copilot CLI not found in common paths, relying on SDK default" }
        }
        val c = CopilotClient(options)
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
                    .setOnPermissionRequest(PermissionHandler.APPROVE_ALL)
                    .setSystemMessage(
                        SystemMessageConfig()
                            .setMode(SystemMessageMode.REPLACE)
                            .setContent(effectiveSystemPrompt)
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
        // Extract JSON object from response — model may include prose around it
        val firstBrace = rawContent.indexOf('{')
        val lastBrace = rawContent.lastIndexOf('}')
        if (firstBrace == -1 || lastBrace == -1 || lastBrace <= firstBrace) {
            throw RuntimeException("No JSON object found in Copilot response: ${rawContent.take(200)}")
        }
        val cleaned = rawContent.substring(firstBrace, lastBrace + 1)

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

    /** Query available models from the Copilot API. Returns model id to display name pairs. */
    suspend fun listModels(): Result<List<Pair<String, String>>> = withContext(Dispatchers.IO) {
        try {
            val c = client ?: return@withContext Result.failure(RuntimeException("Copilot client not started"))
            val models: List<ModelInfo> = c.listModels().await()
            val result = models.map { it.id to (it.name ?: it.id) }
            log.i { "Available models: ${result.map { it.first }}" }
            Result.success(result)
        } catch (e: Exception) {
            log.e(e) { "Failed to list models" }
            Result.failure(e)
        }
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

        /** Search common binary paths for the copilot CLI. */
        private fun findCopilotCli(): String? {
            val home = System.getProperty("user.home")
            val candidates = listOf(
                "/usr/local/bin/copilot",
                "/opt/homebrew/bin/copilot",
                "$home/.local/bin/copilot",
                "$home/bin/copilot",
            )
            // Also check PATH via `which`
            return candidates.firstOrNull { File(it).canExecute() }
                ?: runCatching {
                    val process = ProcessBuilder("/bin/sh", "-c", "which copilot").apply {
                        val path = environment()["PATH"] ?: ""
                        val extraPaths = listOf("/usr/local/bin", "/opt/homebrew/bin", "$home/.local/bin", "$home/bin")
                        environment()["PATH"] = (extraPaths + path.split(":")).distinct().joinToString(":")
                    }.start()
                    val result = process.inputStream.bufferedReader().readText().trim()
                    process.waitFor()
                    result.ifBlank { null }
                }.getOrNull()
        }

        private const val JSON_FORMAT_INSTRUCTION =
            """Respond ONLY with a JSON object in this exact format (no markdown, no explanation outside JSON):
{"level":<1-5>,"label":"<short label>","explanation":"<under 20 words>"}"""
    }
}
