package com.mikepenz.agentbelay.harness.copilot

import com.mikepenz.agentbelay.harness.HarnessRegistrar
import com.mikepenz.agentbelay.harness.OutboardArtifact
import com.mikepenz.agentbelay.hook.CopilotBridgeInstaller
import java.io.File

/**
 * Wraps the legacy [CopilotBridgeInstaller] singleton object behind the
 * [HarnessRegistrar] interface. Copilot CLI integration is **not** direct
 * HTTP — registration emits two POSIX shim scripts under `~/.agent-belay/`
 * and a hooks JSON file at `~/.copilot/hooks/agent-belay.json` that points
 * at the shims. The shims `curl` Belay's HTTP server on stdin/stdout.
 *
 * The `failClosed` flag controls Copilot's behaviour when Belay is
 * unreachable; passed through from [com.mikepenz.agentbelay.model.AppSettings.copilotFailClosed]
 * by the caller.
 */
class CopilotRegistrar(
    private val failClosed: () -> Boolean = { false },
) : HarnessRegistrar {
    override val displayName: String = "GitHub Copilot CLI"

    override fun isRegistered(port: Int): Boolean = CopilotBridgeInstaller.isRegistered(port)
    override fun register(port: Int) = CopilotBridgeInstaller.register(port, failClosed())
    override fun unregister(port: Int) = CopilotBridgeInstaller.unregister(port)

    override fun describeArtifacts(port: Int): List<OutboardArtifact> {
        val home = System.getProperty("user.home")
        val belayDir = File(home, ".agent-belay")
        val copilotHooks = File(home, ".copilot/hooks/agent-belay.json")
        return listOf(
            OutboardArtifact.ShellScript(
                path = File(belayDir, "copilot-permission-request.sh").toPath(),
                contents = "(installed by CopilotBridgeInstaller — bridges permissionRequest stdin/stdout to /approve-copilot)",
            ),
            OutboardArtifact.ShellScript(
                path = File(belayDir, "copilot-pre-tool-use.sh").toPath(),
                contents = "(installed by CopilotBridgeInstaller — bridges preToolUse stdin/stdout to /pre-tool-use-copilot)",
            ),
            OutboardArtifact.JsonFile(
                path = copilotHooks.toPath(),
                contents = "(merged hook entries for permissionRequest + preToolUse pointing at the shim scripts)",
            ),
        )
    }
}
