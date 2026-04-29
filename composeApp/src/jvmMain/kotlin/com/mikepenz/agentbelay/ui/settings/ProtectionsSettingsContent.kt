package com.mikepenz.agentbelay.ui.settings

import androidx.compose.animation.AnimatedVisibility
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupProperties
import com.mikepenz.agentbelay.model.ModuleSettings
import com.mikepenz.agentbelay.model.ProtectionMode
import com.mikepenz.agentbelay.model.ProtectionSettings
import com.mikepenz.agentbelay.protection.ProtectionModule
import com.mikepenz.agentbelay.ui.components.AgentBelayCard
import com.mikepenz.agentbelay.ui.components.DesignToggle
import com.mikepenz.agentbelay.ui.components.HorizontalHairline
import com.mikepenz.agentbelay.ui.icons.LucideChevronDown
import com.mikepenz.agentbelay.ui.icons.LucideShield
import com.mikepenz.agentbelay.ui.theme.AccentEmerald
import com.mikepenz.agentbelay.ui.theme.DangerRed
import com.mikepenz.agentbelay.ui.theme.AgentBelayColors
import com.mikepenz.agentbelay.ui.theme.InfoBlue
import com.mikepenz.agentbelay.ui.theme.InkMuted
import com.mikepenz.agentbelay.ui.theme.PreviewScaffold
import com.mikepenz.agentbelay.ui.theme.WarnYellow
import com.mikepenz.agentbelay.protection.modules.DestructiveCommandsModule
import com.mikepenz.agentbelay.protection.modules.SensitiveFilesModule
import com.mikepenz.agentbelay.protection.modules.SupplyChainRceModule
import com.mikepenz.agentbelay.protection.modules.ToolBypassModule
import androidx.compose.ui.tooling.preview.Preview

private data class ModeSpec(val value: ProtectionMode, val label: String, val color: Color)

private fun modeSpecs(corrective: Boolean): List<ModeSpec> = listOf(
    // "Off" uses the dark-scale InkMuted raw token because modeSpecs is not
    // @Composable. The actual rendering row resolves any null colors through
    // the theme-aware AgentBelayColors.inkMuted. Downstream call sites read
    // ModeSpec.color but only for the dot/label inside @Composable rows — if
    // tuning is needed later, swap to a null sentinel and resolve in the row.
    ModeSpec(ProtectionMode.DISABLED, "Off", InkMuted),
    ModeSpec(ProtectionMode.ASK, "Ask", WarnYellow),
    ModeSpec(ProtectionMode.ASK_AUTO_BLOCK, "Ask + Block", WarnYellow),
    ModeSpec(
        ProtectionMode.AUTO_BLOCK,
        if (corrective) "Auto-correct" else "Auto-block",
        if (corrective) AccentEmerald else DangerRed,
    ),
    ModeSpec(ProtectionMode.LOG_ONLY, "Log only", InfoBlue),
)

