package com.mikepenz.agentapprover.logging

/**
 * Runtime gate for verbose / sensitive log output.
 *
 * When [verbose] is `false` (the default), call sites that would otherwise
 * include raw commands, file paths, request/response JSON, or AI explanations
 * are expected to emit only structural information. The application toggles
 * this from [com.mikepenz.agentapprover.model.AppSettings.verboseLogging] at
 * startup and whenever the user changes the setting — no restart required.
 *
 * The flag is `@Volatile` so writes from the settings ViewModel coroutine are
 * immediately visible to logging call sites running on Ktor / IO threads.
 */
object Logging {
    @Volatile
    var verbose: Boolean = false
}
