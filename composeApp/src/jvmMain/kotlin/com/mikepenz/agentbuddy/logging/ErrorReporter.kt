package com.mikepenz.agentbuddy.logging

import co.touchlab.kermit.Logger
import com.mikepenz.agentbuddy.state.AppStateManager

/**
 * Process-wide sink for recoverable errors that should reach the user.
 *
 * Defensive parse failures, unexpected JSON shapes, and caught render
 * exceptions call [report], which writes the stack to the Kermit file log
 * (see `Theme.FileLogWriter`, ~/.agent-buddy/logs/agent-buddy.log) and
 * appends a transient [com.mikepenz.agentbuddy.state.AppNotice] so users
 * can copy+report instead of wondering why a card disappeared.
 *
 * Singleton because a lot of call sites (adapters, Compose helpers) can't
 * easily reach the DI graph, and because there is only ever one
 * [AppStateManager] per app process.
 */
object ErrorReporter {
    private val logger = Logger.withTag("ErrorReporter")

    @Volatile
    private var stateManager: AppStateManager? = null

    fun bind(manager: AppStateManager) {
        stateManager = manager
    }

    fun report(message: String, throwable: Throwable? = null) {
        val mgr = stateManager
        if (mgr != null) {
            mgr.reportError(message, throwable)
        } else {
            // Bind happens very early; this path should be unreachable in
            // practice, but log locally so we don't drop the error on the floor.
            logger.e(throwable) { message }
        }
    }
}
