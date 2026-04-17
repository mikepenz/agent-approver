package com.mikepenz.agentbuddy.model

import kotlinx.serialization.Serializable

@Serializable
enum class ThemeMode { SYSTEM, DARK, LIGHT }

@Serializable
enum class RiskAnalysisBackend { CLAUDE, COPILOT, OLLAMA }

@Serializable
data class AppSettings(
    val themeMode: ThemeMode = ThemeMode.SYSTEM,
    val serverPort: Int = 19532,
    val serverHost: String = "127.0.0.1",
    val copilotFailClosed: Boolean = false,
    val alwaysOnTop: Boolean = true,
    val defaultTimeoutSeconds: Int = 240,
    val startOnBoot: Boolean = false,
    val riskAnalysisEnabled: Boolean = true,
    val riskAnalysisBackend: RiskAnalysisBackend = RiskAnalysisBackend.CLAUDE,
    val riskAnalysisModel: String = "haiku",
    val riskAnalysisCopilotModel: String = "gpt-4.1-mini",
    val riskAnalysisCopilotCliPath: String = "",
    val riskAnalysisOllamaUrl: String = "http://localhost:11434",
    val riskAnalysisOllamaModel: String = "llama3.2",
    val riskAnalysisCustomPrompt: String = "",
    val autoApproveLevel: Int = 0,
    val autoDenyLevel: Int = 0,
    val awayMode: Boolean = false,
    val newestApprovalFirst: Boolean = false,
    val prominentAlwaysAllow: Boolean = false,
    val windowX: Int? = null,
    val windowY: Int? = null,
    val windowWidth: Int? = null,
    val windowHeight: Int? = null,
    val protectionSettings: ProtectionSettings = ProtectionSettings(),
    val capabilitySettings: CapabilitySettings = CapabilitySettings(),
    val maxHistoryEntries: Int = 2500,
    /**
     * When true, sensitive content (commands, file paths, request/response
     * JSON, AI explanations) is included in log output. Defaults to false so
     * a stock install never writes raw tool input to disk-backed log streams.
     * Toggleable at runtime via the Diagnostics section in Settings.
     */
    val verboseLogging: Boolean = false,
)
