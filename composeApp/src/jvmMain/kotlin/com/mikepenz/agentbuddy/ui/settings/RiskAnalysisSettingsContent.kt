package com.mikepenz.agentbuddy.ui.settings

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalTextStyle
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
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mikepenz.agentbuddy.model.AppSettings
import com.mikepenz.agentbuddy.model.RiskAnalysisBackend
import com.mikepenz.agentbuddy.risk.CopilotInitState
import com.mikepenz.agentbuddy.risk.OllamaInitState
import com.mikepenz.agentbuddy.risk.OllamaMetrics
import com.mikepenz.agentbuddy.risk.RiskMessageBuilder
import com.mikepenz.agentbuddy.ui.components.DecisionStatus
import com.mikepenz.agentbuddy.ui.components.GhostButton
import com.mikepenz.agentbuddy.ui.components.OutlineButton
import com.mikepenz.agentbuddy.ui.components.DesignToggle
import com.mikepenz.agentbuddy.ui.components.PillSegmented
import com.mikepenz.agentbuddy.ui.components.StatusPill
import com.mikepenz.agentbuddy.ui.components.TagSize
import com.mikepenz.agentbuddy.ui.icons.LucideChevronDown
import com.mikepenz.agentbuddy.ui.icons.LucideRefreshCw
import com.mikepenz.agentbuddy.ui.theme.AgentBuddyColors
import com.mikepenz.agentbuddy.ui.theme.DangerRed
import com.mikepenz.agentbuddy.ui.theme.WarnYellow

private fun openBrowserSafely(url: String) {
    try {
        if (!java.awt.Desktop.isDesktopSupported()) return
        val desktop = java.awt.Desktop.getDesktop()
        if (!desktop.isSupported(java.awt.Desktop.Action.BROWSE)) return
        desktop.browse(java.net.URI(url))
    } catch (_: Exception) {
    }
}

@Composable
fun RiskAnalysisSettingsContent(
    settings: AppSettings,
    copilotModels: List<Pair<String, String>>,
    copilotInitState: CopilotInitState = CopilotInitState.IDLE,
    ollamaModels: List<String> = emptyList(),
    ollamaInitState: OllamaInitState = OllamaInitState.IDLE,
    ollamaLastError: String? = null,
    ollamaLastMetrics: OllamaMetrics? = null,
    ollamaVersion: String? = null,
    onRefreshOllamaModels: () -> Unit = {},
    onSettingsChange: (AppSettings) -> Unit,
) {
    SettingSection(
        title = "Risk analysis",
        desc = "An optional LLM pre-screens each request and assigns a risk level 1\u20135.",
    ) {
        SettingItem(
            label = "Enable risk analysis",
            desc = "Calls the backend below for every tool request.",
            first = true,
            right = {
                DesignToggle(
                    checked = settings.riskAnalysisEnabled,
                    onCheckedChange = { onSettingsChange(settings.copy(riskAnalysisEnabled = it)) },
                )
            },
        )
        SettingItem(label = "Backend", right = {
            PillSegmented(
                options = listOf(
                    RiskAnalysisBackend.CLAUDE to "Claude",
                    RiskAnalysisBackend.COPILOT to "Copilot",
                    RiskAnalysisBackend.OLLAMA to "Ollama",
                ),
                selected = settings.riskAnalysisBackend,
                onSelect = { onSettingsChange(settings.copy(riskAnalysisBackend = it)) },
            )
        })
        SettingItem(label = "Model", right = {
            ModelPicker(
                settings = settings,
                copilotModels = copilotModels,
                ollamaModels = ollamaModels,
                ollamaReady = ollamaInitState == OllamaInitState.READY,
                onRefreshOllamaModels = onRefreshOllamaModels,
                onSettingsChange = onSettingsChange,
            )
        })
    }

    AnimatedVisibility(
        visible = settings.riskAnalysisBackend == RiskAnalysisBackend.CLAUDE &&
            settings.riskAnalysisEnabled,
    ) {
        NoticeBanner(
            text = "On macOS, the Claude CLI may trigger file-access permission prompts. " +
                "These can safely be denied \u2014 the app does not need file access to perform risk analysis.",
            color = WarnYellow,
        )
    }

    AnimatedVisibility(
        visible = settings.riskAnalysisBackend == RiskAnalysisBackend.COPILOT &&
            settings.riskAnalysisEnabled,
    ) {
        CopilotSection(
            settings = settings,
            copilotInitState = copilotInitState,
            copilotModelsLoaded = copilotModels.isNotEmpty(),
            onSettingsChange = onSettingsChange,
        )
    }

    AnimatedVisibility(
        visible = settings.riskAnalysisBackend == RiskAnalysisBackend.OLLAMA &&
            settings.riskAnalysisEnabled,
    ) {
        OllamaSection(
            settings = settings,
            ollamaInitState = ollamaInitState,
            ollamaLastError = ollamaLastError,
            ollamaLastMetrics = ollamaLastMetrics,
            ollamaVersion = ollamaVersion,
            onRefreshOllamaModels = onRefreshOllamaModels,
            onSettingsChange = onSettingsChange,
        )
    }

    SettingSection(
        title = "Auto-decision bands",
        desc = "Skip the approval prompt for requests that fall outside this range.",
    ) {
        SettingItem(
            label = "Auto-approve up to",
            first = true,
            right = {
                PillSegmented(
                    options = listOf(0 to "Off", 1 to "1", 2 to "2", 3 to "3"),
                    selected = settings.autoApproveLevel,
                    onSelect = { onSettingsChange(settings.copy(autoApproveLevel = it)) },
                )
            },
        )
        SettingItem(
            label = "Auto-deny at or above",
            right = {
                PillSegmented(
                    options = listOf(0 to "Off", 5 to "5", 4 to "4"),
                    selected = settings.autoDenyLevel,
                    onSelect = { onSettingsChange(settings.copy(autoDenyLevel = it)) },
                )
            },
        )
        SettingItem(
            label = "Auto-approve delay",
            desc = "Seconds to leave the request visible before resolving. 0 = immediate.",
            right = {
                SettingsTextInput(
                    value = settings.autoApproveDelaySeconds.toString(),
                    onChange = { raw ->
                        val parsed = raw.filter { it.isDigit() }.toIntOrNull()?.coerceIn(0, 120)
                        if (parsed != null) {
                            onSettingsChange(settings.copy(autoApproveDelaySeconds = parsed))
                        }
                    },
                    width = 80.dp,
                    mono = true,
                )
            },
        )
        SettingItem(
            label = "Auto-deny countdown",
            desc = "Seconds to show the countdown overlay before auto-denying.",
            right = {
                SettingsTextInput(
                    value = settings.autoDenyCountdownSeconds.toString(),
                    onChange = { raw ->
                        val parsed = raw.filter { it.isDigit() }.toIntOrNull()?.coerceIn(1, 120)
                        if (parsed != null) {
                            onSettingsChange(settings.copy(autoDenyCountdownSeconds = parsed))
                        }
                    },
                    width = 80.dp,
                    mono = true,
                )
            },
        )
    }

    SystemPromptSection(settings = settings, onSettingsChange = onSettingsChange)
}

