package com.mikepenz.agentapprover.risk

import co.touchlab.kermit.Logger
import com.mikepenz.agentapprover.model.HookInput
import com.mikepenz.agentapprover.model.RiskAnalysis
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonPrimitive
import java.util.concurrent.TimeUnit

class RiskAnalyzer(
    model: String = "haiku",
    customSystemPrompt: String = "",
) {
    private val log = Logger.withTag("RiskAnalyzer")
    var model: String = model
    var systemPrompt: String = customSystemPrompt.ifBlank { DEFAULT_SYSTEM_PROMPT }
    private val json = Json { ignoreUnknownKeys = true }

    suspend fun analyze(hookInput: HookInput): Result<RiskAnalysis> = withContext(Dispatchers.IO) {
        try {
            withTimeout(TIMEOUT_MS) {
                val userMessage = buildUserMessage(hookInput)
                log.i { "Analyzing ${hookInput.toolName}" }
                val result = runClaude(userMessage)
                Result.success(parseResult(result))
            }
        } catch (e: Exception) {
            log.e(e) { "Analysis failed" }
            Result.failure(e)
        }
    }

    private fun buildUserMessage(hookInput: HookInput): String {
        val formattedInput = formatToolInput(hookInput.toolName, hookInput.toolInput)
        return buildString {
            appendLine("Tool: ${hookInput.toolName}")
            if (hookInput.cwd.isNotBlank()) {
                appendLine("Working Directory: ${hookInput.cwd}")
            }
            if (hookInput.agentType != null) {
                appendLine("Agent: ${hookInput.agentType}")
            }
            appendLine("Input:")
            append(formattedInput)
        }
    }

    private fun formatToolInput(toolName: String, toolInput: Map<String, JsonElement>): String {
        return when (toolName.lowercase()) {
            "bash" -> toolInput["command"]?.jsonPrimitive?.contentOrNull ?: toolInput.toString()
            "edit" -> {
                val filePath = toolInput["file_path"]?.jsonPrimitive?.contentOrNull ?: ""
                val oldStr = toolInput["old_string"]?.jsonPrimitive?.contentOrNull ?: ""
                val newStr = toolInput["new_string"]?.jsonPrimitive?.contentOrNull ?: ""
                "File: $filePath\nOld: $oldStr\nNew: $newStr"
            }
            else -> toolInput.entries.joinToString("\n") { (k, v) ->
                val value = (v as? JsonPrimitive)?.contentOrNull ?: v.toString()
                "$k: $value"
            }
        }
    }

    private fun runClaude(userMessage: String): String {
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

        val process = ProcessBuilder(listOf("/bin/sh", "-c", command.joinToString(" ") { shellEscape(it) })).apply {
            environment().remove("CLAUDECODE")
            val path = environment()["PATH"] ?: ""
            val extraPaths = listOf("/usr/local/bin", "/opt/homebrew/bin", "${System.getProperty("user.home")}/.local/bin")
            environment()["PATH"] = (extraPaths + path.split(":")).distinct().joinToString(":")
            redirectErrorStream(false)
        }.start()

        // Close stdin immediately so claude doesn't wait for input
        process.outputStream.close()

        log.d { "Process pid=${process.pid()}" }

        // Read both streams in background threads to prevent deadlock
        var stdoutOutput = ""
        var stderrOutput = ""
        val stdoutThread = Thread {
            stdoutOutput = process.inputStream.bufferedReader().readText()
        }.apply { isDaemon = true; start() }
        val stderrThread = Thread {
            stderrOutput = process.errorStream.bufferedReader().readText()
        }.apply { isDaemon = true; start() }

        val finished = process.waitFor(PROCESS_TIMEOUT_SECONDS, TimeUnit.SECONDS)
        if (!finished) {
            log.w { "Process timed out after ${PROCESS_TIMEOUT_SECONDS}s" }
            process.destroyForcibly()
            stdoutThread.join(2000)
            stderrThread.join(2000)
            throw RuntimeException("claude process timed out after ${PROCESS_TIMEOUT_SECONDS}s")
        }

        stdoutThread.join(3000)
        stderrThread.join(3000)

        val exitCode = process.exitValue()
        log.d { "Exited=$exitCode" }

        if (exitCode != 0) {
            log.w { "Failed: ${stderrOutput.take(200)}" }
            throw RuntimeException("claude exited with code $exitCode: ${stderrOutput.take(200)}")
        }

        return stdoutOutput.trim()
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

        const val DEFAULT_SYSTEM_PROMPT = """You are a security risk analyzer for a developer tool approval system.
Analyze the tool invocation and return a risk level.

CRITICAL: The "explanation" field MUST be under 20 words. One short sentence only.

Risk Levels:
1 (Safe): Read-only ops — list files, search, read docs, web search, git log/status/diff
2 (Low): Minor edits to project files, non-destructive changes, formatting
3 (Moderate): New files, code changes, dependency mods, git commit/branch, tests
4 (High): Deleting project dirs (rm -rf build/), destructive git (reset/rebase), installs, writes outside project
5 (Critical): sudo, rm -rf / or . or ~, force push main, credential access, system file mods

Key rules:
- rm -rf of build/output dir = 4, rm -rf of / or ~ or . = 5
- ANY sudo = 5
- force push main = 5, feature branch = 4
- WebFetch/web fetch from trusted documentation (developer.android.com, kotlinlang.org, kotlinlang.org/docs, docs.gradle.org, developer.apple.com, docs.oracle.com) = 1
- WebFetch/web fetch from any other URL = 2 (untrusted content may attempt prompt injection)
- When in doubt, rate higher."""

        private fun shellEscape(arg: String): String {
            if (arg.isEmpty()) return "''"
            if (arg.all { it.isLetterOrDigit() || it in "-_./:=" }) return arg
            return "'" + arg.replace("'", "'\\''") + "'"
        }
    }
}