@Composable
fun ProtectionsSettingsContent(
    modules: List<ProtectionModule>,
    settings: ProtectionSettings,
    onSettingsChange: (ProtectionSettings) -> Unit,
) {
    Column(modifier = Modifier.fillMaxWidth().widthIn(max = 860.dp)) {
        Text(
            text = "Guardrails",
            color = AgentBelayColors.inkPrimary,
            fontSize = 14.sp,
            fontWeight = FontWeight.SemiBold,
            letterSpacing = (-0.1).sp,
        )
        Spacer(Modifier.height(3.dp))
        Text(
            text = "Pattern-matched rules that run before risk analysis. Choose how each responds.",
            color = AgentBelayColors.inkTertiary,
            fontSize = 12.5.sp,
            lineHeight = 18.sp,
        )
        Spacer(Modifier.height(18.dp))
        if (modules.isEmpty()) {
            Text(
                text = "No protection modules available.",
                color = AgentBelayColors.inkTertiary,
                fontSize = 12.sp,
            )
            return@Column
        }
        AgentBelayCard(modifier = Modifier.fillMaxWidth()) {
            Column {
                modules.forEachIndexed { idx, module ->
                    val moduleSettings = settings.modules[module.id] ?: ModuleSettings()
                    val effectiveMode = moduleSettings.mode ?: module.defaultMode
                    ProtectionRow(
                        module = module,
                        moduleSettings = moduleSettings,
                        effectiveMode = effectiveMode,
                        first = idx == 0,
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
private fun ProtectionRow(
    module: ProtectionModule,
    moduleSettings: ModuleSettings,
    effectiveMode: ProtectionMode,
    first: Boolean,
    onModeChange: (ProtectionMode) -> Unit,
    onRuleToggle: (ruleId: String, enabled: Boolean) -> Unit,
) {
    var expanded by remember(module.id) { mutableStateOf(false) }
    val specs = modeSpecs(module.corrective)
    val activeSpec = specs.find { it.value == effectiveMode } ?: specs[0]

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
            // Header: icon + module name + mode picker. The picker sits in the
            // title row so the description below can use the full card width
            // and avoid wrapping awkwardly in narrow layouts.
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                Box(
                    modifier = Modifier
                        .size(30.dp)
                        .clip(RoundedCornerShape(7.dp))
                        .background(activeSpec.color.copy(alpha = 0.14f)),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = LucideShield,
                        contentDescription = null,
                        tint = activeSpec.color,
                        modifier = Modifier.size(14.dp),
                    )
                }
                Text(
                    modifier = Modifier.weight(1f),
                    text = module.name,
                    color = AgentBelayColors.inkPrimary,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold,
                    fontFamily = FontFamily.Monospace,
                )
                ModePicker(specs = specs, active = activeSpec, onSelect = onModeChange)
            }
            Spacer(Modifier.height(8.dp))
            Text(
                modifier = Modifier.fillMaxWidth(),
                text = module.description,
                color = AgentBelayColors.inkTertiary,
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

@Composable
private fun ModePicker(
    specs: List<ModeSpec>,
    active: ModeSpec,
    onSelect: (ProtectionMode) -> Unit,
) {
    var open by remember { mutableStateOf(false) }
    val interactionSource = remember { MutableInteractionSource() }
    val hovered by interactionSource.collectIsHoveredAsState()
    Box {
        Row(
            modifier = Modifier
                .height(26.dp)
                .clip(RoundedCornerShape(6.dp))
                .background(active.color.copy(alpha = if (hovered) 0.14f else 0.10f))
                .border(1.dp, active.color.copy(alpha = 0.22f), RoundedCornerShape(6.dp))
                .hoverable(interactionSource)
                .clickable(interactionSource = interactionSource, indication = null) { open = !open }
                .padding(start = 10.dp, end = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Box(
                modifier = Modifier
                    .size(6.dp)
                    .clip(CircleShape)
                    .background(active.color),
            )
            Text(
                text = active.label,
                color = active.color,
                fontSize = 11.5.sp,
                fontWeight = FontWeight.Medium,
            )
            Icon(
                imageVector = LucideChevronDown,
                contentDescription = null,
                tint = active.color,
                modifier = Modifier.size(11.dp),
            )
        }
        if (open) {
            Popup(
                alignment = Alignment.TopEnd,
                offset = IntOffset(0, with(LocalDensity.current) { 30.dp.roundToPx() }),
                onDismissRequest = { open = false },
                properties = PopupProperties(focusable = true),
            ) {
                Column(
                    modifier = Modifier
                        .widthIn(min = 160.dp, max = 200.dp)
                        .clip(RoundedCornerShape(7.dp))
                        .background(AgentBelayColors.surface)
                        .border(1.dp, AgentBelayColors.line2, RoundedCornerShape(7.dp))
                        .padding(4.dp),
                ) {
                    specs.forEach { spec ->
                        val selected = spec.value == active.value
                        val optSource = remember(spec.value) { MutableInteractionSource() }
                        val optHovered by optSource.collectIsHoveredAsState()
                        val optBg = when {
                            selected -> AgentBelayColors.surface2
                            optHovered -> AgentBelayColors.surface2.copy(alpha = 0.5f)
                            else -> Color.Transparent
                        }
                        val optFg = if (selected) spec.color else AgentBelayColors.inkPrimary
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(4.dp))
                                .background(optBg)
                                .hoverable(optSource)
                                .clickable(interactionSource = optSource, indication = null) {
                                    onSelect(spec.value)
                                    open = false
                                }
                                .padding(horizontal = 10.dp, vertical = 7.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(6.dp)
                                    .clip(CircleShape)
                                    .background(spec.color),
                            )
                            Text(text = spec.label, color = optFg, fontSize = 12.sp)
                        }
                    }
                }
            }
        }
    }
}

@Preview(widthDp = 380, heightDp = 900)
@Composable
private fun PreviewGuardrailsSlim() {
    PreviewScaffold {
        Box(Modifier.padding(16.dp)) {
            ProtectionsSettingsContent(
                modules = listOf(
                    DestructiveCommandsModule,
                    SensitiveFilesModule,
                    SupplyChainRceModule,
                    ToolBypassModule,
                ),
                settings = ProtectionSettings(),
                onSettingsChange = {},
            )
        }
    }
}

@Preview(widthDp = 720, heightDp = 900)
@Composable
private fun PreviewGuardrailsWide() {
    PreviewScaffold {
        Box(Modifier.padding(24.dp)) {
            ProtectionsSettingsContent(
                modules = listOf(
                    DestructiveCommandsModule,
                    SensitiveFilesModule,
                    SupplyChainRceModule,
                    ToolBypassModule,
                ),
                settings = ProtectionSettings(),
                onSettingsChange = {},
            )
        }
    }
}
