package com.mikepenz.agentapprover.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Badge
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.mutableStateSetOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mikepenz.agentapprover.hook.CopilotBridgeInstaller
import com.mikepenz.agentapprover.hook.HookRegistrar
import com.mikepenz.agentapprover.model.Decision
import com.mikepenz.agentapprover.model.RiskAnalysis
import com.mikepenz.agentapprover.model.ToolType
import com.mikepenz.agentapprover.protection.ProtectionEngine
import com.mikepenz.agentapprover.protection.ProtectionModule
import com.mikepenz.agentapprover.risk.CopilotInitState
import com.mikepenz.agentapprover.risk.RiskAnalyzer
import com.mikepenz.agentapprover.state.AppStateManager
import com.mikepenz.agentapprover.ui.approvals.ApprovalsTab
import com.mikepenz.agentapprover.ui.approvals.RiskStatus
import com.mikepenz.agentapprover.ui.history.HistoryTab
import com.mikepenz.agentapprover.ui.protectionlog.ProtectionLogTab
import com.mikepenz.agentapprover.ui.settings.SettingsTab
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.time.ComparableTimeMark
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds
import kotlin.time.TimeSource

@Composable
fun App(
    stateManager: AppStateManager,
    hookRegistrar: HookRegistrar,
    riskAnalyzer: RiskAnalyzer,
    copilotModels: List<Pair<String, String>> = emptyList(),
    copilotInitState: CopilotInitState = CopilotInitState.IDLE,
    devMode: Boolean = false,
    onPopOut: ((title: String, content: String) -> Unit)? = null,
    onShowLicenses: () -> Unit = {},
    protectionModules: List<ProtectionModule> = emptyList(),
    protectionEngine: ProtectionEngine? = null,
) {
    val state by stateManager.state.collectAsState()
    var selectedTab by remember { mutableIntStateOf(0) }
    val coroutineScope = rememberCoroutineScope()
    var isCopilotInstalled by remember { mutableStateOf(CopilotBridgeInstaller.isInstalled()) }

    val riskStatuses = remember { mutableStateMapOf<String, RiskStatus>() }
    val riskErrors = remember { mutableStateMapOf<String, String>() }
    val autoDenyRequests = remember { mutableStateSetOf<String>() }
    // Last user-interaction time mark per request — used to keep auto-actions paused
    // while the user is interacting and for a quiet period afterward. Uses a monotonic
    // time source so wall-clock changes (NTP, manual edits) don't skew the timing.
    val userInteractionTimestamps = remember { mutableStateMapOf<String, ComparableTimeMark>() }

    // Track pending approval IDs to detect new arrivals
    val knownIds = remember { mutableStateSetOf<String>() }

    LaunchedEffect(state.pendingApprovals) {
        for (approval in state.pendingApprovals) {
            if (approval.id !in knownIds) {
                knownIds.add(approval.id)
                if (state.settings.riskAnalysisEnabled) {
                    riskStatuses[approval.id] = RiskStatus.ANALYZING
                    coroutineScope.launch {
                        val result = riskAnalyzer.analyze(approval.hookInput)
                        result.onSuccess { analysis ->
                            riskStatuses[approval.id] = RiskStatus.COMPLETED
                            stateManager.updateRiskResult(approval.id, analysis)

                            // Auto-actions based on risk level (never for Plan or AskUserQuestion)
                            val skipAutoActions = approval.toolType == ToolType.PLAN || approval.toolType == ToolType.ASK_USER_QUESTION
                            if (!skipAutoActions && analysis.risk == 1 && state.settings.autoApproveRisk1) {
                                delay(500)
                                waitUntilUserQuiet(approval.id, userInteractionTimestamps)
                                if (stateManager.state.value.pendingApprovals.any { it.id == approval.id }) {
                                    stateManager.resolve(
                                        requestId = approval.id,
                                        decision = Decision.AUTO_APPROVED,
                                        feedback = "Auto-approved: risk level 1",
                                        riskAnalysis = analysis,
                                        rawResponseJson = null,
                                    )
                                }
                            } else if (!skipAutoActions && !state.settings.awayMode && analysis.risk == 5 && state.settings.autoDenyRisk5) {
                                runAutoDenyWithRetry(
                                    approvalId = approval.id,
                                    analysis = analysis,
                                    autoDenyRequests = autoDenyRequests,
                                    userInteractionTimestamps = userInteractionTimestamps,
                                    stateManager = stateManager,
                                )
                            }
                        }.onFailure { error ->
                            riskStatuses[approval.id] = RiskStatus.ERROR
                            riskErrors[approval.id] = when {
                                error.message?.contains("CLI not found") == true -> "CLI not found"
                                error.message?.contains("timed out") == true -> "Timeout"
                                else -> "Error"
                            }
                        }
                    }
                }
            }
        }
        // Clean up IDs that are no longer pending
        val currentIds = state.pendingApprovals.map { it.id }.toSet()
        knownIds.removeAll { it !in currentIds }
        userInteractionTimestamps.keys.removeAll { it !in currentIds }
        autoDenyRequests.removeAll { it !in currentIds }
    }

    Column(modifier = Modifier.fillMaxSize()) {
        if (state.settings.awayMode) {
            Surface(color = MaterialTheme.colorScheme.tertiaryContainer, modifier = Modifier.fillMaxWidth()) {
                Text(
                    "Away Mode — Timeouts disabled",
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onTertiaryContainer,
                )
            }
        }

        val tabLabels = buildList {
            add("Approvals")
            add("History")
            if (devMode) add("Protection Log")
            add("Settings")
        }
        val protectionLogIndex = if (devMode) 2 else -1
        val settingsIndex = if (devMode) 3 else 2

        TabRow(selectedTabIndex = selectedTab) {
            tabLabels.forEachIndexed { index, label ->
                Tab(
                    selected = selectedTab == index,
                    onClick = { selectedTab = index },
                    text = {
                        when (label) {
                            "Approvals" -> {
                                if (state.pendingApprovals.isNotEmpty()) {
                                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                        Text("Approvals")
                                        Badge { Text("${state.pendingApprovals.size}") }
                                    }
                                } else {
                                    Text("Approvals")
                                }
                            }
                            "Protection Log" -> {
                                if (state.preToolUseLog.isNotEmpty()) {
                                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                        Text("Protection Log", fontSize = 12.sp)
                                        Badge { Text("${state.preToolUseLog.size}") }
                                    }
                                } else {
                                    Text("Protection Log", fontSize = 12.sp)
                                }
                            }
                            else -> Text(label)
                        }
                    },
                )
            }
        }

        when (selectedTab) {
            0 -> ApprovalsTab(
                pendingApprovals = state.pendingApprovals,
                riskResults = state.riskResults,
                riskStatuses = riskStatuses,
                riskErrors = riskErrors,
                settings = state.settings,
                onApprove = { requestId, feedback ->
                    stateManager.resolve(
                        requestId = requestId,
                        decision = Decision.APPROVED,
                        feedback = feedback,
                        riskAnalysis = null,
                        rawResponseJson = null,
                    )
                },
                onAlwaysAllow = { requestId ->
                    stateManager.resolve(
                        requestId = requestId,
                        decision = Decision.ALWAYS_ALLOWED,
                        feedback = "Always allowed",
                        riskAnalysis = null,
                        rawResponseJson = null,
                    )
                },
                onDeny = { requestId, feedback ->
                    stateManager.resolve(
                        requestId = requestId,
                        decision = Decision.DENIED,
                        feedback = feedback,
                        riskAnalysis = null,
                        rawResponseJson = null,
                    )
                },
                onApproveWithInput = { requestId, updatedInput ->
                    stateManager.resolve(
                        requestId = requestId,
                        decision = Decision.APPROVED,
                        feedback = "User answered question",
                        riskAnalysis = null,
                        rawResponseJson = null,
                        updatedInput = updatedInput,
                    )
                },
                onDismiss = { requestId ->
                    stateManager.resolve(
                        requestId = requestId,
                        decision = Decision.DENIED,
                        feedback = "Dismissed",
                        riskAnalysis = null,
                        rawResponseJson = null,
                    )
                },
                autoDenyRequests = autoDenyRequests,
                onCancelAutoDeny = { requestId -> autoDenyRequests.remove(requestId) },
                onUserInteraction = { requestId ->
                    userInteractionTimestamps[requestId] = TimeSource.Monotonic.markNow()
                },
                onPopOut = onPopOut,
                onSettingsChange = { stateManager.updateSettings(it) },
            )

            1 -> HistoryTab(
                history = state.history,
                onReplay = if (devMode) { result ->
                    val cloned = result.request.copy(
                        id = java.util.UUID.randomUUID().toString(),
                        timestamp = kotlinx.datetime.Clock.System.now(),
                    )
                    stateManager.addPending(cloned)
                    selectedTab = 0
                } else null,
            )

            protectionLogIndex -> {
                if (protectionEngine != null) {
                    ProtectionLogTab(
                        events = state.preToolUseLog,
                        protectionEngine = protectionEngine,
                        onClear = { stateManager.clearPreToolUseLog() },
                    )
                }
            }

            settingsIndex -> {
                var isHookRegistered by remember { mutableStateOf(hookRegistrar.isRegistered(state.settings.serverPort)) }

                // Re-check when port changes
                LaunchedEffect(state.settings.serverPort) {
                    isHookRegistered = hookRegistrar.isRegistered(state.settings.serverPort)
                }

                SettingsTab(
                    settings = state.settings,
                    isHookRegistered = isHookRegistered,
                    isCopilotInstalled = isCopilotInstalled,
                    historyCount = state.history.size,
                    copilotModels = copilotModels,
                    copilotInitState = copilotInitState,
                    onSettingsChange = { stateManager.updateSettings(it) },
                    onRegisterHook = {
                        hookRegistrar.register(state.settings.serverPort)
                        isHookRegistered = hookRegistrar.isRegistered(state.settings.serverPort)
                    },
                    onUnregisterHook = {
                        hookRegistrar.unregister(state.settings.serverPort)
                        isHookRegistered = hookRegistrar.isRegistered(state.settings.serverPort)
                    },
                    onInstallCopilot = {
                        CopilotBridgeInstaller.install()
                        isCopilotInstalled = CopilotBridgeInstaller.isInstalled()
                    },
                    onUninstallCopilot = {
                        CopilotBridgeInstaller.uninstall()
                        isCopilotInstalled = CopilotBridgeInstaller.isInstalled()
                    },
                    onRegisterCopilotHook = { path -> CopilotBridgeInstaller.registerHook(path) },
                    onUnregisterCopilotHook = { path -> CopilotBridgeInstaller.unregisterHook(path) },
                    isCopilotHookRegistered = { path -> CopilotBridgeInstaller.isHookRegistered(path) },
                    onClearHistory = { stateManager.clearHistory() },
                    onShowLicenses = onShowLicenses,
                    protectionModules = protectionModules,
                    onProtectionSettingsChange = { newProtectionSettings ->
                        stateManager.updateSettings(state.settings.copy(protectionSettings = newProtectionSettings))
                    },
                )
            }
        }
    }
}

