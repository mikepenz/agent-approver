package com.mikepenz.agentbelay.ui.settings

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mikepenz.agentbelay.capability.CapabilityModule
import com.mikepenz.agentbelay.model.AppSettings
import com.mikepenz.agentbelay.model.CapabilitySettings
import com.mikepenz.agentbelay.model.ProtectionSettings
import com.mikepenz.agentbelay.model.RedactionSettings
import com.mikepenz.agentbelay.protection.ProtectionModule
import com.mikepenz.agentbelay.redaction.RedactionModule
import com.mikepenz.agentbelay.redaction.builtInRedactionModules
import com.mikepenz.agentbelay.risk.CopilotInitState
import com.mikepenz.agentbelay.risk.OllamaInitState
import com.mikepenz.agentbelay.risk.OllamaMetrics
import com.mikepenz.agentbelay.risk.OpenaiApiInitState
import com.mikepenz.agentbelay.risk.OpenaiApiMetrics
import com.mikepenz.agentbelay.ui.components.LocalPreviewHoverOverride
import com.mikepenz.agentbelay.ui.components.SectionLabel
import com.mikepenz.agentbelay.ui.icons.LucideBrain
import com.mikepenz.agentbelay.ui.icons.LucideEyeOff
import com.mikepenz.agentbelay.ui.icons.LucidePlug
import com.mikepenz.agentbelay.ui.icons.LucideShield
import com.mikepenz.agentbelay.ui.icons.LucideSliders
import com.mikepenz.agentbelay.ui.icons.LucideZap
import com.mikepenz.agentbelay.ui.theme.AccentEmerald
import com.mikepenz.agentbelay.ui.theme.AgentBelayColors
import com.mikepenz.agentbelay.ui.theme.PreviewScaffold
import com.mikepenz.agentbelay.update.UpdateUiState

private enum class SettingsSubTab(val label: String, val icon: ImageVector) {
    General("General", LucideSliders),
    Integrations("Integrations", LucidePlug),
    Risk("Risk Analysis", LucideBrain),
    Protections("Protections", LucideShield),
    Redaction("Redaction", LucideEyeOff),
    Capabilities("Capabilities", LucideZap),
}

