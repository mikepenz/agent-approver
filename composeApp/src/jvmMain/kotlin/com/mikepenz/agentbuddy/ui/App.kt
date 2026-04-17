package com.mikepenz.agentbuddy.ui

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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.mikepenz.agentbuddy.ui.approvals.ApprovalsTabHost
import com.mikepenz.agentbuddy.ui.history.HistoryTabHost
import com.mikepenz.agentbuddy.ui.protectionlog.ProtectionLogTabHost
import com.mikepenz.agentbuddy.ui.settings.SettingsTabHost
import com.mikepenz.agentbuddy.ui.statistics.StatisticsTabHost
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
        AppTab.Approvals -> LabelWithOptionalBadge("Approvals", tabState.pendingCount)
        AppTab.History -> SingleLineTabText("History")
        AppTab.Statistics -> SingleLineTabText("Stats")
        AppTab.ProtectionLog -> LabelWithOptionalBadge("Protection", tabState.protectionLogCount)
        AppTab.Settings -> SingleLineTabText("Settings")
    }
}

@Composable
private fun SingleLineTabText(text: String, modifier: Modifier = Modifier) {
    Text(
        text = text,
        modifier = modifier,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
    )
}

@Composable
private fun LabelWithOptionalBadge(text: String, count: Int) {
    if (count > 0) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            SingleLineTabText(text, modifier = Modifier.weight(1f, fill = false))
            Badge { Text("$count", maxLines = 1) }
        }
    } else {
        SingleLineTabText(text)
    }
}
