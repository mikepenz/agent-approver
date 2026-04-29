package com.mikepenz.agentbelay.app

import com.mikepenz.agentbelay.di.AppEnvironment
import com.mikepenz.agentbelay.di.AppScope
import com.mikepenz.agentbelay.state.AppStateManager
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import io.github.kdroidfilter.nucleus.notification.NotificationCenter
import io.github.kdroidfilter.nucleus.notification.linux.LinuxNotificationCenter
import io.github.kdroidfilter.nucleus.notification.linux.Notification
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

/**
 * Owns the OS-level notification side-effects that follow the pending-approval
 * count: the macOS dock badge and the Linux notification toast. The tray icon
 * itself (with its own baked-in badge) lives in the Compose `AgentBelayTray`
 * composable; this class is deliberately not a composable so the side effects
 * keep running on the application coroutine scope even when no Compose window
 * is mounted.
 */
@SingleIn(AppScope::class)
@Inject
class TrayNotificationsManager(
    private val stateManager: AppStateManager,
    private val environment: AppEnvironment,
) {
    private val isMacOs = System.getProperty("os.name", "").contains("Mac", ignoreCase = true)
    private val isLinux = System.getProperty("os.name", "").contains("Linux", ignoreCase = true)

    fun start(): AutoCloseable {
        val job = environment.appScope.launch {
            var previousPendingCount = 0
            stateManager.state
                .map { it.pendingApprovals.size }
                .distinctUntilChanged()
                .collect { pendingCount ->
                    if (isMacOs) {
                        NotificationCenter.setBadgeCount(pendingCount)
                    } else if (isLinux && pendingCount > 0 && pendingCount > previousPendingCount) {
                        LinuxNotificationCenter.notify(
                            Notification(
                                summary = "Agent Belay",
                                body = "$pendingCount pending approval requests",
                            ),
                        )
                    }
                    previousPendingCount = pendingCount
                }
        }
        return AutoCloseable { job.cancel() }
    }
}
