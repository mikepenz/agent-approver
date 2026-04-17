package com.mikepenz.agentbuddy.protection

import com.mikepenz.agentbuddy.model.HookInput
import kotlinx.serialization.json.JsonPrimitive
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class CommandParserTest {

    private fun hookInput(toolName: String, toolInput: Map<String, kotlinx.serialization.json.JsonElement>) = HookInput(
        sessionId = "test-session",
        toolName = toolName,
        toolInput = toolInput,
        cwd = "/project",
    )

    // --- bashCommand ---

    @Test
    fun bashCommandReturnsBashInput() {
        val input = hookInput("Bash", mapOf("command" to JsonPrimitive("ls -la")))
        assertEquals("ls -la", CommandParser.bashCommand(input))
    }

    @Test
    fun bashCommandReturnsNullForNonBash() {
        val input = hookInput("Edit", mapOf("command" to JsonPrimitive("ls -la")))
        assertNull(CommandParser.bashCommand(input))
    }

    @Test
    fun bashCommandReturnsNullWhenMissing() {
        val input = hookInput("Bash", emptyMap())
        assertNull(CommandParser.bashCommand(input))
    }

    // --- filePath ---

    @Test
    fun filePathExtractsPath() {
        val input = hookInput("Edit", mapOf("file_path" to JsonPrimitive("/src/main.kt")))
        assertEquals("/src/main.kt", CommandParser.filePath(input))
    }

    @Test
    fun filePathReturnsNullWhenMissing() {
        val input = hookInput("Edit", emptyMap())
        assertNull(CommandParser.filePath(input))
    }

    // --- extractPaths ---

    @Test
    fun extractPathsFindsRelativePaths() {
        val paths = CommandParser.extractPaths("cat ./src/main.kt ./test/Test.kt")
        assertEquals(listOf("./src/main.kt", "./test/Test.kt"), paths)
    }

    @Test
    fun extractPathsFindsAbsolutePaths() {
        val paths = CommandParser.extractPaths("cat /usr/local/bin/tool")
        assertEquals(listOf("/usr/local/bin/tool"), paths)
    }

    @Test
    fun extractPathsFindsHomePaths() {
        val paths = CommandParser.extractPaths("cat ~/Documents/file.txt")
        assertEquals(listOf("~/Documents/file.txt"), paths)
    }

    @Test
    fun extractPathsReturnsEmptyForNoMatch() {
        val paths = CommandParser.extractPaths("echo hello world")
        assertEquals(emptyList(), paths)
    }

    // --- countChainedCommands ---

    @Test
    fun singleCommand() {
        assertEquals(1, CommandParser.countChainedCommands("ls -la"))
    }

    @Test
    fun semicolonChain() {
        assertEquals(2, CommandParser.countChainedCommands("cd /tmp; ls"))
    }

    @Test
    fun andChain() {
        assertEquals(2, CommandParser.countChainedCommands("mkdir build && cd build"))
    }

    @Test
    fun orChain() {
        assertEquals(2, CommandParser.countChainedCommands("test -f file || echo missing"))
    }

    @Test
    fun mixedChain() {
        assertEquals(4, CommandParser.countChainedCommands("a && b; c || d"))
    }
}
