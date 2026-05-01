package com.mikepenz.agentbelay.ui.settings

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.tooling.preview.Preview
import com.mikepenz.agentbelay.ui.theme.PreviewScaffold
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mikepenz.agentbelay.harness.HarnessCapabilities
import com.mikepenz.agentbelay.model.AppSettings
import com.mikepenz.agentbelay.ui.components.ColoredIconTile
import com.mikepenz.agentbelay.ui.components.DecisionStatus
import com.mikepenz.agentbelay.ui.components.DesignToggle
import com.mikepenz.agentbelay.ui.components.HorizontalHairline
import com.mikepenz.agentbelay.ui.components.OutlineButton
import com.mikepenz.agentbelay.ui.components.PrimaryButton
import com.mikepenz.agentbelay.ui.components.StatusPill
import com.mikepenz.agentbelay.ui.components.TagSize
import com.mikepenz.agentbelay.ui.icons.LucideChevronDown
import com.mikepenz.agentbelay.ui.icons.LucidePlug
import com.mikepenz.agentbelay.ui.theme.AgentBelayColors
import com.mikepenz.agentbelay.ui.theme.SourceClaudeColor
import com.mikepenz.agentbelay.ui.theme.SourceOpenCodeColor
import com.mikepenz.agentbelay.ui.theme.SourcePiColor
import com.mikepenz.agentbelay.ui.theme.VioletPurple

private val ClaudeCapabilities = HarnessCapabilities(
    supportsArgRewriting = true,
    supportsAlwaysAllowWriteThrough = true,
    supportsOutputRedaction = true,
    supportsDefer = false,
    supportsInterruptOnDeny = true,
    supportsAdditionalContextInjection = true,
)

private val CopilotCapabilities = HarnessCapabilities(
    supportsArgRewriting = true,
    supportsAlwaysAllowWriteThrough = false,
    supportsOutputRedaction = false,
    supportsDefer = false,
    supportsInterruptOnDeny = true,
    supportsAdditionalContextInjection = true,
)

private val OpenCodeCapabilities = HarnessCapabilities(
    supportsArgRewriting = false,
    supportsAlwaysAllowWriteThrough = false,
    supportsOutputRedaction = false,
    supportsDefer = false,
    supportsInterruptOnDeny = true,
    supportsAdditionalContextInjection = true,
)

private val PiCapabilities = HarnessCapabilities(
    supportsArgRewriting = false,
    supportsAlwaysAllowWriteThrough = false,
    supportsOutputRedaction = false,
    supportsDefer = false,
    supportsInterruptOnDeny = true,
    supportsAdditionalContextInjection = false,
)

private val CodexCapabilities = HarnessCapabilities(
    supportsArgRewriting = true,
    supportsAlwaysAllowWriteThrough = false,
    supportsOutputRedaction = false,
    supportsDefer = false,
    supportsInterruptOnDeny = true,
    supportsAdditionalContextInjection = false,
)

