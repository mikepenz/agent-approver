package com.mikepenz.agentbelay.ui.approvals

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.PlainTooltip
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TooltipBox
import androidx.compose.material3.TooltipAnchorPosition
import androidx.compose.material3.TooltipDefaults
import androidx.compose.material3.rememberTooltipState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mikepenz.agentbelay.model.ApprovalRequest
import com.mikepenz.agentbelay.model.HookInput
import com.mikepenz.agentbelay.model.PermissionSuggestion
import com.mikepenz.agentbelay.model.Source
import com.mikepenz.agentbelay.model.ToolType
import com.mikepenz.agentbelay.ui.detail.PopOutSpec
import com.mikepenz.agentbelay.ui.icons.FeatherExternalLink
import com.mikepenz.agentbelay.ui.icons.FontAwesomeCaretDown
import com.mikepenz.agentbelay.ui.theme.PreviewScaffold
import com.mikepenz.markdown.compose.components.markdownComponents
import com.mikepenz.markdown.compose.elements.highlightedCodeBlock
import com.mikepenz.markdown.compose.elements.highlightedCodeFence
import com.mikepenz.markdown.m3.Markdown
import com.mikepenz.markdown.m3.markdownColor
import kotlin.time.Clock
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DefaultCard(
    request: ApprovalRequest,
    onApprove: (String?) -> Unit,
    onDeny: (String) -> Unit,
    onAlwaysAllow: () -> Unit = {},
    onUserInteraction: () -> Unit = {},
    prominentAlwaysAllow: Boolean = false,
    onPopOut: ((PopOutSpec) -> Unit)? = null,
    popOutContent: String = "",
    content: @Composable () -> Unit,
) {
    var denyFeedback by remember { mutableStateOf("") }

    Column(modifier = Modifier.fillMaxWidth()) {
        // Session ID + pop-out button
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = "Session: ${request.hookInput.sessionId}",
                fontSize = 10.sp,
                color = Color.Gray,
            )
            if (onPopOut != null) {
                IconButton(
                    onClick = { onPopOut(PopOutSpec(request.hookInput.toolName, popOutContent)) },
                    modifier = Modifier.size(24.dp),
                ) {
                    Icon(
                        imageVector = FeatherExternalLink,
                        contentDescription = "Open in new window",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(14.dp),
                    )
                }
            }
        }

        Spacer(Modifier.height(8.dp))

        // Content area
        content()

        Spacer(Modifier.height(8.dp))

        // Deny feedback input
        OutlinedTextField(
            value = denyFeedback,
            onValueChange = {
                denyFeedback = it
                if (it.isNotEmpty()) onUserInteraction()
            },
            modifier = Modifier.fillMaxWidth().onPreviewKeyEvent { event ->
                if (event.key == Key.Enter && event.type == KeyEventType.KeyDown) {
                    onDeny(denyFeedback)
                    true
                } else false
            },
            placeholder = { Text("Denial reason (optional)", fontSize = 12.sp) },
            singleLine = true,
            textStyle = LocalTextStyle.current.copy(fontSize = 12.sp),
        )

        Spacer(Modifier.height(8.dp))

        // Buttons: hide Approve when denial reason is entered
        val hasDenyText = denyFeedback.isNotBlank()
        val hasSuggestions = request.hookInput.permissionSuggestions.isNotEmpty()

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            if (hasDenyText) {
                Spacer(Modifier.weight(1f))
                OutlinedButton(
                    onClick = { onDeny(denyFeedback) },
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error),
                ) {
                    Text("Deny")
                }
            } else if (hasSuggestions) {
                OutlinedButton(
                    onClick = { onDeny("") },
                    modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error),
                ) {
                    Text("Deny")
                }

                if (prominentAlwaysAllow) {
                    // Prominent mode: three equal buttons — Deny | Always | Approve
                    val tooltipText = remember(request) { formatPermissionTooltip(request.hookInput.permissionSuggestions) }
                    TooltipBox(
                        positionProvider = TooltipDefaults.rememberTooltipPositionProvider(positioning = TooltipAnchorPosition.Above),
                        tooltip = {
                            PlainTooltip {
                                Text(tooltipText)
                            }
                        },
                        state = rememberTooltipState(isPersistent = true),
                    ) {
                        OutlinedButton(
                            onClick = { onAlwaysAllow() },
                            modifier = Modifier.weight(1f),
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = Color(0xFF00ACC1)),
                        ) {
                            Text("Always", fontSize = 11.sp)
                        }
                    }
                    Button(
                        onClick = { onApprove(null) },
                        modifier = Modifier.weight(1f),
                    ) {
                        Text("Approve")
                    }
                } else {
                    // Split button: Approve + dropdown arrow for "Always allow"
                    var showMenu by remember { mutableStateOf(false) }
                    val tooltipText = remember(request) { formatPermissionTooltip(request.hookInput.permissionSuggestions) }
                    Row(modifier = Modifier.weight(1f)) {
                        Button(
                            onClick = { onApprove(null) },
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(topStartPercent = 50, bottomStartPercent = 50, topEndPercent = 0, bottomEndPercent = 0),
                            contentPadding = PaddingValues(horizontal = 8.dp),
                        ) {
                            Text("Approve")
                        }
                        Spacer(Modifier.width(1.dp))
                        Box {
                            Button(
                                onClick = {
                                    showMenu = true
                                    onUserInteraction()
                                },
                                modifier = Modifier.width(40.dp),
                                shape = RoundedCornerShape(topStartPercent = 0, bottomStartPercent = 0, topEndPercent = 50, bottomEndPercent = 50),
                                contentPadding = PaddingValues(0.dp),
                            ) {
                                Icon(
                                    imageVector = FontAwesomeCaretDown,
                                    contentDescription = "More options",
                                    modifier = Modifier.size(12.dp),
                                )
                            }
                            DropdownMenu(
                                expanded = showMenu,
                                onDismissRequest = { showMenu = false },
                            ) {
                                TooltipBox(
                                    positionProvider = TooltipDefaults.rememberTooltipPositionProvider(positioning = TooltipAnchorPosition.Above),
                                    tooltip = {
                                        PlainTooltip {
                                            Text(tooltipText)
                                        }
                                    },
                                    state = rememberTooltipState(isPersistent = true),
                                ) {
                                    DropdownMenuItem(
                                        text = { Text("Always allow") },
                                        onClick = {
                                            showMenu = false
                                            onAlwaysAllow()
                                        },
                                    )
                                }
                            }
                        }
                    }
                }
            } else {
                OutlinedButton(
                    onClick = { onDeny("") },
                    modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error),
                ) {
                    Text("Deny")
                }
                Button(
                    onClick = { onApprove(null) },
                    modifier = Modifier.weight(1f),
                ) {
                    Text("Approve")
                }
            }
        }
    }
}