@Composable
fun SettingsTab(
    selectedSubTabIndex: Int = 0,
    onSubTabSelect: (Int) -> Unit = {},
    settings: AppSettings,
    isHookRegistered: Boolean,
    isCopilotRegistered: Boolean = false,
    isOpenCodeRegistered: Boolean = false,
    isPiRegistered: Boolean = false,
    isCodexRegistered: Boolean = false,
    historyCount: Int,
    copilotModels: List<Pair<String, String>> = emptyList(),
    copilotInitState: CopilotInitState = CopilotInitState.IDLE,
    ollamaModels: List<String> = emptyList(),
    ollamaInitState: OllamaInitState = OllamaInitState.IDLE,
    ollamaLastError: String? = null,
    ollamaLastMetrics: OllamaMetrics? = null,
    ollamaVersion: String? = null,
    openaiApiModels: List<String> = emptyList(),
    openaiApiInitState: OpenaiApiInitState = OpenaiApiInitState.IDLE,
    openaiApiLastError: String? = null,
    openaiApiLastMetrics: OpenaiApiMetrics? = null,
    onRefreshOpenaiApiModels: () -> Unit = {},
    approveHotkeyError: String? = null,
    denyHotkeyError: String? = null,
    onRefreshOllamaModels: () -> Unit = {},
    onSettingsChange: (AppSettings) -> Unit,
    onRegisterHook: () -> Unit,
    onUnregisterHook: () -> Unit,
    onRegisterCopilot: () -> Unit = {},
    onUnregisterCopilot: () -> Unit = {},
    onRegisterOpenCode: () -> Unit = {},
    onUnregisterOpenCode: () -> Unit = {},
    onRegisterPi: () -> Unit = {},
    onUnregisterPi: () -> Unit = {},
    onRegisterCodex: () -> Unit = {},
    onUnregisterCodex: () -> Unit = {},
    onClearHistory: () -> Unit,
    onShowLicenses: () -> Unit = {},
    protectionModules: List<ProtectionModule> = emptyList(),
    onProtectionSettingsChange: (ProtectionSettings) -> Unit = {},
    capabilityModules: List<CapabilityModule> = emptyList(),
    onCapabilitySettingsChange: (CapabilitySettings) -> Unit = {},
    redactionModules: List<RedactionModule> = builtInRedactionModules,
    onRedactionSettingsChange: (RedactionSettings) -> Unit = {},
    updateState: UpdateUiState = UpdateUiState.Idle,
    isUpdateSupported: Boolean = false,
    onCheckForUpdates: () -> Unit = {},
    onDownloadUpdate: () -> Unit = {},
    onInstallUpdate: () -> Unit = {},
    onResetUpdateState: () -> Unit = {},
) {
    val tab = SettingsSubTab.entries.getOrElse(selectedSubTabIndex) { SettingsSubTab.General }

    BoxWithConstraints(modifier = Modifier.fillMaxSize().background(AgentBelayColors.background)) {
        // Content pane needs ~460dp to render cards comfortably; below this
        // the settings sidebar collapses to icons so the content keeps room.
        val compactSidebar = maxWidth < 680.dp
        val horizontalPad by animateDpAsState(
            targetValue = if (maxWidth < 600.dp) 16.dp else 36.dp,
            animationSpec = tween(220),
            label = "settings-hpad",
        )

        Row(modifier = Modifier.fillMaxSize()) {
            SettingsSidebar(
                selected = tab,
                onSelect = { onSubTabSelect(SettingsSubTab.entries.indexOf(it)) },
                compact = compactSidebar,
            )
            // Use BoxWithConstraints so we know the actual viewport width,
            // then give the Column an explicit width = max(viewport, minWidth).
            // Without this, fillMaxWidth() inside SettingSection expands to
            // its 780dp cap in the unconstrained horizontalScroll context,
            // making the pane always 780dp wide regardless of widthIn(min).
            val hScroll = rememberScrollState()
            BoxWithConstraints(
                modifier = Modifier.weight(1f).fillMaxHeight(),
            ) {
            val contentWidth = maxOf(maxWidth, 340.dp)
            Box(
                modifier = Modifier
                    .fillMaxHeight()
                    .horizontalScroll(hScroll),
            ) {
            Column(
                modifier = Modifier
                    .width(contentWidth)
                    .fillMaxHeight()
                    .verticalScroll(rememberScrollState())
                    .padding(start = horizontalPad, end = horizontalPad, top = 28.dp, bottom = 36.dp),
                verticalArrangement = Arrangement.spacedBy(28.dp),
            ) {
            when (tab) {
                SettingsSubTab.General -> GeneralSettingsContent(
                    settings = settings,
                    historyCount = historyCount,
                    approveHotkeyError = approveHotkeyError,
                    denyHotkeyError = denyHotkeyError,
                    onSettingsChange = onSettingsChange,
                    onClearHistory = onClearHistory,
                    onShowLicenses = onShowLicenses,
                    updateState = updateState,
                    isUpdateSupported = isUpdateSupported,
                    onCheckForUpdates = onCheckForUpdates,
                    onDownloadUpdate = onDownloadUpdate,
                    onInstallUpdate = onInstallUpdate,
                    onResetUpdateState = onResetUpdateState,
                )
                SettingsSubTab.Integrations -> IntegrationsSettingsContent(
                    settings = settings,
                    isHookRegistered = isHookRegistered,
                    isCopilotRegistered = isCopilotRegistered,
                    isOpenCodeRegistered = isOpenCodeRegistered,
                    isPiRegistered = isPiRegistered,
                    isCodexRegistered = isCodexRegistered,
                    onSettingsChange = onSettingsChange,
                    onRegisterHook = onRegisterHook,
                    onUnregisterHook = onUnregisterHook,
                    onRegisterCopilot = onRegisterCopilot,
                    onUnregisterCopilot = onUnregisterCopilot,
                    onRegisterOpenCode = onRegisterOpenCode,
                    onUnregisterOpenCode = onUnregisterOpenCode,
                    onRegisterPi = onRegisterPi,
                    onUnregisterPi = onUnregisterPi,
                    onRegisterCodex = onRegisterCodex,
                    onUnregisterCodex = onUnregisterCodex,
                )
                SettingsSubTab.Risk -> RiskAnalysisSettingsContent(
                    settings = settings,
                    copilotModels = copilotModels,
                    copilotInitState = copilotInitState,
                    ollamaModels = ollamaModels,
                    ollamaInitState = ollamaInitState,
                    ollamaLastError = ollamaLastError,
                    ollamaLastMetrics = ollamaLastMetrics,
                    ollamaVersion = ollamaVersion,
                    onRefreshOllamaModels = onRefreshOllamaModels,
                    openaiApiModels = openaiApiModels,
                    openaiApiInitState = openaiApiInitState,
                    openaiApiLastError = openaiApiLastError,
                    openaiApiLastMetrics = openaiApiLastMetrics,
                    onRefreshOpenaiApiModels = onRefreshOpenaiApiModels,
                    onSettingsChange = onSettingsChange,
                )
                SettingsSubTab.Protections -> ProtectionsSettingsContent(
                    modules = protectionModules,
                    settings = settings.protectionSettings,
                    onSettingsChange = onProtectionSettingsChange,
                )
                SettingsSubTab.Redaction -> RedactionSettingsContent(
                    modules = redactionModules,
                    settings = settings.redactionSettings,
                    onSettingsChange = onRedactionSettingsChange,
                )
                SettingsSubTab.Capabilities -> CapabilitiesSettingsContent(
                    modules = capabilityModules,
                    settings = settings.capabilitySettings,
                    onSettingsChange = onCapabilitySettingsChange,
                )
            }
            }
            } // end horizontalScroll Box
            } // end BoxWithConstraints
        }
    }
}

