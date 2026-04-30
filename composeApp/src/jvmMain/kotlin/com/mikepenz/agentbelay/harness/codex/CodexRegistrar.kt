package com.mikepenz.agentbelay.harness.codex

import com.mikepenz.agentbelay.harness.HarnessRegistrar
import com.mikepenz.agentbelay.harness.OutboardArtifact
import com.mikepenz.agentbelay.hook.CodexBridgeInstaller
import java.io.File

/**
 * Wraps [CodexBridgeInstaller] behind the [HarnessRegistrar] interface.
 * Installation is a managed-block read-modify-write of
 * `~/.codex/config.toml`; no shim scripts or external installs required
 * because Codex's hooks crate speaks HTTP natively.
 */
class CodexRegistrar : HarnessRegistrar {
    override val displayName: String = "Codex"

    override fun isRegistered(port: Int): Boolean = CodexBridgeInstaller.isRegistered(port)
    override fun register(port: Int) = CodexBridgeInstaller.register(port)
    override fun unregister(port: Int) = CodexBridgeInstaller.unregister(port)

    override fun describeArtifacts(port: Int): List<OutboardArtifact> {
        val home = System.getProperty("user.home")
        val configFile = File(home, ".codex/config.toml")
        return listOf(
            OutboardArtifact.TomlFile(
                path = configFile.toPath(),
                contents = "(managed block in ${configFile.absolutePath} pointing at /approve-codex and /pre-tool-use-codex on port $port)",
            ),
        )
    }
}
