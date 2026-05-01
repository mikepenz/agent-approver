package com.mikepenz.agentbelay.risk

import co.touchlab.kermit.Logger
import com.mikepenz.agentbelay.logging.Logging
import com.mikepenz.agentbelay.model.HookInput
import com.mikepenz.agentbelay.model.RiskAnalysis
import io.ktor.client.HttpClient
import io.ktor.client.engine.cio.CIO
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.timeout
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
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject

/**
 * Risk analyzer backed by a llama.cpp server via its OpenAI-compatible API.
 *
 * Uses the `POST /v1/chat/completions` endpoint with a JSON-schema
 * `response_format` to constrain output. Concurrent calls are serialised
 * via [mutex] because local llama.cpp is GPU/CPU bound.
 */
class OpenaiApiRiskAnalyzer(
    baseUrl: String = "http://localhost:8080",
    model: String = "llama3.2",
    customSystemPrompt: String = "",
) : RiskAnalyzer {
    private val log = Logger.withTag("OpenaiApiRiskAnalyzer")
    private val json = Json { ignoreUnknownKeys = true }
    private val mutex = Mutex()

    var baseUrl: String = baseUrl.trimEnd('/')
        set(value) { field = value.trimEnd('/') }
    var model: String = model
    var systemPrompt: String = customSystemPrompt.ifBlank { RiskMessageBuilder.DEFAULT_SYSTEM_PROMPT }

    /** Per-request timeout in milliseconds. */
    var timeoutMs: Long = DEFAULT_TIMEOUT_MS

    /** `num_ctx` option. 0 = omit. */
    var numCtx: Int = 0

    /** Most recent successful chat metrics. Reset to null on failure. */
    var lastMetrics: OpenaiApiMetrics? = null
        private set

    /** Last error string suitable for display to the user. */
    var lastError: String? = null
        private set

    /** Optional listener invoked after a successful analysis with the parsed metrics. */
    var onMetrics: ((OpenaiApiMetrics) -> Unit)? = null

    /** Optional listener invoked whenever [lastError] changes (null = cleared). */
    var onError: ((String?) -> Unit)? = null

    private fun setError(value: String?) {
        lastError = value
        onError?.invoke(value)
    }

    private val http: HttpClient = HttpClient(CIO) {
        engine {
            requestTimeout = MAX_REQUEST_TIMEOUT_MS
        }
        install(HttpTimeout) {
            connectTimeoutMillis = CONNECT_TIMEOUT_MS
            requestTimeoutMillis = MAX_REQUEST_TIMEOUT_MS
            socketTimeoutMillis = MAX_REQUEST_TIMEOUT_MS
        }
        expectSuccess = false
    }

    /**
     * Probe `/v1/models` so the lifecycle can flip to READY only when
     * the server answers. Returns the available model IDs.
     */
    suspend fun start(): List<String> {
        return listModels().getOrThrow()
    }

    /** GET /v1/models — populates the model dropdown in Settings. */
    suspend fun listModels(): Result<List<String>> = withContext(Dispatchers.IO) {
        try {
            val response: HttpResponse = http.get("$baseUrl/v1/models") {
                timeout { requestTimeoutMillis = CONNECT_TIMEOUT_MS + 5_000L }
            }
            if (!response.status.isSuccess()) {
                val msg = "OpenAI API /v1/models returned ${response.status.value}"
                setError(msg)
                log.w { msg }
                return@withContext Result.failure(RuntimeException(msg))
            }
            val body = response.bodyAsText()
            val parsed = json.decodeFromString<ModelsResponse>(body)
            val names = parsed.data.map { it.id }
            log.i { "Available models: $names" }
            setError(null)
            Result.success(names)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            if (isConnectionError(e)) {
                val msg = "OpenAI API not reachable at $baseUrl"
                setError(msg)
                log.w(e) { msg }
                Result.failure(RuntimeException(msg, e))
            } else {
                setError("Failed to list models: ${e.message ?: e::class.simpleName}")
                log.e(e) { "Failed to list models" }
                Result.failure(e)
            }
        }
    }

    override suspend fun analyze(hookInput: HookInput): Result<RiskAnalysis> = withContext(Dispatchers.IO) {
        try {
            mutex.withLock {
                withTimeout(timeoutMs) {
                    Result.success(callOpenAI(hookInput))
                }
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            if (isConnectionError(e)) {
                val msg = "OpenAI API not reachable at $baseUrl"
                setError(msg)
                log.w(e) { msg }
                Result.failure(RuntimeException(msg, e))
            } else {
                setError("Analysis failed: ${e.message ?: e::class.simpleName}")
                log.e(e) { "Analysis failed" }
                Result.failure(e)
            }
        }
    }

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

    private suspend fun callOpenAI(hookInput: HookInput): RiskAnalysis {
        val userMessage = RiskMessageBuilder.buildUserMessage(hookInput)
        log.i { "Analyzing ${hookInput.toolName} with model=$model" }
        val startMs = System.currentTimeMillis()

        val requestBody = buildJsonObject {
            put("model", model)
            put("temperature", 0)
            putJsonObject("response_format") {
                put("type", "json_schema")
                putJsonObject("json_schema") {
                    put("name", "risk_analysis")
                    put("description", "Structured risk analysis result")
                    put("strict", true)
                    putJsonObject("schema") {
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
                }
            }
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
            // `options.n_ctx` is a llama.cpp extension; ignored by other OpenAI-compatible servers.
            putJsonObject("options") {
                if (numCtx > 0) put("n_ctx", numCtx)
            }
        }

        val response: HttpResponse = http.post("$baseUrl/v1/chat/completions") {
            contentType(ContentType.Application.Json)
            timeout { requestTimeoutMillis = timeoutMs }
            setBody(json.encodeToString(kotlinx.serialization.json.JsonObject.serializer(), requestBody))
        }

        if (!response.status.isSuccess()) {
            val body = runCatching { response.bodyAsText() }.getOrDefault("")
            val raw = body.take(MAX_RAW_RESPONSE_CHARS).ifBlank { null }
            if (response.status == HttpStatusCode.NotFound && body.contains("not found", ignoreCase = true)) {
                throw RiskAnalyzerException(
                    "model '$model' not found — start llama.cpp with the desired model",
                    rawResponse = raw,
                )
            }
            throw RiskAnalyzerException(
                "OpenAI API returned ${response.status.value}: ${body.take(200)}",
                rawResponse = raw,
            )
        }

        val rawBody = response.bodyAsText()
        if (Logging.verbose) log.d { "Raw response: ${rawBody.take(200)}" }
        val chatResponse = try {
            json.decodeFromString<ChatResponse>(rawBody)
        } catch (e: Exception) {
            throw RiskAnalyzerException(
                "Failed to parse OpenAI API envelope: ${e.message ?: e::class.simpleName}",
                rawResponse = rawBody.take(MAX_RAW_RESPONSE_CHARS),
                cause = e,
            )
        }
        val content = chatResponse.choices.firstOrNull()?.message?.content
            ?: throw RiskAnalyzerException(
                "No message.content in OpenAI API response",
                rawBody.take(MAX_RAW_RESPONSE_CHARS),
            )

        val parsed = try {
            json.decodeFromString<RiskResponse>(content)
        } catch (e: Exception) {
            throw RiskAnalyzerException(
                "Failed to parse risk JSON from model output",
                rawResponse = rawBody.take(MAX_RAW_RESPONSE_CHARS),
                cause = e,
            )
        }
        val level = parsed.level.coerceIn(1, 5)

        val metrics = chatResponse.toMetrics(System.currentTimeMillis() - startMs)
        if (metrics != null) {
            lastMetrics = metrics
            onMetrics?.invoke(metrics)
            log.i {
                "Risk: level=$level (${parsed.label}) - " +
                    "${metrics.totalMs}ms total " +
                    "(prompt ${metrics.promptTokens}t, eval ${metrics.evalTokens}t)"
            }
            if (Logging.verbose) log.d { "Explanation: ${parsed.explanation}" }
        } else {
            log.i {
                if (Logging.verbose) "Risk: level=$level (${parsed.label}) - ${parsed.explanation}"
                else "Risk: level=$level (${parsed.label})"
            }
        }
        setError(null)
        return RiskAnalysis(
            risk = level,
            label = parsed.label,
            message = parsed.explanation,
            source = "openai_api",
            rawResponse = rawBody.take(MAX_RAW_RESPONSE_CHARS),
        )
    }

    override fun shutdown() {
        log.i { "Shutting down OpenaiApiRiskAnalyzer" }
        runCatching { http.close() }
    }

    @Serializable
    private data class ModelsResponse(val data: List<ModelInfo> = emptyList())

    @Serializable
    private data class ModelInfo(val id: String)

    @Serializable
    private data class ChatResponse(
        val choices: List<Choice> = emptyList(),
        val usage: Usage? = null,
    ) {
        fun toMetrics(totalMs: Long): OpenaiApiMetrics? {
            val usage = usage ?: return null
            return OpenaiApiMetrics(
                totalMs = totalMs,
                promptTokens = usage.promptTokens ?: 0,
                evalTokens = usage.completionTokens ?: 0,
            )
        }
    }

    @Serializable
    private data class Choice(val message: ChatMessage? = null)

    @Serializable
    private data class ChatMessage(val role: String = "", val content: String = "")

    @Serializable
    private data class Usage(
        val promptTokens: Int? = null,
        val completionTokens: Int? = null,
        val totalTokens: Int? = null,
    )

    @Serializable
    private data class RiskResponse(
        val level: Int,
        val label: String = "",
        val explanation: String = "",
    )

    companion object {
        private const val DEFAULT_TIMEOUT_MS = 60_000L
        private const val MAX_REQUEST_TIMEOUT_MS = 600_000L
        private const val CONNECT_TIMEOUT_MS = 5_000L
        private const val MAX_RAW_RESPONSE_CHARS = 65_536
    }
}
