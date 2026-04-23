package com.mikepenz.agentbuddy.ui.approvals

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.mikepenz.agentbuddy.ui.theme.PreviewScaffold
import com.mikepenz.markdown.compose.components.markdownComponents
import com.mikepenz.markdown.compose.elements.highlightedCodeBlock
import com.mikepenz.markdown.compose.elements.highlightedCodeFence
import com.mikepenz.markdown.m3.Markdown
import com.mikepenz.markdown.m3.markdownColor
import com.mikepenz.agentbuddy.util.asStringOrNull
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonPrimitive

@Composable
fun BashContent(toolInput: Map<String, JsonElement>, cwd: String = "") {
    val command = toolInput["command"].asStringOrNull() ?: ""
    val description = toolInput["description"].asStringOrNull()

    Column(modifier = Modifier.fillMaxWidth()) {
        if (!description.isNullOrBlank()) {
            Text(
                text = description,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(bottom = 4.dp),
            )
        }
        if (cwd.isNotBlank()) {
            Text(
                text = cwd,
                style = MaterialTheme.typography.labelSmall.copy(fontFamily = FontFamily.Monospace),
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(bottom = 4.dp),
            )
        }
        Surface(
            modifier = Modifier.fillMaxWidth(),
            color = Color(0xFF1E1E1E),
            shape = MaterialTheme.shapes.small,
        ) {
            Box(modifier = Modifier.padding(horizontal = 8.dp)) {
                Markdown(
                    content = "```bash\n$command\n```",
                    colors = markdownColor(codeBackground = Color.Transparent),
                    components = markdownComponents(
                        codeFence = highlightedCodeFence,
                        codeBlock = highlightedCodeBlock,
                    ),
                )
            }
        }
    }
}

// -- Previews --

@Preview
@Composable
private fun PreviewBashSimple() {
    PreviewScaffold {
        Box(modifier = Modifier.width(350.dp).padding(12.dp)) {
            BashContent(
                toolInput = mapOf(
                    "command" to JsonPrimitive("git status && git diff HEAD"),
                    "description" to JsonPrimitive("Show working tree status"),
                ),
                cwd = "/Users/mike/project",
            )
        }
    }
}

@Preview
@Composable
private fun PreviewBashMultiline() {
    PreviewScaffold {
        Box(modifier = Modifier.width(350.dp).padding(12.dp)) {
            BashContent(
                toolInput = mapOf(
                    "command" to JsonPrimitive(
                        "javap -classpath ./build/tmp/kotlin-classes/debug com.example.PreviewsKt 2>/dev/null | grep \"Preview\" | head -20"
                    ),
                    "description" to JsonPrimitive("Check method signatures for Preview"),
                ),
                cwd = "/Users/mike/Development/compose-buddy",
            )
        }
    }
}

@Preview
@Composable
private fun PreviewBashNoDescription() {
    PreviewScaffold {
        Box(modifier = Modifier.width(350.dp).padding(12.dp)) {
            BashContent(
                toolInput = mapOf("command" to JsonPrimitive("ls -la")),
            )
        }
    }
}

fun bashPopOutContent(toolInput: Map<String, JsonElement>): String {
    val command = toolInput["command"].asStringOrNull() ?: ""
    val description = toolInput["description"].asStringOrNull()
    return buildString {
        if (!description.isNullOrBlank()) appendLine("**$description**\n")
        appendLine("```bash\n$command\n```")
    }
}