@Composable
fun IntegrationsSettingsContent(
    settings: AppSettings,
    isHookRegistered: Boolean,
    isCopilotRegistered: Boolean,
    isOpenCodeRegistered: Boolean = false,
    isPiRegistered: Boolean = false,
    isCodexRegistered: Boolean = false,
    onSettingsChange: (AppSettings) -> Unit,
    onRegisterHook: () -> Unit,
    onUnregisterHook: () -> Unit,
    onRegisterCopilot: () -> Unit,
    onUnregisterCopilot: () -> Unit,
    onRegisterOpenCode: () -> Unit = {},
    onUnregisterOpenCode: () -> Unit = {},
    onRegisterPi: () -> Unit = {},
    onUnregisterPi: () -> Unit = {},
    onRegisterCodex: () -> Unit = {},
    onUnregisterCodex: () -> Unit = {},
) {
    SettingSection(
        title = "Integrations",
        desc = "Agents that route approvals through Agent Belay.",
    ) {
        val items = listOf(
            IntegrationItemData(
                id = "claude",
                name = "Claude Code",
                desc = "Hook in ~/.claude/settings.json",
                color = SourceClaudeColor,
                registered = isHookRegistered,
                capabilities = ClaudeCapabilities,
                onRegister = onRegisterHook,
                onUnregister = onUnregisterHook,
            ),
            IntegrationItemData(
                id = "copilot",
                name = "GitHub Copilot",
                desc = "User-scoped hook in ~/.copilot/hooks/agent-belay.json " +
                    "(PreToolUse + PermissionRequest, requires Copilot CLI ≥ v1.0.21)",
                color = VioletPurple,
                registered = isCopilotRegistered,
                capabilities = CopilotCapabilities,
                onRegister = onRegisterCopilot,
                onUnregister = onUnregisterCopilot,
                extra = {
                    CopilotFailClosedCard(
                        failClosed = settings.copilotFailClosed,
                        onChange = { onSettingsChange(settings.copy(copilotFailClosed = it)) },
                    )
                },
            ),
            IntegrationItemData(
                id = "opencode",
                name = "OpenCode",
                desc = "Plugin in ~/.config/opencode/plugin/agent-belay.ts " +
                    "(tool.execute.before gate, fail-open)",
                color = SourceOpenCodeColor,
                registered = isOpenCodeRegistered,
                capabilities = OpenCodeCapabilities,
                onRegister = onRegisterOpenCode,
                onUnregister = onUnregisterOpenCode,
            ),
            IntegrationItemData(
                id = "pi",
                name = "Pi",
                desc = "Extension in ~/.pi/agent/extensions/agent-belay.ts " +
                    "(tool_call gate, fail-open)",
                color = SourcePiColor,
                registered = isPiRegistered,
                capabilities = PiCapabilities,
                onRegister = onRegisterPi,
                onUnregister = onUnregisterPi,
            ),
            IntegrationItemData(
                id = "codex",
                name = "Codex",
                desc = "Managed block in ~/.codex/config.toml " +
                    "(PreToolUse + PermissionRequest, requires Codex CLI with HTTP hooks support)",
                color = Color(0xFF1F6FEB),
                registered = isCodexRegistered,
                capabilities = CodexCapabilities,
                onRegister = onRegisterCodex,
                onUnregister = onUnregisterCodex,
                experimental = true,
            ),
        )
        items.forEachIndexed { idx, it -> IntegrationRow(item = it, first = idx == 0) }
    }
}

private data class IntegrationItemData(
    val id: String,
    val name: String,
    val desc: String,
    val color: Color,
    val registered: Boolean,
    val capabilities: HarnessCapabilities,
    val onRegister: () -> Unit,
    val onUnregister: () -> Unit,
    val extra: (@Composable () -> Unit)? = null,
    /** Show an "Experimental" pill next to the name and a caveat below the description. */
    val experimental: Boolean = false,
)

private data class CapabilityBadge(val label: String, val supported: Boolean)

private fun HarnessCapabilities.badges(): List<CapabilityBadge> = listOf(
    CapabilityBadge("Approvals", true),
    CapabilityBadge("Arg Rewriting", supportsArgRewriting),
    CapabilityBadge("Always Allow", supportsAlwaysAllowWriteThrough),
    CapabilityBadge("Output Redaction", supportsOutputRedaction),
    CapabilityBadge("Defer", supportsDefer),
    CapabilityBadge("Interrupt on Deny", supportsInterruptOnDeny),
    CapabilityBadge("Context Injection", supportsAdditionalContextInjection),
)

