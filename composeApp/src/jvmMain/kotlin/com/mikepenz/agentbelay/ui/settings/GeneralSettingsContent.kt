package com.mikepenz.agentbelay.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.unit.dp
import com.mikepenz.agentbelay.model.AppSettings
import com.mikepenz.agentbelay.model.ThemeMode
import com.mikepenz.agentbelay.platform.StartupManager
import com.mikepenz.agentbelay.ui.components.DesignToggle
import com.mikepenz.agentbelay.ui.components.GhostButton
import com.mikepenz.agentbelay.ui.components.OutlineButton
import com.mikepenz.agentbelay.ui.components.PillSegmented
import com.mikepenz.agentbelay.ui.theme.AccentEmerald
import com.mikepenz.agentbelay.ui.theme.AgentBelayColors
import com.mikepenz.agentbelay.ui.theme.DangerRed
import com.mikepenz.agentbelay.ui.theme.WarnYellow
import com.mikepenz.agentbelay.update.UpdateUiState
import io.github.kdroidfilter.nucleus.notification.AuthorizationOption
import io.github.kdroidfilter.nucleus.notification.AuthorizationStatus
import io.github.kdroidfilter.nucleus.notification.NotificationCenter

private val isMacOs = System.getProperty("os.name", "").contains("Mac", ignoreCase = true)

@Composable
fun GeneralSettingsContent(
    settings: AppSettings,
    historyCount: Int,
    approveHotkeyError: String? = null,
    denyHotkeyError: String? = null,
    onSettingsChange: (AppSettings) -> Unit,
    onClearHistory: () -> Unit,
    onShowLicenses: () -> Unit,
    updateState: UpdateUiState = UpdateUiState.Idle,
    isUpdateSupported: Boolean = false,
    onCheckForUpdates: () -> Unit = {},
    onDownloadUpdate: () -> Unit = {},
    onInstallUpdate: () -> Unit = {},
    onResetUpdateState: () -> Unit = {},
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
                TextButton(onClick = { showClearDialog = false }) { Text("Cancel") }
            },
        )
    }

    SettingSection(title = "Appearance", desc = "How Agent Belay looks across your desktop.") {
        SettingItem(label = "Theme", first = true, right = {
            PillSegmented(
                options = listOf(
                    ThemeMode.SYSTEM to "System",
                    ThemeMode.DARK to "Dark",
                    ThemeMode.LIGHT to "Light",
                ),
                selected = settings.themeMode,
                onSelect = { onSettingsChange(settings.copy(themeMode = it)) },
            )
        })
    }

    SettingSection(title = "Behavior") {
        SettingItem(
            label = "Always on top",
            desc = "Keep the window above other apps when an approval arrives.",
            first = true,
            right = {
                DesignToggle(
                    checked = settings.alwaysOnTop,
                    onCheckedChange = { onSettingsChange(settings.copy(alwaysOnTop = it)) },
                )
            },
        )
        SettingItem(
            label = "Start on boot",
            right = {
                DesignToggle(
                    checked = settings.startOnBoot,
                    onCheckedChange = {
                        StartupManager.setStartOnBoot(it)
                        onSettingsChange(settings.copy(startOnBoot = it))
                    },
                )
            },
        )
        SettingItem(
            label = "Away mode",
            desc = "Disable timeouts while you're away. Approvals wait indefinitely.",
            right = {
                DesignToggle(
                    checked = settings.awayMode,
                    onCheckedChange = { onSettingsChange(settings.copy(awayMode = it)) },
                )
            },
        )
        SettingItem(
            label = "Prominent \"always allow\" button",
            desc = "Surface the sticky-approve option in the approval footer.",
            right = {
                DesignToggle(
                    checked = settings.prominentAlwaysAllow,
                    onCheckedChange = { onSettingsChange(settings.copy(prominentAlwaysAllow = it)) },
                )
            },
        )
    }

    SettingSection(
        title = "Global hotkeys",
        desc = "Resolve the oldest pending request from anywhere on your desktop. " +
            "Click a row to record a key combination, click again or press the X to clear.",
    ) {
        SettingItem(
            label = "Approve oldest",
            desc = approveHotkeyError,
            first = true,
            right = {
                HotkeyCaptureField(
                    hotkey = settings.approveOldestHotkey,
                    onChange = { onSettingsChange(settings.copy(approveOldestHotkey = it)) },
                    hasError = approveHotkeyError != null,
                )
            },
        )
        SettingItem(
            label = "Deny oldest",
            desc = denyHotkeyError,
            right = {
                HotkeyCaptureField(
                    hotkey = settings.denyOldestHotkey,
                    onChange = { onSettingsChange(settings.copy(denyOldestHotkey = it)) },
                    hasError = denyHotkeyError != null,
                )
            },
        )
    }

    if (isMacOs) {
        SettingSection(
            title = "Notifications",
            desc = "macOS dock badge requires notification permission. " +
                "Without it the pending-approval count won't show on the dock icon.",
        ) {
            NotificationPermissionItem()
        }
    }

    SettingSection(title = "Server", desc = "Local approval endpoint used by your agents.") {
        SettingItem(label = "Listening port", first = true, right = {
            SettingsTextInput(
                value = settings.serverPort.toString(),
                onChange = { raw ->
                    raw.toIntOrNull()?.let { port ->
                        onSettingsChange(settings.copy(serverPort = port))
                    }
                },
                mono = true,
            )
        })
        SettingItem(
            label = "Bind address",
            desc = "\"All interfaces\" exposes the approval server to your LAN. " +
                "No authentication \u2014 only enable on trusted networks.",
            right = {
                PillSegmented(
                    options = listOf(
                        "127.0.0.1" to "Loopback",
                        "0.0.0.0" to "All interfaces",
                    ),
                    selected = settings.serverHost,
                    onSelect = { onSettingsChange(settings.copy(serverHost = it)) },
                )
            },
        )
        SettingItem(
            label = "Default timeout",
            desc = "How long an approval waits before the agent falls back to a default.",
            right = {
                SettingsTextInput(
                    value = settings.defaultTimeoutSeconds.toString(),
                    onChange = { raw ->
                        raw.toIntOrNull()?.let { t ->
                            onSettingsChange(settings.copy(defaultTimeoutSeconds = t))
                        }
                    },
                    suffix = "sec",
                    mono = true,
                    width = 140.dp,
                )
            },
        )
    }

    SettingSection(title = "Data", desc = "Stored locally; nothing leaves your machine.") {
        SettingItem(
            label = "History",
            desc = "$historyCount of ${settings.maxHistoryEntries} entries used.",
            first = true,
            right = { OutlineButton(text = "Export JSON", onClick = {}) },
        )
        SettingItem(
            label = "Clear history",
            desc = "Permanently removes all recorded decisions and protections.",
            right = {
                GhostButton(
                    text = "Clear\u2026",
                    color = DangerRed,
                    onClick = { showClearDialog = true },
                )
            },
        )
    }

    SettingSection(title = "Diagnostics") {
        SettingItem(
            label = "Verbose logging",
            desc = "Include commands, file paths, and request/response details in logs. " +
                "Keep off unless diagnosing an issue \u2014 sensitive content may appear in log files.",
            first = true,
            right = {
                DesignToggle(
                    checked = settings.verboseLogging,
                    onCheckedChange = { onSettingsChange(settings.copy(verboseLogging = it)) },
                )
            },
        )
    }

    SettingSection(title = "About", desc = "Agent Belay v${com.mikepenz.agentbelay.VERSION}") {
        UpdateCheckRow(
            state = updateState,
            isSupported = isUpdateSupported,
            first = true,
            onCheck = onCheckForUpdates,
            onDownload = onDownloadUpdate,
            onInstall = onInstallUpdate,
            onDismissError = onResetUpdateState,
        )
        SettingItem(
            label = "Open source libraries",
            desc = "View third-party licenses bundled with Agent Belay.",
            right = { OutlineButton(text = "View\u2026", onClick = onShowLicenses) },
        )
    }
}

