package com.mikepenz.agentbelay.hook

import co.touchlab.kermit.Logger
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import java.io.File
import java.nio.file.Files
import java.nio.file.StandardCopyOption

private val logger = Logger.withTag("CopilotBridgeInstaller")

private val json = Json {
    ignoreUnknownKeys = true
    prettyPrint = true
}

/**
 * Installs Agent Belay as a **user-scoped** hook for GitHub Copilot CLI.
 *
 * Mirrors [HookRegistrar] (which writes `~/.claude/settings.json`) so that
 * registering Copilot is a single click — no per-project setup. Two pieces
 * are written:
 *
 *  1. **Bridge scripts** under `~/.agent-belay/` — small bash wrappers
 *     that POST stdin JSON to Agent Belay and echo the response. Copilot
 *     CLI's hook system runs commands, not HTTP, so a thin shell shim is
 *     required (Claude supports HTTP hooks natively).
 *  2. **Hook config** at `~/.copilot/hooks/agent-belay.json` — a single
 *     hooks.json file containing both `preToolUse` (Protection Engine) and
 *     `permissionRequest` (interactive approvals) entries. User-scoped hook
 *     loading was added in copilot-cli v0.0.422; the `permissionRequest`
 *     event requires v1.0.16+.
 *
 * The configured server port is baked into the bridge scripts at registration
 * time, so [register]/[unregister] take a port just like [HookRegistrar].
 */
object CopilotBridgeInstaller {

    private const val PRE_TOOL_USE_SCRIPT_NAME = "copilot-hook.sh"
    private const val PERMISSION_REQUEST_SCRIPT_NAME = "copilot-approve.sh"
    private const val CAPABILITY_SCRIPT_NAME = "copilot-capability.sh"

    private const val PRE_TOOL_USE_ENDPOINT = "pre-tool-use-copilot"
    private const val PERMISSION_REQUEST_ENDPOINT = "approve-copilot"
    private const val CAPABILITY_ENDPOINT = "capability/inject-copilot"

    // Hook event keys in Copilot CLI hooks.json. We use **camelCase** because
    // every documented example and every working in-the-wild user-scoped hook
    // file uses camelCase: the official "Use hooks with Copilot CLI" page
    // (docs.github.com) only documents `sessionStart`, `userPromptSubmitted`,
    // `preToolUse`, `postToolUse`, `errorOccurred`; the maintainer who closed
    // copilot-cli#1651 referred to the v1.0.16 event as `permissionRequest`
    // (camelCase) even though the changelog title spelled it "PermissionRequest".
    // PascalCase aliases were added in v1.0.6 for Claude-Code interop, but the
    // alias appears to live in the Claude-format loader path (.claude/settings.json),
    // not the user-scoped ~/.copilot/hooks/ discovery path — empirically the
    // PascalCase variant doesn't fire from this location.
    private const val HOOK_PRE_TOOL_USE_KEY = "preToolUse"
    private const val HOOK_PERMISSION_REQUEST_KEY = "permissionRequest"

    // Capability context injection is wired to `sessionStart` — NOT
    // `userPromptSubmitted` — because Copilot CLI's command-type
    // `userPromptSubmitted` hook runner ignores stdout entirely: its output
    // parser in the bundled app.js is literally `a => {}` (empty block,
    // returns undefined), so any `additionalContext` we print is discarded.
    // `sessionStart`'s parser does honor `additionalContext`, and the
    // returned text is prepended as a user message for the whole session,
    // which matches the "inject once, stay in effect" semantics we want.
    private const val HOOK_SESSION_START_KEY = "sessionStart"

    private const val HOOK_FILE_NAME = "agent-belay.json"

    private fun scriptDir(): File {
        val home = System.getProperty("user.home")
        return File(home, ".agent-belay")
    }

    private fun preToolUseScriptFile(): File = File(scriptDir(), PRE_TOOL_USE_SCRIPT_NAME)
    private fun permissionRequestScriptFile(): File = File(scriptDir(), PERMISSION_REQUEST_SCRIPT_NAME)
    private fun capabilityScriptFile(): File = File(scriptDir(), CAPABILITY_SCRIPT_NAME)

    private fun hookFile(): File {
        val home = System.getProperty("user.home")
        return File(home, ".copilot/hooks/$HOOK_FILE_NAME")
    }

    // The hook config references the scripts via `~`-expanded absolute paths
    // so Copilot CLI can resolve them regardless of cwd.
    private fun preToolUseScriptPath(): String = preToolUseScriptFile().absolutePath
    private fun permissionRequestScriptPath(): String = permissionRequestScriptFile().absolutePath
    private fun capabilityScriptPath(): String = capabilityScriptFile().absolutePath

