package com.mikepenz.agentapprover.app

import com.mikepenz.agentapprover.di.AppEnvironment
import com.mikepenz.agentapprover.di.AppScope
import com.mikepenz.agentapprover.platform.AppIcon
import com.mikepenz.agentapprover.platform.MacOsTrayBadge
import com.mikepenz.agentapprover.state.AppStateManager
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import io.github.kdroidfilter.nucleus.notification.linux.LinuxNotificationCenter
import io.github.kdroidfilter.nucleus.notification.linux.Notification
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import java.awt.CheckboxMenuItem
import java.awt.EventQueue
import java.awt.MenuItem
import java.awt.PopupMenu
import java.awt.SystemTray
import java.awt.TrayIcon

/**
 * Application-scoped manager for the AWT system tray icon, popup menu, and
 * platform-native badge / notification integrations.
 *
 * Owns the canonical [visible] flag for the main window — the rest of the app
 * (notably [com.mikepenz.agentapprover.server.ApprovalServer]'s
 * `onNewApproval` callback) sets this to `true` when a new approval comes in;
 * the Compose layer observes it to drive whether the main window is shown.
 *
 * Why this lives outside Compose: the AWT tray icon and the macOS dock badge
 * are process-wide and must survive the main window being closed. Putting the
 * tray inside a `DisposableEffect` keyed off the window meant the tray was
 * destroyed and recreated whenever the user toggled visibility — which the
 * old `Main.kt` papered over by keying the effect on `Unit`. Making the tray
 * an app-scoped manager codifies that lifetime.
 */
@SingleIn(AppScope::class)
@Inject
class TrayManager(
    private val stateManager: AppStateManager,
    private val environment: AppEnvironment,
) {
    private val isMacOs = System.getProperty("os.name", "").contains("Mac", ignoreCase = true)
    private val isLinux = System.getProperty("os.name", "").contains("Linux", ignoreCase = true)

    private val _visible = MutableStateFlow(true)
    val visible: StateFlow<Boolean> = _visible.asStateFlow()

    fun show() { _visible.value = true }
    fun hide() { _visible.value = false }
    fun toggle() { _visible.value = !_visible.value }

    /**
     * Wire the AWT tray icon and start observing pending-count / away-mode
     * changes for badge updates. Returns an [AutoCloseable] that removes the
     * tray icon and cancels the observer jobs.
     *
     * The [onQuit] callback is invoked from the tray's "Quit" menu item — the
     * caller is responsible for terminating the application (e.g. by calling
     * `exitApplication()` from inside the Compose runtime).
     */
    fun install(onQuit: () -> Unit): AutoCloseable {
        if (!SystemTray.isSupported()) {
            return AutoCloseable { /* no-op */ }
        }
        val systemTray = SystemTray.getSystemTray()

        val showHideItem = MenuItem(if (_visible.value) "Hide" else "Show")
        val awayModeItem = CheckboxMenuItem(
            "Away Mode",
            stateManager.state.value.settings.awayMode,
        )

        val icon = TrayIcon(AppIcon.createTrayIconMultiRes(0, drawBadge = !isMacOs)).apply {
            isImageAutoSize = false
            toolTip = "Agent Approver"
            addActionListener { toggle() }
            popupMenu = PopupMenu().apply {
                showHideItem.addActionListener { toggle() }
                add(showHideItem)

                awayModeItem.addItemListener {
                    val newAwayMode = awayModeItem.state
                    // The item listener fires on the AWT EDT and
                    // updateSettings() does synchronous disk I/O — dispatch
                    // to appScope to keep the tray responsive.
                    environment.appScope.launch {
                        val current = stateManager.state.value.settings
                        stateManager.updateSettings(current.copy(awayMode = newAwayMode))
                    }
                }
                add(awayModeItem)

                add(MenuItem("Quit").apply { addActionListener { onQuit() } })
            }
        }
        systemTray.add(icon)
        if (isMacOs) MacOsTrayBadge.update(icon, 0)

        // Observers run on the application coroutine scope so they survive
        // window close. All AWT mutations (MenuItem.label, CheckboxMenuItem.state,
        // TrayIcon.image, TrayIcon.toolTip) are dispatched to the EDT via
        // EventQueue.invokeLater because Swing/AWT components are not
        // thread-safe.
        val visibleJob = environment.appScope.launch {
            _visible.collect { visible ->
                runOnEdt { showHideItem.label = if (visible) "Hide" else "Show" }
            }
        }
        val awayModeJob = environment.appScope.launch {
            stateManager.state
                .map { it.settings.awayMode }
                .distinctUntilChanged()
                .collect { awayMode ->
                    runOnEdt { awayModeItem.state = awayMode }
                }
        }
        val badgeJob = environment.appScope.launch {
            var previousPendingCount = 0
            stateManager.state
                .map { it.pendingApprovals.size to it.settings.awayMode }
                .distinctUntilChanged()
                .collect { (pendingCount, awayMode) ->
                    val tooltip = buildString {
                        append("Agent Approver")
                        if (pendingCount > 0) append(" ($pendingCount pending)")
                        if (awayMode) append(" [Away]")
                    }
                    runOnEdt {
                        icon.image = AppIcon.createTrayIconMultiRes(pendingCount, drawBadge = !isMacOs)
                        if (isMacOs) MacOsTrayBadge.update(icon, pendingCount)
                        icon.toolTip = tooltip
                    }
                    // Notification APIs are independent of AWT and safe off the EDT.
                    if (isMacOs) {
                        io.github.kdroidfilter.nucleus.notification.NotificationCenter
                            .setBadgeCount(pendingCount)
                    } else if (isLinux && pendingCount > 0 && pendingCount > previousPendingCount) {
                        LinuxNotificationCenter.notify(
                            Notification(
                                summary = "Agent Approver",
                                body = "$pendingCount pending approval requests",
                            ),
                        )
                    }
                    previousPendingCount = pendingCount
                }
        }

        return AutoCloseable {
            visibleJob.cancel()
            awayModeJob.cancel()
            badgeJob.cancel()
            runOnEdt {
                try {
                    systemTray.remove(icon)
                } catch (_: Exception) {
                    // Tray peer may already be torn down on shutdown.
                }
            }
        }
    }

    /** Run [block] on the AWT event-dispatch thread, optimising for the case
     *  where the caller is already on it. */
    private inline fun runOnEdt(crossinline block: () -> Unit) {
        if (EventQueue.isDispatchThread()) {
            block()
        } else {
            EventQueue.invokeLater { block() }
        }
    }
}
