package com.mikepenz.agentapprover.server

import com.mikepenz.agentapprover.model.Source
import com.mikepenz.agentapprover.model.ToolType
import kotlinx.serialization.json.JsonPrimitive
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class CopilotAdapterTest {

    private val adapter = CopilotAdapter()

    @Test
    fun parseBashRequest() {
        val json = """{"toolName":"bash","toolArgs":"{\"command\":\"npm test\",\"description\":\"Run tests\"}","timestamp":1704614600000,"cwd":"/tmp"}"""
        val result = adapter.parse(json)
        assertNotNull(result)
        assertEquals("bash", result.hookInput.toolName)
        assertEquals(ToolType.DEFAULT, result.toolType)
        assertEquals("/tmp", result.hookInput.cwd)
        assertEquals(Source.COPILOT, result.source)
        assertEquals(JsonPrimitive("npm test"), result.hookInput.toolInput["command"])
        assertEquals("preToolUse", result.hookInput.hookEventName)
        assertEquals(json, result.rawRequestJson)
    }

    @Test
    fun parseEditRequest() {
        val json = """{"toolName":"edit","toolArgs":"{\"file\":\"src/main.kt\",\"content\":\"hello\"}","timestamp":1704614600000,"cwd":"/project"}"""
        val result = adapter.parse(json)
        assertNotNull(result)
        assertEquals("edit", result.hookInput.toolName)
        assertEquals(ToolType.DEFAULT, result.toolType)
        assertEquals(Source.COPILOT, result.source)
    }

    @Test
    fun allToolsMapToDefault() {
        val tools = listOf("bash", "edit", "view", "create")
        for (tool in tools) {
            val json = """{"toolName":"$tool","toolArgs":"{}","timestamp":0,"cwd":""}"""
            val result = adapter.parse(json)
            assertNotNull(result, "Failed for tool: $tool")
            assertEquals(ToolType.DEFAULT, result.toolType, "Wrong type for tool: $tool")
        }
    }

    @Test
    fun generatesSessionId() {
        val json = """{"toolName":"bash","toolArgs":"{}","timestamp":0,"cwd":""}"""
        val result = adapter.parse(json)
        assertNotNull(result)
        assertTrue(result.hookInput.sessionId.isNotBlank())
    }

    @Test
    fun invalidToolArgsDefaultsToEmptyMap() {
        val json = """{"toolName":"bash","toolArgs":"not valid json","timestamp":0,"cwd":""}"""
        val result = adapter.parse(json)
        assertNotNull(result)
        assertTrue(result.hookInput.toolInput.isEmpty())
    }

    @Test
    fun missingToolNameReturnsNull() {
        val json = """{"toolArgs":"{}","timestamp":0,"cwd":""}"""
        val result = adapter.parse(json)
        assertNull(result)
    }

    @Test
    fun blankToolNameReturnsNull() {
        val json = """{"toolName":"","toolArgs":"{}","timestamp":0,"cwd":""}"""
        val result = adapter.parse(json)
        assertNull(result)
    }

    @Test
    fun malformedJsonReturnsNull() {
        val result = adapter.parse("not json at all")
        assertNull(result)
    }

    @Test
    fun convertsTimestampToInstant() {
        val json = """{"toolName":"bash","toolArgs":"{}","timestamp":1704614600000,"cwd":""}"""
        val result = adapter.parse(json)
        assertNotNull(result)
        assertEquals(1704614600L, result.timestamp.epochSeconds)
    }

    @Test
    fun hookEventNameAlwaysPreToolUse() {
        val json = """{"toolName":"bash","toolArgs":"{}","timestamp":0,"cwd":""}"""
        val result = adapter.parse(json)
        assertNotNull(result)
        assertEquals("preToolUse", result.hookInput.hookEventName)
    }
}
