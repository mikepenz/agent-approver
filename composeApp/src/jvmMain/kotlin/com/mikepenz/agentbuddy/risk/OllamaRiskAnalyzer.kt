package com.mikepenz.agentbuddy.risk

import co.touchlab.kermit.Logger
import com.mikepenz.agentbuddy.logging.Logging
import com.mikepenz.agentbuddy.model.HookInput
import com.mikepenz.agentbuddy.model.RiskAnalysis
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
 * Risk analyzer backed by a local (or remote) Ollama daemon.
 *
 * Uses the `POST /api/chat` endpoint with a JSON-schema `format` field to
 * constrain output to `{level, label, explanation}`. Concurrent calls are
 * serialised via [mutex] because local Ollama is GPU/CPU bound and gains
 * nothing from parallelism.
 *
 * Callers can observe per-request metrics through [lastMetrics] and the daemon
 * version through [version] (populated by [start]).
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

    /** Whether to send `think: true` to models that support reasoning. */
    var thinking: Boolean = false

    /** `keep_alive` value (e.g. "10m"). Empty = omit (Ollama default applies). */
    var keepAlive: String = "10m"

    /** Per-request timeout in milliseconds. */
    var timeoutMs: Long = DEFAULT_TIMEOUT_MS

    /** `num_ctx` option. 0 = omit. */
    var numCtx: Int = 0

    /** Daemon version reported by `/api/version`, captured during [start]. */
    var version: String? = null
        private set

    /** Most recent successful chat metrics. Reset to null on failure. */
    var lastMetrics: OllamaMetrics? = null
        private set

    /** Last error string suitable for display to the user. */
    var lastError: String? = null
        private set

    /** Optional listener invoked after a successful analysis with the parsed metrics. */
    var onMetrics: ((OllamaMetrics) -> Unit)? = null

    /** Optional listener invoked whenever [lastError] changes (null = cleared). */
    var onError: ((String?) -> Unit)? = null

    private fun setError(value: String?) {
        lastError = value
        onError?.invoke(value)
    }

    private val http: HttpClient = HttpClient(CIO) {
        engine {
            // Generous upper bound; effective timeout is enforced per-request via
            // the [timeout] plugin block plus an outer `withTimeout`.
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
     * Probe `/api/version` and `/api/tags` so the lifecycle can flip to READY
     * only when the daemon answers. Returns the locally installed models.
     */
    suspend fun start(): List<String> {
        version = fetchVersion()
        if (version != null) log.i { "Connected to Ollama $version at $baseUrl" }
        return listModels().getOrThrow()
    }

    /** GET /api/version — best effort, swallows errors. */
    private suspend fun fetchVersion(): String? = withContext(Dispatchers.IO) {
        runCatching {
            val response: HttpResponse = http.get("$baseUrl/api/version") {
                timeout { requestTimeoutMillis = CONNECT_TIMEOUT_MS + 2_000L }
            }
            if (!response.status.isSuccess()) return@runCatching null
            json.decodeFromString<VersionResponse>(response.bodyAsText()).version
        }.getOrNull()
    }

    /** GET /api/tags — populates the model dropdown in Settings. */
    suspend fun listModels(): Result<List<String>> = withContext(Dispatchers.IO) {
        try {
            val response: HttpResponse = http.get("$baseUrl/api/tags") {
                timeout { requestTimeoutMillis = CONNECT_TIMEOUT_MS + 5_000L }
            }
            if (!response.status.isSuccess()) {
                val msg = "Ollama /api/tags returned ${response.status.value}"
                setError(msg)
                log.w { msg }
                return@withContext Result.failure(RuntimeException(msg))
            }
            val body = response.bodyAsText()
            val parsed = json.decodeFromString<TagsResponse>(body)
            val names = parsed.models.map { it.name }
            log.i { "Available models: $names" }
            setError(null)
            Result.success(names)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            if (isConnectionError(e)) {
                val msg = "Ollama not reachable at $baseUrl"
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
                    Result.success(callOllama(hookInput))
                }
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            if (isConnectionError(e)) {
                val msg = "Ollama not reachable at $baseUrl"
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
            put("think", thinking)
            if (keepAlive.isNotBlank()) put("keep_alive", keepAlive)
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
                if (numCtx > 0) put("num_ctx", numCtx)
            }
        }

        val response: HttpResponse = http.post("$baseUrl/api/chat") {
            contentType(ContentType.Application.Json)
            timeout { requestTimeoutMillis = timeoutMs }
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

        val parsed = try {
            json.decodeFromString<RiskResponse>(content)
        } catch (e: Exception) {
            throw RuntimeException(
                "Failed to parse risk JSON from model output: ${content.take(300)}",
                e,
            )
        }
        val level = parsed.level.coerceIn(1, 5)

        val metrics = chatResponse.toMetrics()
        if (metrics != null) {
            lastMetrics = metrics
            onMetrics?.invoke(metrics)
            log.i {
                "Risk: level=$level (${parsed.label}) - " +
                    "${metrics.totalMs}ms total " +
                    "(load ${metrics.loadMs}ms, prompt ${metrics.promptTokens}t/${metrics.promptEvalMs}ms, " +
                    "eval ${metrics.evalTokens}t/${metrics.evalMs}ms)"
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
            source = "ollama",
            rawResponse = rawBody.take(MAX_RAW_RESPONSE_CHARS),
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
    private data class VersionResponse(val version: String = "")

    @Serializable
    private data class ChatResponse(
        val message: ChatMessage? = null,
        val total_duration: Long? = null,
        val load_duration: Long? = null,
        val prompt_eval_count: Int? = null,
        val prompt_eval_duration: Long? = null,
        val eval_count: Int? = null,
        val eval_duration: Long? = null,
    ) {
        fun toMetrics(): OllamaMetrics? {
            val total = total_duration ?: return null
            return OllamaMetrics(
                totalMs = total / 1_000_000,
                loadMs = (load_duration ?: 0L) / 1_000_000,
                promptEvalMs = (prompt_eval_duration ?: 0L) / 1_000_000,
                evalMs = (eval_duration ?: 0L) / 1_000_000,
                promptTokens = prompt_eval_count ?: 0,
                evalTokens = eval_count ?: 0,
            )
        }
    }

    @Serializable
    private data class ChatMessage(val role: String = "", val content: String = "")

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
        /** Cap the raw response payload we keep on each [RiskAnalysis] (history + DB). */
        private const val MAX_RAW_RESPONSE_CHARS = 65_536
    }
}
