package com.mikepenz.agentbelay.model

import kotlinx.serialization.Serializable

@Serializable
enum class ThemeMode { SYSTEM, DARK, LIGHT }

@Serializable
enum class RiskAnalysisBackend { CLAUDE, COPILOT, OLLAMA, OPENAI_API }

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
    val riskAnalysisOpenaiApiUrl: String = "http://localhost:8080",
    val riskAnalysisOpenaiApiModel: String = "llama3.2",
    val riskAnalysisOpenaiApiTimeoutSeconds: Int = 60,
    val riskAnalysisOpenaiApiNumCtx: Int = 0,
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
    /**
     * Optional OS-level hotkey that approves the oldest pending request from
     * any application. Null = disabled (default).
     */
    val approveOldestHotkey: GlobalHotkey? = null,
    /** Same as [approveOldestHotkey] but denies the oldest pending request. */
    val denyOldestHotkey: GlobalHotkey? = null,
    val awayMode: Boolean = false,
    val newestApprovalFirst: Boolean = false,
    val prominentAlwaysAllow: Boolean = false,
    val windowX: Int? = null,
    val windowY: Int? = null,
    val windowWidth: Int? = null,
    val windowHeight: Int? = null,
    val protectionSettings: ProtectionSettings = ProtectionSettings(),
    val capabilitySettings: CapabilitySettings = CapabilitySettings(),
    val redactionSettings: RedactionSettings = RedactionSettings(),
    val maxHistoryEntries: Int = 2500,
    /**
     * When true, sensitive content (commands, file paths, request/response
     * JSON, AI explanations) is included in log output. Defaults to false so
     * a stock install never writes raw tool input to disk-backed log streams.
     * Toggleable at runtime via the Diagnostics section in Settings.
     */
    val verboseLogging: Boolean = false,
    /**
     * When true, the app silently checks for new releases on startup,
     * subject to a 24h throttle keyed off [lastUpdateCheckEpochMillis].
     * When an update is found, a banner is rendered above the tabs.
     * Disabled platforms (non-installed builds) ignore this flag —
     * `UpdateManager.isSupported` is false there regardless.
     */
    val autoCheckForUpdates: Boolean = true,
    /**
     * Wall-clock epoch millis of the most recent successful update check.
     * Updated by `AutoUpdateChecker` after a check completes (Available,
     * UpToDate, or Failed). Default 0 means "never checked".
     */
    val lastUpdateCheckEpochMillis: Long = 0L,
    /**
     * When true, the updater accepts pre-release GitHub releases (alpha /
     * beta / rc) in addition to stable ones. Default off so users opt in
     * explicitly to less-tested builds. Read at check time, so toggling
     * takes effect on the next `check()` without an app restart.
     */
    val allowPrerelease: Boolean = false,
    /**
     * When true, the Usage tab's background scanner ingests harness session
     * files to compute token / cost / performance metrics. Disabling stops
     * the scanner entirely and skips all on-disk reads of harness files.
     * Default on — the feature is opt-out, not opt-in.
     */
    val usageTrackingEnabled: Boolean = true,
    /**
     * When true, the Insights tab can elevate a heuristic finding into a
     * personalized AI suggestion via the active Risk Analysis backend. Off
     * by default — pre-crafted insights still show, but no LLM calls are
     * made until the user opts in.
     */
    val insightsAiEnabled: Boolean = false,
    /**
     * Optional override for which Risk Analysis backend the Insights AI
     * adapter routes through. `null` = inherit whatever Risk Analysis is
     * configured globally.
     */
    val insightsAiBackend: RiskAnalysisBackend? = null,
    /**
     * Optional system-prompt override for the Insights AI adapter. Blank =
     * use the bundled default prompt.
     */
    val insightsAiCustomPrompt: String = "",
)
