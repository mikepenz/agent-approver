package com.mikepenz.agentbelay.ui.history

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mikepenz.agentbelay.model.ToolType
import com.mikepenz.agentbelay.ui.components.HorizontalHairline
import com.mikepenz.agentbelay.ui.components.IconActionButton
import com.mikepenz.agentbelay.ui.components.LocalPreviewHoverOverride
import com.mikepenz.agentbelay.ui.components.MetadataField
import com.mikepenz.agentbelay.ui.icons.FeatherExternalLink
import com.mikepenz.agentbelay.ui.icons.LucideChevronDown
import com.mikepenz.agentbelay.ui.icons.LucideChevronRight
import com.mikepenz.agentbelay.ui.icons.LucideCopy
import com.mikepenz.agentbelay.ui.icons.LucideGlobe
import com.mikepenz.agentbelay.ui.theme.AgentBelayColors
import com.mikepenz.agentbelay.ui.theme.ToolWeb
import com.mikepenz.agentbelay.ui.theme.WarnYellow
import com.mikepenz.agentbelay.util.asArrayOrNull
import com.mikepenz.agentbelay.util.asBooleanOrNull
import com.mikepenz.agentbelay.util.asObjectOrNull
import com.mikepenz.agentbelay.util.asStringOrNull
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import java.awt.Desktop
import java.net.URI

private val prettyJson = Json { prettyPrint = true; ignoreUnknownKeys = true }

