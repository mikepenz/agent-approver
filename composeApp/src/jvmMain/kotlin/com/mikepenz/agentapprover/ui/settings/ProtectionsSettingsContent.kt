package com.mikepenz.agentapprover.ui.settings

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mikepenz.agentapprover.model.ModuleSettings
import com.mikepenz.agentapprover.model.ProtectionMode
import com.mikepenz.agentapprover.model.ProtectionSettings
import com.mikepenz.agentapprover.protection.ProtectionModule

@Composable
fun ProtectionsSettingsContent(
    modules: List<ProtectionModule>,
    settings: ProtectionSettings,
    onSettingsChange: (ProtectionSettings) -> Unit,
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
                "No protection modules available.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.fillMaxWidth().padding(vertical = 24.dp),
                textAlign = TextAlign.Center,
            )
        }

        modules.forEach { module ->
            val moduleSettings = settings.modules[module.id] ?: ModuleSettings()
            val effectiveMode = moduleSettings.mode ?: module.defaultMode
            var expanded by remember { mutableStateOf(false) }

            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            ) {
                Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    // Collapsed header - always visible
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { expanded = !expanded },
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
                        ModeBadge(mode = effectiveMode, corrective = module.corrective)
                    }

                    // Expanded content
                    AnimatedVisibility(visible = expanded) {
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            // Mode selector dropdown
                            ModeSelector(
                                currentMode = effectiveMode,
                                corrective = module.corrective,
                                onModeChange = { newMode ->
                                    val newSettings = settings.copy(
                                        modules = settings.modules + (module.id to moduleSettings.copy(mode = newMode))
                                    )
                                    onSettingsChange(newSettings)
                                },
                            )

                            // Rules list with toggle switches
                            if (module.rules.isNotEmpty()) {
                                Text(
                                    "Rules",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.primary,
                                )
                                module.rules.forEach { rule ->
                                    val isDisabled = rule.id in moduleSettings.disabledRules
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically,
                                    ) {
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(rule.name, style = MaterialTheme.typography.bodySmall)
                                            Text(
                                                rule.description,
                                                style = MaterialTheme.typography.labelSmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            )
                                        }
                                        Switch(
                                            checked = !isDisabled,
                                            onCheckedChange = { enabled ->
                                                val newDisabled = if (!enabled) {
                                                    moduleSettings.disabledRules + rule.id
                                                } else {
                                                    moduleSettings.disabledRules - rule.id
                                                }
                                                val newSettings = settings.copy(
                                                    modules = settings.modules + (module.id to moduleSettings.copy(disabledRules = newDisabled))
                                                )
                                                onSettingsChange(newSettings)
                                            },
                                            enabled = effectiveMode != ProtectionMode.DISABLED,
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
}

@Composable
private fun ModeBadge(mode: ProtectionMode, corrective: Boolean) {
    val (text, color) = when (mode) {
        ProtectionMode.AUTO_BLOCK -> (if (corrective) "Auto-correct" else "Auto-block") to Color(0xFFF44336)
        ProtectionMode.ASK_AUTO_BLOCK -> "Ask + Auto-block" to Color(0xFFFF9800)
        ProtectionMode.ASK -> "Ask" to Color(0xFFFFC107)
        ProtectionMode.LOG_ONLY -> "Log only" to Color(0xFF2196F3)
        ProtectionMode.DISABLED -> "Disabled" to Color(0xFF9E9E9E)
    }
    StatusBadge(text = text, color = color)
}

@Composable
private fun ModeSelector(
    currentMode: ProtectionMode,
    corrective: Boolean,
    onModeChange: (ProtectionMode) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    val modes = ProtectionMode.entries

    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text("Mode", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
        Box(modifier = Modifier.fillMaxWidth()) {
            OutlinedButton(
                onClick = { expanded = true },
                modifier = Modifier.fillMaxWidth(),
            ) {
                val label = when (currentMode) {
                    ProtectionMode.AUTO_BLOCK -> if (corrective) "Auto-correct" else "Auto-block"
                    ProtectionMode.ASK_AUTO_BLOCK -> "Ask + Auto-block"
                    ProtectionMode.ASK -> "Ask"
                    ProtectionMode.LOG_ONLY -> "Log only"
                    ProtectionMode.DISABLED -> "Disabled"
                }
                Text(label, fontSize = 12.sp, modifier = Modifier.weight(1f), textAlign = TextAlign.Start)
                Text("\u25BE", fontSize = 12.sp)
            }
            DropdownMenu(
                expanded = expanded,
                onDismissRequest = { expanded = false },
            ) {
                modes.forEach { mode ->
                    val label = when (mode) {
                        ProtectionMode.AUTO_BLOCK -> if (corrective) "Auto-correct" else "Auto-block"
                        ProtectionMode.ASK_AUTO_BLOCK -> "Ask + Auto-block"
                        ProtectionMode.ASK -> "Ask"
                        ProtectionMode.LOG_ONLY -> "Log only"
                        ProtectionMode.DISABLED -> "Disabled"
                    }
                    DropdownMenuItem(
                        text = { Text(label, fontSize = 12.sp) },
                        onClick = {
                            onModeChange(mode)
                            expanded = false
                        },
                    )
                }
            }
        }
    }
}
