package com.mikepenz.agentbelay.ui.approvals

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.mikepenz.agentbelay.ui.theme.PreviewScaffold
import com.mikepenz.agentbelay.ui.theme.ToolWebColor
import com.mikepenz.agentbelay.util.asArrayOrNull
import com.mikepenz.agentbelay.util.asStringOrNull
import com.mikepenz.agentbelay.util.strings
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonPrimitive

@Composable
fun WebSearchContent(toolInput: Map<String, JsonElement>) {
    val query = toolInput["query"].asStringOrNull() ?: ""
    val allowed = toolInput["allowed_domains"].asArrayOrNull()?.strings().orEmpty()
    val blocked = toolInput["blocked_domains"].asArrayOrNull()?.strings().orEmpty()

    Column(modifier = Modifier.fillMaxWidth()) {
        Surface(
            modifier = Modifier.fillMaxWidth(),
            color = ToolWebColor.copy(alpha = 0.08f),
            shape = MaterialTheme.shapes.small,
        ) {
            Text(
                text = query,
                modifier = Modifier.padding(8.dp),
                style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                color = ToolWebColor,
                maxLines = 4,
                overflow = TextOverflow.Ellipsis,
            )
        }
        if (allowed.isNotEmpty() || blocked.isNotEmpty()) {
            Spacer(Modifier.height(4.dp))
            Surface(
                modifier = Modifier.fillMaxWidth(),
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                shape = MaterialTheme.shapes.small,
            ) {
                val text = buildString {
                    if (allowed.isNotEmpty()) append("Allowed: ${allowed.joinToString(", ")}")
                    if (blocked.isNotEmpty()) {
                        if (isNotEmpty()) append('\n')
                        append("Blocked: ${blocked.joinToString(", ")}")
                    }
                }
                Text(
                    text = text,
                    modifier = Modifier.padding(8.dp),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 4,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

fun webSearchPopOutContent(toolInput: Map<String, JsonElement>): String {
    val query = toolInput["query"].asStringOrNull() ?: ""
    val allowed = toolInput["allowed_domains"].asArrayOrNull()?.strings().orEmpty()
    val blocked = toolInput["blocked_domains"].asArrayOrNull()?.strings().orEmpty()
    return buildString {
        appendLine("**Query:** $query")
        if (allowed.isNotEmpty()) {
            appendLine()
            appendLine("Allowed domains: ${allowed.joinToString(", ")}")
        }
        if (blocked.isNotEmpty()) {
            appendLine()
            appendLine("Blocked domains: ${blocked.joinToString(", ")}")
        }
    }
}

@Preview
@Composable
private fun PreviewWebSearch() {
    PreviewScaffold {
        Box(modifier = Modifier.width(350.dp).padding(12.dp)) {
            WebSearchContent(
                toolInput = mapOf(
                    "query" to JsonPrimitive("LLM generative UI security constrained component catalog zod"),
                ),
            )
        }
    }
}

@Preview
@Composable
private fun PreviewWebSearchWithDomains() {
    PreviewScaffold {
        Box(modifier = Modifier.width(350.dp).padding(12.dp)) {
            WebSearchContent(
                toolInput = mapOf(
                    "query" to JsonPrimitive("kotlin coroutines structured concurrency"),
                    "allowed_domains" to JsonArray(listOf(JsonPrimitive("kotlinlang.org"))),
                ),
            )
        }
    }
}