@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun HistoryExpandedDetails(
    entry: HistoryEntry,
    onReplay: ((id: String) -> Unit)? = null,
    compact: Boolean = false,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(
                start = if (compact) 16.dp else 64.dp,
                end = if (compact) 16.dp else 28.dp,
                bottom = if (compact) 14.dp else 20.dp,
            ),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        if (onReplay != null) {
            DevReplayRow(onClick = { onReplay(entry.id) })
        }
        ContextBlock(entry)
        ToolDetailBlock(entry)
        if (!entry.prompt.isNullOrBlank() && entry.toolType != ToolType.ASK_USER_QUESTION) {
            PromptBlock(entry.prompt)
        }
        RawJsonBlock(title = "Raw request", json = entry.rawRequestJson)
        RawJsonBlock(title = "Raw response", json = entry.rawResponseJson)
        RawJsonBlock(title = "Raw validation response", json = entry.rawValidationResponseJson)
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun DevReplayRow(onClick: () -> Unit) {
    val interactionSource = remember { MutableInteractionSource() }
    val liveHover by interactionSource.collectIsHoveredAsState()
    val hovered = LocalPreviewHoverOverride.current ?: liveHover
    val borderAlpha = if (hovered) 0.9f else 0.55f
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(WarnYellow.copy(alpha = if (hovered) 0.14f else 0.08f))
            .border(1.dp, WarnYellow.copy(alpha = borderAlpha), RoundedCornerShape(8.dp))
            .hoverable(interactionSource)
            .clickable(interactionSource = interactionSource, indication = null) { onClick() }
            .padding(horizontal = 14.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Box(
            modifier = Modifier
                .clip(RoundedCornerShape(3.dp))
                .background(WarnYellow.copy(alpha = 0.22f))
                .padding(horizontal = 6.dp, vertical = 1.dp),
        ) {
            Text(
                text = "DEV",
                color = WarnYellow,
                fontSize = 10.sp,
                fontWeight = FontWeight.SemiBold,
                letterSpacing = 0.5.sp,
                fontFamily = FontFamily.Monospace,
            )
        }
        Text(
            text = "Replay as new approval",
            color = AgentBelayColors.inkPrimary,
            fontSize = 12.sp,
            fontWeight = FontWeight.Medium,
            modifier = Modifier.weight(1f),
        )
        Text(
            text = "→",
            color = AgentBelayColors.inkMuted,
            fontSize = 14.sp,
        )
    }
}

/* ── Context (assessment + meta) ─────────────────────────────────────── */

@Composable
private fun ContextBlock(entry: HistoryEntry) {
    val hasAssessment = entry.assessment != null
    val hasMeta = entry.workingDir != null || entry.timeToDecision != null || entry.feedback != null
    if (!hasAssessment && !hasMeta) return

    DetailCard {
        if (hasAssessment) {
            DetailField(label = "Risk assessment") {
                Text(
                    text = entry.assessment.orEmpty(),
                    color = AgentBelayColors.inkSecondary,
                    fontSize = 12.sp,
                    lineHeight = 18.sp,
                )
            }
        }
        if (hasMeta) {
            Row(horizontalArrangement = Arrangement.spacedBy(14.dp), modifier = Modifier.fillMaxWidth()) {
                DetailField(label = "Working directory", modifier = Modifier.weight(1f)) {
                    Text(
                        text = entry.workingDir.orEmpty().ifBlank { "—" },
                        color = AgentBelayColors.inkPrimary,
                        fontSize = 12.sp,
                        fontFamily = FontFamily.Monospace,
                    )
                }
                DetailField(label = "Decision time", modifier = Modifier.weight(1f)) {
                    Text(
                        text = entry.timeToDecision.orEmpty().ifBlank { "—" },
                        color = AgentBelayColors.inkPrimary,
                        fontSize = 12.sp,
                        fontFamily = FontFamily.Monospace,
                    )
                }
                DetailField(label = "Feedback", modifier = Modifier.weight(1f)) {
                    Text(
                        text = entry.feedback ?: "—",
                        color = AgentBelayColors.inkSecondary,
                        fontSize = 12.sp,
                    )
                }
            }
        }
    }
}

/* ── Tool-specific detail router ─────────────────────────────────────── */

@Composable
private fun ToolDetailBlock(entry: HistoryEntry) {
    if (entry.toolType == ToolType.ASK_USER_QUESTION) {
        AskUserQuestionDetail(entry.toolInput)
        return
    }
    when (entry.tool.lowercase()) {
        "bash" -> BashDetail(entry.toolInput)
        "webfetch" -> WebFetchDetail(entry.toolInput)
        "edit" -> EditDetail(entry.toolInput)
        "write" -> WriteDetail(entry.toolInput)
        "read" -> ReadDetail(entry.toolInput)
        else -> {}
    }
}

@Composable
private fun BashDetail(toolInput: Map<String, JsonElement>) {
    val command = toolInput["command"].asStringOrNull().orEmpty()
    val description = toolInput["description"].asStringOrNull()
    if (command.isBlank() && description.isNullOrBlank()) return

    DetailCard {
        if (!description.isNullOrBlank()) {
            DetailField(label = "Description") {
                Text(
                    text = description,
                    color = AgentBelayColors.inkSecondary,
                    fontSize = 12.sp,
                    lineHeight = 18.sp,
                )
            }
        }
        if (command.isNotBlank()) {
            LabeledCodeBlock(
                label = "Command",
                code = command,
                copyPayload = command,
            )
        }
    }
}

@Composable
private fun WebFetchDetail(toolInput: Map<String, JsonElement>) {
    val url = toolInput["url"].asStringOrNull().orEmpty()
    val prompt = toolInput["prompt"].asStringOrNull()
    if (url.isBlank() && prompt.isNullOrBlank()) return

    DetailCard {
        if (url.isNotBlank()) {
            DetailField(label = "URL") {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(6.dp))
                        .background(AgentBelayColors.surface)
                        .border(1.dp, AgentBelayColors.line1, RoundedCornerShape(6.dp))
                        .padding(horizontal = 12.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    Icon(
                        imageVector = LucideGlobe,
                        contentDescription = null,
                        tint = ToolWeb,
                        modifier = Modifier.size(14.dp),
                    )
                    Text(
                        text = url,
                        color = ToolWeb,
                        fontSize = 12.sp,
                        fontFamily = FontFamily.Monospace,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f),
                    )
                    IconActionButton(
                        icon = LucideCopy,
                        contentDescription = "Copy URL",
                        onClick = { copyToClipboard(url) },
                    )
                    IconActionButton(
                        icon = FeatherExternalLink,
                        contentDescription = "Open in browser",
                        onClick = { openInBrowser(url) },
                    )
                }
            }
        }
        if (!prompt.isNullOrBlank()) {
            DetailField(label = "Prompt") {
                Text(
                    text = prompt,
                    color = AgentBelayColors.inkSecondary,
                    fontSize = 12.sp,
                    lineHeight = 18.sp,
                )
            }
        }
    }
}

@Composable
private fun EditDetail(toolInput: Map<String, JsonElement>) {
    val path = toolInput["file_path"].asStringOrNull().orEmpty()
    val oldStr = toolInput["old_string"].asStringOrNull().orEmpty()
    val newStr = toolInput["new_string"].asStringOrNull().orEmpty()
    val replaceAll = toolInput["replace_all"].asBooleanOrNull() == true
    if (path.isBlank() && oldStr.isBlank() && newStr.isBlank()) return

    DetailCard {
        if (path.isNotBlank()) FilePathField(path)
        if (replaceAll) {
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(3.dp))
                    .background(Color(0xFFE63845).copy(alpha = 0.18f))
                    .padding(horizontal = 6.dp, vertical = 1.dp),
            ) {
                Text(
                    text = "REPLACE ALL",
                    color = Color(0xFFE63845),
                    fontSize = 10.sp,
                    fontWeight = FontWeight.SemiBold,
                    letterSpacing = 0.5.sp,
                )
            }
        }
        DetailField(label = "Diff") {
            DiffBlock(oldStr = oldStr, newStr = newStr)
        }
    }
}