@Composable
private fun SettingsSidebar(
    selected: SettingsSubTab,
    onSelect: (SettingsSubTab) -> Unit,
    compact: Boolean = false,
) {
    val sidebarWidth by animateDpAsState(
        targetValue = if (compact) 56.dp else 210.dp,
        animationSpec = tween(220),
        label = "settings-sidebar-width",
    )
    val sidebarHPad by animateDpAsState(
        targetValue = if (compact) 8.dp else 10.dp,
        animationSpec = tween(220),
        label = "settings-sidebar-hpad",
    )

    Row(
        modifier = Modifier
            .width(sidebarWidth)
            .fillMaxHeight()
            .background(AgentBelayColors.background),
    ) {
        Column(
            modifier = Modifier
                .weight(1f)
                .fillMaxHeight()
                .padding(vertical = 20.dp, horizontal = sidebarHPad),
            verticalArrangement = Arrangement.spacedBy(1.dp),
        ) {
            if (!compact) {
                SectionLabel(
                    text = "Settings",
                    modifier = Modifier.padding(start = 10.dp, end = 10.dp, bottom = 12.dp),
                )
            }
            SettingsSubTab.entries.forEach { tab ->
                SidebarItem(
                    tab = tab,
                    active = selected == tab,
                    compact = compact,
                    onClick = { onSelect(tab) },
                )
            }
        }
        Box(
            modifier = Modifier
                .width(1.dp)
                .fillMaxHeight()
                .background(AgentBelayColors.line1),
        )
    }
}

@Composable
private fun SidebarItem(
    tab: SettingsSubTab,
    active: Boolean,
    compact: Boolean = false,
    onClick: () -> Unit,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val liveHovered by interactionSource.collectIsHoveredAsState()
    val hovered = LocalPreviewHoverOverride.current ?: liveHovered
    val bg = when {
        active -> AgentBelayColors.surface3
        hovered -> AgentBelayColors.surface2
        else -> Color.Transparent
    }
    val fg = if (active) AgentBelayColors.inkPrimary else AgentBelayColors.inkSecondary
    val iconColor = if (active) AccentEmerald else AgentBelayColors.inkTertiary

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(32.dp)
            .clip(RoundedCornerShape(6.dp))
            .background(bg)
            .hoverable(interactionSource)
            .clickable(interactionSource = interactionSource, indication = null) { onClick() }
            .padding(horizontal = if (compact) 0.dp else 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = if (compact) Arrangement.Center else Arrangement.spacedBy(10.dp),
    ) {
        Icon(
            imageVector = tab.icon,
            contentDescription = tab.label,
            tint = iconColor,
            modifier = Modifier.size(14.dp),
        )
        if (!compact) {
            Text(
                text = tab.label,
                color = fg,
                fontSize = 13.sp,
                fontWeight = if (active) FontWeight.Medium else FontWeight.Normal,
                letterSpacing = (-0.05).sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

// -- Previews --

@Preview
@Composable
private fun PreviewRegistered() {
    PreviewScaffold {
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
    PreviewScaffold {
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
