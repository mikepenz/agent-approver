package com.mikepenz.agentbelay.server

import com.mikepenz.agentbelay.model.Source
import com.mikepenz.agentbelay.model.ToolType
import kotlinx.serialization.json.JsonPrimitive
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class OpenCodeAdapterTest {

    private val adapter = OpenCodeAdapter()

    @Test
    fun parseBashRequest() {
        val json = """{"toolName":"bash","toolInput":{"command":"npm test"},"cwd":"/tmp","sessionId":"s1","timestamp":1704614600000}"""
        val result = adapter.parse(json)
        assertNotNull(result)
        assertEquals("Bash", result.hookInput.toolName)
        assertEquals(ToolType.DEFAULT, result.toolType)
        assertEquals("/tmp", result.hookInput.cwd)
        assertEquals(Source.OPENCODE, result.source)
        assertEquals(JsonPrimitive("npm test"), result.hookInput.toolInput["command"])
        assertEquals("s1", result.hookInput.sessionId)
        assertEquals("PermissionRequest", result.hookInput.hookEventName)
        assertEquals(json, result.rawRequestJson)
    }

    @Test
    fun parseEditRequest() {
        val json = """{"toolName":"edit","toolInput":{"filePath":"src/main.kt","content":"hello"},"cwd":"/project","sessionId":"s2","timestamp":1704614600000}"""
        val result = adapter.parse(json)
        assertNotNull(result)
        assertEquals("Edit", result.hookInput.toolName)
        assertEquals(Source.OPENCODE, result.source)
        assertEquals(JsonPrimitive("src/main.kt"), result.hookInput.toolInput["filePath"])
    }

    @Test
    fun parseReadRequest() {
        val json = """{"toolName":"read","toolInput":{"filePath":"README.md"},"cwd":"/home"}"""
        val result = adapter.parse(json)
        assertNotNull(result)
        assertEquals("Read", result.hookInput.toolName)
        assertEquals(Source.OPENCODE, result.source)
    }

    @Test
    fun unknownToolNamePassedThrough() {
        val json = """{"toolName":"custom_tool","toolInput":{"arg":"val"},"cwd":""}"""
        val result = adapter.parse(json)
        assertNotNull(result)
        assertEquals("custom_tool", result.hookInput.toolName)
    }

    @Test
    fun missingToolNameReturnsNull() {
        val json = """{"toolInput":{"command":"test"},"cwd":"/tmp"}"""
        val result = adapter.parse(json)
        assertNull(result)
    }

    @Test
    fun blankToolNameReturnsNull() {
        val json = """{"toolName":"","toolInput":{},"cwd":"/tmp"}"""
        val result = adapter.parse(json)
        assertNull(result)
    }

    @Test
    fun missingSessionIdGeneratesUUID() {
        val json = """{"toolName":"bash","toolInput":{"command":"ls"},"cwd":"/tmp"}"""
        val result = adapter.parse(json)
        assertNotNull(result)
        assertTrue(result.hookInput.sessionId.isNotBlank())
    }

    @Test
    fun missingCwdDefaultsToEmpty() {
        val json = """{"toolName":"bash","toolInput":{"command":"ls"}}"""
        val result = adapter.parse(json)
        assertNotNull(result)
        assertEquals("", result.hookInput.cwd)
    }

    @Test
    fun snakeCaseFieldsAlsoWork() {
        val json = """{"tool_name":"bash","tool_input":{"command":"echo hi"},"cwd":"/x","session_id":"abc"}"""
        val result = adapter.parse(json)
        assertNotNull(result)
        assertEquals("Bash", result.hookInput.toolName)
        assertEquals("abc", result.hookInput.sessionId)
        assertEquals(JsonPrimitive("echo hi"), result.hookInput.toolInput["command"])
    }

    @Test
    fun emptyToolInputDefaultsToEmptyMap() {
        val json = """{"toolName":"glob","cwd":"/tmp"}"""
        val result = adapter.parse(json)
        assertNotNull(result)
        assertEquals("Glob", result.hookInput.toolName)
        assertTrue(result.hookInput.toolInput.isEmpty())
    }
}
