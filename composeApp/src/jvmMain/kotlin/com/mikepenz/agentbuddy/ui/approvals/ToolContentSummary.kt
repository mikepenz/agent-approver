package com.mikepenz.agentbuddy.ui.approvals

import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.runtime.Composable
import com.mikepenz.agentbuddy.util.asStringOrNull
import kotlinx.serialization.json.JsonElement

@Composable
fun ToolContentSummary(toolName: String, toolInput: Map<String, JsonElement>, cwd: String = "") {
    SelectionContainer {
    when {
        toolName.equals("Bash", ignoreCase = true) -> BashContent(toolInput, cwd)
        toolName.equals("Read", ignoreCase = true) ||
                toolName.equals("Edit", ignoreCase = true) ||
                toolName.equals("Write", ignoreCase = true) -> FileOperationContent(toolName, toolInput)

        toolName.equals("Grep", ignoreCase = true) ||
                toolName.equals("Glob", ignoreCase = true) -> SearchContent(toolName, toolInput)

        toolName.equals("WebFetch", ignoreCase = true) -> WebFetchContent(toolInput)
        toolName.equals("WebSearch", ignoreCase = true) -> WebSearchContent(toolInput)
        else -> FallbackContent(toolInput)
    }
    }
}

/** Single-line plain-text summary of the tool operation for queue rows. */
fun toolSummaryText(toolName: String, toolInput: Map<String, JsonElement>): String = when {
    toolName.equals("Bash", ignoreCase = true) ->
        toolInput["command"].asStringOrNull() ?: toolName
    toolName.equals("Read", ignoreCase = true) ||
    toolName.equals("Edit", ignoreCase = true) ||
    toolName.equals("Write", ignoreCase = true) ||
    toolName.equals("MultiEdit", ignoreCase = true) ->
        toolInput["file_path"].asStringOrNull() ?: toolName
    toolName.equals("WebFetch", ignoreCase = true) ->
        toolInput["url"].asStringOrNull() ?: toolName
    toolName.equals("WebSearch", ignoreCase = true) ->
        toolInput["query"].asStringOrNull() ?: toolName
    toolName.equals("Grep", ignoreCase = true) ->
        buildString {
            toolInput["pattern"].asStringOrNull()?.let { append(it) }
            toolInput["path"].asStringOrNull()?.let { append(" in $it") }
        }.ifBlank { toolName }
    toolName.equals("Glob", ignoreCase = true) ->
        toolInput["pattern"].asStringOrNull() ?: toolName
    else ->
        toolInput.values.firstOrNull().asStringOrNull()?.take(120) ?: toolName
}

fun toolPopOutContent(toolName: String, toolInput: Map<String, JsonElement>): String {
    return when {
        toolName.equals("Bash", ignoreCase = true) -> bashPopOutContent(toolInput)
        toolName.equals("Read", ignoreCase = true) ||
                toolName.equals("Edit", ignoreCase = true) ||
                toolName.equals("Write", ignoreCase = true) -> fileOperationPopOutContent(toolName, toolInput)

        toolName.equals("Grep", ignoreCase = true) ||
                toolName.equals("Glob", ignoreCase = true) -> searchPopOutContent(toolName, toolInput)

        toolName.equals("WebFetch", ignoreCase = true) -> webFetchPopOutContent(toolInput)
        toolName.equals("WebSearch", ignoreCase = true) -> webSearchPopOutContent(toolInput)
        else -> {
            val json = kotlinx.serialization.json.Json { prettyPrint = true }
            "```json\n${json.encodeToString(kotlinx.serialization.json.JsonObject.serializer(), kotlinx.serialization.json.JsonObject(toolInput))}\n```"
        }
    }
}
