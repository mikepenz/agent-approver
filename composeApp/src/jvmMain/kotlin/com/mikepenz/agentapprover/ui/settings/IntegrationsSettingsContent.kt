package com.mikepenz.agentapprover.ui.settings

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mikepenz.agentapprover.model.AppSettings

@Composable
fun IntegrationsSettingsContent(
    settings: AppSettings,
    isHookRegistered: Boolean,
    isCopilotInstalled: Boolean,
    onRegisterHook: () -> Unit,
    onUnregisterHook: () -> Unit,
    onInstallCopilot: () -> Unit,
    onUninstallCopilot: () -> Unit,
    onRegisterCopilotHook: (String) -> Unit,
    onUnregisterCopilotHook: (String) -> Unit,
    isCopilotHookRegistered: (String) -> Boolean,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        SectionHeader("Integration")

        // Claude Code card
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        ) {
            Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Claude Code", style = MaterialTheme.typography.titleSmall)
                    StatusBadge(
                        text = if (isHookRegistered) "Registered" else "Not registered",
                        color = if (isHookRegistered) Color(0xFF4CAF50) else Color(0xFF9E9E9E),
                    )
                }
                Text(
                    "Hook in ~/.claude/settings.json",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Button(
                    onClick = if (isHookRegistered) onUnregisterHook else onRegisterHook,
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (isHookRegistered) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
                    ),
                ) {
                    Text(if (isHookRegistered) "Unregister" else "Register")
                }
            }
        }

        // GitHub Copilot card
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        ) {
            Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("GitHub Copilot", style = MaterialTheme.typography.titleSmall)
                    StatusBadge(
                        text = if (isCopilotInstalled) "Installed" else "Not installed",
                        color = if (isCopilotInstalled) Color(0xFF4CAF50) else Color(0xFF9E9E9E),
                    )
                }
                Text(
                    "Bridge script at ~/.agent-approver/copilot-hook.sh",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Button(
                    onClick = if (isCopilotInstalled) onUninstallCopilot else onInstallCopilot,
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (isCopilotInstalled) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
                    ),
                ) {
                    Text(if (isCopilotInstalled) "Uninstall bridge script" else "Install bridge script")
                }

                AnimatedVisibility(visible = isCopilotInstalled) {
                    CopilotProjectHookSection(
                        onRegisterCopilotHook = onRegisterCopilotHook,
                        onUnregisterCopilotHook = onUnregisterCopilotHook,
                        isCopilotHookRegistered = isCopilotHookRegistered,
                    )
                }
            }
        }
    }
}

@Composable
private fun CopilotProjectHookSection(
    onRegisterCopilotHook: (String) -> Unit,
    onUnregisterCopilotHook: (String) -> Unit,
    isCopilotHookRegistered: (String) -> Boolean,
) {
    var projectPath by remember { mutableStateOf("") }
    val isRegistered = projectPath.isNotBlank() && isCopilotHookRegistered(projectPath)

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            "Per-project hook (.github/hooks/hooks.json)",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        OutlinedTextField(
            value = projectPath,
            onValueChange = { projectPath = it },
            label = { Text("Project path", fontSize = 12.sp) },
            placeholder = { Text("/path/to/your/project", fontSize = 12.sp) },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
        )
        if (projectPath.isNotBlank()) {
            Button(
                onClick = {
                    if (isRegistered) onUnregisterCopilotHook(projectPath)
                    else onRegisterCopilotHook(projectPath)
                },
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (isRegistered) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
                ),
            ) {
                Text(if (isRegistered) "Unregister hook" else "Register hook")
            }
        }
    }
}