    /**
     * Renders a bridge script that POSTs the stdin payload to the given
     * Agent Belay endpoint and echoes the response. The configured port
     * is baked in at install time so the script doesn't depend on env vars.
     * Both scripts are byte-identical except for the endpoint path.
     *
     * Robustness notes — every detail here matters for Copilot CLI honoring
     * the response:
     *
     *  - `printf '%s'` instead of `echo` — `echo` can interpret backslash
     *    escapes when bash is built with `xpg_echo` enabled (e.g. POSIX mode).
     *    Hook payloads contain JSON with embedded backslashes; we want them
     *    forwarded verbatim.
     *
     *  - `--data-binary @-` instead of `-d @-` — curl's `-d` is form-data
     *    semantics and **strips newlines** from the body. JSON is whitespace
     *    tolerant so this rarely breaks parsing, but `--data-binary` is the
     *    correct flag for byte-exact JSON forwarding.
     *
     *  - Capturing `${'$'}?` immediately — `${'$'}?` reflects the exit code of
     *    the *previous* command, and any edit that adds a command between the
     *    curl call and the check would silently break it.
     *
     *  - `printf '%s'` for the response — no trailing newline. Most JSON
     *    parsers tolerate trailing whitespace but Copilot's hook reader is
     *    undocumented and we have no need to add bytes the server didn't send.
     */
    private fun buildScriptContent(endpoint: String, port: Int, failClosed: Boolean): String {
        // When fail-closed, a non-zero exit signals "deny" to Copilot CLI for
        // preToolUse; for permissionRequest an error exit is treated as a
        // blocked action. We also emit a short stderr message so the user
        // sees *why* their command was blocked rather than a silent failure.
        //
        // NOTE: this function uses `trimMargin("|")` rather than `trimIndent()`
        // because the failure branch used to be interpolated as a
        // pre-`trimIndent()`'d string, which left lines at column 0 inside
        // the outer template — that made `trimIndent()`'s min-indent
        // detection collapse to 0 and silently leave the shebang indented.
        // A leading space in front of `#!/usr/bin/env bash` defeats kernel
        // shebang lookup when Copilot CLI execs the script directly, so we
        // keep explicit `|` margins and drop the whole min-indent dance.
        val failureBranch = if (failClosed) {
            """
            |if [ "${'$'}CURL_EXIT" -ne 0 ] || [ -z "${'$'}RESPONSE" ]; then
            |    # Server unreachable — fail closed (Agent Belay setting)
            |    echo "Agent Belay unreachable — blocking (fail-closed)" >&2
            |    exit 1
            |fi
            """.trimMargin()
        } else {
            """
            |if [ "${'$'}CURL_EXIT" -ne 0 ] || [ -z "${'$'}RESPONSE" ]; then
            |    # Server unreachable — fail open
            |    exit 0
            |fi
            """.trimMargin()
        }

        val headerComment = if (failClosed) {
            "# Fail-closed: if server is unreachable, exits 1 so Copilot blocks the action."
        } else {
            "# Fail-open: if server is unreachable, exits 0 so Copilot proceeds normally."
        }

        return """
            |#!/usr/bin/env bash
            |# Agent Belay bridge script for GitHub Copilot CLI
            |# Reads hook JSON from stdin, POSTs to Agent Belay, returns response.
            |$headerComment
            |
            |set -uo pipefail
            |
            |URL="http://localhost:$port/$endpoint"
            |INPUT=${'$'}(cat)
            |
            |RESPONSE=${'$'}(printf '%s' "${'$'}INPUT" | curl -sS --max-time 300 \
            |    -X POST \
            |    -H "Content-Type: application/json" \
            |    --data-binary @- \
            |    "${'$'}URL" 2>/dev/null)
            |CURL_EXIT=${'$'}?
            |
            |$failureBranch
            |
            |printf '%s' "${'$'}RESPONSE"
        """.trimMargin()
    }

