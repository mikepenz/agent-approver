package com.mikepenz.agentbelay.storage

import co.touchlab.kermit.Logger
import com.mikepenz.agentbelay.hook.CopilotBridgeInstaller
import com.mikepenz.agentbelay.hook.HookRegistry
import java.io.File

/**
 * Refreshes the on-disk bridge scripts and hook configs for whichever
 * integrations were active before a brand rename.
 *
 * [LegacyDataMigration] moves the existing files into the new locations and
 * rewrites their internal absolute paths so they keep working — but the
 * scripts still carry the *old* template content from whichever Agent Belay
 * version originally registered them. Re-running each registrar regenerates
 * the scripts from the current build's templates and refreshes the hook
 * config, so users come out of the rename on parity with a fresh Register
 * click in Settings.
 *
 * Detection is filesystem-only: each `isRegistered()` check looks at the
 * post-migration state, so we only re-register what was actually present.
 *
 * The marker file (`<dataDir>/.needs-integration-refresh`) is dropped by
 * Main.kt when the migration reports work done. We delete it after a clean
 * pass; on failure we leave it in place so the next launch retries.
 */
object IntegrationRefresh {

    private val logger = Logger.withTag("IntegrationRefresh")
    internal const val MARKER_NAME = ".needs-integration-refresh"

    /**
     * Marks `dataDir` so the next [runIfNeeded] call performs the refresh.
     * Idempotent — overwriting an existing marker is harmless.
     */
    fun markPending(dataDir: String) {
        try {
            val dir = File(dataDir).also { it.mkdirs() }
            File(dir, MARKER_NAME).writeText("pending\n")
        } catch (e: Throwable) {
            logger.w(e) { "Could not write integration-refresh marker: ${e.message}" }
        }
    }

    /**
     * If a refresh marker is present, calls each registrar's `register()` for
     * integrations whose post-migration state shows them as registered. Each
     * registrar is independently try/caught so a partial failure (e.g. Claude
     * settings.json corrupt) doesn't block the others or abort startup.
     */
    fun runIfNeeded(
        dataDir: String,
        port: Int,
        copilotFailClosed: Boolean,
        hookRegistry: HookRegistry,
        copilotInstaller: CopilotInstaller = DefaultCopilotInstaller,
    ) {
        val marker = File(dataDir, MARKER_NAME)
        if (!marker.exists()) return

        var anyFailed = false
        fun safe(label: String, block: () -> Unit) {
            try {
                block()
            } catch (e: Throwable) {
                anyFailed = true
                logger.w(e) { "Refresh of $label failed: ${e.message}" }
            }
        }

        safe("Claude main hooks") {
            if (hookRegistry.isRegistered(port)) {
                hookRegistry.register(port)
                logger.i { "Refreshed Claude main hooks for port $port" }
            }
        }
        safe("Claude capability hook") {
            if (hookRegistry.isCapabilityHookRegistered(port)) {
                hookRegistry.registerCapabilityHook(port)
                logger.i { "Refreshed Claude capability hook for port $port" }
            }
        }
        safe("Claude SessionStart hook") {
            if (hookRegistry.isSessionStartHookRegistered(port)) {
                hookRegistry.registerSessionStartHook(port)
                logger.i { "Refreshed Claude SessionStart bridge script for port $port" }
            }
        }
        safe("Copilot main hooks") {
            if (copilotInstaller.isRegistered(port)) {
                copilotInstaller.register(port, copilotFailClosed)
                logger.i { "Refreshed Copilot bridge scripts for port $port" }
            }
        }
        safe("Copilot capability hook") {
            if (copilotInstaller.isCapabilityHookRegistered(port)) {
                copilotInstaller.registerCapabilityHook(port, copilotFailClosed)
                logger.i { "Refreshed Copilot capability bridge script for port $port" }
            }
        }

        if (anyFailed) {
            logger.w { "One or more refresh steps failed — leaving marker for retry on next launch" }
            return
        }

        if (!marker.delete()) {
            logger.w { "Refresh succeeded but could not remove marker ${marker.absolutePath}" }
        }
    }

    /**
     * Thin testable interface over [CopilotBridgeInstaller]. Mirrors
     * [HookRegistry] so tests can stub Copilot without touching the real
     * `~/.copilot/hooks/` tree.
     */
    interface CopilotInstaller {
        fun isRegistered(port: Int): Boolean
        fun register(port: Int, failClosed: Boolean)
        fun isCapabilityHookRegistered(port: Int): Boolean
        fun registerCapabilityHook(port: Int, failClosed: Boolean)
    }

    object DefaultCopilotInstaller : CopilotInstaller {
        override fun isRegistered(port: Int): Boolean = CopilotBridgeInstaller.isRegistered(port)
        override fun register(port: Int, failClosed: Boolean) =
            CopilotBridgeInstaller.register(port, failClosed)
        override fun isCapabilityHookRegistered(port: Int): Boolean =
            CopilotBridgeInstaller.isCapabilityHookRegistered(port)
        override fun registerCapabilityHook(port: Int, failClosed: Boolean) =
            CopilotBridgeInstaller.registerCapabilityHook(port, failClosed)
    }
}
