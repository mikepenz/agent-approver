package com.mikepenz.agentbelay.harness.opencode

import com.mikepenz.agentbelay.harness.HarnessRegistrar
import com.mikepenz.agentbelay.harness.OutboardArtifact
import com.mikepenz.agentbelay.hook.OpenCodeBridgeInstaller
import java.io.File

/**
 * Wraps the legacy [OpenCodeBridgeInstaller] singleton object behind the
 * [HarnessRegistrar] interface. OpenCode is plugin-based: registration
 * writes a single TypeScript file under `~/.config/opencode/plugin/` which
 * OpenCode's plugin runtime auto-discovers on startup. The plugin opens
 * outbound HTTP connections back to Belay's server.
 */
class OpenCodeRegistrar : HarnessRegistrar {
    override val displayName: String = "OpenCode"

    override fun isRegistered(port: Int): Boolean = OpenCodeBridgeInstaller.isRegistered(port)
    override fun register(port: Int) = OpenCodeBridgeInstaller.register(port)
    override fun unregister(port: Int) = OpenCodeBridgeInstaller.unregister(port)

    override fun describeArtifacts(port: Int): List<OutboardArtifact> {
        val home = System.getProperty("user.home")
        val pluginFile = File(home, ".config/opencode/plugin/agent-belay.ts")
        return listOf(
            OutboardArtifact.JsonFile(
                path = pluginFile.toPath(),
                contents = "(installed by OpenCodeBridgeInstaller — TypeScript plugin posting to /approve-opencode and /pre-tool-use-opencode)",
            ),
        )
    }
}
