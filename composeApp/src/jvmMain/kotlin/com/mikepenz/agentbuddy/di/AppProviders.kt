package com.mikepenz.agentbuddy.di

import com.mikepenz.agentbuddy.capability.CapabilityEngine
import com.mikepenz.agentbuddy.capability.modules.ResponseCompressionCapability
import com.mikepenz.agentbuddy.capability.modules.SocraticThinkingCapability
import com.mikepenz.agentbuddy.hook.CopilotBridge
import com.mikepenz.agentbuddy.hook.DefaultCopilotBridge
import com.mikepenz.agentbuddy.hook.DefaultHookRegistry
import com.mikepenz.agentbuddy.hook.HookRegistry
import com.mikepenz.agentbuddy.protection.ProtectionEngine
import com.mikepenz.agentbuddy.protection.modules.AbsolutePathsModule
import com.mikepenz.agentbuddy.protection.modules.DestructiveCommandsModule
import com.mikepenz.agentbuddy.protection.modules.GitAwareGuardModule
import com.mikepenz.agentbuddy.protection.modules.InlineScriptsModule
import com.mikepenz.agentbuddy.protection.modules.PipeAbuseModule
import com.mikepenz.agentbuddy.protection.modules.PipedTailHeadModule
import com.mikepenz.agentbuddy.protection.modules.PythonVenvModule
import com.mikepenz.agentbuddy.protection.modules.SecretsScanningModule
import com.mikepenz.agentbuddy.protection.modules.SoftwareInstallModule
import com.mikepenz.agentbuddy.protection.modules.SensitiveFilesModule
import com.mikepenz.agentbuddy.protection.modules.SupplyChainRceModule
import com.mikepenz.agentbuddy.protection.modules.ToolBypassModule
import com.mikepenz.agentbuddy.protection.modules.UncommittedFilesModule
import com.mikepenz.agentbuddy.risk.ClaudeCliRiskAnalyzer
import com.mikepenz.agentbuddy.state.AppStateManager
import com.mikepenz.agentbuddy.storage.ColumnCipher
import com.mikepenz.agentbuddy.storage.DatabaseStorage
import com.mikepenz.agentbuddy.storage.DbKeyManager
import com.mikepenz.agentbuddy.storage.SettingsStorage
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
    ).also {
        it.initialize()
        com.mikepenz.agentbuddy.logging.ErrorReporter.bind(it)
    }

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
