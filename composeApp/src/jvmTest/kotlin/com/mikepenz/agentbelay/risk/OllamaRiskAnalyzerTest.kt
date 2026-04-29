package com.mikepenz.agentbelay.risk

import com.mikepenz.agentbelay.model.HookInput
import com.sun.net.httpserver.HttpExchange
import com.sun.net.httpserver.HttpHandler
import com.sun.net.httpserver.HttpServer
import java.net.InetSocketAddress
import java.util.concurrent.CountDownLatch
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.JsonPrimitive
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class OllamaRiskAnalyzerTest {

    private lateinit var server: HttpServer
    private lateinit var serverExecutor: ExecutorService
    private val port: Int get() = server.address.port

    private val tagsBody = """
        {"models":[{"name":"llama3.2"},{"name":"qwen2.5"}]}
    """.trimIndent()

    private val chatBody = """
        {"model":"llama3.2","message":{"role":"assistant","content":"{\"level\":2,\"label\":\"Low\",\"explanation\":\"Read-only command\"}"},"done":true}
    """.trimIndent()

    private fun hookInput() = HookInput(
        sessionId = "s",
        toolName = "Bash",
        toolInput = mapOf("command" to JsonPrimitive("ls")),
        cwd = "/tmp",
        agentType = null,
    )

    @BeforeTest
    fun setUp() {
        server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
        // Explicit multi-threaded executor so the concurrency test would actually
        // observe overlap if the analyzer's mutex were missing — `null` would let
        // the JDK serialise requests on a single thread, masking the bug.
        serverExecutor = Executors.newFixedThreadPool(4)
        server.executor = serverExecutor
        server.start()
    }

    @AfterTest
    fun tearDown() {
        server.stop(0)
        serverExecutor.shutdownNow()
    }

    private fun handle(path: String, body: String, status: Int = 200, latch: CountDownLatch? = null, overlap: AtomicInteger? = null, max: AtomicInteger? = null) {
        server.createContext(path, HttpHandler { exchange: HttpExchange ->
            overlap?.let {
                val cur = it.incrementAndGet()
                max?.updateAndGet { m -> if (cur > m) cur else m }
            }
            latch?.await(2, TimeUnit.SECONDS)
            try {
                val bytes = body.toByteArray()
                exchange.sendResponseHeaders(status, bytes.size.toLong())
                exchange.responseBody.use { it.write(bytes) }
            } finally {
                overlap?.decrementAndGet()
            }
        })
    }

    @Test
    fun startSucceedsWhenTagsReturnsModels() = runBlocking {
        handle("/api/tags", tagsBody)
        val analyzer = OllamaRiskAnalyzer(baseUrl = "http://127.0.0.1:$port", model = "llama3.2")
        try {
            analyzer.start()
            val models = analyzer.listModels().getOrThrow()
            assertEquals(listOf("llama3.2", "qwen2.5"), models)
        } finally {
            analyzer.shutdown()
        }
    }

    @Test
    fun analyzeParsesStructuredResponse() = runBlocking {
        handle("/api/tags", tagsBody)
        handle("/api/chat", chatBody)
        val analyzer = OllamaRiskAnalyzer(baseUrl = "http://127.0.0.1:$port", model = "llama3.2")
        try {
            val result = analyzer.analyze(hookInput())
            val analysis = result.getOrThrow()
            assertEquals(2, analysis.risk)
            assertEquals("Low", analysis.label)
            assertEquals("ollama", analysis.source)
            // Raw body is captured verbatim so the history can show what the model
            // actually emitted (helpful when the parsed result looks suspicious).
            assertEquals(chatBody, analysis.rawResponse)
        } finally {
            analyzer.shutdown()
        }
    }

    @Test
    fun analyzeFailsWithReachabilityErrorWhenServerDown() = runBlocking {
        // Pick a port and immediately stop the server so connections refuse.
        val deadPort = port
        server.stop(0)
        val analyzer = OllamaRiskAnalyzer(baseUrl = "http://127.0.0.1:$deadPort", model = "llama3.2")
        try {
            val result = analyzer.analyze(hookInput())
            assertTrue(result.isFailure)
            val msg = result.exceptionOrNull()?.message.orEmpty()
            assertTrue(msg.contains("not reachable", ignoreCase = true), "got: $msg")
        } finally {
            analyzer.shutdown()
            // Re-create so AfterTest stop() is a no-op
            server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
        }
    }

    @Test
    fun concurrentAnalyzeCallsAreSerialised() = runBlocking {
        handle("/api/tags", tagsBody)
        val gate = CountDownLatch(1)
        val overlap = AtomicInteger(0)
        val maxOverlap = AtomicInteger(0)
        handle("/api/chat", chatBody, latch = gate, overlap = overlap, max = maxOverlap)

        val analyzer = OllamaRiskAnalyzer(baseUrl = "http://127.0.0.1:$port", model = "llama3.2")
        try {
            val a = async { analyzer.analyze(hookInput()) }
            val b = async { analyzer.analyze(hookInput()) }
            // Wait deterministically until exactly one request has entered the
            // server handler before releasing the gate. If the mutex is broken,
            // both requests would arrive and `overlap` would briefly exceed 1,
            // which `maxOverlap` records. `delay()` (not Thread.sleep) is
            // critical here so the runBlocking event loop can actually dispatch
            // `a` and `b` while we wait.
            val deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5)
            while (overlap.get() < 1 && System.nanoTime() < deadline) {
                delay(10)
            }
            assertEquals(1, overlap.get(), "first request never reached the server")
            gate.countDown()
            a.await().getOrThrow()
            b.await().getOrThrow()
            assertEquals(1, maxOverlap.get(), "expected mutex to serialise calls")
        } finally {
            analyzer.shutdown()
        }
    }
}
