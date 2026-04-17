package com.mikepenz.agentbuddy.protection

import com.mikepenz.agentbuddy.model.HookInput
import com.mikepenz.agentbuddy.protection.parser.ParsedCommand
import com.mikepenz.agentbuddy.protection.parser.allLiteralPaths
import com.mikepenz.agentbuddy.protection.parser.chainedCommandCount
import com.mikepenz.agentbuddy.protection.parser.parseShellCommand
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import java.util.Collections
import java.util.WeakHashMap

object CommandParser {
    // Cache parsed bash commands per HookInput so each hook request only pays the parse cost
    // once even though every module invokes parsedBash() independently.
    private val parseCache: MutableMap<HookInput, ParsedCommand> =
        Collections.synchronizedMap(WeakHashMap())

    fun bashCommand(hookInput: HookInput): String? {
        if (hookInput.toolName != "Bash") return null
        val cmd = hookInput.toolInput["command"]
        return (cmd as? JsonPrimitive)?.contentOrNull
    }

    fun filePath(hookInput: HookInput): String? {
        val path = hookInput.toolInput["file_path"]
        return (path as? JsonPrimitive)?.contentOrNull
    }

    /** Parse the Bash tool command into a structured [ParsedCommand]. Cached per [hookInput]. */
    internal fun parsedBash(hookInput: HookInput): ParsedCommand? {
        val cmd = bashCommand(hookInput) ?: return null
        return parseCache.getOrPut(hookInput) { parseShellCommand(cmd) }
    }

    /**
     * Returns literal file-like paths recovered from the parsed command, including redirect
     * targets and positional arguments from every simple command in the chain (recursively).
     * Opaque tokens (variables, substitutions) are excluded — rules that need fail-closed
     * behavior should query [ParsedCommand] directly via [allLiteralPaths].
     */
    fun extractPaths(command: String): List<String> =
        parseShellCommand(command).allLiteralPaths()
            .filter { it.startsWith("./") || it.startsWith("/") || it.startsWith("~") }

    fun countChainedCommands(command: String): Int = parseShellCommand(command).chainedCommandCount()
}