@Composable
private fun NoticeBanner(text: String, color: Color) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .widthIn(max = 780.dp)
            .clip(RoundedCornerShape(7.dp))
            .background(color.copy(alpha = 0.10f))
            .border(1.dp, color.copy(alpha = 0.22f), RoundedCornerShape(7.dp))
            .padding(horizontal = 12.dp, vertical = 10.dp),
    ) {
        Text(text = text, color = color, fontSize = 12.sp, lineHeight = 17.sp)
    }
}

@Composable
private fun ModelPicker(
    settings: AppSettings,
    copilotModels: List<Pair<String, String>>,
    ollamaModels: List<String>,
    ollamaReady: Boolean,
    onRefreshOllamaModels: () -> Unit,
    onSettingsChange: (AppSettings) -> Unit,
) {
    when (settings.riskAnalysisBackend) {
        RiskAnalysisBackend.CLAUDE -> {
            PillSegmented(
                options = listOf("haiku" to "Haiku", "sonnet" to "Sonnet", "opus" to "Opus"),
                selected = settings.riskAnalysisModel,
                onSelect = { onSettingsChange(settings.copy(riskAnalysisModel = it)) },
            )
        }
        RiskAnalysisBackend.COPILOT -> {
            val models = copilotModels.ifEmpty {
                listOf(
                    "gpt-4.1-mini" to "GPT-4.1 Mini",
                    "gpt-4.1" to "GPT-4.1",
                    "claude-sonnet-4.5" to "Sonnet 4.5",
                )
            }
            val label = models.find { it.first == settings.riskAnalysisCopilotModel }?.second
                ?: settings.riskAnalysisCopilotModel
            DropdownField(
                value = label,
                options = models.map { it.first to it.second },
                onSelect = { id -> onSettingsChange(settings.copy(riskAnalysisCopilotModel = id)) },
                width = 220.dp,
            )
        }
        RiskAnalysisBackend.OLLAMA -> {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                if (ollamaReady && ollamaModels.isNotEmpty()) {
                    DropdownField(
                        value = settings.riskAnalysisOllamaModel,
                        options = ollamaModels.map { it to it },
                        onSelect = { onSettingsChange(settings.copy(riskAnalysisOllamaModel = it)) },
                        width = 184.dp,
                    )
                } else {
                    SettingsTextInput(
                        value = settings.riskAnalysisOllamaModel,
                        onChange = { onSettingsChange(settings.copy(riskAnalysisOllamaModel = it)) },
                        width = 184.dp,
                        mono = true,
                    )
                }
                Box(
                    modifier = Modifier
                        .size(30.dp)
                        .clip(RoundedCornerShape(7.dp))
                        .background(AgentBuddyColors.surface)
                        .border(1.dp, AgentBuddyColors.line1, RoundedCornerShape(7.dp))
                        .clickable { onRefreshOllamaModels() },
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = LucideRefreshCw,
                        contentDescription = "Refresh Ollama models",
                        tint = AgentBuddyColors.inkSecondary,
                        modifier = Modifier.size(14.dp),
                    )
                }
            }
        }
    }
}

