package com.mikepenz.agentbelay.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Badge
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.mikepenz.agentbelay.ui.approvals.ApprovalsTabHost
import com.mikepenz.agentbelay.ui.components.NoticeBannerStack
import com.mikepenz.agentbelay.ui.components.UpdateBanner
import com.mikepenz.agentbelay.ui.history.HistoryTabHost
import com.mikepenz.agentbelay.ui.protectionlog.ProtectionLogTabHost
import com.mikepenz.agentbelay.ui.settings.SettingsTabHost
import com.mikepenz.agentbelay.ui.shell.AppSidebar
import com.mikepenz.agentbelay.ui.shell.CommandPaletteHost
import com.mikepenz.agentbelay.ui.shell.LocalCommandPaletteController
import com.mikepenz.agentbelay.ui.slim.SlimHost
import com.mikepenz.agentbelay.ui.statistics.StatisticsTabHost
import dev.zacsweers.metrox.viewmodel.metroViewModel

/**
 * Top-level Compose entry point. Owns nothing beyond TabRow rendering and tab
 * routing — every tab's state and side effects live in its own ViewModel,
 * accessed via [metroViewModel] inside the per-tab `*TabHost` composables.
 */
/**
 * Below this width the app renders the chromeless slim approvals view
 * (sidebar + tab content are too cramped to be useful). Matches the 340dp
 * slim window shown in the Claude design handoff, with a small buffer.
 */
private const val SLIM_THRESHOLD_DP = 500

@Composable
fun App(
    onPopOut: ((com.mikepenz.agentbelay.ui.detail.PopOutSpec) -> Unit)? = null,
    onShowLicenses: () -> Unit = {},
    onExpand: () -> Unit = {},
) {
    val appViewModel: AppViewModel = metroViewModel()
    val tabState by appViewModel.tabState.collectAsState()
    val selectedTab by appViewModel.selectedTab.collectAsState()
    val notices by appViewModel.notices.collectAsState()
    val updateState by appViewModel.updateState.collectAsState()
    val visibleTabs = visibleTabs(tabState.devMode)
    val currentTab = resolveTab(selectedTab, tabState.devMode)

    val paletteController = LocalCommandPaletteController.current

    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
            val slim = maxWidth < SLIM_THRESHOLD_DP.dp
            // Between 500–730dp collapse AppSidebar to icon-only so the
            // content pane retains enough room to render.
            val compactSidebar = !slim && maxWidth < 730.dp

            if (slim) {
                SlimHost(
                    onExpand = onExpand,
                    onPopOut = onPopOut,
                    modifier = Modifier.fillMaxSize(),
                )
            } else {
                Column(modifier = Modifier.fillMaxSize()) {
                    NoticeBannerStack(notices = notices, onDismiss = appViewModel::dismissNotice)
                    if (tabState.awayMode) {
                        Surface(
                            color = MaterialTheme.colorScheme.tertiaryContainer,
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Text(
                                "Away Mode — Timeouts disabled",
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp),
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onTertiaryContainer,
                            )
                        }
                    }

                    Row(modifier = Modifier.fillMaxSize().weight(1f)) {
                        AppSidebar(
                            selectedTab = currentTab,
                            onTabSelect = { tab ->
                                val index = visibleTabs.indexOf(tab)
                                if (index >= 0) appViewModel.selectTab(index)
                            },
                            pendingCount = tabState.pendingCount,
                            appVersion = tabState.appVersion,
                            serverPort = tabState.serverPort,
                            agentRegistrations = tabState.agentRegistrations,
                            compact = compactSidebar,
                        )

                        Box(modifier = Modifier.weight(1f).fillMaxHeight()) {
                            when (currentTab) {
                                AppTab.Approvals -> ApprovalsTabHost(onPopOut = onPopOut)
                                AppTab.History -> HistoryTabHost(onJumpToApprovals = { appViewModel.selectTab(0) })
                                AppTab.Statistics -> StatisticsTabHost()
                                AppTab.Usage -> com.mikepenz.agentbelay.ui.usage.UsageTabHost()
                                AppTab.ProtectionLog -> ProtectionLogTabHost()
                                AppTab.Settings -> SettingsTabHost(onShowLicenses = onShowLicenses)
                            }
                        }
                    }
                    UpdateBanner(
                        state = updateState,
                        onDownload = appViewModel::downloadUpdate,
                        onInstall = appViewModel::installUpdate,
                        onDismiss = appViewModel::dismissUpdate,
                    )
                }
            }

        CommandPaletteHost(
            controller = paletteController,
            onNavigate = { tab ->
                val index = visibleTabs.indexOf(tab)
                if (index >= 0) appViewModel.selectTab(index)
            },
            onShowLicenses = onShowLicenses,
        )
    }
}

@Composable
private fun TabLabel(tab: AppTab, tabState: TabState) {
    when (tab) {
        AppTab.Approvals -> LabelWithOptionalBadge("Approvals", tabState.pendingCount)
        AppTab.History -> SingleLineTabText("History")
        AppTab.Statistics -> SingleLineTabText("Stats")
        AppTab.Usage -> SingleLineTabText("Usage")
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