    /**
     * True if the bridge scripts exist + are executable AND the user-scoped
     * hook file exists with both event entries pointing at our scripts.
     *
     * The [port] is not currently checked against the script contents — only
     * presence is verified — because the SettingsViewModel re-runs
     * [register] whenever the port changes, which overwrites the scripts
     * with the new port baked in.
     */
    fun isRegistered(@Suppress("UNUSED_PARAMETER") port: Int): Boolean {
        val pre = preToolUseScriptFile()
        val perm = permissionRequestScriptFile()
        if (!pre.exists() || !pre.canExecute()) return false
        if (!perm.exists() || !perm.canExecute()) return false

        val hooks = hookFile()
        if (!hooks.exists()) return false
        return try {
            val root = json.parseToJsonElement(hooks.readText()).jsonObject
            val hooksObj = root["hooks"]?.jsonObject ?: return false
            hasHookEntry(hooksObj, HOOK_PRE_TOOL_USE_KEY, preToolUseScriptPath()) &&
                hasHookEntry(hooksObj, HOOK_PERMISSION_REQUEST_KEY, permissionRequestScriptPath())
        } catch (e: Exception) {
            logger.w(e) { "Failed to read $HOOK_FILE_NAME" }
            false
        }
    }

    /**
     * Installs (or refreshes) the bridge scripts and writes the user-scoped
     * hook config. Idempotent — safe to call repeatedly. The [port] is baked
     * into the bridge scripts so the hook target matches the running server.
     *
     * Preserves any capability hook entry already present: if the capability
     * bridge script exists on disk, the rewritten hook file keeps the
     * `sessionStart` entry alongside the approval entries.
     */
    fun register(port: Int, failClosed: Boolean = false) {
        installScripts(port, failClosed)
        val keepCapability = capabilityScriptFile().exists()
        if (keepCapability) {
            // Refresh the capability script so its baked-in port matches.
            writeCapabilityScript(port, failClosed)
        }
        writeHookFile(includeCapability = keepCapability)
        logger.i { "Registered Copilot user-scoped hook for port $port (failClosed=$failClosed)" }
    }

    /**
     * True iff the capability bridge script exists and is executable AND the
     * hook file contains a `sessionStart` entry pointing at it.
     */
    fun isCapabilityHookRegistered(@Suppress("UNUSED_PARAMETER") port: Int): Boolean {
        val cap = capabilityScriptFile()
        if (!cap.exists() || !cap.canExecute()) return false
        val hooks = hookFile()
        if (!hooks.exists()) return false
        return try {
            val root = json.parseToJsonElement(hooks.readText()).jsonObject
            val hooksObj = root["hooks"]?.jsonObject ?: return false
            hasHookEntry(hooksObj, HOOK_SESSION_START_KEY, capabilityScriptPath())
        } catch (e: Exception) {
            logger.w(e) { "Failed to read $HOOK_FILE_NAME" }
            false
        }
    }

    /**
     * Installs the capability bridge script and adds a `sessionStart`
     * entry to the hook file. The approval entries (if any) are preserved.
     * Idempotent.
     */
    fun registerCapabilityHook(port: Int, failClosed: Boolean = false) {
        writeCapabilityScript(port, failClosed)
        // Preserve existing approval hooks if their scripts are still on disk.
        val keepApproval = preToolUseScriptFile().exists() && permissionRequestScriptFile().exists()
        writeHookFile(includeApproval = keepApproval, includeCapability = true)
        logger.i { "Registered Copilot capability hook for port $port (failClosed=$failClosed)" }
    }

    /**
     * Removes the capability bridge script and rewrites the hook file without
     * the `sessionStart` entry. If that leaves the hook file with no
     * entries at all (no approval scripts either), the file is deleted.
     */
    fun unregisterCapabilityHook(@Suppress("UNUSED_PARAMETER") port: Int) {
        val cap = capabilityScriptFile()
        if (cap.exists()) {
            cap.delete()
            logger.i { "Removed capability bridge script ${cap.absolutePath}" }
        }
        val keepApproval = preToolUseScriptFile().exists() && permissionRequestScriptFile().exists()
        if (keepApproval) {
            writeHookFile(includeApproval = true, includeCapability = false)
        } else {
            // Nothing left to write — delete the hook file to match the
            // "empty state" invariant maintained by unregister().
            val hooks = hookFile()
            if (hooks.exists()) {
                hooks.delete()
                logger.i { "Removed ${hooks.absolutePath} (no entries left)" }
            }
        }
    }

    /**
     * Removes the bridge scripts and the Agent Belay hook entries. If the
     * hook file ends up empty (no other tools share it), the file itself is
     * removed too.
     */
    fun unregister(@Suppress("UNUSED_PARAMETER") port: Int) {
        listOf(preToolUseScriptFile(), permissionRequestScriptFile()).forEach { file ->
            if (file.exists()) {
                file.delete()
                logger.i { "Removed bridge script ${file.absolutePath}" }
            }
        }

        val hooks = hookFile()
        val keepCapability = capabilityScriptFile().exists()
        if (keepCapability) {
            // Capability is still enabled — rewrite the file with just the
            // capability entry instead of deleting it.
            writeHookFile(includeApproval = false, includeCapability = true)
        } else if (hooks.exists()) {
            // Nothing left — delete the file entirely. Safe because
            // agent-belay.json is owned by us.
            hooks.delete()
            logger.i { "Removed ${hooks.absolutePath}" }
        }
    }

