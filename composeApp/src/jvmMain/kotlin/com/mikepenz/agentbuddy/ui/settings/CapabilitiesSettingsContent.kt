package com.mikepenz.agentbuddy.ui.settings

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.mikepenz.agentbuddy.capability.CapabilityModule
import com.mikepenz.agentbuddy.capability.modules.ResponseCompressionCapability
import com.mikepenz.agentbuddy.model.CapabilityModuleSettings
import com.mikepenz.agentbuddy.model.CapabilitySettings
import com.mikepenz.agentbuddy.model.CompressionIntensity

@Composable
fun CapabilitiesSettingsContent(
    modules: List<CapabilityModule>,
    settings: CapabilitySettings,
    onSettingsChange: (CapabilitySettings) -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        if (modules.isEmpty()) {
            Text(
                "No capabilities available.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.fillMaxWidth().padding(vertical = 24.dp),
                textAlign = TextAlign.Center,
            )
        }

        modules.forEach { module ->
            val moduleSettings = settings.modules[module.id] ?: CapabilityModuleSettings()
            var expanded by remember(module.id) { mutableStateOf(false) }

            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            ) {
                Column {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .clickable { expanded = !expanded }
                            .padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(module.name, style = MaterialTheme.typography.titleSmall)
                            Text(
                                module.description,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        val badgeText = if (moduleSettings.enabled) "On" else "Off"
                        val badgeColor =
                            if (moduleSettings.enabled) Color(0xFF4CAF50) else Color(0xFF9E9E9E)
                        StatusBadge(text = badgeText, color = badgeColor)
                        Switch(
                            checked = moduleSettings.enabled,
                            onCheckedChange = { enabled ->
                                val updated = moduleSettings.copy(enabled = enabled)
                                onSettingsChange(
                                    settings.copy(modules = settings.modules + (module.id to updated))
                                )
                            },
                        )
                    }

                    AnimatedVisibility(visible = expanded) {
                        Column(
                            modifier = Modifier.padding(start = 12.dp, end = 12.dp, bottom = 12.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            if (module.id == ResponseCompressionCapability.id) {
                                IntensitySelector(
                                    current = moduleSettings.intensity ?: CompressionIntensity.FULL,
                                    enabled = moduleSettings.enabled,
                                    onChange = { newIntensity ->
                                        val updated = moduleSettings.copy(intensity = newIntensity)
                                        onSettingsChange(
                                            settings.copy(modules = settings.modules + (module.id to updated))
                                        )
                                    },
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun IntensitySelector(
    current: CompressionIntensity,
    enabled: Boolean,
    onChange: (CompressionIntensity) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(
            "Intensity",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.primary,
        )
        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            CompressionIntensity.entries.forEach { intensity ->
                FilterChip(
                    selected = current == intensity,
                    onClick = { if (enabled) onChange(intensity) },
                    enabled = enabled,
                    label = {
                        Text(
                            when (intensity) {
                                CompressionIntensity.LITE -> "Lite"
                                CompressionIntensity.FULL -> "Full"
                                CompressionIntensity.ULTRA -> "Ultra"
                            },
                            style = MaterialTheme.typography.labelSmall,
                        )
                    },
                )
            }
        }
        Text(
            when (current) {
                CompressionIntensity.LITE -> "Filler removed, grammar intact. ~25–35% fewer output tokens."
                CompressionIntensity.FULL -> "Fragments, no articles. ~60–70% fewer output tokens."
                CompressionIntensity.ULTRA -> "Telegraphic, heavy abbreviation. ~75%+ fewer output tokens."
            },
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
