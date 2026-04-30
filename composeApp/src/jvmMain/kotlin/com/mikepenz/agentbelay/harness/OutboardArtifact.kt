package com.mikepenz.agentbelay.harness

import java.nio.file.Path

/**
 * A file or installation step the registrar emits onto the user's machine
 * to bridge a harness into Belay's HTTP server. Allows registrars to
 * declare what they will install without coupling to the install logic
 * (useful for UI listing, uninstall verification, dry-run inspection).
 *
 * Existing harnesses fit these forms:
 *
 *  - **Claude Code**: only [JsonFile] (`~/.claude/settings.json`), HTTP
 *    direct — no shim required.
 *  - **Copilot CLI**: [ShellScript] bridge scripts under `~/.agent-belay/`
 *    plus a [JsonFile] at `~/.copilot/hooks/agent-belay.json`.
 *  - **Future Codex**: [TomlFile] entry in `~/.codex/config.toml`.
 *  - **Future OpenCode**: [NpmPlugin] installed plus a [JsonFile] for
 *    `opencode.json` mutation.
 */
sealed interface OutboardArtifact {
    val path: Path

    /** A POSIX shell-script bridge that pipes harness stdin/stdout to Belay's HTTP server. */
    data class ShellScript(
        override val path: Path,
        val contents: String,
        val executable: Boolean = true,
    ) : OutboardArtifact

    /** A JSON config file the registrar reads-modifies-writes. */
    data class JsonFile(
        override val path: Path,
        val contents: String,
    ) : OutboardArtifact

    /** A TOML config file (Codex CLI). */
    data class TomlFile(
        override val path: Path,
        val contents: String,
    ) : OutboardArtifact

    /** An npm plugin that needs `npm install -g <spec>` to be runnable. */
    data class NpmPlugin(
        override val path: Path,
        val installSpec: String,
    ) : OutboardArtifact
}
