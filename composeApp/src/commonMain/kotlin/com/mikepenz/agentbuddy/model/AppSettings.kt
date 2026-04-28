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
    /** Whether to enable model "thinking" / chain-of-thought. Off by default — pure latency for a structured classifier. */
    val riskAnalysisOllamaThinking: Boolean = false,
    /** Ollama `keep_alive` (e.g. "10m", "1h", "0"). Keeps weights resident between calls. */
    val riskAnalysisOllamaKeepAlive: String = "10m",
    /** Per-request timeout in seconds. Cold-start CPU eval can blow past 30s. */
    val riskAnalysisOllamaTimeoutSeconds: Int = 60,
    /** Optional `num_ctx` override. 0 = use model default. */
    val riskAnalysisOllamaNumCtx: Int = 0,
    val riskAnalysisCustomPrompt: String = "",
    val autoApproveLevel: Int = 0,
    val autoDenyLevel: Int = 0,
    /**
     * Master switch over both [autoApproveLevel] and [autoDenyLevel]. When
     * false, every analyzed request waits for a manual decision regardless
     * of the configured bands. Toggleable from the tray menu so the user
     * can pause auto-decisions for a session without losing their
     * configured levels.
     */
    val autoDecisionsEnabled: Boolean = true,
    /**
     * Minimum visible age (seconds) before an auto-approve fires. 0 = resolve
     * as soon as analysis settles. Bump to e.g. 5 to leave a manual-review
     * window in which the user can deny the request before it disappears.
     */
    val autoApproveDelaySeconds: Int = 0,
    /**
     * Visible countdown (seconds) before an auto-deny fires. The card shows
     * a cancel overlay during this window so the user can intervene.
     */
    val autoDenyCountdownSeconds: Int = 15,
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
