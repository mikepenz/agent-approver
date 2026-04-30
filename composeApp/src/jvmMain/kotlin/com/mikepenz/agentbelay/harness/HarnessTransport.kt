package com.mikepenz.agentbelay.harness

/**
 * Declares which HTTP routes this harness terminates on Belay's server.
 *
 * The data path uniformly ends at HTTP regardless of how the harness
 * itself is wired up — direct (Claude Code, Codex), via a shim shell
 * script (Copilot CLI, Cursor, Gemini CLI), or via an in-process plugin
 * (OpenCode). The shim/plugin is emitted by the registrar as an
 * [OutboardArtifact]; the transport just declares the server-side path.
 *
 * Endpoints are namespaced per-harness so multiple harnesses can coexist
 * on the same Ktor server without route collisions
 * (e.g. `/approve` for Claude, `/approve-copilot` for Copilot).
 */
interface HarnessTransport {
    /**
     * Route paths the Ktor server should mount for this harness, keyed
     * by logical event. Only events the harness fires are present.
     */
    fun endpoints(): Map<HookEvent, String>
}
