package com.mikepenz.agentbelay.ui.settings

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import dev.zacsweers.metrox.viewmodel.metroViewModel

/**
 * Resolves the [SettingsViewModel] from Metro and fans its [SettingsUiState]
 * out to the (still parameter-heavy) [SettingsTab] composable.
 *
 * Keeping `SettingsTab`'s signature unchanged means the four sub-tab content
 * composables and their previews stay stateless and trivially renderable
 * without a graph.
 */
@Composable
fun SettingsTabHost(onShowLicenses: () -> Unit) {
    val viewModel: SettingsViewModel = metroViewModel()
    val ui by viewModel.uiState.collectAsState()
    val updateState by viewModel.updateState.collectAsState()

    SettingsTab(
        settings = ui.settings,
        isHookRegistered = ui.isHookRegistered,
        isCopilotRegistered = ui.isCopilotRegistered,
        historyCount = ui.historyCount,
        copilotModels = ui.copilotModels,
        copilotInitState = ui.copilotInitState,
        ollamaModels = ui.ollamaModels,
        ollamaInitState = ui.ollamaInitState,
        ollamaLastError = ui.ollamaLastError,
        ollamaLastMetrics = ui.ollamaLastMetrics,
        ollamaVersion = ui.ollamaVersion,
        approveHotkeyError = ui.approveHotkeyError,
        denyHotkeyError = ui.denyHotkeyError,
        onRefreshOllamaModels = viewModel::refreshOllamaModels,
        onSettingsChange = viewModel::updateSettings,
        onRegisterHook = viewModel::registerHook,
        onUnregisterHook = viewModel::unregisterHook,
        onRegisterCopilot = viewModel::registerCopilot,
        onUnregisterCopilot = viewModel::unregisterCopilot,
        onClearHistory = viewModel::clearHistory,
        onShowLicenses = onShowLicenses,
        protectionModules = viewModel.protectionModules,
        onProtectionSettingsChange = viewModel::updateProtectionSettings,
        capabilityModules = viewModel.capabilityModules,
        onCapabilitySettingsChange = viewModel::updateCapabilitySettings,
        onRedactionSettingsChange = viewModel::updateRedactionSettings,
        updateState = updateState,
        isUpdateSupported = viewModel.isUpdateSupported,
        onCheckForUpdates = viewModel::checkForUpdates,
        onDownloadUpdate = viewModel::downloadUpdate,
        onInstallUpdate = viewModel::installUpdate,
        onResetUpdateState = viewModel::resetUpdateState,
    )
}
