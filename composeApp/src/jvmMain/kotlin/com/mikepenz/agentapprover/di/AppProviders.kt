package com.mikepenz.agentapprover.di

import com.mikepenz.agentapprover.capability.CapabilityEngine
import com.mikepenz.agentapprover.capability.modules.ResponseCompressionCapability
import com.mikepenz.agentapprover.capability.modules.SocraticThinkingCapability
import com.mikepenz.agentapprover.hook.CopilotBridge
import com.mikepenz.agentapprover.hook.DefaultCopilotBridge
import com.mikepenz.agentapprover.hook.DefaultHookRegistry
import com.mikepenz.agentapprover.hook.HookRegistry
import com.mikepenz.agentapprover.protection.ProtectionEngine
import com.mikepenz.agentapprover.protection.modules.AbsolutePathsModule
import com.mikepenz.agentapprover.protection.modules.DestructiveCommandsModule
import com.mikepenz.agentapprover.protection.modules.GitAwareGuardModule
import com.mikepenz.agentapprover.protection.modules.InlineScriptsModule
import com.mikepenz.agentapprover.protection.modules.PipeAbuseModule
import com.mikepenz.agentapprover.protection.modules.PipedTailHeadModule
import com.mikepenz.agentapprover.protection.modules.PythonVenvModule
import com.mikepenz.agentapprover.protection.modules.SecretsScanningModule
import com.mikepenz.agentapprover.protection.modules.SoftwareInstallModule
import com.mikepenz.agentapprover.protection.modules.SensitiveFilesModule
import com.mikepenz.agentapprover.protection.modules.SupplyChainRceModule
import com.mikepenz.agentapprover.protection.modules.ToolBypassModule
import com.mikepenz.agentapprover.protection.modules.UncommittedFilesModule
import com.mikepenz.agentapprover.risk.ClaudeCliRiskAnalyzer
import com.mikepenz.agentapprover.state.AppStateManager
import com.mikepenz.agentapprover.storage.ColumnCipher
import com.mikepenz.agentapprover.storage.DatabaseStorage
import com.mikepenz.agentapprover.storage.DbKeyManager
import com.mikepenz.agentapprover.storage.SettingsStorage
import dev.zacsweers.metro.ContributesTo
import dev.zacsweers.metro.Provides
import dev.zacsweers.metro.SingleIn

/**
 * Bindings for the app-scoped object graph.
 *
 * Uses `@Provides` (rather than constructor `@Inject`) so we don't have to
 * touch the source files of the existing managers in this phase. Each binding
 * is `@SingleIn(AppScope::class)` to match the previous "constructed once in
 * `main()`" lifetime.
 */
@ContributesTo(AppScope::class)
interface AppProviders {

    @Provides
    @SingleIn(AppScope::class)
    fun provideSettingsStorage(env: AppEnvironment): SettingsStorage =
        SettingsStorage(env.dataDir)

    @Provides
    @SingleIn(AppScope::class)
    fun provideDatabaseStorage(
        env: AppEnvironment,
        settingsStorage: SettingsStorage,
    ): DatabaseStorage {
        val maxEntries = settingsStorage.load().maxHistoryEntries
        val cipher = ColumnCipher(DbKeyManager.loadOrCreate(env.dataDir))
        return DatabaseStorage(env.dataDir, maxEntries = maxEntries, cipher = cipher)
    }

    @Provides
    @SingleIn(AppScope::class)
    fun provideAppStateManager(
        databaseStorage: DatabaseStorage,
        settingsStorage: SettingsStorage,
        env: AppEnvironment,
    ): AppStateManager = AppStateManager(
        databaseStorage = databaseStorage,
        settingsStorage = settingsStorage,
        devMode = env.devMode,
    ).also { it.initialize() }

    @Provides
    @SingleIn(AppScope::class)
    fun provideProtectionEngine(stateManager: AppStateManager): ProtectionEngine =
        ProtectionEngine(
            modules = listOf(
                DestructiveCommandsModule,
                SensitiveFilesModule,
                SecretsScanningModule,
                SupplyChainRceModule,
                ToolBypassModule,
                InlineScriptsModule,
                PipeAbuseModule,
                UncommittedFilesModule,
                GitAwareGuardModule,
                PythonVenvModule,
                AbsolutePathsModule,
                PipedTailHeadModule,
                SoftwareInstallModule,
            ),
            settingsProvider = { stateManager.state.value.settings.protectionSettings },
        )

    @Provides
    @SingleIn(AppScope::class)
    fun provideCapabilityEngine(stateManager: AppStateManager): CapabilityEngine =
        CapabilityEngine(
            modules = listOf(ResponseCompressionCapability, SocraticThinkingCapability),
            settingsProvider = { stateManager.state.value.settings.capabilitySettings },
        )

    @Provides
    @SingleIn(AppScope::class)
    fun provideCopilotBridge(): CopilotBridge = DefaultCopilotBridge

    @Provides
    @SingleIn(AppScope::class)
    fun provideHookRegistry(): HookRegistry = DefaultHookRegistry

    @Provides
    @SingleIn(AppScope::class)
    fun provideClaudeAnalyzer(stateManager: AppStateManager): ClaudeCliRiskAnalyzer {
        val settings = stateManager.state.value.settings
        return ClaudeCliRiskAnalyzer(
            model = settings.riskAnalysisModel,
            customSystemPrompt = settings.riskAnalysisCustomPrompt,
        )
    }
}
