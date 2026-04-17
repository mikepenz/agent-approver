package com.mikepenz.agentbuddy.ui.settings

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.ScrollableTabRow
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mikepenz.agentbuddy.capability.CapabilityModule
import com.mikepenz.agentbuddy.model.AppSettings
import com.mikepenz.agentbuddy.model.CapabilitySettings
import com.mikepenz.agentbuddy.model.ProtectionSettings
import com.mikepenz.agentbuddy.protection.ProtectionModule
import com.mikepenz.agentbuddy.risk.CopilotInitState
import com.mikepenz.agentbuddy.risk.OllamaInitState
import com.mikepenz.agentbuddy.ui.theme.AgentBuddyTheme

@Composable
fun SettingsTab(
    settings: AppSettings,
    isHookRegistered: Boolean,
    isCopilotRegistered: Boolean = false,
    historyCount: Int,
    copilotModels: List<Pair<String, String>> = emptyList(),
    copilotInitState: CopilotInitState = CopilotInitState.IDLE,
    ollamaModels: List<String> = emptyList(),
    ollamaInitState: OllamaInitState = OllamaInitState.IDLE,
    onSettingsChange: (AppSettings) -> Unit,
    onRegisterHook: () -> Unit,
    onUnregisterHook: () -> Unit,
    onRegisterCopilot: () -> Unit = {},
    onUnregisterCopilot: () -> Unit = {},
    onClearHistory: () -> Unit,
    onShowLicenses: () -> Unit = {},
    protectionModules: List<ProtectionModule> = emptyList(),
    onProtectionSettingsChange: (ProtectionSettings) -> Unit = {},
    capabilityModules: List<CapabilityModule> = emptyList(),
    onCapabilitySettingsChange: (CapabilitySettings) -> Unit = {},
) {
    var selectedTab by remember { mutableIntStateOf(0) }
    val tabTitles = listOf("General", "Integrations", "Risk Analysis", "Protections", "Capabilities")

    Column(modifier = Modifier.fillMaxSize()) {
        ScrollableTabRow(
            selectedTabIndex = selectedTab,
            modifier = Modifier.fillMaxWidth(),
            edgePadding = 0.dp,
        ) {
            tabTitles.forEachIndexed { index, title ->
                Tab(
                    selected = selectedTab == index,
                    onClick = { selectedTab = index },
                    text = { Text(title, fontSize = 11.sp, maxLines = 1) },
                )
            }
        }

        when (selectedTab) {
            0 -> GeneralSettingsContent(settings, historyCount, onSettingsChange, onClearHistory, onShowLicenses)
            1 -> IntegrationsSettingsContent(
                settings = settings,
                isHookRegistered = isHookRegistered,
                isCopilotRegistered = isCopilotRegistered,
                onSettingsChange = onSettingsChange,
                onRegisterHook = onRegisterHook,
                onUnregisterHook = onUnregisterHook,
                onRegisterCopilot = onRegisterCopilot,
                onUnregisterCopilot = onUnregisterCopilot,
            )
            2 -> RiskAnalysisSettingsContent(settings, copilotModels, copilotInitState, ollamaModels, ollamaInitState, onSettingsChange)
            3 -> ProtectionsSettingsContent(protectionModules, settings.protectionSettings, onProtectionSettingsChange)
            4 -> CapabilitiesSettingsContent(capabilityModules, settings.capabilitySettings, onCapabilitySettingsChange)
        }
    }
}

// -- Previews --

@Preview
@Composable
private fun PreviewRegistered() {
    AgentBuddyTheme {
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
    AgentBuddyTheme {
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
