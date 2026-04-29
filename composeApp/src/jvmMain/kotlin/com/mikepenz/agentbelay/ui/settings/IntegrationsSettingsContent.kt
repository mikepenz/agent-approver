package com.mikepenz.agentbelay.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import com.mikepenz.agentbelay.ui.theme.PreviewScaffold
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mikepenz.agentbelay.model.AppSettings
import com.mikepenz.agentbelay.ui.components.ColoredIconTile
import com.mikepenz.agentbelay.ui.components.DecisionStatus
import com.mikepenz.agentbelay.ui.components.DesignToggle
import com.mikepenz.agentbelay.ui.components.HorizontalHairline
import com.mikepenz.agentbelay.ui.components.OutlineButton
import com.mikepenz.agentbelay.ui.components.PrimaryButton
import com.mikepenz.agentbelay.ui.components.StatusPill
import com.mikepenz.agentbelay.ui.components.TagSize
import com.mikepenz.agentbelay.ui.icons.LucidePlug
import com.mikepenz.agentbelay.ui.theme.AgentBelayColors
import com.mikepenz.agentbelay.ui.theme.VioletPurple

@Composable
fun IntegrationsSettingsContent(
    settings: AppSettings,
    isHookRegistered: Boolean,
    isCopilotRegistered: Boolean,
    onSettingsChange: (AppSettings) -> Unit,
    onRegisterHook: () -> Unit,
    onUnregisterHook: () -> Unit,
    onRegisterCopilot: () -> Unit,
    onUnregisterCopilot: () -> Unit,
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
                color = Color(0xFFD97757),
                registered = isHookRegistered,
                onRegister = onRegisterHook,
                onUnregister = onUnregisterHook,
            ),
            IntegrationItemData(
                id = "copilot",
                name = "GitHub Copilot",
                desc = "User-scoped hook in ~/.copilot/hooks/agent-belay.json " +
                    "(PreToolUse + PermissionRequest, requires Copilot CLI \u2265 v1.0.21)",
                color = VioletPurple,
                registered = isCopilotRegistered,
                onRegister = onRegisterCopilot,
                onUnregister = onUnregisterCopilot,
                extra = {
                    CopilotFailClosedCard(
                        failClosed = settings.copilotFailClosed,
                        onChange = { onSettingsChange(settings.copy(copilotFailClosed = it)) },
                    )
                },
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
    val onRegister: () -> Unit,
    val onUnregister: () -> Unit,
    val extra: (@Composable () -> Unit)? = null,
)

@Composable
private fun IntegrationRow(item: IntegrationItemData, first: Boolean) {
    Column(modifier = Modifier.fillMaxWidth()) {
        if (!first) {
            HorizontalHairline()
        }
        Column(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
            // Header row: icon + title/status pill + action button. The action
            // lives here (not next to the description) so the title row owns
            // the trailing space and the description below can use the full
            // card width.
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
                    StatusPill(
                        status = if (item.registered) DecisionStatus.APPROVED
                        else DecisionStatus.TIMEOUT,
                        size = TagSize.SMALL,
                        text = if (item.registered) "Registered" else DecisionStatus.TIMEOUT.label,
                    )
                }
                if (item.registered) {
                    OutlineButton(text = "Unregister", onClick = item.onUnregister)
                } else {
                    PrimaryButton(text = "Register", onClick = item.onRegister)
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

@Preview(widthDp = 380, heightDp = 700)
@Composable
private fun PreviewIntegrationsSlim() {
    PreviewScaffold {
        androidx.compose.foundation.layout.Box(Modifier.padding(16.dp)) {
            IntegrationsSettingsContent(
                settings = AppSettings(),
                isHookRegistered = true,
                isCopilotRegistered = true,
                onSettingsChange = {},
                onRegisterHook = {},
                onUnregisterHook = {},
                onRegisterCopilot = {},
                onUnregisterCopilot = {},
            )
        }
    }
}

@Preview(widthDp = 720, heightDp = 700)
@Composable
private fun PreviewIntegrationsWide() {
    PreviewScaffold {
        androidx.compose.foundation.layout.Box(Modifier.padding(24.dp)) {
            IntegrationsSettingsContent(
                settings = AppSettings(),
                isHookRegistered = true,
                isCopilotRegistered = true,
                onSettingsChange = {},
                onRegisterHook = {},
                onUnregisterHook = {},
                onRegisterCopilot = {},
                onUnregisterCopilot = {},
            )
        }
    }
}

@Preview(widthDp = 380, heightDp = 600)
@Composable
private fun PreviewIntegrationsUnregistered() {
    PreviewScaffold {
        androidx.compose.foundation.layout.Box(Modifier.padding(16.dp)) {
            IntegrationsSettingsContent(
                settings = AppSettings(),
                isHookRegistered = false,
                isCopilotRegistered = false,
                onSettingsChange = {},
                onRegisterHook = {},
                onUnregisterHook = {},
                onRegisterCopilot = {},
                onUnregisterCopilot = {},
            )
        }
    }
}
