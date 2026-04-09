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
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mikepenz.agentapprover.ui.approvals.ApprovalsTabHost
import com.mikepenz.agentapprover.ui.history.HistoryTabHost
import com.mikepenz.agentapprover.ui.protectionlog.ProtectionLogTabHost
import com.mikepenz.agentapprover.ui.settings.SettingsTabHost
import com.mikepenz.agentapprover.ui.statistics.StatisticsTabHost
import dev.zacsweers.metrox.viewmodel.metroViewModel

/**
 * Top-level Compose entry point. Owns nothing beyond TabRow rendering and tab
 * routing — every tab's state and side effects live in its own ViewModel,
 * accessed via [metroViewModel] inside the per-tab `*TabHost` composables.
 */
@Composable
fun App(
    onPopOut: ((title: String, content: String) -> Unit)? = null,
    onShowLicenses: () -> Unit = {},
) {
    val appViewModel: AppViewModel = metroViewModel()
    val tabState by appViewModel.tabState.collectAsState()
    val selectedTab by appViewModel.selectedTab.collectAsState()
    val visibleTabs = visibleTabs(tabState.devMode)

    Column(modifier = Modifier.fillMaxSize()) {
        if (tabState.awayMode) {
            Surface(color = MaterialTheme.colorScheme.tertiaryContainer, modifier = Modifier.fillMaxWidth()) {
                Text(
                    "Away Mode — Timeouts disabled",
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onTertiaryContainer,
                )
            }
        }

        TabRow(selectedTabIndex = selectedTab) {
            visibleTabs.forEachIndexed { index, tab ->
                Tab(
                    selected = selectedTab == index,
                    onClick = { appViewModel.selectTab(index) },
                    text = { TabLabel(tab, tabState) },
                )
            }
        }

        when (resolveTab(selectedTab, tabState.devMode)) {
            AppTab.Approvals -> ApprovalsTabHost(onPopOut = onPopOut)
            AppTab.History -> HistoryTabHost(onJumpToApprovals = { appViewModel.selectTab(0) })
            AppTab.Statistics -> StatisticsTabHost()
            AppTab.ProtectionLog -> ProtectionLogTabHost()
            AppTab.Settings -> SettingsTabHost(onShowLicenses = onShowLicenses)
        }
    }
}

@Composable
private fun TabLabel(tab: AppTab, tabState: TabState) {
    when (tab) {
        AppTab.Approvals -> if (tabState.pendingCount > 0) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Text("Approvals")
                Badge { Text("${tabState.pendingCount}") }
            }
        } else {
            Text("Approvals")
        }

        AppTab.History -> Text("History")

        AppTab.Statistics -> Text("Stats")

        AppTab.ProtectionLog -> if (tabState.protectionLogCount > 0) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Text("Protection Log", fontSize = 12.sp)
                Badge { Text("${tabState.protectionLogCount}") }
            }
        } else {
            Text("Protection Log", fontSize = 12.sp)
        }

        AppTab.Settings -> Text("Settings")
    }
}
