package com.mikepenz.agentbelay.ui.approvals

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mikepenz.agentbelay.ui.theme.PreviewScaffold
import com.mikepenz.markdown.compose.components.markdownComponents
import com.mikepenz.markdown.compose.elements.highlightedCodeBlock
import com.mikepenz.markdown.compose.elements.highlightedCodeFence
import com.mikepenz.markdown.m3.Markdown
import com.mikepenz.markdown.m3.markdownColor
import com.mikepenz.agentbelay.util.asBooleanOrNull
import com.mikepenz.agentbelay.util.asIntOrNull
import com.mikepenz.agentbelay.util.asStringOrNull
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonPrimitive

@Composable
fun FileOperationContent(toolName: String, toolInput: Map<String, JsonElement>) {
    val filePath = toolInput["file_path"].asStringOrNull() ?: ""
    val extension = filePath.substringAfterLast('.', "")

    Column(modifier = Modifier.fillMaxWidth()) {
        // File path row
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(
                text = filePath,
                style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
        }

        Spacer(Modifier.height(4.dp))

        when (toolName.lowercase()) {
            "read" -> ReadDetail(toolInput)
            "edit" -> EditDetail(toolInput, extension)
            "write" -> WriteDetail(toolInput, extension)
        }
    }
}

@Composable
private fun ReadDetail(toolInput: Map<String, JsonElement>) {
    val offset = toolInput["offset"].asIntOrNull()
    val limit = toolInput["limit"].asIntOrNull()
    val rangeText = when {
        offset != null && limit != null -> "Lines ${offset + 1}\u2013${offset + limit}"
        offset != null -> "From line ${offset + 1}"
        limit != null -> "First $limit lines"
        else -> "Entire file"
    }
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
        shape = MaterialTheme.shapes.small,
    ) {
        Text(
            text = rangeText,
            modifier = Modifier.padding(8.dp),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun EditDetail(toolInput: Map<String, JsonElement>, extension: String) {
    val oldStr = toolInput["old_string"].asStringOrNull() ?: ""
    val newStr = toolInput["new_string"].asStringOrNull() ?: ""
    val replaceAll = toolInput["replace_all"].asBooleanOrNull() ?: false

    if (replaceAll) {
        Surface(
            shape = RoundedCornerShape(4.dp),
            color = Color(0xFFE63845).copy(alpha = 0.2f),
            border = BorderStroke(1.dp, Color(0xFFE63845).copy(alpha = 0.5f)),
        ) {
            Text(
                text = "Replace All",
                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                color = Color(0xFFE63845),
                fontSize = 10.sp,
                style = MaterialTheme.typography.labelSmall,
            )
        }
        Spacer(Modifier.height(4.dp))
    }

    val diffContent = buildString {
        appendLine("```diff")
        oldStr.lines().forEach { appendLine("- $it") }
        newStr.lines().forEach { appendLine("+ $it") }
        appendLine("```")
    }
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = Color(0xFF1E1E1E),
        shape = MaterialTheme.shapes.small,
    ) {
        Box(modifier = Modifier.padding(horizontal = 8.dp)) {
            Markdown(
                content = diffContent,
                colors = markdownColor(codeBackground = Color.Transparent),
                components = markdownComponents(
                    codeFence = highlightedCodeFence,
                    codeBlock = highlightedCodeBlock,
                ),
            )
        }
    }
}

@Composable
private fun WriteDetail(toolInput: Map<String, JsonElement>, extension: String) {
    val content = toolInput["content"].asStringOrNull() ?: ""
    var expanded by remember { mutableStateOf(false) }
    val lineCount = content.count { it == '\n' } + 1
    val canExpand = lineCount > 3
    val lang = languageFromExtension(extension)

    val codeBlock = "```$lang\n$content\n```"

    if (expanded && canExpand) {
        Surface(
            onClick = { expanded = false },
            modifier = Modifier.fillMaxWidth().heightIn(max = 300.dp),
            color = Color(0xFF1E1E1E),
            shape = MaterialTheme.shapes.small,
        ) {
            Box(modifier = Modifier.padding(horizontal = 8.dp)) {
                Markdown(
                    content = codeBlock,
                    colors = markdownColor(codeBackground = Color.Transparent),
                    components = markdownComponents(
                        codeFence = highlightedCodeFence,
                        codeBlock = highlightedCodeBlock,
                    ),
                )
            }
        }
    } else {
        Surface(
            onClick = { expanded = true },
            enabled = canExpand,
            modifier = Modifier.fillMaxWidth(),
            color = Color(0xFF1E1E1E),
            shape = MaterialTheme.shapes.small,
        ) {
            Box(modifier = Modifier.padding(horizontal = 8.dp)) {
                Text(
                    text = content,
                    style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                    color = Color(0xFFCCCCCC),
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

private fun languageFromExtension(ext: String): String = when (ext.lowercase()) {
    "kt", "kts" -> "kotlin"
    "java" -> "java"
    "py" -> "python"
    "js" -> "javascript"
    "ts" -> "typescript"
    "tsx" -> "typescript"
    "jsx" -> "javascript"
    "json" -> "json"
    "xml" -> "xml"
    "yaml", "yml" -> "yaml"
    "toml" -> "toml"
    "md" -> "markdown"
    "sh", "bash", "zsh" -> "bash"
    "swift" -> "swift"
    "rs" -> "rust"
    "go" -> "go"
    "rb" -> "ruby"
    "css" -> "css"
    "html" -> "html"
    "sql" -> "sql"
    "gradle" -> "groovy"
    else -> ""
}

// -- Previews --

@Preview
@Composable
private fun PreviewReadWithRange() {
    PreviewScaffold {
        Box(modifier = Modifier.width(350.dp).padding(12.dp)) {
            FileOperationContent(
                toolName = "Read",
                toolInput = mapOf(
                    "file_path" to JsonPrimitive("/tmp/paparazzi/paparazzi/src/main/java/app/cash/paparazzi/PaparazziSdk.kt"),
                    "offset" to JsonPrimitive(199),
                    "limit" to JsonPrimitive(120),
                ),
            )
        }
    }
}

@Preview
@Composable
private fun PreviewReadEntireFile() {
    PreviewScaffold {
        Box(modifier = Modifier.width(350.dp).padding(12.dp)) {
            FileOperationContent(
                toolName = "Read",
                toolInput = mapOf(
                    "file_path" to JsonPrimitive("/tmp/paparazzi/paparazzi/src/main/java/app/cash/paparazzi/internal/Renderer.kt"),
                ),
            )
        }
    }
}

@Preview
@Composable
private fun PreviewEdit() {
    PreviewScaffold {
        Box(modifier = Modifier.width(350.dp).padding(12.dp)) {
            FileOperationContent(
                toolName = "Edit",
                toolInput = mapOf(
                    "file_path" to JsonPrimitive("/Users/mike/project/src/main/kotlin/App.kt"),
                    "old_string" to JsonPrimitive("fun greet() = \"Hello\""),
                    "new_string" to JsonPrimitive("fun greet(name: String) = \"Hello, \$name\""),
                ),
            )
        }
    }
}

@Preview
@Composable
private fun PreviewEditReplaceAll() {
    PreviewScaffold {
        Box(modifier = Modifier.width(350.dp).padding(12.dp)) {
            FileOperationContent(
                toolName = "Edit",
                toolInput = mapOf(
                    "file_path" to JsonPrimitive("/Users/mike/project/build.gradle.kts"),
                    "old_string" to JsonPrimitive("implementation"),
                    "new_string" to JsonPrimitive("api"),
                    "replace_all" to JsonPrimitive(true),
                ),
            )
        }
    }
}

@Preview
@Composable
private fun PreviewWrite() {
    PreviewScaffold {
        Box(modifier = Modifier.width(350.dp).padding(12.dp)) {
            FileOperationContent(
                toolName = "Write",
                toolInput = mapOf(
                    "file_path" to JsonPrimitive("/Users/mike/project/specs/quickstart.md"),
                    "content" to JsonPrimitive("# Quickstart: Compose Buddy\n\n## Prerequisites\n\n- Android project using Jetpack Compose\n- Gradle 8.x+ with Kotlin DSL\n- AGP 8.0+\n\n## Installation\n\nAdd the plugin to your build.gradle.kts"),
                ),
            )
        }
    }
}

fun fileOperationPopOutContent(toolName: String, toolInput: Map<String, JsonElement>): String {
    val filePath = toolInput["file_path"].asStringOrNull() ?: ""
    val extension = filePath.substringAfterLast('.', "")
    val lang = languageFromExtension(extension)
    return when (toolName.lowercase()) {
        "read" -> {
            val offset = toolInput["offset"].asIntOrNull()
            val limit = toolInput["limit"].asIntOrNull()
            val range = when {
                offset != null && limit != null -> "Lines ${offset + 1}\u2013${offset + limit}"
                offset != null -> "From line ${offset + 1}"
                limit != null -> "First $limit lines"
                else -> "Entire file"
            }
            "**File:** `$filePath`\n\n$range"
        }

        "edit" -> {
            val oldStr = toolInput["old_string"].asStringOrNull() ?: ""
            val newStr = toolInput["new_string"].asStringOrNull() ?: ""
            val replaceAll = toolInput["replace_all"].asBooleanOrNull() ?: false
            buildString {
                appendLine("**File:** `$filePath`")
                if (replaceAll) appendLine("**\u26A0 Replace All**")
                appendLine()
                appendLine("```diff")
                oldStr.lines().forEach { appendLine("- $it") }
                newStr.lines().forEach { appendLine("+ $it") }
                appendLine("```")
            }
        }

        "write" -> {
            val content = toolInput["content"].asStringOrNull() ?: ""
            "**File:** `$filePath`\n\n```$lang\n$content\n```"
        }

        else -> "`$filePath`"
    }
}