@Composable
private fun <T> DropdownField(
    value: String,
    options: List<Pair<T, String>>,
    onSelect: (T) -> Unit,
    width: androidx.compose.ui.unit.Dp,
) {
    var open by remember { mutableStateOf(false) }
    Box {
        Row(
            modifier = Modifier
                .width(width)
                .height(34.dp)
                .clip(RoundedCornerShape(7.dp))
                .background(AgentBuddyColors.surface)
                .border(1.dp, AgentBuddyColors.line1, RoundedCornerShape(7.dp))
                .clickable { open = true }
                .padding(horizontal = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(text = value, color = AgentBuddyColors.inkPrimary, fontSize = 13.sp, modifier = Modifier.weight(1f))
            Icon(
                imageVector = LucideChevronDown,
                contentDescription = null,
                tint = AgentBuddyColors.inkMuted,
                modifier = Modifier.size(12.dp),
            )
        }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            options.forEach { (id, label) ->
                DropdownMenuItem(
                    text = { Text(label, fontSize = 12.5.sp) },
                    onClick = {
                        onSelect(id)
                        open = false
                    },
                )
            }
        }
    }
}

@Composable
private fun CopilotSection(
    settings: AppSettings,
    copilotInitState: CopilotInitState,
    copilotModelsLoaded: Boolean,
    onSettingsChange: (AppSettings) -> Unit,
) {
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
                process.inputStream.bufferedReader().readText()
                val exitCode = process.waitFor()
                ghAuthOk = exitCode == 0
                ghAuthStatus = if (exitCode == 0) "Authenticated" else "Not authenticated"
            } catch (_: Exception) {
                ghAuthOk = false
                ghAuthStatus = "gh CLI not found"
            }
        }
    }

    SettingSection(title = "Copilot backend") {
        SettingItem(label = "Copilot CLI", first = true, right = {
            when (copilotInitState) {
                CopilotInitState.LOADING -> CircularProgressIndicator(
                    modifier = Modifier.size(14.dp), strokeWidth = 2.dp,
                )
                CopilotInitState.READY -> StatusPill(
                    status = DecisionStatus.APPROVED, size = TagSize.SMALL, text = "Connected",
                )
                CopilotInitState.ERROR -> StatusPill(
                    status = DecisionStatus.DENIED, size = TagSize.SMALL,
                )
                CopilotInitState.IDLE -> Text(
                    text = "Idle", color = AgentBuddyColors.inkMuted, fontSize = 11.5.sp,
                )
            }
        })
        SettingItem(
            label = "GitHub auth",
            desc = "Requires GitHub CLI (gh) and a Copilot subscription.",
            right = {
                when (ghAuthOk) {
                    true -> StatusPill(
                        status = DecisionStatus.APPROVED, size = TagSize.SMALL, text = "Verified",
                    )
                    false -> StatusPill(
                        status = DecisionStatus.DENIED, size = TagSize.SMALL,
                    )
                    null -> Text(
                        text = ghAuthStatus ?: "\u2026",
                        color = AgentBuddyColors.inkMuted,
                        fontSize = 11.5.sp,
                    )
                }
            },
        )
        if (ghAuthOk != true) {
            SettingItem(
                label = "Login with GitHub",
                desc = "Opens the gh auth login documentation.",
                right = {
                    OutlineButton(text = "Open", onClick = {
                        openBrowserSafely("https://cli.github.com/manual/gh_auth_login")
                    })
                },
            )
        }
        if (copilotInitState != CopilotInitState.READY || !copilotModelsLoaded) {
            SettingItem(
                label = "Copilot CLI path",
                desc = "Leave empty to auto-detect on PATH.",
                right = {
                    SettingsTextInput(
                        value = settings.riskAnalysisCopilotCliPath,
                        onChange = { onSettingsChange(settings.copy(riskAnalysisCopilotCliPath = it)) },
                        width = 220.dp,
                        mono = true,
                    )
                },
            )
        }
    }
}

