package com.mikepenz.agentbelay.di

import com.mikepenz.agentbelay.capability.CapabilityEngine
import com.mikepenz.agentbelay.capability.modules.ResponseCompressionCapability
import com.mikepenz.agentbelay.capability.modules.SocraticThinkingCapability
import com.mikepenz.agentbelay.hook.CopilotBridge
import com.mikepenz.agentbelay.hook.DefaultCopilotBridge
import com.mikepenz.agentbelay.hook.DefaultHookRegistry
import com.mikepenz.agentbelay.hook.HookRegistry
import com.mikepenz.agentbelay.protection.ProtectionEngine
import com.mikepenz.agentbelay.protection.modules.AbsolutePathsModule
import com.mikepenz.agentbelay.protection.modules.DestructiveCommandsModule
import com.mikepenz.agentbelay.protection.modules.GitAwareGuardModule
import com.mikepenz.agentbelay.protection.modules.InlineScriptsModule
import com.mikepenz.agentbelay.protection.modules.PipeAbuseModule
import com.mikepenz.agentbelay.protection.modules.PipedTailHeadModule
import com.mikepenz.agentbelay.protection.modules.PythonVenvModule
import com.mikepenz.agentbelay.protection.modules.SecretsScanningModule
import com.mikepenz.agentbelay.protection.modules.SoftwareInstallModule
import com.mikepenz.agentbelay.protection.modules.SensitiveFilesModule
import com.mikepenz.agentbelay.protection.modules.SupplyChainRceModule
import com.mikepenz.agentbelay.protection.modules.ToolBypassModule
import com.mikepenz.agentbelay.protection.modules.UncommittedFilesModule
import com.mikepenz.agentbelay.risk.ClaudeCliRiskAnalyzer
import com.mikepenz.agentbelay.state.AppStateManager
import com.mikepenz.agentbelay.storage.ColumnCipher
import com.mikepenz.agentbelay.storage.DatabaseStorage
import com.mikepenz.agentbelay.storage.DbKeyManager
import com.mikepenz.agentbelay.storage.SettingsStorage
import com.mikepenz.agentbelay.update.UpdateManager
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
        com.mikepenz.agentbelay.logging.ErrorReporter.bind(it)
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
    fun provideRedactionEngine(stateManager: AppStateManager): com.mikepenz.agentbelay.redaction.RedactionEngine =
        com.mikepenz.agentbelay.redaction.RedactionEngine(
            modules = com.mikepenz.agentbelay.redaction.builtInRedactionModules,
            settingsProvider = { stateManager.state.value.settings.redactionSettings },
        )

    @Provides
    @SingleIn(AppScope::class)
    fun provideCopilotBridge(): CopilotBridge = DefaultCopilotBridge

    @Provides
    @SingleIn(AppScope::class)
    fun provideHookRegistry(): HookRegistry = DefaultHookRegistry

    @Provides
    @SingleIn(AppScope::class)
    fun provideUpdateManager(env: AppEnvironment): UpdateManager =
        UpdateManager(scope = env.appScope)

    @Provides
    @SingleIn(AppScope::class)
    fun provideAutoUpdateChecker(
        env: AppEnvironment,
        updateManager: UpdateManager,
        stateManager: AppStateManager,
    ): com.mikepenz.agentbelay.update.AutoUpdateChecker =
        com.mikepenz.agentbelay.update.AutoUpdateChecker(
            updateManager = updateManager,
            stateManager = stateManager,
            scope = env.appScope,
        )

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
