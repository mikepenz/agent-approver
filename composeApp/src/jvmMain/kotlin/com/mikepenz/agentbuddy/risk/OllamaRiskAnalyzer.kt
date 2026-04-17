package com.mikepenz.agentbuddy.risk

import co.touchlab.kermit.Logger
import com.mikepenz.agentbuddy.logging.Logging
import com.mikepenz.agentbuddy.model.HookInput
import com.mikepenz.agentbuddy.model.RiskAnalysis
import io.ktor.client.HttpClient
import io.ktor.client.engine.cio.CIO
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.request.get
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import io.ktor.http.isSuccess
import java.net.ConnectException
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.add
import kotlinx.serialization.json.addJsonObject
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject

/**
 * Risk analyzer backed by a local (or remote) Ollama daemon.
 *
 * Uses the `POST /api/chat` endpoint with a JSON-schema `format` field to
 * constrain output to `{level, label, explanation}`. Concurrent calls are
 * serialised via [mutex] because local Ollama is GPU/CPU bound and gains
 * nothing from parallelism.
 */
class OllamaRiskAnalyzer(
    baseUrl: String = "http://localhost:11434",
    model: String = "llama3.2",
    customSystemPrompt: String = "",
) : RiskAnalyzer {
    private val log = Logger.withTag("OllamaRiskAnalyzer")
    private val json = Json { ignoreUnknownKeys = true }
    private val mutex = Mutex()

    var baseUrl: String = baseUrl.trimEnd('/')
        set(value) { field = value.trimEnd('/') }
    var model: String = model
    var systemPrompt: String = customSystemPrompt.ifBlank { RiskMessageBuilder.DEFAULT_SYSTEM_PROMPT }

    private val http: HttpClient = HttpClient(CIO) {
        engine {
            requestTimeout = TIMEOUT_MS
        }
        install(HttpTimeout) {
            connectTimeoutMillis = CONNECT_TIMEOUT_MS
            requestTimeoutMillis = TIMEOUT_MS
            socketTimeoutMillis = TIMEOUT_MS
        }
        expectSuccess = false
    }

    /**
     * Probe `/api/tags` so the lifecycle can flip to READY only when the daemon
     * answers. Returns the locally installed models on success so the caller
     * doesn't need a second `/api/tags` round-trip to populate its UI cache.
     */
    suspend fun start(): List<String> = listModels().getOrThrow()

    /** GET /api/tags — populates the model dropdown in Settings. */
    suspend fun listModels(): Result<List<String>> = withContext(Dispatchers.IO) {
        try {
            val response: HttpResponse = http.get("$baseUrl/api/tags")
            if (!response.status.isSuccess()) {
                return@withContext Result.failure(
                    RuntimeException("Ollama /api/tags returned ${response.status.value}")
                )
            }
            val body = response.bodyAsText()
            val parsed = json.decodeFromString<TagsResponse>(body)
            val names = parsed.models.map { it.name }
            log.i { "Available models: $names" }
            Result.success(names)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            if (isConnectionError(e)) {
                Result.failure(RuntimeException("Ollama not reachable at $baseUrl", e))
            } else {
                log.e(e) { "Failed to list models" }
                Result.failure(e)
            }
        }
    }

    override suspend fun analyze(hookInput: HookInput): Result<RiskAnalysis> = withContext(Dispatchers.IO) {
        try {
            mutex.withLock {
                withTimeout(TIMEOUT_MS) {
                    Result.success(callOllama(hookInput))
                }
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            if (isConnectionError(e)) {
                Result.failure(RuntimeException("Ollama not reachable at $baseUrl", e))
            } else {
                log.e(e) { "Analysis failed" }
                Result.failure(e)
            }
        }
    }

    /**
     * True for any IOException-class failure that means the daemon is unreachable —
     * connection refused, host unreachable, no route to host, etc. Walks the cause
     * chain because ktor wraps engine errors in [io.ktor.client.network.sockets.ConnectTimeoutException]
     * and similar.
     */
    private fun isConnectionError(e: Throwable): Boolean {
        var current: Throwable? = e
        val visited = mutableSetOf<Throwable>()
        while (current != null && visited.add(current)) {
            if (current is ConnectException) return true
            val msg = current.message.orEmpty().lowercase()
            if ("connect" in msg || "refused" in msg || "unreachable" in msg || "no route" in msg) {
                return true
            }
            current = current.cause
        }
        return false
    }

    private suspend fun callOllama(hookInput: HookInput): RiskAnalysis {
        val userMessage = RiskMessageBuilder.buildUserMessage(hookInput)
        log.i { "Analyzing ${hookInput.toolName} with model=$model" }

        val requestBody = buildJsonObject {
            put("model", model)
            put("stream", false)
            putJsonArray("messages") {
                addJsonObject {
                    put("role", "system")
                    put("content", systemPrompt)
                }
                addJsonObject {
                    put("role", "user")
                    put("content", userMessage)
                }
            }
            putJsonObject("format") {
                put("type", "object")
                putJsonObject("properties") {
                    putJsonObject("level") { put("type", "integer") }
                    putJsonObject("label") { put("type", "string") }
                    putJsonObject("explanation") { put("type", "string") }
                }
                putJsonArray("required") {
                    add("level"); add("label"); add("explanation")
                }
            }
            putJsonObject("options") {
                put("temperature", 0)
            }
        }

        val response: HttpResponse = http.post("$baseUrl/api/chat") {
            contentType(ContentType.Application.Json)
            setBody(json.encodeToString(kotlinx.serialization.json.JsonObject.serializer(), requestBody))
        }

        if (!response.status.isSuccess()) {
            val body = runCatching { response.bodyAsText() }.getOrDefault("")
            if (response.status == HttpStatusCode.NotFound && body.contains("not found", ignoreCase = true)) {
                throw RuntimeException("model '$model' not found — run: ollama pull $model")
            }
            throw RuntimeException("Ollama returned ${response.status.value}: ${body.take(200)}")
        }

        val rawBody = response.bodyAsText()
        if (Logging.verbose) log.d { "Raw response: ${rawBody.take(200)}" }
        val chatResponse = json.decodeFromString<ChatResponse>(rawBody)
        val content = chatResponse.message?.content
            ?: throw RuntimeException("No message.content in Ollama response")

        val parsed = json.decodeFromString<RiskResponse>(content)
        val level = parsed.level.coerceIn(1, 5)
        log.i {
            if (Logging.verbose) "Risk: level=$level (${parsed.label}) - ${parsed.explanation}"
            else "Risk: level=$level (${parsed.label})"
        }
        return RiskAnalysis(
            risk = level,
            label = parsed.label,
            message = parsed.explanation,
            source = "ollama",
        )
    }

    override fun shutdown() {
        log.i { "Shutting down OllamaRiskAnalyzer" }
        runCatching { http.close() }
    }

    @Serializable
    private data class TagsResponse(val models: List<TagModel> = emptyList())

    @Serializable
    private data class TagModel(val name: String)

    @Serializable
    private data class ChatResponse(val message: ChatMessage? = null)

    @Serializable
    private data class ChatMessage(val role: String = "", val content: String = "")

    @Serializable
    private data class RiskResponse(
        val level: Int,
        val label: String = "",
        val explanation: String = "",
    )

    companion object {
        private const val TIMEOUT_MS = 30_000L
        private const val CONNECT_TIMEOUT_MS = 5_000L
    }
}
