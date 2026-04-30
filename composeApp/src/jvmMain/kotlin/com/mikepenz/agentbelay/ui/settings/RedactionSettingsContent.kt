package com.mikepenz.agentbelay.ui.settings

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mikepenz.agentbelay.model.RedactionMode
import com.mikepenz.agentbelay.model.RedactionModuleSettings
import com.mikepenz.agentbelay.model.RedactionSettings
import com.mikepenz.agentbelay.redaction.RedactionModule
import com.mikepenz.agentbelay.redaction.builtInRedactionModules
import com.mikepenz.agentbelay.ui.components.AgentBelayCard
import com.mikepenz.agentbelay.ui.components.ColoredIconTile
import com.mikepenz.agentbelay.ui.components.ColoredModeOption
import com.mikepenz.agentbelay.ui.components.ColoredModePicker
import com.mikepenz.agentbelay.ui.components.DesignToggle
import com.mikepenz.agentbelay.ui.components.HorizontalHairline
import com.mikepenz.agentbelay.ui.icons.LucideEyeOff
import com.mikepenz.agentbelay.ui.theme.AccentEmerald
import com.mikepenz.agentbelay.ui.theme.AgentBelayColors
import com.mikepenz.agentbelay.ui.theme.InfoBlue
import com.mikepenz.agentbelay.ui.theme.InkMuted
import com.mikepenz.agentbelay.ui.theme.PreviewScaffold

private val redactionModeOptions: List<ColoredModeOption<RedactionMode>> = listOf(
    ColoredModeOption(RedactionMode.DISABLED, "Off", InkMuted),
    ColoredModeOption(RedactionMode.LOG_ONLY, "Log only", InfoBlue),
    ColoredModeOption(RedactionMode.ENABLED, "Redact", AccentEmerald),
)

/**
 * Settings page for the post-tool-use redaction engine. Visually mirrors
 * [ProtectionsSettingsContent] (same Card shell, same icon-tile + name +
 * description + mode-picker row layout) so the two engines feel like
 * siblings under the Settings sidebar. The mode set is intentionally
 * smaller — output redaction is non-interactive, so there is no `ASK`
 * variant.
 *
 * The header carries an extra master switch ([RedactionSettings.enabled])
 * — when off, the engine short-circuits regardless of per-module mode.
 */
@Composable
fun RedactionSettingsContent(
    modules: List<RedactionModule>,
    settings: RedactionSettings,
    onSettingsChange: (RedactionSettings) -> Unit,
) {
    Column(modifier = Modifier.fillMaxWidth().widthIn(max = 860.dp)) {
        // --- Header: title + description + master switch ----------------
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Column(modifier = Modifier.fillMaxWidth().widthIn(max = 720.dp)) {
                Text(
                    text = "Output redaction",
                    color = AgentBelayColors.inkPrimary,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold,
                    letterSpacing = (-0.1).sp,
                )
                Spacer(Modifier.height(3.dp))
                Text(
                    text = "Scans tool output for secrets and rewrites the response before the agent reads it. Claude Code only — Copilot's PostToolUse cannot modify results.",
                    color = AgentBelayColors.inkTertiary,
                    fontSize = 12.5.sp,
                    lineHeight = 18.sp,
                )
            }
        }
        Spacer(Modifier.height(12.dp))
        AgentBelayCard(modifier = Modifier.fillMaxWidth()) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(14.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Master switch",
                        color = AgentBelayColors.inkPrimary,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Spacer(Modifier.height(2.dp))
                    Text(
                        text = if (settings.enabled) {
                            "Engine runs after every supported tool call."
                        } else {
                            "Engine is paused — no rewrites or detections."
                        },
                        color = AgentBelayColors.inkTertiary,
                        fontSize = 12.sp,
                        lineHeight = 18.sp,
                    )
                }
                DesignToggle(
                    checked = settings.enabled,
                    onCheckedChange = { onSettingsChange(settings.copy(enabled = it)) },
                )
            }
        }
        Spacer(Modifier.height(18.dp))
        Text(
            text = "Modules",
            color = AgentBelayColors.inkPrimary,
            fontSize = 14.sp,
            fontWeight = FontWeight.SemiBold,
            letterSpacing = (-0.1).sp,
        )
        Spacer(Modifier.height(3.dp))
        Text(
            text = "Pattern groups that detect secrets in tool output. Tap a row to inspect or disable individual rules.",
            color = AgentBelayColors.inkTertiary,
            fontSize = 12.5.sp,
            lineHeight = 18.sp,
        )
        Spacer(Modifier.height(12.dp))
        if (modules.isEmpty()) {
            Text(
                text = "No redaction modules available.",
                color = AgentBelayColors.inkTertiary,
                fontSize = 12.sp,
            )
            return@Column
        }
        AgentBelayCard(modifier = Modifier.fillMaxWidth()) {
            Column {
                modules.forEachIndexed { idx, module ->
                    val moduleSettings = settings.modules[module.id] ?: RedactionModuleSettings()
                    val effectiveMode = moduleSettings.mode ?: module.defaultMode
                    RedactionRow(
                        module = module,
                        moduleSettings = moduleSettings,
                        effectiveMode = effectiveMode,
                        first = idx == 0,
                        masterEnabled = settings.enabled,
                        onModeChange = { newMode ->
                            onSettingsChange(
                                settings.copy(
                                    modules = settings.modules +
                                        (module.id to moduleSettings.copy(mode = newMode)),
                                ),
                            )
                        },
                        onRuleToggle = { ruleId, enabled ->
                            val newDisabled = if (!enabled) {
                                moduleSettings.disabledRules + ruleId
                            } else {
                                moduleSettings.disabledRules - ruleId
                            }
                            onSettingsChange(
                                settings.copy(
                                    modules = settings.modules +
                                        (module.id to moduleSettings.copy(disabledRules = newDisabled)),
                                ),
                            )
                        },
                    )
                }
            }
        }
    }
}

