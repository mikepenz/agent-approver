package com.mikepenz.agentapprover.risk

import co.touchlab.kermit.Logger
import com.mikepenz.agentapprover.model.ApprovalRequest
import com.mikepenz.agentapprover.model.RiskAnalysis
import kotlinx.coroutines.*
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlin.coroutines.CoroutineContext

class RiskAnalyzer(
    private val scope: CoroutineScope,
    private val dispatcher: CoroutineContext = Dispatchers.IO,
) {
    private val activeJobs = mutableMapOf<String, Job>()
    private val json = Json { ignoreUnknownKeys = true }

    fun analyze(
        request: ApprovalRequest,
        onResult: (RiskAnalysis) -> Unit,
        onError: (String) -> Unit,
    ): Job {
        val job = scope.launch(dispatcher) {
            try {
                val prompt = buildPrompt(request.toolName, request.toolInput.toString())
                val result = runCli(prompt)
                val analysis = parseResult(result)
                withContext(Dispatchers.Main) { onResult(analysis) }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Logger.e("RiskAnalyzer") { "Analysis failed for ${request.id}: ${e.message}" }
                val message = when {
                    e.message?.contains("CLI not found") == true -> "CLI not found"
                    e.message?.contains("Timeout") == true -> "Timeout"
                    else -> "Error"
                }
                withContext(Dispatchers.Main) { onError(message) }
            } finally {
                synchronized(activeJobs) { activeJobs.remove(request.id) }
            }
        }
        synchronized(activeJobs) { activeJobs[request.id] = job }
        return job
    }

    fun cancel(requestId: String) {
        synchronized(activeJobs) { activeJobs.remove(requestId) }?.cancel()
    }

    private fun findClaudeCli(): String {
        // Try common locations since spawned JVM processes may not have user's PATH
        val candidates = listOf(
            "claude",
            "${System.getProperty("user.home")}/.local/bin/claude",
            "/usr/local/bin/claude",
            "/opt/homebrew/bin/claude",
        )
        for (candidate in candidates) {
            try {
                val test = ProcessBuilder(candidate, "--version")
                    .redirectErrorStream(true)
                    .start()
                if (test.waitFor() == 0) return candidate
            } catch (_: Exception) {
                continue
            }
        }
        throw IllegalStateException("CLI not found")
    }

    private suspend fun runCli(prompt: String): String = withTimeout(30_000) {
        val claudePath = findClaudeCli()
        val process = try {
            ProcessBuilder(claudePath, "--model", "haiku", "-p", prompt, "--output-format", "json")
                .redirectErrorStream(false)
                .redirectInput(ProcessBuilder.Redirect.from(java.io.File("/dev/null")))
                .start()
        } catch (e: Exception) {
            Logger.e("RiskAnalyzer") { "CLI not found: ${e.message}" }
            throw IllegalStateException("CLI not found")
        }

        try {
            val outputDeferred = async { process.inputStream.bufferedReader().readText() }
            val stderrDeferred = async { process.errorStream.bufferedReader().readText() }
            val exitCode = process.waitFor()
            val output = outputDeferred.await()
            val stderr = stderrDeferred.await()

            if (exitCode != 0) {
                Logger.e("RiskAnalyzer") { "CLI exited with code $exitCode, stderr: $stderr" }
                throw IllegalStateException("Error")
            }
            output
        } catch (e: CancellationException) {
            process.destroyForcibly()
            throw e
        }
    }

    private fun parseResult(output: String): RiskAnalysis {
        // The claude CLI with --output-format json wraps the result in an envelope:
        // {"type":"result","result":"```json\n{\"risk\":1,\"message\":\"...\"}\n```",...}
        // We need to extract the inner JSON from the "result" field.
        val innerJson = try {
            val envelope = json.parseToJsonElement(output).jsonObject
            val resultStr = envelope["result"]?.jsonPrimitive?.content ?: output
            // Strip markdown code fences if present
            resultStr
                .replace(Regex("```json\\s*"), "")
                .replace(Regex("```\\s*"), "")
                .trim()
        } catch (_: Exception) {
            // Not an envelope, try raw output
            output
        }

        Logger.d("RiskAnalyzer") { "Inner JSON after envelope extraction: $innerJson" }

        val jsonStr = extractJson(innerJson) ?: run {
            Logger.e("RiskAnalyzer") { "Could not extract JSON from: $innerJson" }
            throw IllegalStateException("Error")
        }

        Logger.d("RiskAnalyzer") { "Extracted JSON object: $jsonStr" }

        return try {
            val obj = json.parseToJsonElement(jsonStr).jsonObject
            val risk = obj["risk"]?.jsonPrimitive?.int
                ?: throw IllegalStateException("Missing risk field in: $jsonStr")
            val message = obj["message"]?.jsonPrimitive?.content
                ?: throw IllegalStateException("Missing message field in: $jsonStr")
            RiskAnalysis(risk = risk.coerceIn(1, 5), message = message)
        } catch (e: Exception) {
            Logger.e("RiskAnalyzer") { "Failed to parse: ${e.message}" }
            throw IllegalStateException("Error")
        }
    }

    private fun extractJson(output: String): String? {
        val start = output.indexOf('{')
        if (start == -1) return null
        var depth = 0
        for (i in start until output.length) {
            when (output[i]) {
                '{' -> depth++
                '}' -> {
                    depth--
                    if (depth == 0) return output.substring(start, i + 1)
                }
            }
        }
        return null
    }

    private fun buildPrompt(toolName: String, toolInput: String): String = """
You are a security risk analyzer for AI agent tool requests.
Analyze this tool request and rate its risk level from 1-5:
1 = No risk (read-only, safe commands)
2 = Low risk (minor modifications, safe patterns)
3 = Medium risk (modifies files, config changes)
4 = High risk (deletes files, modifies system config, network operations)
5 = Critical risk (destructive commands, production impact, security concern)

Tool: $toolName
Input: $toolInput

Respond with ONLY a JSON object: {"risk": <1-5>, "message": "<~10 word explanation>"}
""".trimIndent()
}
