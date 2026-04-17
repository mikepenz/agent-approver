package com.mikepenz.agentbuddy.ui.settings

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
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.mikepenz.agentbuddy.model.AppSettings

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
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        SectionHeader("Integration")

        // Claude Code card
        IntegrationCard(
            title = "Claude Code",
            isRegistered = isHookRegistered,
            description = "Hook in ~/.claude/settings.json",
            onRegister = onRegisterHook,
            onUnregister = onUnregisterHook,
        )

        // GitHub Copilot card
        IntegrationCard(
            title = "GitHub Copilot",
            isRegistered = isCopilotRegistered,
            description = "User-scoped hook in ~/.copilot/hooks/agent-buddy.json " +
                "(PreToolUse + PermissionRequest, requires Copilot CLI ≥ v1.0.21)",
            onRegister = onRegisterCopilot,
            onUnregister = onUnregisterCopilot,
        ) {
            CopilotFailClosedToggle(
                failClosed = settings.copilotFailClosed,
                onChange = { onSettingsChange(settings.copy(copilotFailClosed = it)) },
            )
        }
    }
}

@Composable
private fun CopilotFailClosedToggle(
    failClosed: Boolean,
    onChange: (Boolean) -> Unit,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text("Fail closed when unreachable", style = MaterialTheme.typography.bodyMedium)
            Text(
                if (failClosed) {
                    "Copilot blocks the action if Agent Buddy isn't running."
                } else {
                    "Copilot proceeds normally if Agent Buddy isn't running (default)."
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Switch(checked = failClosed, onCheckedChange = onChange)
    }
}

@Composable
private fun IntegrationCard(
    title: String,
    isRegistered: Boolean,
    description: String,
    onRegister: () -> Unit,
    onUnregister: () -> Unit,
    extraContent: @Composable (() -> Unit)? = null,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
    ) {
        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(title, style = MaterialTheme.typography.titleSmall)
                StatusBadge(
                    text = if (isRegistered) "Registered" else "Not registered",
                    color = if (isRegistered) Color(0xFF4CAF50) else Color(0xFF9E9E9E),
                )
            }
            Text(
                description,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            extraContent?.invoke()
            Button(
                onClick = if (isRegistered) onUnregister else onRegister,
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (isRegistered) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
                ),
            ) {
                Text(if (isRegistered) "Unregister" else "Register")
            }
        }
    }
}
