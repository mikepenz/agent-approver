package com.mikepenz.agentbuddy.risk

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
import com.mikepenz.agentbuddy.logging.Logging
import com.mikepenz.agentbuddy.model.HookInput
import com.mikepenz.agentbuddy.model.RiskAnalysis
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.future.await
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
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

    /** Serializes analyze calls — the copilot CLI JSON-RPC pipe cannot handle concurrent sessions. */
    private val analyzeMutex = Mutex()

    /** Optional explicit path to the copilot CLI binary, from settings. */
    var cliPath: String = ""

    fun start() {
        log.i { "Starting CopilotClient" }
        val options = CopilotClientOptions()
            .setLogLevel("error")
        // Resolve copilot CLI path — packaged apps don't inherit the user's shell PATH
        val resolvedPath = if (cliPath.isNotBlank() && File(cliPath).canExecute()) {
            cliPath
        } else {
            findCopilotCli()
        }
        if (resolvedPath != null) {
            log.i { "Using Copilot CLI at: $resolvedPath" }
            options.setCliPath(resolvedPath)
        } else {
            log.w { "Copilot CLI not found, relying on SDK default" }
        }
        // Ensure node is on the PATH — the copilot CLI is a #!/usr/bin/env node script,
        // and packaged apps don't inherit the user's shell PATH.
        val env = buildEnvWithNode()
        if (env != null) {
            options.setEnvironment(env)
        }
        val c = CopilotClient(options)
        client = c
        c.start()
        log.d { "CopilotClient started" }
    }

    override suspend fun analyze(hookInput: HookInput): Result<RiskAnalysis> = withContext(Dispatchers.IO) {
        // Serialize requests — the copilot CLI pipe cannot handle concurrent sessions.
        // Timeout is applied inside the lock so queued requests don't time out while waiting.
        try {
            analyzeMutex.withLock {
                withTimeout(TIMEOUT_MS) {
                    analyzeOnce(hookInput, retried = false)
                }
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            log.e(e) { "Analysis failed" }
            Result.failure(e)
        }
    }

    private suspend fun analyzeOnce(hookInput: HookInput, retried: Boolean): Result<RiskAnalysis> {
        try {
            val c = client ?: run {
                log.w { "CopilotClient not started, starting now" }
                start()
                client ?: throw RuntimeException("CopilotClient failed to start")
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
                if (Logging.verbose) log.d { "Raw response: ${rawContent.take(200)}" }
                return Result.success(parseResult(rawContent))
            } finally {
                session.close()
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            // If the CLI process died (stream closed / broken pipe), restart and retry once
            if (!retried && isStreamError(e)) {
                log.w { "Copilot CLI stream broken, restarting client and retrying..." }
                val restartResult = runCatching { restartClient() }
                restartResult.exceptionOrNull()?.let { restartError ->
                    restartError.addSuppressed(e)
                    log.e(restartError) { "Failed to restart Copilot client after stream error" }
                    return Result.failure(restartError)
                }
                return analyzeOnce(hookInput, retried = true)
            }
            return Result.failure(e)
        }
    }

    /** Detect errors that indicate the underlying CLI process has died. */
    private fun isStreamError(e: Throwable): Boolean {
        var current: Throwable? = e
        val visited = mutableSetOf<Throwable>()
        while (current != null && visited.add(current)) {
            val msg = current.message.orEmpty().lowercase()
            if ("stream closed" in msg || "broken pipe" in msg) return true
            current = current.cause
        }
        return false
    }

    /** Stop the broken client and start a fresh one. */
    private fun restartClient() {
        try {
            client?.stop()
        } catch (e: Exception) {
            log.w(e) { "Error stopping broken client" }
        }
        client = null
        start()
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
        log.i {
            if (Logging.verbose) "Risk: level=$level (${response.label}) - ${response.explanation}"
            else "Risk: level=$level (${response.label})"
        }
        return RiskAnalysis(
            risk = level,
            label = response.label,
            message = response.explanation,
            source = "copilot",
            rawResponse = rawContent.take(MAX_RAW_RESPONSE_CHARS),
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
        /** Cap the raw content we keep on each [RiskAnalysis] (history + DB). */
        private const val MAX_RAW_RESPONSE_CHARS = 65_536

        /**
         * Build an environment map that ensures `node` is on the PATH.
         * Returns null if node is already on PATH or cannot be found.
         */
        private fun buildEnvWithNode(): Map<String, String>? {
            // Check if node is already reachable
            val alreadyAvailable = runCatching {
                val p = ProcessBuilder("which", "node").start()
                val result = p.inputStream.bufferedReader().readText().trim()
                p.waitFor()
                result.isNotBlank() && p.exitValue() == 0
            }.getOrDefault(false)
            if (alreadyAvailable) return null

            val nodeDir = findNodeBinDir() ?: return null
            val currentPath = System.getenv("PATH").orEmpty()
            val newPath = if (currentPath.isNotEmpty()) "$nodeDir:$currentPath" else nodeDir
            return mapOf("PATH" to newPath)
        }

        /** Find the directory containing the `node` binary. */
        private fun findNodeBinDir(): String? {
            val home = System.getProperty("user.home")
            val candidates = mutableListOf(
                "/usr/local/bin/node",
                "/opt/homebrew/bin/node",
                "$home/.local/bin/node",
            )
            addNvmBinCandidates(home, "node", candidates)
            val found = candidates.firstOrNull { File(it).canExecute() }
            return found?.let { File(it).parent }
        }

        /** Search common binary paths for the copilot CLI, including NVM-managed installs. */
        private fun findCopilotCli(): String? {
            val home = System.getProperty("user.home")
            val candidates = mutableListOf(
                "/usr/local/bin/copilot",
                "/opt/homebrew/bin/copilot",
                "$home/.local/bin/copilot",
                "$home/bin/copilot",
            )
            // Add NVM-managed node bin directories
            addNvmBinCandidates(home, "copilot", candidates)
            // Check static candidates first
            val found = candidates.firstOrNull { File(it).canExecute() }
            if (found != null) return found
            // Fall back to `which` with extended PATH
            return runCatching {
                val extraPaths = candidates.map { File(it).parent }.distinct()
                val process = ProcessBuilder("/bin/sh", "-c", "which copilot").apply {
                    val path = environment()["PATH"] ?: ""
                    environment()["PATH"] = (extraPaths + path.split(":")).distinct().joinToString(":")
                }.start()
                val result = process.inputStream.bufferedReader().readText().trim()
                process.waitFor()
                result.ifBlank { null }
            }.getOrNull()
        }

        /** Add NVM-managed bin candidates for [binary], sorted newest version first. */
        private fun addNvmBinCandidates(home: String, binary: String, candidates: MutableList<String>) {
            val nvmDir = File("$home/.nvm/versions/node")
            if (nvmDir.isDirectory) {
                nvmDir.listFiles()
                    ?.sortedByDescending { dir ->
                        // Parse "v18.17.1" as list of ints for proper numeric comparison
                        dir.name.removePrefix("v").split(".").mapNotNull { it.toIntOrNull() }
                            .let { parts -> parts.getOrElse(0) { 0 } * 1_000_000 + parts.getOrElse(1) { 0 } * 1_000 + parts.getOrElse(2) { 0 } }
                    }
                    ?.forEach { candidates.add("${it.absolutePath}/bin/$binary") }
            }
        }

        private const val JSON_FORMAT_INSTRUCTION =
            """Respond ONLY with a JSON object in this exact format (no markdown, no explanation outside JSON):
{"level":<1-5>,"label":"<short label>","explanation":"<under 20 words>"}"""
    }
}