@Composable
private fun OllamaSection(
    settings: AppSettings,
    ollamaInitState: OllamaInitState,
    ollamaLastError: String?,
    ollamaLastMetrics: OllamaMetrics?,
    ollamaVersion: String?,
    onRefreshOllamaModels: () -> Unit,
    onSettingsChange: (AppSettings) -> Unit,
) {
    // AnimatedVisibility content stacks children Box-style, so multiple
    // top-level emissions would overlap. Wrap in a Column with explicit
    // spacing (matching the parent settings layout).
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {

    AnimatedVisibility(visible = ollamaLastError != null) {
        NoticeBanner(
            text = "Ollama error: ${ollamaLastError.orEmpty()}",
            color = DangerRed,
        )
    }

    SettingSection(title = "Ollama backend") {
        SettingItem(label = "Connection", first = true, right = {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                if (ollamaVersion != null && ollamaInitState == OllamaInitState.READY) {
                    Text(
                        text = "v$ollamaVersion",
                        color = AgentBuddyColors.inkMuted,
                        fontSize = 11.sp,
                    )
                }
                when (ollamaInitState) {
                    OllamaInitState.LOADING -> CircularProgressIndicator(
                        modifier = Modifier.size(14.dp), strokeWidth = 2.dp,
                    )
                    OllamaInitState.READY -> StatusPill(
                        status = DecisionStatus.APPROVED, size = TagSize.SMALL, text = "Connected",
                    )
                    OllamaInitState.ERROR -> StatusPill(
                        status = DecisionStatus.TIMEOUT, size = TagSize.SMALL,
                    )
                    OllamaInitState.IDLE -> Text(
                        text = "Idle", color = AgentBuddyColors.inkMuted, fontSize = 11.5.sp,
                    )
                }
                Box(
                    modifier = Modifier
                        .size(28.dp)
                        .clip(RoundedCornerShape(6.dp))
                        .background(AgentBuddyColors.surface)
                        .border(1.dp, AgentBuddyColors.line1, RoundedCornerShape(6.dp))
                        .clickable { onRefreshOllamaModels() },
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = LucideRefreshCw,
                        contentDescription = "Retry Ollama connection",
                        tint = AgentBuddyColors.inkSecondary,
                        modifier = Modifier.size(13.dp),
                    )
                }
            }
        })
        SettingItem(
            label = "Base URL",
            desc = "Install Ollama from ollama.com, then run `ollama pull llama3.2`.",
            right = {
                SettingsTextInput(
                    value = settings.riskAnalysisOllamaUrl,
                    onChange = { onSettingsChange(settings.copy(riskAnalysisOllamaUrl = it)) },
                    width = 220.dp,
                    mono = true,
                )
            },
        )
        SettingItem(
            label = "Thinking",
            desc = "Send `think: true` for reasoning models. Off keeps latency low.",
            right = {
                DesignToggle(
                    checked = settings.riskAnalysisOllamaThinking,
                    onCheckedChange = { onSettingsChange(settings.copy(riskAnalysisOllamaThinking = it)) },
                )
            },
        )
        SettingItem(
            label = "Keep alive",
            desc = "How long Ollama keeps the model in memory (e.g. 10m, 1h, 0).",
            right = {
                SettingsTextInput(
                    value = settings.riskAnalysisOllamaKeepAlive,
                    onChange = { onSettingsChange(settings.copy(riskAnalysisOllamaKeepAlive = it)) },
                    width = 120.dp,
                    mono = true,
                )
            },
        )
        SettingItem(
            label = "Request timeout",
            desc = "Per-call deadline in seconds. Cold-start CPU eval may need 60+.",
            right = {
                SettingsTextInput(
                    value = settings.riskAnalysisOllamaTimeoutSeconds.toString(),
                    onChange = { raw ->
                        val parsed = raw.filter { it.isDigit() }.toIntOrNull()?.coerceIn(5, 600)
                        if (parsed != null) {
                            onSettingsChange(settings.copy(riskAnalysisOllamaTimeoutSeconds = parsed))
                        }
                    },
                    width = 80.dp,
                    mono = true,
                )
            },
        )
        SettingItem(
            label = "Context size",
            desc = "`num_ctx` override. 0 = use model default.",
            right = {
                SettingsTextInput(
                    value = settings.riskAnalysisOllamaNumCtx.toString(),
                    onChange = { raw ->
                        val parsed = raw.filter { it.isDigit() }.toIntOrNull()?.coerceIn(0, 131072)
                        if (parsed != null) {
                            onSettingsChange(settings.copy(riskAnalysisOllamaNumCtx = parsed))
                        }
                    },
                    width = 80.dp,
                    mono = true,
                )
            },
        )
        SettingItem(
            label = "Download Ollama",
            desc = "Opens ollama.com/download in your browser.",
            right = { OutlineButton(text = "Open", onClick = { openBrowserSafely("https://ollama.com/download") }) },
        )
    }

    AnimatedVisibility(visible = ollamaLastMetrics != null) {
        val m = ollamaLastMetrics
        if (m != null) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .widthIn(max = 780.dp)
                    .clip(RoundedCornerShape(7.dp))
                    .background(AgentBuddyColors.surface)
                    .border(1.dp, AgentBuddyColors.line1, RoundedCornerShape(7.dp))
                    .padding(horizontal = 12.dp, vertical = 10.dp),
            ) {
                Text(
                    text = "Last call: ${m.totalMs}ms total · load ${m.loadMs}ms · " +
                        "prompt ${m.promptTokens}t/${m.promptEvalMs}ms · " +
                        "eval ${m.evalTokens}t/${m.evalMs}ms",
                    color = AgentBuddyColors.inkSecondary,
                    fontSize = 11.5.sp,
                    fontFamily = FontFamily.Monospace,
                )
            }
        }
    }
    } // end Column wrapper
}

