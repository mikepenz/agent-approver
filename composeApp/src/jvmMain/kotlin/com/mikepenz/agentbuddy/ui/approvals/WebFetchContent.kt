package com.mikepenz.agentbuddy.ui.approvals

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.mikepenz.agentbuddy.ui.icons.FeatherExternalLink
import com.mikepenz.agentbuddy.ui.theme.PreviewScaffold
import com.mikepenz.agentbuddy.ui.theme.ToolWebColor
import com.mikepenz.agentbuddy.util.asStringOrNull
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonPrimitive
import java.awt.Desktop
import java.net.URI

@Composable
fun WebFetchContent(toolInput: Map<String, JsonElement>) {
    val url = toolInput["url"].asStringOrNull() ?: ""
    val prompt = toolInput["prompt"].asStringOrNull()

    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Surface(
                modifier = Modifier.weight(1f),
                color = ToolWebColor.copy(alpha = 0.08f),
                shape = MaterialTheme.shapes.small,
            ) {
                Text(
                    text = url,
                    modifier = Modifier.padding(8.dp),
                    style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                    color = ToolWebColor,
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            if (url.isNotBlank()) {
                IconButton(
                    onClick = {
                        try {
                            Desktop.getDesktop().browse(URI(url))
                        } catch (_: Exception) {
                        }
                    },
                    modifier = Modifier.size(28.dp),
                ) {
                    Icon(
                        imageVector = FeatherExternalLink,
                        contentDescription = "Open in browser",
                        modifier = Modifier.size(14.dp),
                        tint = ToolWebColor,
                    )
                }
            }
        }

        if (!prompt.isNullOrBlank()) {
            Spacer(Modifier.height(4.dp))
            Surface(
                modifier = Modifier.fillMaxWidth(),
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                shape = MaterialTheme.shapes.small,
            ) {
                Text(
                    text = prompt,
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

// -- Previews --

@Preview
@Composable
private fun PreviewWebFetchWithPrompt() {
    PreviewScaffold {
        Box(modifier = Modifier.width(350.dp).padding(12.dp)) {
            WebFetchContent(
                toolInput = mapOf(
                    "url" to JsonPrimitive("https://mvnrepository.com/artifact/com.android.tools.layoutlib/layoutlib-runtime"),
                    "prompt" to JsonPrimitive("What is the latest version of layoutlib-runtime? List the recent versions."),
                ),
            )
        }
    }
}

@Preview
@Composable
private fun PreviewWebFetchGitHub() {
    PreviewScaffold {
        Box(modifier = Modifier.width(350.dp).padding(12.dp)) {
            WebFetchContent(
                toolInput = mapOf(
                    "url" to JsonPrimitive("https://github.com/cashapp/paparazzi/blob/master/paparazzi/src/main/java/app/cash/paparazzi/Paparazzi.kt"),
                    "prompt" to JsonPrimitive("Extract the full class structure, key methods, how layoutlib is invoked, how rendering is set up, and how snapshots are captured."),
                ),
            )
        }
    }
}

@Preview
@Composable
private fun PreviewWebFetchNoPrompt() {
    PreviewScaffold {
        Box(modifier = Modifier.width(350.dp).padding(12.dp)) {
            WebFetchContent(
                toolInput = mapOf(
                    "url" to JsonPrimitive("https://www.google.com"),
                ),
            )
        }
    }
}

fun webFetchPopOutContent(toolInput: Map<String, JsonElement>): String {
    val url = toolInput["url"].asStringOrNull() ?: ""
    val prompt = toolInput["prompt"].asStringOrNull()
    return buildString {
        appendLine("**URL:** $url")
        if (!prompt.isNullOrBlank()) {
            appendLine()
            appendLine("> $prompt")
        }
    }
}
