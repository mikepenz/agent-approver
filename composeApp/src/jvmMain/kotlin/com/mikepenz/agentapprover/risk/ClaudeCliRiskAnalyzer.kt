package com.mikepenz.agentapprover.risk

import co.touchlab.kermit.Logger
import com.mikepenz.agentapprover.model.HookInput
import com.mikepenz.agentapprover.model.RiskAnalysis
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.coroutines.coroutineScope
import java.util.concurrent.TimeUnit

class ClaudeCliRiskAnalyzer(
    model: String = "haiku",
    customSystemPrompt: String = "",
) : RiskAnalyzer {
    private val log = Logger.withTag("ClaudeCliRiskAnalyzer")
    var model: String = model
    var systemPrompt: String = customSystemPrompt.ifBlank { RiskMessageBuilder.DEFAULT_SYSTEM_PROMPT }
    private val json = Json { ignoreUnknownKeys = true }

    override suspend fun analyze(hookInput: HookInput): Result<RiskAnalysis> = withContext(Dispatchers.IO) {
        try {
            withTimeout(TIMEOUT_MS) {
                val userMessage = RiskMessageBuilder.buildUserMessage(hookInput)
                log.i { "Analyzing ${hookInput.toolName}" }
                val result = runClaude(userMessage)
                Result.success(parseResult(result))
            }
        } catch (e: Exception) {
            log.e(e) { "Analysis failed" }
            Result.failure(e)
        }
    }

    private suspend fun runClaude(userMessage: String): String = coroutineScope {
        val command = listOf(
            "claude",
            "-p",
            "--model", model,
            "--effort", "low",
            "--system-prompt", systemPrompt,
            "--output-format", "json",
            "--json-schema", JSON_SCHEMA,
            "--no-session-persistence",
            userMessage,
        )

        log.d { "Spawning claude -p --model $model --effort low" }

        val process = ProcessBuilder(command).apply {
            environment().remove("CLAUDECODE")
            val path = environment()["PATH"] ?: ""
            val extraPaths = listOf("/usr/local/bin", "/opt/homebrew/bin", "${System.getProperty("user.home")}/.local/bin")
            environment()["PATH"] = (extraPaths + path.split(":")).distinct().joinToString(":")
            redirectErrorStream(false)
        }.start()

        // Close stdin immediately so claude doesn't wait for input
        process.outputStream.close()

        log.d { "Process pid=${process.pid()}" }

        // Read both streams concurrently via coroutines to prevent deadlock
        val stdoutDeferred = async { process.inputStream.bufferedReader().readText() }
        val stderrDeferred = async { process.errorStream.bufferedReader().readText() }

        val finished = process.waitFor(PROCESS_TIMEOUT_SECONDS, TimeUnit.SECONDS)
        if (!finished) {
            log.w { "Process timed out after ${PROCESS_TIMEOUT_SECONDS}s" }
            process.destroyForcibly()
            stdoutDeferred.cancel()
            stderrDeferred.cancel()
            throw RuntimeException("claude process timed out after ${PROCESS_TIMEOUT_SECONDS}s")
        }

        val stdoutOutput = stdoutDeferred.await()
        val stderrOutput = stderrDeferred.await()

        val exitCode = process.exitValue()
        log.d { "Exited=$exitCode" }

        if (exitCode != 0) {
            log.w { "Failed: ${stderrOutput.take(200)}" }
            throw RuntimeException("claude exited with code $exitCode: ${stderrOutput.take(200)}")
        }

        stdoutOutput.trim()
    }

    private fun parseResult(rawOutput: String): RiskAnalysis {
        val wrapper = json.decodeFromString<ClaudeJsonResponse>(rawOutput)

        if (wrapper.isError) {
            throw RuntimeException("Claude returned error: ${wrapper.result.take(200)}")
        }

        val structuredOutput = wrapper.structuredOutput
        if (structuredOutput != null) {
            val level = structuredOutput.level.coerceIn(1, 5)
            log.i { "Risk: level=$level (${structuredOutput.label}) - ${structuredOutput.explanation}" }
            return RiskAnalysis(
                risk = level,
                label = structuredOutput.label,
                message = structuredOutput.explanation,
                source = "claude",
            )
        }

        throw RuntimeException("No structured_output in response")
    }

    @Serializable
    private data class ClaudeJsonResponse(
        val type: String = "",
        val subtype: String = "",
        val result: String = "",
        @SerialName("is_error")
        val isError: Boolean = false,
        @SerialName("structured_output")
        val structuredOutput: RiskResponse? = null,
    )

    @Serializable
    private data class RiskResponse(
        val level: Int,
        val label: String = "",
        val explanation: String = "",
    )

    companion object {
        private const val TIMEOUT_MS = 30_000L
        private const val PROCESS_TIMEOUT_SECONDS = 25L

        private const val JSON_SCHEMA = """{"type":"object","properties":{"level":{"type":"integer"},"label":{"type":"string"},"explanation":{"type":"string"}},"required":["level","label","explanation"]}"""
    }
}