@Composable
private fun IntegrationRow(item: IntegrationItemData, first: Boolean) {
    var expanded by remember(item.id) { mutableStateOf(false) }

    Column(modifier = Modifier.fillMaxWidth()) {
        if (!first) {
            HorizontalHairline()
        }
        BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
            val narrowLayout = maxWidth < 520.dp
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { expanded = !expanded }
                    .padding(16.dp),
            ) {
                // Header row: icon + title (+ inline badges in wide layout) + action button.
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(14.dp),
                ) {
                    ColoredIconTile(
                        icon = LucidePlug,
                        tint = item.color,
                        contentDescription = null,
                    )
                    Row(
                        modifier = Modifier.weight(1f),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Text(
                            text = item.name,
                            color = AgentBelayColors.inkPrimary,
                            fontSize = 13.5.sp,
                            fontWeight = FontWeight.SemiBold,
                            maxLines = 1,
                            overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f, fill = false),
                        )
                        if (!narrowLayout) {
                            if (item.registered) {
                                StatusPill(
                                    status = DecisionStatus.APPROVED,
                                    size = TagSize.SMALL,
                                    text = "Registered",
                                )
                            }
                            if (item.experimental) {
                                StatusPill(
                                    status = DecisionStatus.PENDING,
                                    size = TagSize.SMALL,
                                    text = "Experimental",
                                )
                            }
                        }
                    }
                    if (item.registered) {
                        OutlineButton(text = "Unregister", onClick = item.onUnregister)
                    } else {
                        PrimaryButton(text = "Register", onClick = item.onRegister)
                    }
                    Icon(
                        imageVector = LucideChevronDown,
                        contentDescription = if (expanded) "Collapse" else "Expand",
                        tint = AgentBelayColors.inkTertiary,
                        modifier = Modifier
                            .size(16.dp)
                            .rotate(if (expanded) 180f else 0f),
                    )
                }
                // In narrow layout, show badges below the title row.
                if (narrowLayout && (item.registered || item.experimental)) {
                    Spacer(Modifier.height(6.dp))
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        if (item.registered) {
                            StatusPill(
                                status = DecisionStatus.APPROVED,
                                size = TagSize.SMALL,
                                text = "Registered",
                            )
                        }
                        if (item.experimental) {
                            StatusPill(
                                status = DecisionStatus.PENDING,
                                size = TagSize.SMALL,
                                text = "Experimental",
                            )
                        }
                    }
                }
            Spacer(Modifier.height(8.dp))
            Text(
                modifier = Modifier.fillMaxWidth(),
                text = item.desc,
                color = AgentBelayColors.inkTertiary,
                fontSize = 11.5.sp,
                lineHeight = 17.sp,
                fontFamily = FontFamily.Monospace,
                maxLines = 4,
                overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
            )
            if (item.extra != null && item.registered) {
                Spacer(Modifier.height(12.dp))
                item.extra.invoke()
            }
        }
        }
        AnimatedVisibility(visible = expanded) {
            Column(modifier = Modifier.fillMaxWidth()) {
                HorizontalHairline()
                FlowRow(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    item.capabilities.badges().forEach { badge ->
                        StatusPill(
                            status = if (badge.supported) DecisionStatus.APPROVED else DecisionStatus.DENIED,
                            size = TagSize.SMALL,
                            text = badge.label,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun CopilotFailClosedCard(failClosed: Boolean, onChange: (Boolean) -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(6.dp))
            .background(AgentBelayColors.surface2)
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = "Fail-closed when unreachable",
                color = AgentBelayColors.inkPrimary,
                fontSize = 12.5.sp,
                fontWeight = FontWeight.Medium,
            )
            Spacer(Modifier.height(2.dp))
            Text(
                text = "Copilot blocks the action if Agent Belay isn't running.",
                color = AgentBelayColors.inkTertiary,
                fontSize = 11.sp,
            )
        }
        DesignToggle(checked = failClosed, onCheckedChange = onChange)
    }
}

@Preview(widthDp = 380, heightDp = 800)
@Composable
private fun PreviewIntegrationsSlim() {
    PreviewScaffold {
        androidx.compose.foundation.layout.Box(
            Modifier
                .requiredWidth(380.dp)
                .padding(16.dp)
        ) {
            IntegrationsSettingsContent(
                settings = AppSettings(),
                isHookRegistered = true,
                isCopilotRegistered = true,
                isOpenCodeRegistered = true,
                isPiRegistered = false,
                isCodexRegistered = false,
                onSettingsChange = {},
                onRegisterHook = {},
                onUnregisterHook = {},
                onRegisterCopilot = {},
                onUnregisterCopilot = {},
            )
        }
    }
}

@Preview(widthDp = 720, heightDp = 800)
@Composable
private fun PreviewIntegrationsWide() {
    PreviewScaffold {
        androidx.compose.foundation.layout.Box(Modifier.padding(24.dp)) {
            IntegrationsSettingsContent(
                settings = AppSettings(),
                isHookRegistered = true,
                isCopilotRegistered = true,
                isOpenCodeRegistered = true,
                isPiRegistered = false,
                isCodexRegistered = false,
                onSettingsChange = {},
                onRegisterHook = {},
                onUnregisterHook = {},
                onRegisterCopilot = {},
                onUnregisterCopilot = {},
            )
        }
    }
}

@Preview(widthDp = 380, heightDp = 700)
@Composable
private fun PreviewIntegrationsUnregistered() {
    PreviewScaffold {
        androidx.compose.foundation.layout.Box(Modifier.padding(16.dp)) {
            IntegrationsSettingsContent(
                settings = AppSettings(),
                isHookRegistered = false,
                isCopilotRegistered = false,
                isOpenCodeRegistered = false,
                isPiRegistered = false,
                isCodexRegistered = false,
                onSettingsChange = {},
                onRegisterHook = {},
                onUnregisterHook = {},
                onRegisterCopilot = {},
                onUnregisterCopilot = {},
            )
        }
    }
}
