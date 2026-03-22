package com.mikepenz.agentapprover.ui.approvals

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mikepenz.agentapprover.ui.theme.ToolSearchColor
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonPrimitive

@Composable
fun SearchContent(toolName: String, toolInput: Map<String, JsonElement>) {
    val pattern = toolInput["pattern"]?.jsonPrimitive?.contentOrNull ?: ""
    val path = toolInput["path"]?.jsonPrimitive?.contentOrNull

    Column(modifier = Modifier.fillMaxWidth()) {
        Text(
            text = "Pattern",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Surface(
            modifier = Modifier.fillMaxWidth(),
            color = Color(0xFF1E1E1E),
            shape = MaterialTheme.shapes.small,
        ) {
            Text(
                text = pattern,
                modifier = Modifier.padding(8.dp),
                style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                color = ToolSearchColor,
            )
        }

        Spacer(Modifier.height(4.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            if (!path.isNullOrBlank()) {
                Text(
                    text = path,
                    style = MaterialTheme.typography.labelSmall.copy(fontFamily = FontFamily.Monospace),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false),
                )
            }

            if (toolName.equals("Grep", ignoreCase = true)) {
                val glob = toolInput["glob"]?.jsonPrimitive?.contentOrNull
                val outputMode = toolInput["output_mode"]?.jsonPrimitive?.contentOrNull
                val context = toolInput["context"]?.jsonPrimitive?.intOrNull

                if (!glob.isNullOrBlank()) {
                    SmallBadge(text = glob, color = ToolSearchColor)
                }
                if (!outputMode.isNullOrBlank() && outputMode != "files_with_matches") {
                    SmallBadge(text = outputMode, color = ToolSearchColor)
                }
                if (context != null && context > 0) {
                    SmallBadge(text = "\u00B1$context", color = ToolSearchColor)
                }
            }
        }
    }
}

@Composable
fun SmallBadge(text: String, color: Color) {
    Surface(
        shape = RoundedCornerShape(4.dp),
        color = color.copy(alpha = 0.15f),
        border = BorderStroke(0.5.dp, color.copy(alpha = 0.3f)),
    ) {
        Text(
            text = text,
            modifier = Modifier.padding(horizontal = 5.dp, vertical = 1.dp),
            color = color,
            fontSize = 9.sp,
            style = MaterialTheme.typography.labelSmall,
        )
    }
}

fun searchPopOutContent(toolName: String, toolInput: Map<String, JsonElement>): String {
    val pattern = toolInput["pattern"]?.jsonPrimitive?.contentOrNull ?: ""
    val path = toolInput["path"]?.jsonPrimitive?.contentOrNull ?: ""
    return buildString {
        appendLine("**Pattern:** `$pattern`")
        if (path.isNotBlank()) appendLine("**Path:** `$path`")
        if (toolName.equals("Grep", ignoreCase = true)) {
            val glob = toolInput["glob"]?.jsonPrimitive?.contentOrNull
            val outputMode = toolInput["output_mode"]?.jsonPrimitive?.contentOrNull
            if (!glob.isNullOrBlank()) appendLine("**Glob:** `$glob`")
            if (!outputMode.isNullOrBlank()) appendLine("**Mode:** $outputMode")
        }
    }
}