private fun formatPermissionTooltip(suggestions: List<PermissionSuggestion>): String {
    return suggestions.flatMap { suggestion ->
        val destination = when (suggestion.destination) {
            "projectSettings" -> "project"
            "userSettings" -> "user"
            "localSettings" -> "local"
            else -> suggestion.destination ?: "session"
        }
        suggestion.rules?.map { rule ->
            val ruleText = if (rule.ruleContent.isNotBlank()) {
                "${rule.toolName}(${rule.ruleContent})"
            } else {
                rule.toolName
            }
            "$ruleText — $destination"
        } ?: emptyList()
    }.joinToString("\n")
}

@Composable
fun FallbackContent(toolInput: Map<String, JsonElement>) {
    val json = Json { prettyPrint = true }
    val text = "```json\n${json.encodeToString(JsonObject.serializer(), JsonObject(toolInput))}\n```"
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = Color(0xFF1E1E1E),
        shape = MaterialTheme.shapes.small,
    ) {
        Box(modifier = Modifier.padding(horizontal = 8.dp)) {
            Markdown(
                content = text,
                colors = markdownColor(codeBackground = Color.Transparent),
                components = markdownComponents(
                    codeFence = highlightedCodeFence,
                    codeBlock = highlightedCodeBlock,
                ),
            )
        }
    }
}