@Composable
private fun SystemPromptSection(
    settings: AppSettings,
    onSettingsChange: (AppSettings) -> Unit,
) {
    var show by remember { mutableStateOf(false) }
    val clipboardManager = LocalClipboardManager.current
    val effectivePrompt = settings.riskAnalysisCustomPrompt.ifBlank { RiskMessageBuilder.DEFAULT_SYSTEM_PROMPT }

    SettingSection(
        title = "System prompt",
        desc = "Used by the risk grader. Empty = default.",
    ) {
        SettingItem(
            label = "Custom prompt",
            desc = "Leave empty to fall back to the default system prompt.",
            first = true,
            right = {},
        )
        Column(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
        ) {
            MultilineTextInput(
                value = settings.riskAnalysisCustomPrompt,
                onChange = { onSettingsChange(settings.copy(riskAnalysisCustomPrompt = it)) },
                placeholder = "Uses default prompt if empty\u2026",
            )
            Spacer(Modifier.height(10.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlineButton(
                    text = if (show) "Hide default" else "View default",
                    onClick = { show = !show },
                )
                GhostButton(
                    text = "Copy default",
                    onClick = { clipboardManager.setText(AnnotatedString(effectivePrompt)) },
                )
            }
            AnimatedVisibility(visible = show) {
                Column(modifier = Modifier.padding(top = 10.dp)) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = 240.dp)
                            .clip(RoundedCornerShape(7.dp))
                            .background(AgentBuddyColors.background)
                            .border(1.dp, AgentBuddyColors.line1, RoundedCornerShape(7.dp))
                            .padding(12.dp)
                            .verticalScroll(rememberScrollState()),
                    ) {
                        Text(
                            text = effectivePrompt,
                            color = AgentBuddyColors.inkSecondary,
                            fontSize = 12.sp,
                            lineHeight = 18.sp,
                            fontFamily = FontFamily.Monospace,
                        )
                    }
                }
            }
            Spacer(Modifier.height(8.dp))
        }
    }
}

@Composable
private fun MultilineTextInput(
    value: String,
    onChange: (String) -> Unit,
    placeholder: String,
) {
    val textStyle = LocalTextStyle.current.merge(
        TextStyle(
            color = AgentBuddyColors.inkPrimary,
            fontSize = 12.5.sp,
            fontFamily = FontFamily.Monospace,
            lineHeight = 18.sp,
        ),
    )
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 96.dp)
            .clip(RoundedCornerShape(7.dp))
            .background(AgentBuddyColors.background)
            .border(1.dp, AgentBuddyColors.line1, RoundedCornerShape(7.dp))
            .padding(12.dp),
    ) {
        if (value.isEmpty()) {
            Text(
                text = placeholder,
                color = AgentBuddyColors.inkMuted,
                fontSize = 12.5.sp,
                fontFamily = FontFamily.Monospace,
            )
        }
        BasicTextField(
            value = value,
            onValueChange = onChange,
            textStyle = textStyle,
            cursorBrush = SolidColor(AgentBuddyColors.inkPrimary),
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