    /** JSON snippet shown in the README / settings UI for documentation. */
    fun hooksJsonSnippet(): String = """
        {
          "version": 1,
          "hooks": {
            "preToolUse": [
              {
                "type": "command",
                "bash": "~/.agent-belay/$PRE_TOOL_USE_SCRIPT_NAME",
                "timeoutSec": 300
              }
            ],
            "permissionRequest": [
              {
                "type": "command",
                "bash": "~/.agent-belay/$PERMISSION_REQUEST_SCRIPT_NAME",
                "timeoutSec": 300
              }
            ]
          }
        }
    """.trimIndent()

    // ----- internals -----

    private fun installScripts(port: Int, failClosed: Boolean) {
        val dir = scriptDir()
        dir.mkdirs()

        atomicWriteExecutable(preToolUseScriptFile(), buildScriptContent(PRE_TOOL_USE_ENDPOINT, port, failClosed))
        logger.i { "Installed bridge script ${preToolUseScriptFile().absolutePath}" }

        atomicWriteExecutable(permissionRequestScriptFile(), buildScriptContent(PERMISSION_REQUEST_ENDPOINT, port, failClosed))
        logger.i { "Installed bridge script ${permissionRequestScriptFile().absolutePath}" }
    }

    private fun writeCapabilityScript(port: Int, failClosed: Boolean) {
        val dir = scriptDir()
        dir.mkdirs()
        // Capability injection is additive context, not a gate — but we still
        // honour the fail-closed flag for consistency across the three scripts
        // so a single toggle reliably describes all three files' behaviour.
        atomicWriteExecutable(capabilityScriptFile(), buildScriptContent(CAPABILITY_ENDPOINT, port, failClosed))
        logger.i { "Installed bridge script ${capabilityScriptFile().absolutePath}" }
    }

    private fun writeHookFile(
        includeApproval: Boolean = true,
        includeCapability: Boolean = false,
    ) {
        val file = hookFile()
        file.parentFile.mkdirs()

        val root = buildJsonObject {
            put("version", 1)
            put("hooks", buildJsonObject {
                if (includeApproval) {
                    put(HOOK_PRE_TOOL_USE_KEY, buildJsonArray { add(buildHookEntry(preToolUseScriptPath())) })
                    put(HOOK_PERMISSION_REQUEST_KEY, buildJsonArray { add(buildHookEntry(permissionRequestScriptPath())) })
                }
                if (includeCapability) {
                    put(HOOK_SESSION_START_KEY, buildJsonArray { add(buildHookEntry(capabilityScriptPath())) })
                }
            })
        }
        atomicWrite(file, json.encodeToString(JsonElement.serializer(), root))
    }

    /**
     * Builds a single hook entry. Uses the documented `bash` field rather than
     * the `command` cross-platform alias because every working Copilot CLI hook
     * file in the wild uses `bash`/`powershell` — `command` was added in v1.0.2
     * as an alias but is not used in any of the official examples we could find.
     * Same reasoning for `timeoutSec` over the `timeout` Claude-format alias.
     */
    private fun buildHookEntry(scriptPath: String): JsonObject = buildJsonObject {
        put("type", "command")
        put("bash", scriptPath)
        put("timeoutSec", 300)
    }

    private fun atomicWrite(target: File, content: String) {
        val tmp = File(target.parentFile, "${target.name}.tmp")
        tmp.writeText(content)
        Files.move(tmp.toPath(), target.toPath(), StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE)
    }

    private fun atomicWriteExecutable(target: File, content: String) {
        val tmp = File(target.parentFile, "${target.name}.tmp")
        tmp.writeText(content)
        if (!tmp.setExecutable(true)) {
            logger.w { "Failed to set executable bit on ${tmp.absolutePath}" }
        }
        Files.move(tmp.toPath(), target.toPath(), StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE)
    }

    private fun hasHookEntry(hooks: JsonObject, eventKey: String, scriptPath: String): Boolean {
        val entries = hooks[eventKey]?.jsonArray ?: return false
        return entries.any { entry ->
            val bash = entry.jsonObject["bash"]?.jsonPrimitive?.content
            bash == scriptPath
        }
    }
}