@Preview(widthDp = 404, heightDp = 360)
@Composable
private fun PreviewBashCard() {
    val req = ApprovalRequest(
        id = "preview-1",
        source = Source.CLAUDE_CODE,
        toolType = ToolType.DEFAULT,
        hookInput = HookInput(
            sessionId = "sess-abc123",
            toolName = "Bash",
            toolInput = mapOf(
                "command" to JsonPrimitive("git status && git diff HEAD"),
                "description" to JsonPrimitive("Show working tree status"),
            ),
            cwd = "/home/user/project",
        ),
        timestamp = Clock.System.now(),
        rawRequestJson = "{}",
    )
    PreviewScaffold {
        Box(
            modifier = Modifier.fillMaxSize().padding(20.dp),
            contentAlignment = Alignment.TopCenter,
        ) {
          Box(modifier = Modifier.width(380.dp)) {
            ApprovalCard(
                request = req,
                riskResult = com.mikepenz.agentbelay.model.RiskAnalysis(
                    risk = 2,
                    label = "Low",
                    message = "Read-only git inspection",
                ),
                riskStatus = RiskStatus.COMPLETED,
                riskError = null,
                timeoutSeconds = 120,
                onApprove = {}, onDeny = {}, onAlwaysAllow = {}, onApproveWithInput = {}, onDismiss = {},
                autoDenyActive = false, onCancelAutoDeny = {},
            )
          }
        }
    }
}

@Preview(widthDp = 404, heightDp = 420)
@Composable
private fun PreviewBashCardWithSuggestions() {
    val req = ApprovalRequest(
        id = "preview-2",
        source = Source.CLAUDE_CODE,
        toolType = ToolType.DEFAULT,
        hookInput = HookInput(
            sessionId = "sess-abc123",
            toolName = "Bash",
            toolInput = mapOf("command" to JsonPrimitive("pnpm install")),
            cwd = "/home/user/project",
            permissionSuggestions = listOf(
                com.mikepenz.agentbelay.model.PermissionSuggestion(
                    type = "addRules",
                    destination = "projectSettings",
                    rules = listOf(
                        com.mikepenz.agentbelay.model.RuleEntry(
                            toolName = "Bash",
                            ruleContent = "pnpm install",
                        ),
                    ),
                ),
            ),
        ),
        timestamp = Clock.System.now(),
        rawRequestJson = "{}",
    )
    PreviewScaffold {
        Box(
            modifier = Modifier.fillMaxSize().padding(20.dp),
            contentAlignment = Alignment.TopCenter,
        ) {
          Box(modifier = Modifier.width(380.dp)) {
            ApprovalCard(
                request = req,
                riskResult = com.mikepenz.agentbelay.model.RiskAnalysis(
                    risk = 1,
                    label = "Safe",
                    message = "Well-known package manager install",
                ),
                riskStatus = RiskStatus.COMPLETED,
                riskError = null,
                timeoutSeconds = 120,
                onApprove = {}, onDeny = {}, onAlwaysAllow = {}, onApproveWithInput = {}, onDismiss = {},
                autoDenyActive = false, onCancelAutoDeny = {},
            )
          }
        }
    }
}

@Preview(widthDp = 404, heightDp = 420)
@Composable
private fun PreviewBashCardProminentAlways() {
    val req = ApprovalRequest(
        id = "preview-3",
        source = Source.CLAUDE_CODE,
        toolType = ToolType.DEFAULT,
        hookInput = HookInput(
            sessionId = "sess-abc123",
            toolName = "Bash",
            toolInput = mapOf("command" to JsonPrimitive("pnpm test")),
            cwd = "/home/user/project",
            permissionSuggestions = listOf(
                com.mikepenz.agentbelay.model.PermissionSuggestion(
                    type = "addRules",
                    destination = "projectSettings",
                    rules = listOf(
                        com.mikepenz.agentbelay.model.RuleEntry(
                            toolName = "Bash",
                            ruleContent = "pnpm test",
                        ),
                    ),
                ),
            ),
        ),
        timestamp = Clock.System.now(),
        rawRequestJson = "{}",
    )
    PreviewScaffold {
        Box(
            modifier = Modifier.fillMaxSize().padding(20.dp),
            contentAlignment = Alignment.TopCenter,
        ) {
          Box(modifier = Modifier.width(380.dp)) {
            ApprovalCard(
                request = req,
                riskResult = com.mikepenz.agentbelay.model.RiskAnalysis(
                    risk = 1,
                    label = "Safe",
                    message = "Repeated test command",
                ),
                riskStatus = RiskStatus.COMPLETED,
                riskError = null,
                timeoutSeconds = 120,
                onApprove = {}, onDeny = {}, onAlwaysAllow = {}, onApproveWithInput = {}, onDismiss = {},
                autoDenyActive = false, onCancelAutoDeny = {},
                prominentAlwaysAllow = true,
            )
          }
        }
    }
}
