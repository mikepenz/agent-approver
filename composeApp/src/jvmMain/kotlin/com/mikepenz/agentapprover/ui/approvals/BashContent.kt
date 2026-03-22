package com.mikepenz.agentapprover.ui.approvals

import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.mikepenz.markdown.m3.Markdown
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonPrimitive

@Composable
fun BashContent(toolInput: Map<String, JsonElement>, cwd: String = "") {
    val command = toolInput["command"]?.jsonPrimitive?.contentOrNull ?: ""
    val description = toolInput["description"]?.jsonPrimitive?.contentOrNull

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
            Box(modifier = Modifier.padding(8.dp)) {
                Markdown(content = "```bash\n$command\n```")
            }
        }
    }
}

fun bashPopOutContent(toolInput: Map<String, JsonElement>): String {
    val command = toolInput["command"]?.jsonPrimitive?.contentOrNull ?: ""
    val description = toolInput["description"]?.jsonPrimitive?.contentOrNull
    return buildString {
        if (!description.isNullOrBlank()) appendLine("**$description**\n")
        appendLine("```bash\n$command\n```")
    }
}
