package com.mikepenz.agentbuddy.risk

import com.mikepenz.agentbuddy.model.HookInput
import kotlinx.serialization.json.JsonPrimitive
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertFalse

class RiskMessageBuilderTest {

    private fun hookInput(
        toolName: String,
        toolInput: Map<String, kotlinx.serialization.json.JsonElement>,
        cwd: String = "/project",
        agentType: String? = null,
    ) = HookInput(
        sessionId = "test-session",
        toolName = toolName,
        toolInput = toolInput,
        cwd = cwd,
        agentType = agentType,
    )

    // --- buildUserMessage ---

    @Test
    fun bashToolIncludesCommand() {
        val input = hookInput("Bash", mapOf("command" to JsonPrimitive("ls -la")))
        val message = RiskMessageBuilder.buildUserMessage(input)
        assertContains(message, "Tool: Bash")
        assertContains(message, "ls -la")
    }

    @Test
    fun editToolIncludesFileParts() {
        val input = hookInput(
            "Edit",
            mapOf(
                "file_path" to JsonPrimitive("/src/Main.kt"),
                "old_string" to JsonPrimitive("foo"),
                "new_string" to JsonPrimitive("bar"),
            ),
        )
        val message = RiskMessageBuilder.buildUserMessage(input)
        assertContains(message, "Tool: Edit")
        assertContains(message, "File: /src/Main.kt")
        assertContains(message, "Old: foo")
        assertContains(message, "New: bar")
    }

    @Test
    fun includesWorkingDirectory() {
        val input = hookInput("Bash", mapOf("command" to JsonPrimitive("pwd")), cwd = "/home/dev/project")
        val message = RiskMessageBuilder.buildUserMessage(input)
        assertContains(message, "Working Directory: /home/dev/project")
    }

    @Test
    fun includesAgentType() {
        val input = hookInput("Bash", mapOf("command" to JsonPrimitive("ls")), agentType = "task")
        val message = RiskMessageBuilder.buildUserMessage(input)
        assertContains(message, "Agent: task")
    }

    @Test
    fun omitsAgentTypeWhenNull() {
        val input = hookInput("Bash", mapOf("command" to JsonPrimitive("ls")))
        val message = RiskMessageBuilder.buildUserMessage(input)
        assertFalse(message.contains("Agent:"))
    }

    @Test
    fun omitsBlankCwd() {
        val input = hookInput("Bash", mapOf("command" to JsonPrimitive("ls")), cwd = "")
        val message = RiskMessageBuilder.buildUserMessage(input)
        assertFalse(message.contains("Working Directory:"))
    }

    @Test
    fun unknownToolFormatsAsKeyValue() {
        val input = hookInput(
            "WebFetch",
            mapOf("url" to JsonPrimitive("https://example.com"), "prompt" to JsonPrimitive("get data")),
        )
        val message = RiskMessageBuilder.buildUserMessage(input)
        assertContains(message, "Tool: WebFetch")
        assertContains(message, "url: https://example.com")
        assertContains(message, "prompt: get data")
    }

    // --- DEFAULT_SYSTEM_PROMPT ---

    @Test
    fun systemPromptIsNotEmpty() {
        assert(RiskMessageBuilder.DEFAULT_SYSTEM_PROMPT.isNotBlank())
        assertContains(RiskMessageBuilder.DEFAULT_SYSTEM_PROMPT, "risk level")
    }
}