@Composable
private fun WriteDetail(toolInput: Map<String, JsonElement>) {
    val path = toolInput["file_path"].asStringOrNull().orEmpty()
    val content = toolInput["content"].asStringOrNull().orEmpty()
    if (path.isBlank() && content.isBlank()) return

    DetailCard {
        if (path.isNotBlank()) FilePathField(path)
        if (content.isNotBlank()) {
            LabeledCodeBlock(label = "Content", code = content, copyPayload = content, maxHeight = 260.dp)
        }
    }
}

@Composable
private fun ReadDetail(toolInput: Map<String, JsonElement>) {
    val path = toolInput["file_path"].asStringOrNull().orEmpty()
    val offset = toolInput["offset"].asStringOrNull()?.toIntOrNull()
    val limit = toolInput["limit"].asStringOrNull()?.toIntOrNull()
    if (path.isBlank()) return

    val range = when {
        offset != null && limit != null -> "Lines ${offset + 1}–${offset + limit}"
        offset != null -> "From line ${offset + 1}"
        limit != null -> "First $limit lines"
        else -> "Entire file"
    }
    DetailCard {
        FilePathField(path)
        DetailField(label = "Range") {
            Text(
                text = range,
                color = AgentBelayColors.inkSecondary,
                fontSize = 12.sp,
                fontFamily = FontFamily.Monospace,
            )
        }
    }
}

@Composable
private fun AskUserQuestionDetail(toolInput: Map<String, JsonElement>) {
    val questions = toolInput["questions"] as? JsonArray ?: return
    if (questions.isEmpty()) return

    DetailCard {
        DetailField(label = "Questions") {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                questions.forEachIndexed { idx, element ->
                    val obj = element.asObjectOrNull() ?: return@forEachIndexed
                    val header = obj["header"].asStringOrNull().orEmpty()
                    val question = obj["question"].asStringOrNull().orEmpty()
                    val multi = obj["multiSelect"].asBooleanOrNull() == true
                    val options = (obj["options"] as? JsonArray).orEmpty()
                    Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                        if (header.isNotBlank()) {
                            Text(
                                text = header,
                                color = AgentBelayColors.inkPrimary,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.SemiBold,
                            )
                        }
                        if (question.isNotBlank()) {
                            Text(
                                text = if (questions.size > 1) "${idx + 1}. $question" else question,
                                color = AgentBelayColors.inkSecondary,
                                fontSize = 12.sp,
                                lineHeight = 18.sp,
                            )
                        }
                        if (options.isNotEmpty()) {
                            Column(modifier = Modifier.padding(start = 8.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                options.forEach { optEl ->
                                    val opt = optEl.asObjectOrNull() ?: return@forEach
                                    val label = opt["label"].asStringOrNull().orEmpty()
                                    val desc = opt["description"].asStringOrNull().orEmpty()
                                    val bullet = if (multi) "☐" else "○"
                                    val text = buildString {
                                        append(bullet); append("  ")
                                        append(label)
                                        if (desc.isNotBlank()) {
                                            append(" — ")
                                            append(desc)
                                        }
                                    }
                                    Text(
                                        text = text,
                                        color = AgentBelayColors.inkSecondary,
                                        fontSize = 12.sp,
                                        lineHeight = 18.sp,
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

/* ── Prompt / raw JSON / shared blocks ───────────────────────────────── */

@Composable
private fun PromptBlock(prompt: String) {
    DetailCard {
        DetailField(label = "Prompt") {
            Text(
                text = prompt,
                color = AgentBelayColors.inkSecondary,
                fontSize = 12.sp,
                lineHeight = 18.sp,
            )
        }
    }
}

@Composable
private fun RawJsonBlock(title: String, json: String?) {
    if (json.isNullOrBlank()) return
    val pretty = remember(json) { prettyPrintJson(json) }
    var expanded by remember(json) { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(AgentBelayColors.background)
            .border(1.dp, AgentBelayColors.line1, RoundedCornerShape(8.dp)),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { expanded = !expanded }
                .padding(horizontal = 14.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Icon(
                imageVector = if (expanded) LucideChevronDown else LucideChevronRight,
                contentDescription = null,
                tint = AgentBelayColors.inkMuted,
                modifier = Modifier.size(12.dp),
            )
            Text(
                text = title.uppercase(),
                color = AgentBelayColors.inkMuted,
                fontSize = 10.5.sp,
                fontWeight = FontWeight.SemiBold,
                letterSpacing = 0.5.sp,
                modifier = Modifier.weight(1f),
            )
            Text(
                text = "${pretty.length} chars",
                color = AgentBelayColors.inkMuted,
                fontSize = 10.5.sp,
                fontFamily = FontFamily.Monospace,
            )
            IconActionButton(
                icon = LucideCopy,
                contentDescription = "Copy $title",
                onClick = { copyToClipboard(pretty) },
            )
        }
        if (expanded) {
            HorizontalHairline()
            val scroll = rememberScrollState()
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 320.dp)
                    .verticalScroll(scroll)
                    .padding(14.dp),
            ) {
                Text(
                    text = pretty,
                    color = AgentBelayColors.inkPrimary,
                    fontSize = 11.5.sp,
                    fontFamily = FontFamily.Monospace,
                    lineHeight = 17.sp,
                )
            }
        }
    }
}

@Composable
private fun DetailCard(content: @Composable () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(AgentBelayColors.background)
            .border(1.dp, AgentBelayColors.line1, RoundedCornerShape(8.dp))
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
        content = { content() },
    )
}

@Composable
private fun DetailField(
    label: String,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    MetadataField(label = label, modifier = modifier, content = content)
}

@Composable
private fun FilePathField(path: String) {
    DetailField(label = "File") {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                text = path,
                color = AgentBelayColors.inkPrimary,
                fontSize = 12.sp,
                fontFamily = FontFamily.Monospace,
                modifier = Modifier.weight(1f),
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            IconActionButton(
                icon = LucideCopy,
                contentDescription = "Copy path",
                onClick = { copyToClipboard(path) },
            )
        }
    }
}

@Composable
private fun LabeledCodeBlock(
    label: String,
    code: String,
    copyPayload: String,
    maxHeight: androidx.compose.ui.unit.Dp = 200.dp,
) {
    DetailField(label = label) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(6.dp))
                .background(AgentBelayColors.surface)
                .border(1.dp, AgentBelayColors.line1, RoundedCornerShape(6.dp)),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(start = 12.dp, end = 6.dp, top = 4.dp, bottom = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Spacer(Modifier.weight(1f))
                IconActionButton(
                    icon = LucideCopy,
                    contentDescription = "Copy $label",
                    onClick = { copyToClipboard(copyPayload) },
                )
            }
            HorizontalHairline()
            val scroll = rememberScrollState()
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = maxHeight)
                    .verticalScroll(scroll)
                    .padding(horizontal = 12.dp, vertical = 10.dp),
            ) {
                Text(
                    text = code,
                    color = AgentBelayColors.inkPrimary,
                    fontSize = 12.sp,
                    fontFamily = FontFamily.Monospace,
                    lineHeight = 18.sp,
                )
            }
        }
    }
}

