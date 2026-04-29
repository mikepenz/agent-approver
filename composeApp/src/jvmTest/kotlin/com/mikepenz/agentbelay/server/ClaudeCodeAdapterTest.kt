package com.mikepenz.agentbelay.server

import com.mikepenz.agentbelay.model.Source
import com.mikepenz.agentbelay.model.ToolType
import kotlinx.serialization.json.JsonPrimitive
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

class ClaudeCodeAdapterTest {

    private val adapter = ClaudeCodeAdapter()

    @Test
    fun parseBashRequest() {
        val json = """{"session_id":"abc","cwd":"/tmp","hook_event_name":"PermissionRequest","tool_name":"Bash","tool_input":{"command":"npm test"}}"""
        val result = adapter.parse(json)
        assertNotNull(result)
        assertEquals("Bash", result.hookInput.toolName)
        assertEquals(ToolType.DEFAULT, result.toolType)
        assertEquals("abc", result.hookInput.sessionId)
        assertEquals("/tmp", result.hookInput.cwd)
        assertEquals(Source.CLAUDE_CODE, result.source)
        assertEquals(JsonPrimitive("npm test"), result.hookInput.toolInput["command"])
        assertEquals(json, result.rawRequestJson)
    }

    @Test
    fun parseAskUserQuestion() {
        val json = """{"session_id":"abc","cwd":"/tmp","hook_event_name":"PermissionRequest","tool_name":"AskUserQuestion","tool_input":{"question":"Continue?"}}"""
        val result = adapter.parse(json)
        assertNotNull(result)
        assertEquals(ToolType.ASK_USER_QUESTION, result.toolType)
    }

    @Test
    fun parsePlan() {
        val json = """{"session_id":"abc","cwd":"/tmp","hook_event_name":"PermissionRequest","tool_name":"ExitPlanMode","tool_input":{}}"""
        val result = adapter.parse(json)
        assertNotNull(result)
        assertEquals(ToolType.PLAN, result.toolType)
        assertEquals("ExitPlanMode", result.hookInput.toolName)
    }

    @Test
    fun parseUnknownToolAsDefault() {
        val json = """{"session_id":"abc","cwd":"/tmp","hook_event_name":"PermissionRequest","tool_name":"SomeFancyTool","tool_input":{"key":"value"}}"""
        val result = adapter.parse(json)
        assertNotNull(result)
        assertEquals(ToolType.DEFAULT, result.toolType)
    }

    @Test
    fun malformedJsonReturnsNull() {
        val result = adapter.parse("not json at all")
        assertNull(result)
    }

    @Test
    fun missingFieldsReturnsNull() {
        // Missing tool_name - should fail deserialization
        val json = """{"session_id":"abc"}"""
        val result = adapter.parse(json)
        assertNull(result)
    }
}