@Composable
private fun NotificationPermissionItem() {
    var status by remember { mutableStateOf<AuthorizationStatus?>(null) }
    var badgeEnabled by remember { mutableStateOf(false) }

    fun refresh() {
        NotificationCenter.getNotificationSettings { settings ->
            status = settings.authorizationStatus
            badgeEnabled = settings.badgeSetting ==
                io.github.kdroidfilter.nucleus.notification.NotificationSetting.ENABLED
        }
    }

    LaunchedEffect(Unit) { refresh() }

    val (label, color) = when (status) {
        AuthorizationStatus.AUTHORIZED, AuthorizationStatus.PROVISIONAL,
        AuthorizationStatus.EPHEMERAL -> "Granted" to AccentEmerald
        AuthorizationStatus.DENIED -> "Denied" to DangerRed
        AuthorizationStatus.NOT_DETERMINED -> "Not requested" to WarnYellow
        null -> "Checking\u2026" to AgentBelayColors.inkTertiary
    }

    val desc = when (status) {
        AuthorizationStatus.DENIED ->
            "Notifications are blocked. Open System Settings \u203a Notifications \u203a " +
                "Agent Belay to allow badges."
        AuthorizationStatus.AUTHORIZED, AuthorizationStatus.PROVISIONAL,
        AuthorizationStatus.EPHEMERAL ->
            if (badgeEnabled) "Dock badge is allowed."
            else "Permission granted, but the badge sub-setting is disabled in System Settings."
        AuthorizationStatus.NOT_DETERMINED ->
            "Permission has not been requested yet. Grant to enable the dock badge."
        null -> null
    }

    SettingItem(
        label = "Permission status",
        desc = desc,
        first = true,
        right = {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                StatusBadge(text = label, color = color)
                when (status) {
                    AuthorizationStatus.NOT_DETERMINED -> OutlineButton(
                        text = "Grant",
                        onClick = {
                            NotificationCenter.requestAuthorization(
                                setOf(
                                    AuthorizationOption.BADGE,
                                    AuthorizationOption.ALERT,
                                    AuthorizationOption.SOUND,
                                ),
                            ) { _, _ -> refresh() }
                        },
                    )
                    AuthorizationStatus.DENIED -> OutlineButton(
                        text = "Recheck",
                        onClick = { refresh() },
                    )
                    else -> Unit
                }
            }
        },
    )
}
