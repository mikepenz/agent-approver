package com.mikepenz.agentbelay.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mikepenz.agentbelay.capability.CapabilityModule
import com.mikepenz.agentbelay.capability.modules.ResponseCompressionCapability
import com.mikepenz.agentbelay.model.CapabilityModuleSettings
import com.mikepenz.agentbelay.model.CapabilitySettings
import com.mikepenz.agentbelay.model.CompressionIntensity
import com.mikepenz.agentbelay.ui.components.DesignToggle
import com.mikepenz.agentbelay.ui.components.PillSegmented
import com.mikepenz.agentbelay.ui.icons.LucideBrain
import com.mikepenz.agentbelay.ui.icons.LucideShield
import com.mikepenz.agentbelay.ui.icons.LucideSliders
import com.mikepenz.agentbelay.ui.icons.LucideZap
import com.mikepenz.agentbelay.ui.theme.AccentEmerald
import com.mikepenz.agentbelay.ui.theme.AgentBelayColors
import com.mikepenz.agentbelay.ui.theme.InfoBlue
import com.mikepenz.agentbelay.ui.theme.VioletPurple
import com.mikepenz.agentbelay.ui.theme.WarnYellow

@Composable
fun CapabilitiesSettingsContent(
    modules: List<CapabilityModule>,
    settings: CapabilitySettings,
    onSettingsChange: (CapabilitySettings) -> Unit,
) {
    SettingSection(
        title = "Session capabilities",
        desc = "Instructions injected at the start of every session with participating agents.",
    ) {
        if (modules.isEmpty()) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(16.dp),
            ) {
                Text(
                    text = "No capabilities available.",
                    color = AgentBelayColors.inkTertiary,
                    fontSize = 12.sp,
                )
            }
            return@SettingSection
        }
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            modules.forEach { module ->
                val moduleSettings =
                    settings.modules[module.id] ?: CapabilityModuleSettings()
                CapabilityCard(
                    module = module,
                    moduleSettings = moduleSettings,
                    onToggle = { enabled ->
                        onSettingsChange(
                            settings.copy(
                                modules = settings.modules +
                                    (module.id to moduleSettings.copy(enabled = enabled)),
                            ),
                        )
                    },
                    onIntensityChange = { intensity ->
                        onSettingsChange(
                            settings.copy(
                                modules = settings.modules +
                                    (module.id to moduleSettings.copy(intensity = intensity)),
                            ),
                        )
                    },
                )
            }
        }
    }
}

private fun iconFor(moduleId: String): Pair<ImageVector, Color> = when (moduleId) {
    "response-compression" -> LucideZap to WarnYellow
    "socratic-thinking" -> LucideBrain to VioletPurple
    "safety-checklist" -> LucideShield to AccentEmerald
    else -> LucideSliders to InfoBlue
}

@Composable
private fun CapabilityCard(
    module: CapabilityModule,
    moduleSettings: CapabilityModuleSettings,
    onToggle: (Boolean) -> Unit,
    onIntensityChange: (CompressionIntensity) -> Unit,
) {
    val (icon, color) = iconFor(module.id)
    val checked = moduleSettings.enabled
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(if (checked) AgentBelayColors.surface2 else AgentBelayColors.background)
            .border(
                1.dp,
                if (checked) color.copy(alpha = 0.3f) else AgentBelayColors.line1,
                RoundedCornerShape(8.dp),
            )
            .clickable { onToggle(!checked) }
            .padding(14.dp),
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            Box(
                modifier = Modifier
                    .size(30.dp)
                    .clip(RoundedCornerShape(7.dp))
                    .background(color.copy(alpha = 0.14f)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = color,
                    modifier = Modifier.size(14.dp),
                )
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = module.name,
                    color = AgentBelayColors.inkPrimary,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold,
                )
                Spacer(Modifier.size(3.dp))
                Text(
                    text = module.description,
                    color = AgentBelayColors.inkTertiary,
                    fontSize = 12.sp,
                    lineHeight = 18.sp,
                )
            }
            DesignToggle(checked = checked, onCheckedChange = onToggle)
        }
        if (checked && module.id == ResponseCompressionCapability.id) {
            Spacer(Modifier.size(12.dp))
            Row(
                modifier = Modifier.padding(start = 44.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = "Intensity",
                    color = AgentBelayColors.inkTertiary,
                    fontSize = 11.5.sp,
                )
                PillSegmented(
                    options = listOf(
                        CompressionIntensity.LITE to "Lite",
                        CompressionIntensity.FULL to "Full",
                        CompressionIntensity.ULTRA to "Ultra",
                    ),
                    selected = moduleSettings.intensity ?: CompressionIntensity.FULL,
                    onSelect = onIntensityChange,
                )
            }
        }
    }
}
