package com.mikepenz.agentbuddy.ui.approvals

import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.runtime.Composable
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
        else -> FallbackContent(toolInput)
    }
    }
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
        else -> {
            val json = kotlinx.serialization.json.Json { prettyPrint = true }
            "```json\n${json.encodeToString(kotlinx.serialization.json.JsonObject.serializer(), kotlinx.serialization.json.JsonObject(toolInput))}\n```"
        }
    }
}
