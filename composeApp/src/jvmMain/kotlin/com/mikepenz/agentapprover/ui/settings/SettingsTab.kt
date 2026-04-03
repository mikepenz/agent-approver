package com.mikepenz.agentapprover.ui.settings

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mikepenz.agentapprover.model.AppSettings
import com.mikepenz.agentapprover.model.RiskAnalysisBackend
import com.mikepenz.agentapprover.platform.StartupManager
import com.mikepenz.agentapprover.risk.RiskMessageBuilder
import com.mikepenz.agentapprover.ui.theme.AgentApproverTheme

@Composable
fun SettingsTab(
    settings: AppSettings,
    isHookRegistered: Boolean,
    historyCount: Int,
    onSettingsChange: (AppSettings) -> Unit,
    onRegisterHook: () -> Unit,
    onUnregisterHook: () -> Unit,
    onClearHistory: () -> Unit,
    onShowLicenses: () -> Unit = {},
) {
    var showClearDialog by remember { mutableStateOf(false) }

    if (showClearDialog) {
        AlertDialog(
            onDismissRequest = { showClearDialog = false },
            title = { Text("Clear all history?") },
            text = { Text("This will permanently remove all history entries.") },
            confirmButton = {
                TextButton(onClick = {
                    onClearHistory()
                    showClearDialog = false
                }) {
                    Text("Clear", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { showClearDialog = false }) {
                    Text("Cancel")
                }
            },
        )
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        // -- Integration Section --
        SectionHeader("Integration")

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

        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.5f)),
        ) {
            Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("GitHub Copilot", style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f))
                    StatusBadge(text = "Coming Soon", color = Color(0xFF9E9E9E))
                }
            }
        }

        // -- General Section --
        SectionHeader("General")

        // Theme mode selector
        Column(
            verticalArrangement = Arrangement.spacedBy(4.dp),
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text("Theme", style = MaterialTheme.typography.bodyMedium)
            val modes = com.mikepenz.agentapprover.model.ThemeMode.entries
            SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                modes.forEachIndexed { index, mode ->
                    SegmentedButton(
                        selected = settings.themeMode == mode,
                        onClick = { onSettingsChange(settings.copy(themeMode = mode)) },
                        shape = SegmentedButtonDefaults.itemShape(index = index, count = modes.size),
                    ) {
                        Text(
                            when (mode) {
                                com.mikepenz.agentapprover.model.ThemeMode.SYSTEM -> "System"
                                com.mikepenz.agentapprover.model.ThemeMode.DARK -> "Dark"
                                com.mikepenz.agentapprover.model.ThemeMode.LIGHT -> "Light"
                            },
                            fontSize = 12.sp,
                        )
                    }
                }
            }
        }

        // macOS notification permission
        if (System.getProperty("os.name", "").contains("Mac", ignoreCase = true)) {
            var permissionStatus by remember { mutableStateOf<String?>(null) }
            var badgeEnabled by remember { mutableStateOf<Boolean?>(null) }

            // Check current permission status
            androidx.compose.runtime.LaunchedEffect(Unit) {
                io.github.kdroidfilter.nucleus.notification.NotificationCenter.getNotificationSettings { settings ->
                    permissionStatus = settings.authorizationStatus.name
                    badgeEnabled = settings.badgeSetting == io.github.kdroidfilter.nucleus.notification.NotificationSetting.ENABLED
                }
            }

            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            ) {
                Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("Notifications", style = MaterialTheme.typography.titleSmall)
                        permissionStatus?.let { status ->
                            val isAuthorized = status == "AUTHORIZED"
                            StatusBadge(
                                text = if (isAuthorized) "Allowed" else status.lowercase().replace('_', ' ').replaceFirstChar { it.uppercase() },
                                color = if (isAuthorized) Color(0xFF4CAF50) else Color(0xFFF44336),
                            )
                        }
                    }
                    if (badgeEnabled == false) {
                        Text(
                            "Badge permission is disabled. Enable \"Badge application icon\" in macOS notification settings.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    when (permissionStatus) {
                        "NOT_DETERMINED" -> {
                            Text(
                                "Grant notification permission for dock badge and alerts.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            OutlinedButton(
                                onClick = {
                                    io.github.kdroidfilter.nucleus.notification.NotificationCenter.requestAuthorization(
                                        setOf(
                                            io.github.kdroidfilter.nucleus.notification.AuthorizationOption.BADGE,
                                            io.github.kdroidfilter.nucleus.notification.AuthorizationOption.SOUND,
                                            io.github.kdroidfilter.nucleus.notification.AuthorizationOption.ALERT,
                                        )
                                    ) { granted, _ ->
                                        io.github.kdroidfilter.nucleus.notification.NotificationCenter.getNotificationSettings { settings ->
                                            permissionStatus = settings.authorizationStatus.name
                                            badgeEnabled = settings.badgeSetting == io.github.kdroidfilter.nucleus.notification.NotificationSetting.ENABLED
                                        }
                                    }
                                },
                                modifier = Modifier.fillMaxWidth(),
                            ) {
                                Text("Request Permission", fontSize = 12.sp)
                            }
                        }
                        "DENIED" -> {
                            Text(
                                "Notifications were denied. Open System Settings to enable them.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            OutlinedButton(
                                onClick = {
                                    @Suppress("DEPRECATION")
                                    Runtime.getRuntime().exec(
                                        arrayOf("open", "x-apple.systempreferences:com.apple.Notifications-Settings.extension")
                                    )
                                },
                                modifier = Modifier.fillMaxWidth(),
                            ) {
                                Text("Open Notification Settings", fontSize = 12.sp)
                            }
                        }
                    }
                }
            }
        }

        SettingsSwitch(
            label = "Always on top",
            checked = settings.alwaysOnTop,
            onCheckedChange = { onSettingsChange(settings.copy(alwaysOnTop = it)) },
        )

        SettingsSwitch(
            label = "Start on boot",
            checked = settings.startOnBoot,
            onCheckedChange = {
                StartupManager.setStartOnBoot(it)
                onSettingsChange(settings.copy(startOnBoot = it))
            },
        )

        SettingsSwitch(
            label = "Away mode (disable all timeouts)",
            checked = settings.awayMode,
            onCheckedChange = { onSettingsChange(settings.copy(awayMode = it)) },
        )

        // -- Risk Analysis Section --
        SectionHeader("Risk Analysis")

        SettingsSwitch(
            label = "Enable risk analysis",
            checked = settings.riskAnalysisEnabled,
            onCheckedChange = { onSettingsChange(settings.copy(riskAnalysisEnabled = it)) },
        )

        // Backend selector
        Column(
            verticalArrangement = Arrangement.spacedBy(4.dp),
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text("Backend", style = MaterialTheme.typography.bodyMedium)
            val backends = RiskAnalysisBackend.entries
            SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                backends.forEachIndexed { index, backend ->
                    SegmentedButton(
                        selected = settings.riskAnalysisBackend == backend,
                        onClick = { onSettingsChange(settings.copy(riskAnalysisBackend = backend)) },
                        shape = SegmentedButtonDefaults.itemShape(index = index, count = backends.size),
                        enabled = settings.riskAnalysisEnabled,
                    ) {
                        Text(
                            when (backend) {
                                RiskAnalysisBackend.CLAUDE -> "Claude"
                                RiskAnalysisBackend.COPILOT -> "Copilot"
                            },
                            fontSize = 12.sp,
                        )
                    }
                }
            }
        }

        // Model selector
        Column(
            verticalArrangement = Arrangement.spacedBy(4.dp),
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text("Model", style = MaterialTheme.typography.bodyMedium)
            if (settings.riskAnalysisBackend == RiskAnalysisBackend.CLAUDE) {
                val models = listOf("haiku", "sonnet", "opus")
                SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                    models.forEachIndexed { index, model ->
                        SegmentedButton(
                            selected = settings.riskAnalysisModel == model,
                            onClick = { onSettingsChange(settings.copy(riskAnalysisModel = model)) },
                            shape = SegmentedButtonDefaults.itemShape(index = index, count = models.size),
                            enabled = settings.riskAnalysisEnabled,
                        ) {
                            Text(model.replaceFirstChar { it.uppercase() }, fontSize = 12.sp)
                        }
                    }
                }
            } else {
                val copilotModels = listOf(
                    "gpt-4.1-mini" to "GPT-4.1 Mini",
                    "gpt-4.1" to "GPT-4.1",
                    "claude-sonnet-4.5" to "Sonnet 4.5",
                )
                SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                    copilotModels.forEachIndexed { index, (id, label) ->
                        SegmentedButton(
                            selected = settings.riskAnalysisCopilotModel == id,
                            onClick = { onSettingsChange(settings.copy(riskAnalysisCopilotModel = id)) },
                            shape = SegmentedButtonDefaults.itemShape(index = index, count = copilotModels.size),
                            enabled = settings.riskAnalysisEnabled,
                        ) {
                            Text(label, fontSize = 12.sp)
                        }
                    }
                }
            }
        }

        // Copilot auth section
        AnimatedVisibility(visible = settings.riskAnalysisBackend == RiskAnalysisBackend.COPILOT && settings.riskAnalysisEnabled) {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            ) {
                Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        "Requires GitHub Copilot CLI and a Copilot subscription.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    OutlinedButton(
                        onClick = {
                            java.awt.Desktop.getDesktop().browse(
                                java.net.URI("https://docs.github.com/en/copilot/managing-copilot/configure-personal-settings/using-github-copilot-in-the-command-line")
                            )
                        },
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text("Login with GitHub", fontSize = 12.sp)
                    }
                }
            }
        }

        // System prompt viewer
        var showSystemPrompt by remember { mutableStateOf(false) }
        val clipboardManager = LocalClipboardManager.current
        val effectivePrompt = settings.riskAnalysisCustomPrompt.ifBlank { RiskMessageBuilder.DEFAULT_SYSTEM_PROMPT }

        OutlinedButton(
            onClick = { showSystemPrompt = !showSystemPrompt },
            modifier = Modifier.fillMaxWidth(),
            enabled = settings.riskAnalysisEnabled,
        ) {
            Text(if (showSystemPrompt) "Hide System Prompt" else "View System Prompt", fontSize = 12.sp)
        }

        AnimatedVisibility(visible = showSystemPrompt) {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Surface(
                    modifier = Modifier.fillMaxWidth().heightIn(max = 200.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant,
                    shape = MaterialTheme.shapes.small,
                ) {
                    Column(
                        modifier = Modifier
                            .padding(8.dp)
                            .verticalScroll(rememberScrollState()),
                    ) {
                        Text(
                            text = effectivePrompt,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                    }
                }
                OutlinedButton(
                    onClick = { clipboardManager.setText(AnnotatedString(effectivePrompt)) },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text("Copy to Clipboard", fontSize = 12.sp)
                }
            }
        }

        // Custom system prompt
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(
                "Custom system prompt (leave empty for default)",
                style = MaterialTheme.typography.bodyMedium,
                color = if (settings.riskAnalysisEnabled) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f),
            )
            OutlinedTextField(
                value = settings.riskAnalysisCustomPrompt,
                onValueChange = { onSettingsChange(settings.copy(riskAnalysisCustomPrompt = it)) },
                modifier = Modifier.fillMaxWidth(),
                placeholder = { Text("Uses default prompt if empty", fontSize = 12.sp) },
                textStyle = MaterialTheme.typography.bodySmall,
                minLines = 3,
                maxLines = 6,
                enabled = settings.riskAnalysisEnabled,
            )
        }

        SettingsSwitch(
            label = "Auto-approve risk 1",
            checked = settings.autoApproveRisk1,
            onCheckedChange = { onSettingsChange(settings.copy(autoApproveRisk1 = it)) },
            enabled = settings.riskAnalysisEnabled,
        )

        SettingsSwitch(
            label = "Auto-deny risk 5",
            checked = settings.autoDenyRisk5,
            onCheckedChange = { onSettingsChange(settings.copy(autoDenyRisk5 = it)) },
            enabled = settings.riskAnalysisEnabled,
        )

        // -- Data Section --
        SectionHeader("Data")

        Text(
            "History entries: $historyCount / 250",
            style = MaterialTheme.typography.bodyMedium,
        )

        Button(
            onClick = { showClearDialog = true },
            modifier = Modifier.fillMaxWidth(),
            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
        ) {
            Text("Clear History")
        }

        // -- Server Section --
        SectionHeader("Server")

        SettingsTextField(
            label = "Server port",
            value = settings.serverPort.toString(),
            onValueChange = { it.toIntOrNull()?.let { port -> onSettingsChange(settings.copy(serverPort = port)) } },
        )

        SettingsTextField(
            label = "Default timeout (seconds)",
            value = settings.defaultTimeoutSeconds.toString(),
            onValueChange = { it.toIntOrNull()?.let { timeout -> onSettingsChange(settings.copy(defaultTimeoutSeconds = timeout)) } },
        )

        // -- About --
        SectionHeader("About")

        OutlinedButton(
            onClick = onShowLicenses,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text("Open Source Libraries")
        }

        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = "Agent Approver v${com.mikepenz.agentapprover.VERSION}",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f),
            modifier = Modifier.fillMaxWidth(),
            textAlign = TextAlign.Center,
        )

        Spacer(modifier = Modifier.height(8.dp))
    }
}