@Composable
private fun DiffBlock(oldStr: String, newStr: String) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(6.dp))
            .background(AgentBelayColors.surface)
            .border(1.dp, AgentBelayColors.line1, RoundedCornerShape(6.dp)),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(start = 12.dp, end = 6.dp, top = 4.dp, bottom = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Spacer(Modifier.weight(1f))
            IconActionButton(
                icon = LucideCopy,
                contentDescription = "Copy new value",
                onClick = { copyToClipboard(newStr) },
            )
        }
        HorizontalHairline()
        val scroll = rememberScrollState()
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(max = 260.dp)
                .verticalScroll(scroll)
                .padding(horizontal = 12.dp, vertical = 10.dp),
        ) {
            oldStr.lines().filter { it.isNotEmpty() || oldStr.contains('\n') }.forEach {
                Text(
                    text = "- $it",
                    color = Color(0xFFE06C75),
                    fontSize = 12.sp,
                    fontFamily = FontFamily.Monospace,
                    lineHeight = 18.sp,
                )
            }
            newStr.lines().filter { it.isNotEmpty() || newStr.contains('\n') }.forEach {
                Text(
                    text = "+ $it",
                    color = Color(0xFF98C379),
                    fontSize = 12.sp,
                    fontFamily = FontFamily.Monospace,
                    lineHeight = 18.sp,
                )
            }
        }
    }
}

/* ── Actions ─────────────────────────────────────────────────────────── */

// Uses the AWT system clipboard directly so callers can invoke from plain
// click callbacks (LocalClipboardManager would require @Composable scope).
private fun copyToClipboard(text: String) {
    try {
        val clipboard = java.awt.Toolkit.getDefaultToolkit().systemClipboard
        clipboard.setContents(java.awt.datatransfer.StringSelection(text), null)
    } catch (_: Exception) {
    }
}

private fun openInBrowser(url: String) {
    try {
        Desktop.getDesktop().browse(URI(url))
    } catch (_: Exception) {
    }
}

private fun prettyPrintJson(raw: String): String = try {
    val element = prettyJson.parseToJsonElement(raw)
    prettyJson.encodeToString(JsonElement.serializer(), element)
} catch (_: Exception) {
    raw
}
