package com.mikepenz.agentapprover.ui.settings

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mikepenz.agentapprover.model.AppSettings
import com.mikepenz.agentapprover.model.RiskAnalysisBackend
import com.mikepenz.agentapprover.risk.RiskMessageBuilder

@Composable
fun RiskAnalysisSettingsContent(
    settings: AppSettings,
    copilotModels: List<Pair<String, String>>,
    onSettingsChange: (AppSettings) -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
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
                val models = copilotModels.ifEmpty {
                    listOf("gpt-4.1-mini" to "GPT-4.1 Mini", "gpt-4.1" to "GPT-4.1", "claude-sonnet-4.5" to "Sonnet 4.5")
                }
                val selectedLabel = models.find { it.first == settings.riskAnalysisCopilotModel }?.second
                    ?: settings.riskAnalysisCopilotModel
                var expanded by remember { mutableStateOf(false) }
                Box(modifier = Modifier.fillMaxWidth()) {
                    OutlinedButton(
                        onClick = { expanded = true },
                        modifier = Modifier.fillMaxWidth(),
                        enabled = settings.riskAnalysisEnabled,
                    ) {
                        Text(selectedLabel, fontSize = 12.sp, modifier = Modifier.weight(1f), textAlign = TextAlign.Start)
                        Text("\u25BE", fontSize = 12.sp)
                    }
                    DropdownMenu(
                        expanded = expanded,
                        onDismissRequest = { expanded = false },
                    ) {
                        models.forEach { (id, label) ->
                            DropdownMenuItem(
                                text = { Text(label, fontSize = 12.sp) },
                                onClick = {
                                    onSettingsChange(settings.copy(riskAnalysisCopilotModel = id))
                                    expanded = false
                                },
                            )
                        }
                    }
                }
            }
        }

        // Copilot auth section
        AnimatedVisibility(visible = settings.riskAnalysisBackend == RiskAnalysisBackend.COPILOT && settings.riskAnalysisEnabled) {
            var ghAuthStatus by remember { mutableStateOf<String?>(null) }
            var ghAuthOk by remember { mutableStateOf<Boolean?>(null) }

            androidx.compose.runtime.LaunchedEffect(Unit) {
                kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                    try {
                        val home = System.getProperty("user.home")
                        val process = ProcessBuilder("/bin/sh", "-c", "gh auth status").apply {
                            redirectErrorStream(true)
                            val path = environment()["PATH"] ?: ""
                            val extraPaths = listOf(
                                "/usr/local/bin",
                                "/opt/homebrew/bin",
                                "$home/.local/bin",
                                "$home/bin",
                            )
                            environment()["PATH"] = (extraPaths + path.split(":")).distinct().joinToString(":")
                        }.start()
                        val output = process.inputStream.bufferedReader().readText().trim()
                        val exitCode = process.waitFor()
                        ghAuthOk = exitCode == 0
                        ghAuthStatus = if (exitCode == 0) "Authenticated" else "Not authenticated"
                    } catch (_: Exception) {
                        ghAuthOk = false
                        ghAuthStatus = "gh CLI not found"
                    }
                }
            }

            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            ) {
                Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("GitHub Auth", style = MaterialTheme.typography.titleSmall)
                        ghAuthStatus?.let { status ->
                            StatusBadge(
                                text = status,
                                color = if (ghAuthOk == true) Color(0xFF4CAF50) else Color(0xFFF44336),
                            )
                        }
                    }
                    if (ghAuthOk == true) {
                        Text(
                            "GitHub CLI authenticated. Copilot subscription required.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    } else {
                        Text(
                            "Requires GitHub CLI (gh) and a Copilot subscription.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        OutlinedButton(
                            onClick = {
                                java.awt.Desktop.getDesktop().browse(
                                    java.net.URI("https://cli.github.com/")
                                )
                            },
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Text("Install GitHub CLI", fontSize = 12.sp)
                        }
                    }
                    if (ghAuthOk != true) {
                        OutlinedButton(
                            onClick = {
                                java.awt.Desktop.getDesktop().browse(
                                    java.net.URI("https://cli.github.com/manual/gh_auth_login")
                                )
                            },
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Text("Login with GitHub (gh auth login)", fontSize = 12.sp)
                        }
                    }
                    // Copilot CLI path override
                    OutlinedTextField(
                        value = settings.riskAnalysisCopilotCliPath,
                        onValueChange = { onSettingsChange(settings.copy(riskAnalysisCopilotCliPath = it)) },
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text("Copilot CLI path", fontSize = 12.sp) },
                        placeholder = { Text("Auto-detect", fontSize = 12.sp) },
                        textStyle = MaterialTheme.typography.bodySmall,
                        singleLine = true,
                    )
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
    }
}