@Composable
private fun SectionHeader(title: String) {
    Text(
        text = title,
        style = MaterialTheme.typography.titleMedium,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(top = 4.dp),
    )
}

@Composable
private fun StatusBadge(text: String, color: Color) {
    Surface(
        shape = MaterialTheme.shapes.small,
        color = color.copy(alpha = 0.15f),
    ) {
        Text(
            text = text,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
            style = MaterialTheme.typography.labelSmall,
            color = color,
        )
    }
}

@Composable
private fun SettingsTextField(
    label: String,
    value: String,
    onValueChange: (String) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(label, style = MaterialTheme.typography.bodyMedium)
        OutlinedTextField(
            value = value,
            onValueChange = onValueChange,
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            textStyle = MaterialTheme.typography.bodySmall,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
        )
    }
}

@Composable
private fun SettingsSwitch(
    label: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    enabled: Boolean = true,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            label,
            style = MaterialTheme.typography.bodyMedium,
            color = if (enabled) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f),
        )
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            enabled = enabled,
        )
    }
}

// -- Previews --

@Preview
@Composable
private fun PreviewRegistered() {
    AgentApproverTheme {
        SettingsTab(
            settings = AppSettings(),
            isHookRegistered = true,
            historyCount = 42,
            onSettingsChange = {},
            onRegisterHook = {},
            onUnregisterHook = {},
            onClearHistory = {},
        )
    }
}

@Preview
@Composable
private fun PreviewUnregistered() {
    AgentApproverTheme {
        SettingsTab(
            settings = AppSettings(),
            isHookRegistered = false,
            historyCount = 0,
            onSettingsChange = {},
            onRegisterHook = {},
            onUnregisterHook = {},
            onClearHistory = {},
        )
    }
}
