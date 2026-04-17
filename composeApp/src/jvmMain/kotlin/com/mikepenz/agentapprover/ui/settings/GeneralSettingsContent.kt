package com.mikepenz.agentapprover.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mikepenz.agentapprover.model.AppSettings
import com.mikepenz.agentapprover.model.ThemeMode
import com.mikepenz.agentapprover.platform.StartupManager

@Composable
fun GeneralSettingsContent(
    settings: AppSettings,
    historyCount: Int,
    onSettingsChange: (AppSettings) -> Unit,
    onClearHistory: () -> Unit,
    onShowLicenses: () -> Unit,
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
        // Theme mode selector
        Column(
            verticalArrangement = Arrangement.spacedBy(4.dp),
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text("Theme", style = MaterialTheme.typography.bodyMedium)
            val modes = ThemeMode.entries
            SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                modes.forEachIndexed { index, mode ->
                    SegmentedButton(
                        selected = settings.themeMode == mode,
                        onClick = { onSettingsChange(settings.copy(themeMode = mode)) },
                        shape = SegmentedButtonDefaults.itemShape(index = index, count = modes.size),
                    ) {
                        Text(
                            when (mode) {
                                ThemeMode.SYSTEM -> "System"
                                ThemeMode.DARK -> "Dark"
                                ThemeMode.LIGHT -> "Light"
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
                io.github.kdroidfilter.nucleus.notification.NotificationCenter.getNotificationSettings { notifSettings ->
                    permissionStatus = notifSettings.authorizationStatus.name
                    badgeEnabled = notifSettings.badgeSetting == io.github.kdroidfilter.nucleus.notification.NotificationSetting.ENABLED
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
                                    ) { _, _ ->
                                        io.github.kdroidfilter.nucleus.notification.NotificationCenter.getNotificationSettings { notifSettings ->
                                            permissionStatus = notifSettings.authorizationStatus.name
                                            badgeEnabled = notifSettings.badgeSetting == io.github.kdroidfilter.nucleus.notification.NotificationSetting.ENABLED
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

        SettingsSwitch(
            label = "Prominent always-allow button",
            checked = settings.prominentAlwaysAllow,
            onCheckedChange = { onSettingsChange(settings.copy(prominentAlwaysAllow = it)) },
        )

        // -- Server Section --
        SectionHeader("Server")

        SettingsTextField(
            label = "Server port",
            value = settings.serverPort.toString(),
            onValueChange = { it.toIntOrNull()?.let { port -> onSettingsChange(settings.copy(serverPort = port)) } },
        )

        Column(
            verticalArrangement = Arrangement.spacedBy(4.dp),
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text("Bind address", style = MaterialTheme.typography.bodyMedium)
            val hostOptions = listOf(
                "127.0.0.1" to "Loopback",
                "0.0.0.0" to "All interfaces",
            )
            SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                hostOptions.forEachIndexed { index, (host, label) ->
                    SegmentedButton(
                        selected = settings.serverHost == host,
                        onClick = { onSettingsChange(settings.copy(serverHost = host)) },
                        shape = SegmentedButtonDefaults.itemShape(index = index, count = hostOptions.size),
                    ) {
                        Text(label, fontSize = 12.sp)
                    }
                }
            }
            Text(
                "\"All interfaces\" exposes the approval server to your local network. " +
                    "There is currently no authentication — only enable on trusted networks. " +
                    "Restart the app to apply.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        SettingsTextField(
            label = "Default timeout (seconds)",
            value = settings.defaultTimeoutSeconds.toString(),
            onValueChange = { it.toIntOrNull()?.let { timeout -> onSettingsChange(settings.copy(defaultTimeoutSeconds = timeout)) } },
        )

        // -- Data Section --
        SectionHeader("Data")

        Text(
            "History entries: $historyCount / ${settings.maxHistoryEntries}",
            style = MaterialTheme.typography.bodyMedium,
        )

        SettingsTextField(
            label = "Max history entries",
            value = settings.maxHistoryEntries.toString(),
            onValueChange = { it.toIntOrNull()?.let { max -> onSettingsChange(settings.copy(maxHistoryEntries = max)) } },
        )

        Button(
            onClick = { showClearDialog = true },
            modifier = Modifier.fillMaxWidth(),
            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
        ) {
            Text("Clear History")
        }

        // -- Diagnostics --
        SectionHeader("Diagnostics")

        SettingsSwitch(
            label = "Verbose logging",
            checked = settings.verboseLogging,
            onCheckedChange = { onSettingsChange(settings.copy(verboseLogging = it)) },
        )
        Text(
            "Include commands, file paths, and request/response details in logs. " +
                "Keep off unless diagnosing an issue — sensitive content may appear in log files.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
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
