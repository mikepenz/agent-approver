package com.mikepenz.agentapprover.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateSetOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import com.mikepenz.agentapprover.hook.HookRegistrar
import com.mikepenz.agentapprover.model.Decision
import com.mikepenz.agentapprover.risk.RiskAnalyzer
import com.mikepenz.agentapprover.state.AppStateManager
import com.mikepenz.agentapprover.ui.approvals.ApprovalsTab
import com.mikepenz.agentapprover.ui.approvals.RiskStatus
import com.mikepenz.agentapprover.ui.history.HistoryTab
import com.mikepenz.agentapprover.ui.settings.SettingsTab
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@Composable
fun App(
    stateManager: AppStateManager,
    hookRegistrar: HookRegistrar,
    riskAnalyzer: RiskAnalyzer,
    coroutineScope: CoroutineScope,
) {
    val state by stateManager.state.collectAsState()
    var selectedTab by remember { mutableIntStateOf(0) }

    val riskStatuses = remember { mutableStateMapOf<String, RiskStatus>() }
    val riskErrors = remember { mutableStateMapOf<String, String>() }
    val autoDenyRequests = remember { mutableStateSetOf<String>() }

    // Track pending approval IDs to detect new arrivals
    val knownIds = remember { mutableStateSetOf<String>() }

    LaunchedEffect(state.pendingApprovals) {
        for (approval in state.pendingApprovals) {
            if (approval.id !in knownIds) {
                knownIds.add(approval.id)
                if (state.settings.riskAnalysisEnabled) {
                    riskStatuses[approval.id] = RiskStatus.ANALYZING
                    riskAnalyzer.analyze(
                        request = approval,
                        onResult = { analysis ->
                            riskStatuses[approval.id] = RiskStatus.COMPLETED
                            stateManager.updateRiskResult(approval.id, analysis)

                            // Auto-actions based on risk level
                            if (analysis.risk == 1 && state.settings.autoApproveRisk1) {
                                coroutineScope.launch {
                                    delay(500)
                                    stateManager.resolve(
                                        requestId = approval.id,
                                        decision = Decision.AUTO_APPROVED,
                                        feedback = "Auto-approved: risk level 1",
                                        riskAnalysis = analysis,
                                        rawResponseJson = null,
                                    )
                                }
                            } else if (analysis.risk == 5 && state.settings.autoDenyRisk5) {
                                autoDenyRequests.add(approval.id)
                                coroutineScope.launch {
                                    delay(15_000)
                                    if (approval.id in autoDenyRequests) {
                                        autoDenyRequests.remove(approval.id)
                                        stateManager.resolve(
                                            requestId = approval.id,
                                            decision = Decision.AUTO_DENIED,
                                            feedback = "Auto-denied: risk level 5",
                                            riskAnalysis = analysis,
                                            rawResponseJson = null,
                                        )
                                    }
                                }
                            }
                        },
                        onError = { error ->
                            riskStatuses[approval.id] = RiskStatus.ERROR
                            riskErrors[approval.id] = error
                        },
                    )
                }
            }
        }
        // Clean up IDs that are no longer pending
        val currentIds = state.pendingApprovals.map { it.id }.toSet()
        knownIds.removeAll { it !in currentIds }
    }

    Column(modifier = Modifier.fillMaxSize()) {
        TabRow(selectedTabIndex = selectedTab) {
            Tab(
                selected = selectedTab == 0,
                onClick = { selectedTab = 0 },
                text = {
                    if (state.pendingApprovals.isNotEmpty()) {
                        BadgedBox(badge = { Badge { Text("${state.pendingApprovals.size}") } }) {
                            Text("Approvals")
                        }
                    } else {
                        Text("Approvals")
                    }
                },
            )
            Tab(
                selected = selectedTab == 1,
                onClick = { selectedTab = 1 },
                text = { Text("History") },
            )
            Tab(
                selected = selectedTab == 2,
                onClick = { selectedTab = 2 },
                text = { Text("Settings") },
            )
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
                onDeny = { requestId, feedback ->
                    stateManager.resolve(
                        requestId = requestId,
                        decision = Decision.DENIED,
                        feedback = feedback,
                        riskAnalysis = null,
                        rawResponseJson = null,
                    )
                },
                onSendResponse = { requestId, response ->
                    stateManager.resolve(
                        requestId = requestId,
                        decision = Decision.DENIED,
                        feedback = "User response: $response",
                        riskAnalysis = null,
                        rawResponseJson = null,
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
            )

            1 -> HistoryTab(history = state.history)

            2 -> SettingsTab(
                settings = state.settings,
                isHookRegistered = hookRegistrar.isRegistered(state.settings.serverPort),
                historyCount = state.history.size,
                onSettingsChange = { stateManager.updateSettings(it) },
                onRegisterHook = { hookRegistrar.register(state.settings.serverPort) },
                onUnregisterHook = { hookRegistrar.unregister(state.settings.serverPort) },
                onClearHistory = { stateManager.clearHistory() },
            )
        }
    }
}
