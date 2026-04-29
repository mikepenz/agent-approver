package com.mikepenz.agentbelay.server

import com.mikepenz.agentbelay.model.Source
import com.mikepenz.agentbelay.model.ToolType
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
        assertEquals("Bash", result.hookInput.toolName)
        assertEquals(ToolType.DEFAULT, result.toolType)
        assertEquals("/tmp", result.hookInput.cwd)
        assertEquals(Source.COPILOT, result.source)
        assertEquals(JsonPrimitive("npm test"), result.hookInput.toolInput["command"])
        assertEquals("preToolUse", result.hookInput.hookEventName)
        assertEquals(json, result.rawRequestJson)
        assertNotNull(result.hookInput.sessionId)
        assertTrue(result.hookInput.sessionId.isNotBlank())
    }

    @Test
    fun parseEditRequest() {
        val json = """{"toolName":"edit","toolArgs":"{\"file\":\"src/main.kt\",\"content\":\"hello\"}","timestamp":1704614600000,"cwd":"/project"}"""
        val result = adapter.parse(json)
        assertNotNull(result)
        assertEquals("Edit", result.hookInput.toolName)
        assertEquals(ToolType.DEFAULT, result.toolType)
        assertEquals(Source.COPILOT, result.source)
    }

    @Test
    fun normalizesRunTerminalCmdToBash() {
        val json = """{"toolName":"run_terminal_cmd","toolArgs":"{\"command\":\"rm -rf dist\"}","timestamp":0,"cwd":""}"""
        val result = adapter.parse(json)
        assertNotNull(result)
        assertEquals("Bash", result.hookInput.toolName)
        assertEquals(JsonPrimitive("rm -rf dist"), result.hookInput.toolInput["command"])
    }

    @Test
    fun normalizesCreateFileToWriteAndRemapsPath() {
        val json = """{"toolName":"create_file","toolArgs":"{\"path\":\"src/Foo.kt\",\"content\":\"class Foo\"}","timestamp":0,"cwd":""}"""
        val result = adapter.parse(json)
        assertNotNull(result)
        assertEquals("Write", result.hookInput.toolName)
        assertEquals(JsonPrimitive("src/Foo.kt"), result.hookInput.toolInput["file_path"])
        assertTrue("path" !in result.hookInput.toolInput)
    }

    @Test
    fun unknownToolNamePassesThrough() {
        val json = """{"toolName":"some_unknown_tool","toolArgs":"{}","timestamp":0,"cwd":""}"""
        val result = adapter.parse(json)
        assertNotNull(result)
        assertEquals("some_unknown_tool", result.hookInput.toolName)
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
    fun hookEventNameDefaultsToPreToolUseWhenAbsent() {
        val json = """{"toolName":"bash","toolArgs":"{}","timestamp":0,"cwd":""}"""
        val result = adapter.parse(json)
        assertNotNull(result)
        assertEquals("preToolUse", result.hookInput.hookEventName)
    }

    // ----- camelCase permissionRequest payload (v1.0.16+ camelCase event keys) -----

    @Test
    fun parseCamelCasePermissionRequestWithToolInputObject() {
        // Real payload captured from a live Copilot CLI run with hook key
        // "permissionRequest" (camelCase). toolInput is an OBJECT, not a
        // toolArgs string; there's a hookName field; there's permissionSuggestions.
        val json = """
            {
                "hookName": "permissionRequest",
                "sessionId": "d71c31a6-1907-4551-ab29-036d72da3b83",
                "timestamp": 1775740932526,
                "cwd": "/home/user/projects/example-app",
                "toolName": "web_fetch",
                "toolInput": { "url": "https://www.google.com" },
                "permissionSuggestions": []
            }
        """.trimIndent()

        val result = adapter.parse(json)
        assertNotNull(result)
        assertEquals("WebFetch", result.hookInput.toolName)
        assertEquals("d71c31a6-1907-4551-ab29-036d72da3b83", result.hookInput.sessionId)
        assertEquals("permissionRequest", result.hookInput.hookEventName)
        assertEquals(JsonPrimitive("https://www.google.com"), result.hookInput.toolInput["url"])
        assertEquals("/home/user/projects/example-app", result.hookInput.cwd)
        assertEquals(Source.COPILOT, result.source)
    }

    // ----- snake_case PascalCase payload (v1.0.21+ VS Code-compatible mode) -----

    @Test
    fun parseSnakeCasePermissionRequestPayload() {
        // PascalCase event keys ("PermissionRequest") on copilot-cli ≥ v1.0.21
        // deliver a snake_case payload identical to Claude Code's hook format.
        val json = """
            {
                "session_id": "abc123",
                "hook_event_name": "PermissionRequest",
                "tool_name": "web_fetch",
                "tool_input": { "url": "https://example.com" },
                "cwd": "/tmp"
            }
        """.trimIndent()

        val result = adapter.parse(json)
        assertNotNull(result)
        assertEquals("WebFetch", result.hookInput.toolName)
        assertEquals("abc123", result.hookInput.sessionId)
        assertEquals("PermissionRequest", result.hookInput.hookEventName)
        assertEquals(JsonPrimitive("https://example.com"), result.hookInput.toolInput["url"])
        assertEquals(Source.COPILOT, result.source)
    }

    @Test
    fun parseSnakeCasePreToolUsePayload() {
        val json = """
            {
                "session_id": "xyz",
                "hook_event_name": "PreToolUse",
                "tool_name": "run_terminal_cmd",
                "tool_input": { "command": "ls -la" },
                "cwd": "/home/user"
            }
        """.trimIndent()

        val result = adapter.parse(json)
        assertNotNull(result)
        assertEquals("Bash", result.hookInput.toolName)
        assertEquals("PreToolUse", result.hookInput.hookEventName)
        assertEquals(JsonPrimitive("ls -la"), result.hookInput.toolInput["command"])
    }

    @Test
    fun webFetchAliasMapsToWebFetch() {
        val json = """{"toolName":"web_fetch","toolInput":{"url":"https://x.io"},"timestamp":0,"cwd":""}"""
        val result = adapter.parse(json)
        assertNotNull(result)
        assertEquals("WebFetch", result.hookInput.toolName)
    }

    @Test
    fun fetchAliasStillMapsToWebFetch() {
        val json = """{"toolName":"fetch","toolArgs":"{\"url\":\"https://x.io\"}","timestamp":0,"cwd":""}"""
        val result = adapter.parse(json)
        assertNotNull(result)
        assertEquals("WebFetch", result.hookInput.toolName)
    }
}