@Composable
private fun RedactionRow(
    module: RedactionModule,
    moduleSettings: RedactionModuleSettings,
    effectiveMode: RedactionMode,
    first: Boolean,
    masterEnabled: Boolean,
    onModeChange: (RedactionMode) -> Unit,
    onRuleToggle: (ruleId: String, enabled: Boolean) -> Unit,
) {
    var expanded by remember(module.id) { mutableStateOf(false) }
    val activeOption = redactionModeOptions.find { it.value == effectiveMode } ?: redactionModeOptions[0]
    // When the master switch is off, dim the row's icon tile + mode pill
    // so the user can see modes are configured but currently inert.
    val rowAlpha = if (masterEnabled) 1f else 0.55f

    Column(modifier = Modifier.fillMaxWidth()) {
        if (!first) {
            HorizontalHairline()
        }
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { expanded = !expanded }
                .padding(14.dp),
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                ColoredIconTile(
                    icon = LucideEyeOff,
                    color = activeOption.color,
                    alpha = rowAlpha,
                )
                Text(
                    modifier = Modifier.weight(1f),
                    text = module.name,
                    color = AgentBelayColors.inkPrimary.copy(alpha = rowAlpha),
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold,
                    fontFamily = FontFamily.Monospace,
                )
                ColoredModePicker(
                    options = redactionModeOptions,
                    active = activeOption,
                    onSelect = onModeChange,
                    alpha = rowAlpha,
                )
            }
            Spacer(Modifier.height(8.dp))
            Text(
                modifier = Modifier.fillMaxWidth(),
                text = module.description,
                color = AgentBelayColors.inkTertiary.copy(alpha = rowAlpha),
                fontSize = 12.sp,
                lineHeight = 18.sp,
            )
        }
        AnimatedVisibility(visible = expanded && module.rules.isNotEmpty()) {
            Column(
                modifier = Modifier.padding(start = 14.dp, end = 14.dp, bottom = 14.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                module.rules.forEach { rule ->
                    val isDisabled = rule.id in moduleSettings.disabledRules
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = rule.name,
                                color = AgentBelayColors.inkPrimary,
                                fontSize = 12.5.sp,
                            )
                            Spacer(Modifier.height(2.dp))
                            Text(
                                text = rule.description,
                                color = AgentBelayColors.inkTertiary,
                                fontSize = 11.sp,
                                lineHeight = 16.sp,
                            )
                        }
                        DesignToggle(
                            checked = !isDisabled,
                            onCheckedChange = { onRuleToggle(rule.id, it) },
                        )
                    }
                }
            }
        }
    }
}

@Preview(widthDp = 380, heightDp = 720)
@Composable
private fun PreviewRedactionSlim() {
    PreviewScaffold {
        Box(Modifier.padding(16.dp)) {
            RedactionSettingsContent(
                modules = builtInRedactionModules,
                settings = RedactionSettings(),
                onSettingsChange = {},
            )
        }
    }
}

@Preview(widthDp = 720, heightDp = 720)
@Composable
private fun PreviewRedactionWide() {
    PreviewScaffold {
        Box(Modifier.padding(24.dp)) {
            RedactionSettingsContent(
                modules = builtInRedactionModules,
                settings = RedactionSettings(),
                onSettingsChange = {},
            )
        }
    }
}

@Preview(widthDp = 720, heightDp = 720)
@Composable
private fun PreviewRedactionMasterOff() {
    PreviewScaffold {
        Box(Modifier.padding(24.dp)) {
            RedactionSettingsContent(
                modules = builtInRedactionModules,
                settings = RedactionSettings(enabled = false),
                onSettingsChange = {},
            )
        }
    }
}
