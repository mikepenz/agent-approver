package com.mikepenz.agentapprover.ui.settings

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
    val copilotHookRegistrations by viewModel.copilotHookRegistrations.collectAsState()

    SettingsTab(
        settings = ui.settings,
        isHookRegistered = ui.isHookRegistered,
        isCopilotInstalled = ui.isCopilotInstalled,
        historyCount = ui.historyCount,
        copilotModels = ui.copilotModels,
        copilotInitState = ui.copilotInitState,
        onSettingsChange = viewModel::updateSettings,
        onRegisterHook = viewModel::registerHook,
        onUnregisterHook = viewModel::unregisterHook,
        onInstallCopilot = viewModel::installCopilot,
        onUninstallCopilot = viewModel::uninstallCopilot,
        onRegisterCopilotHook = viewModel::registerCopilotHook,
        onUnregisterCopilotHook = viewModel::unregisterCopilotHook,
        // Pure cache lookup — no side effects from inside composition. The
        // refresh is triggered exactly once per typed path by a
        // LaunchedEffect(projectPath) inside CopilotProjectHookSection via
        // onQueryCopilotHookRegistered below.
        isCopilotHookRegistered = { path -> copilotHookRegistrations[path] ?: false },
        onQueryCopilotHookRegistered = viewModel::queryCopilotHookRegistered,
        onClearHistory = viewModel::clearHistory,
        onShowLicenses = onShowLicenses,
        protectionModules = viewModel.protectionModules,
        onProtectionSettingsChange = viewModel::updateProtectionSettings,
    )
}
