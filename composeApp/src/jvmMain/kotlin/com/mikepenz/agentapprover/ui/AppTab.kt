package com.mikepenz.agentapprover.ui

/**
 * The set of top-level tabs hosted by [App]. The Protection Log tab is only
 * visible in dev mode.
 */
enum class AppTab { Approvals, History, Statistics, ProtectionLog, Settings }

/**
 * Visible tab order computed from [devMode]. Centralised here (rather than
 * scattered in `App.kt`) so the index → tab mapping has a single source of
 * truth and can't drift.
 */
fun visibleTabs(devMode: Boolean): List<AppTab> = buildList {
    add(AppTab.Approvals)
    add(AppTab.History)
    add(AppTab.Statistics)
    if (devMode) add(AppTab.ProtectionLog)
    add(AppTab.Settings)
}

/** Resolve a TabRow index to its [AppTab] for the current [devMode]. */
fun resolveTab(index: Int, devMode: Boolean): AppTab =
    visibleTabs(devMode).getOrElse(index) { AppTab.Approvals }
