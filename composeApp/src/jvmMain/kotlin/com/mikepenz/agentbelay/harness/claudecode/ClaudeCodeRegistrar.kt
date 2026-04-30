package com.mikepenz.agentbelay.harness.claudecode

import com.mikepenz.agentbelay.harness.HarnessRegistrar
import com.mikepenz.agentbelay.harness.OutboardArtifact
import com.mikepenz.agentbelay.hook.HookRegistrar
import java.io.File
import java.nio.file.Path

/**
 * Wraps the legacy [HookRegistrar] singleton object behind the
 * [HarnessRegistrar] interface. Preserves the existing file-locking and
 * atomic-write logic; future harnesses get sibling implementations.
 *
 * Capability hooks (UserPromptSubmit / SessionStart for the Capability
 * Engine) are managed through the legacy [HookRegistrar] entry points
 * directly — they are layered on top of the base PermissionRequest /
 * PreToolUse / PostToolUse triplet that this registrar tracks.
 */
class ClaudeCodeRegistrar : HarnessRegistrar {
    override val displayName: String = "Claude Code"

    override fun isRegistered(port: Int): Boolean = HookRegistrar.isRegistered(port)
    override fun register(port: Int) = HookRegistrar.register(port)
    override fun unregister(port: Int) = HookRegistrar.unregister(port)

    override fun describeArtifacts(port: Int): List<OutboardArtifact> {
        // Single artifact: the user's Claude Code settings file. The
        // registrar mutates it in place; the OutboardArtifact descriptor
        // is informational only (used for UI listings / uninstall prompts).
        val home = System.getProperty("user.home")
        val settings = File(home, ".claude/settings.json")
        return listOf(
            OutboardArtifact.JsonFile(
                path = settings.toPath(),
                contents = "(merged hook entries for PermissionRequest, PreToolUse, PostToolUse)",
            )
        )
    }
}