internal val USER_QUIET_PERIOD: Duration = 10.seconds
internal val AUTO_DENY_COUNTDOWN: Duration = 15.seconds

/**
 * Suspends until at least [USER_QUIET_PERIOD] has passed since the user's last
 * interaction with [approvalId]. If there is no recorded interaction, returns immediately.
 */
private suspend fun waitUntilUserQuiet(
    approvalId: String,
    userInteractionTimestamps: Map<String, ComparableTimeMark>,
) {
    while (true) {
        val last = userInteractionTimestamps[approvalId] ?: return
        val remaining = USER_QUIET_PERIOD - last.elapsedNow()
        if (remaining <= Duration.ZERO) return
        delay(remaining)
    }
}

/**
 * Runs the auto-deny countdown for a risk-5 request, retrying after a quiet period
 * whenever the user interacts during the countdown. The overlay (driven by
 * [autoDenyRequests]) is only shown while a countdown is actually running.
 */
private suspend fun runAutoDenyWithRetry(
    approvalId: String,
    analysis: RiskAnalysis,
    autoDenyRequests: MutableSet<String>,
    userInteractionTimestamps: Map<String, ComparableTimeMark>,
    stateManager: AppStateManager,
) {
    while (stateManager.state.value.pendingApprovals.any { it.id == approvalId }) {
        waitUntilUserQuiet(approvalId, userInteractionTimestamps)
        if (stateManager.state.value.pendingApprovals.none { it.id == approvalId }) return

        autoDenyRequests.add(approvalId)
        val countdownStartedAt = TimeSource.Monotonic.markNow()
        var interrupted = false
        while (countdownStartedAt.elapsedNow() < AUTO_DENY_COUNTDOWN) {
            delay(200)
            // Request was resolved manually (or otherwise removed) — stop the countdown.
            if (stateManager.state.value.pendingApprovals.none { it.id == approvalId }) {
                autoDenyRequests.remove(approvalId)
                return
            }
            // User cancelled via the overlay button
            if (approvalId !in autoDenyRequests) return
            // User interacted with the card during the countdown — abort and wait for quiet
            val last = userInteractionTimestamps[approvalId]
            if (last != null && last >= countdownStartedAt) {
                autoDenyRequests.remove(approvalId)
                interrupted = true
                break
            }
        }
        if (interrupted) continue

        if (approvalId in autoDenyRequests) {
            autoDenyRequests.remove(approvalId)
            if (stateManager.state.value.pendingApprovals.any { it.id == approvalId }) {
                stateManager.resolve(
                    requestId = approvalId,
                    decision = Decision.AUTO_DENIED,
                    feedback = "Auto-denied: risk level 5",
                    riskAnalysis = analysis,
                    rawResponseJson = null,
                )
            }
            return
        }
    }
}
