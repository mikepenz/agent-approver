package com.mikepenz.agentbelay.hook

import co.touchlab.kermit.Logger
import java.io.File
import java.nio.file.Files
import java.nio.file.StandardCopyOption

private val logger = Logger.withTag("OpenCodeBridgeInstaller")

/**
 * Installs Agent Belay as a **global** plugin for OpenCode.
 *
 * OpenCode auto-discovers plugins from `~/.config/opencode/plugin/` — any `.ts`
 * file in that directory is loaded automatically. No config file entry is needed.
 *
 * A single file is written:
 *
 *  **Plugin file** at `~/.config/opencode/plugin/agent-belay.ts` — an
 *  OpenCode plugin that intercepts `tool.execute.before` to POST approval
 *  requests to Agent Belay, and optionally fetches capability context on
 *  session events.
 *
 * The server port is baked into the plugin source at registration time, so
 * [register]/[unregister] take a port just like [CopilotBridgeInstaller].
 */
object OpenCodeBridgeInstaller {

    private const val PLUGIN_FILE_NAME = "agent-belay.ts"

    private fun configDir(): File {
        val home = System.getProperty("user.home")
        return File(home, ".config/opencode")
    }

    private fun pluginDir(): File = File(configDir(), "plugin")
    private fun pluginFile(): File = File(pluginDir(), PLUGIN_FILE_NAME)

    /**
     * True if the plugin file exists in the auto-discovery directory.
     */
    fun isRegistered(@Suppress("UNUSED_PARAMETER") port: Int): Boolean {
        return pluginFile().exists()
    }

    /**
     * Installs the plugin file. Idempotent.
     */
    fun register(port: Int) {
        writePlugin(port)
        logger.i { "Registered OpenCode plugin for port $port" }
    }

    /**
     * Removes the plugin file.
     */
    fun unregister(@Suppress("UNUSED_PARAMETER") port: Int) {
        val plugin = pluginFile()
        if (plugin.exists()) {
            plugin.delete()
            logger.i { "Removed plugin file ${plugin.absolutePath}" }
        }
    }

    /**
     * True iff the plugin file contains a capability injection fetch call.
     */
    fun isCapabilityHookRegistered(@Suppress("UNUSED_PARAMETER") port: Int): Boolean {
        val plugin = pluginFile()
        if (!plugin.exists()) return false
        return plugin.readText().contains("/capability/inject-opencode")
    }

    /**
     * Rewrites the plugin with capability injection enabled.
     */
    fun registerCapabilityHook(port: Int) {
        writePlugin(port, includeCapability = true)
        logger.i { "Registered OpenCode capability hook for port $port" }
    }

    /**
     * Rewrites the plugin without capability injection.
     */
    fun unregisterCapabilityHook(port: Int) {
        if (pluginFile().exists()) {
            writePlugin(port, includeCapability = false)
        }
    }

    // ----- internals -----

    private fun writePlugin(port: Int, includeCapability: Boolean = true) {
        val dir = pluginDir()
        dir.mkdirs()
        val content = buildPluginContent(port, includeCapability)
        atomicWrite(pluginFile(), content)
        logger.i { "Installed plugin at ${pluginFile().absolutePath}" }
    }

    private fun buildPluginContent(port: Int, includeCapability: Boolean): String {
        val capabilityBlock = if (includeCapability) {
            """
            |      event: async ({ event }) => {
            |        if (event.type === "session.created" || event.type === "session.updated") {
            |          try {
            |            const res = await fetch(`${'$'}{BASE_URL}/capability/inject-opencode`, {
            |              method: "POST",
            |              headers: { "Content-Type": "application/json" },
            |              body: JSON.stringify({ sessionId: (event as any).properties?.sessionId ?? "" }),
            |              signal: AbortSignal.timeout(5000),
            |            });
            |            if (res.ok) {
            |              const data = await res.json();
            |            }
            |          } catch {
            |            // Agent Belay unreachable — no context injection (non-blocking)
            |          }
            |        }
            |      },
            """.trimMargin()
        } else {
            ""
        }

        return """
            |// Agent Belay plugin for OpenCode
            |// Auto-generated — do not edit manually. Re-register in Agent Belay to update.
            |// Server: http://localhost:$port
            |
            |import type { PluginModule } from "@opencode-ai/plugin";
            |
            |const BASE_URL = "http://localhost:$port";
            |const TIMEOUT_MS = 300000; // 5 minutes — matches Agent Belay's default
            |
            |export default {
            |  id: "agent-belay",
            |  server: async (input) => {
            |    return {
            |      "tool.execute.before": async (hookInput, output) => {
            |        const payload = {
            |          toolName: hookInput.tool,
            |          toolInput: output.args ?? {},
            |          cwd: input.directory,
            |          sessionId: hookInput.sessionID ?? "",
            |          timestamp: Date.now(),
            |        };
            |
            |        let response;
            |        try {
            |          response = await fetch(`${'$'}{BASE_URL}/approve-opencode`, {
            |            method: "POST",
            |            headers: { "Content-Type": "application/json" },
            |            body: JSON.stringify(payload),
            |            signal: AbortSignal.timeout(TIMEOUT_MS),
            |          });
            |        } catch {
            |          // Agent Belay unreachable — fail open (allow tool execution)
            |          return;
            |        }
            |
            |        if (!response.ok) {
            |          // Non-200 — fail open
            |          return;
            |        }
            |
            |        const result = await response.json();
            |        if (result.behavior === "deny") {
            |          throw new Error(
            |            result.message ?? "Blocked by Agent Belay"
            |          );
            |        }
            |        // behavior === "allow" or unrecognized — proceed
            |      },
            |$capabilityBlock
            |    };
            |  },
            |} satisfies PluginModule;
        """.trimMargin()
    }

    private fun atomicWrite(target: File, content: String) {
        val tmp = File(target.parentFile, "${target.name}.tmp")
        tmp.writeText(content)
        Files.move(tmp.toPath(), target.toPath(), StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE)
    }
}
