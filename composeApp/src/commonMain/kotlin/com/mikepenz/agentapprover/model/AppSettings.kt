package com.mikepenz.agentapprover.model

import kotlinx.serialization.Serializable

@Serializable
enum class ThemeMode { SYSTEM, DARK, LIGHT }

@Serializable
enum class RiskAnalysisBackend { CLAUDE, COPILOT, OLLAMA }

@Serializable
data class AppSettings(
    val themeMode: ThemeMode = ThemeMode.SYSTEM,
    val serverPort: Int = 19532,
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
    val autoApproveRisk1: Boolean = false,
    val autoDenyRisk5: Boolean = false,
    val awayMode: Boolean = false,
    val newestApprovalFirst: Boolean = false,
    val windowX: Int? = null,
    val windowY: Int? = null,
    val windowWidth: Int? = null,
    val windowHeight: Int? = null,
    val protectionSettings: ProtectionSettings = ProtectionSettings(),
    val maxHistoryEntries: Int = 2500,
)
