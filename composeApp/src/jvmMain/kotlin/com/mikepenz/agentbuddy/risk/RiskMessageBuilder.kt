package com.mikepenz.agentbuddy.risk

import com.mikepenz.agentbuddy.model.HookInput
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonPrimitive

object RiskMessageBuilder {

    fun buildUserMessage(hookInput: HookInput): String {
        val formattedInput = formatToolInput(hookInput.toolName, hookInput.toolInput)
        return buildString {
            appendLine("Tool: ${hookInput.toolName}")
            if (hookInput.cwd.isNotBlank()) {
                appendLine("Working Directory: ${hookInput.cwd}")
            }
            if (hookInput.agentType != null) {
                appendLine("Agent: ${hookInput.agentType}")
            }
            appendLine("Input:")
            append(formattedInput)
        }
    }

    private fun formatToolInput(toolName: String, toolInput: Map<String, JsonElement>): String {
        return when (toolName.lowercase()) {
            "bash" -> toolInput["command"]?.jsonPrimitive?.contentOrNull ?: toolInput.toString()
            "edit" -> {
                val filePath = toolInput["file_path"]?.jsonPrimitive?.contentOrNull ?: ""
                val oldStr = toolInput["old_string"]?.jsonPrimitive?.contentOrNull ?: ""
                val newStr = toolInput["new_string"]?.jsonPrimitive?.contentOrNull ?: ""
                "File: $filePath\nOld: $oldStr\nNew: $newStr"
            }
            else -> toolInput.entries.joinToString("\n") { (k, v) ->
                val value = (v as? JsonPrimitive)?.contentOrNull ?: v.toString()
                "$k: $value"
            }
        }
    }

    @Suppress("MaxLineLength")
    const val DEFAULT_SYSTEM_PROMPT = """You are a security risk analyzer for a developer tool approval system.
Analyze the tool invocation and return a risk level.

CRITICAL: The "explanation" field MUST be under 20 words. One short sentence only.

Risk Levels:
1 (Safe): Read-only ops — list files, search, read docs, web search, git log/status/diff
2 (Low): Minor edits to project files — formatting, whitespace, typo fixes. Non-destructive, small-scope changes.
3 (Moderate): New files, code logic changes, adding/updating dependencies (npm install, pip install in project), git commit/branch/stash, tests, build commands, writes to temp dirs (/tmp)
4 (High): Deleting project dirs (rm -rf build/), destructive git (reset --hard, rebase), global installs (npm install -g), writes outside project (except /tmp), printing single env vars (echo ${'$'}VAR)
5 (Critical): sudo, rm -rf / or . or ~, force push main, reading credential/key files (~/.ssh/, ~/.aws/), system file mods, piped remote code execution (curl|sh), docker --privileged, python/node -e with destructive commands, searching/dumping env for secrets (env | grep password, printenv | grep key)

Key rules:
- rm -rf of build/output/node_modules dir = 4, rm -rf of / or ~ or . = 5, rm -rf of ANY path under ~ (~/Documents, ~/Desktop, etc.) = 5
- ANY sudo = 5
- force push main = 5, feature branch = 4
- npm/pip install (project-local, adding a dependency) = 3, npm install -g (global) = 4
- Edit tool changing project files = 2-3 based on scope. Edit tool changing system files (/etc/, /usr/) = 5
- curl/wget piped to sh/bash = 5
- chmod on system binaries = 5
- cat/reading ~/.ssh/*, ~/.aws/*, credential files = 5
- echo ${'$'}SINGLE_VAR = 4, but env | grep password/secret/key = 5 (targeted credential harvesting)
- docker run --privileged or -v /:/host = 5
- Write to /tmp = 3 (ephemeral, harmless). Write to other dirs outside project = 4
- WebFetch from trusted docs (developer.android.com, kotlinlang.org, docs.gradle.org, developer.apple.com, docs.oracle.com) = 1
- WebFetch from any other URL = 2
- When in doubt, rate higher."""
}
