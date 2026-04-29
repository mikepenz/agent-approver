package com.mikepenz.agentbelay.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.mikepenz.agentbelay.ui.components.OutlineButton
import com.mikepenz.agentbelay.ui.theme.AccentEmerald
import com.mikepenz.agentbelay.ui.theme.AgentBelayColors
import com.mikepenz.agentbelay.ui.theme.DangerRed
import com.mikepenz.agentbelay.update.UpdateUiState

/**
 * About-section row that triggers update checks via [UpdateUiState].
 *
 * Caller wires the state + callbacks; this composable does NOT touch the
 * [com.mikepenz.agentbelay.update.UpdateManager] directly so previews stay
 * pure and the install action remains an explicit user click (it terminates
 * the process — never auto-fire it).
 */
@Composable
internal fun UpdateCheckRow(
    state: UpdateUiState,
    isSupported: Boolean,
    first: Boolean = false,
    onCheck: () -> Unit,
    onDownload: () -> Unit,
    onInstall: () -> Unit,
    onDismissError: () -> Unit,
) {
    var confirmInstall by remember { mutableStateOf(false) }

    if (confirmInstall && state is UpdateUiState.Ready) {
        AlertDialog(
            onDismissRequest = { confirmInstall = false },
            title = { Text("Install v${state.version}?") },
            text = {
                Text(
                    "Agent Belay will close, install the update, then relaunch. " +
                        "Any unsaved work will be lost.",
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    confirmInstall = false
                    onInstall()
                }) {
                    Text("Install & Restart")
                }
            },
            dismissButton = {
                TextButton(onClick = { confirmInstall = false }) { Text("Cancel") }
            },
        )
    }

    if (!isSupported) {
        SettingItem(
            label = "Updates",
            desc = "Auto-update is only available in installed builds (DMG / MSI / DEB / AppImage).",
            first = first,
        )
        return
    }

    val (desc, trailing) = when (state) {
        is UpdateUiState.Idle -> null to (@Composable {
            OutlineButton(text = "Check for updates", onClick = onCheck)
        })
        is UpdateUiState.Checking -> "Contacting GitHub…" to (@Composable {
            CircularProgressIndicator(
                modifier = Modifier.size(16.dp),
                strokeWidth = 2.dp,
                color = AccentEmerald,
            )
        })
        is UpdateUiState.UpToDate -> "You're on the latest version." to (@Composable {
            OutlineButton(text = "Check again", onClick = onCheck)
        })
        is UpdateUiState.Available -> "Version ${state.version} is available." to (@Composable {
            OutlineButton(text = "Download", onClick = onDownload)
        })
        is UpdateUiState.Downloading -> "Downloading… ${state.percent}%" to (@Composable {
            LinearProgressIndicator(
                progress = { (state.percent.coerceIn(0, 100) / 100f) },
                modifier = Modifier.width(120.dp),
                color = AccentEmerald,
            )
        })
        is UpdateUiState.Ready -> "v${state.version} ready to install." to (@Composable {
            OutlineButton(
                text = "Install & Restart",
                onClick = { confirmInstall = true },
            )
        })
        is UpdateUiState.Failed -> state.message to (@Composable {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                StatusBadge(text = "Failed", color = DangerRed)
                OutlineButton(
                    text = "Retry",
                    onClick = {
                        // Clear the Failed state, then immediately re-check.
                        // `check()` itself overwrites state to Checking, but
                        // resetting first keeps the manager's `inFlight` job
                        // canceled and avoids any hand-off ambiguity.
                        onDismissError()
                        onCheck()
                    },
                )
            }
        })
    }

    SettingItem(
        label = "Updates",
        desc = desc ?: "Check for new Agent Belay releases.",
        first = first,
        right = trailing,
    )
}
