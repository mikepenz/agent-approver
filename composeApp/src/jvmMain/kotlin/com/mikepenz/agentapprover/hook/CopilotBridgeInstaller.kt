package com.mikepenz.agentapprover.hook

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

private val logger = Logger.withTag("CopilotBridgeInstaller")

private val json = Json {
    ignoreUnknownKeys = true
    prettyPrint = true
}

/**
 * Installs Agent Approver as a **user-scoped** hook for GitHub Copilot CLI.
 *
 * Mirrors [HookRegistrar] (which writes `~/.claude/settings.json`) so that
 * registering Copilot is a single click — no per-project setup. Two pieces
 * are written:
 *
 *  1. **Bridge scripts** under `~/.agent-approver/` — small bash wrappers
 *     that POST stdin JSON to Agent Approver and echo the response. Copilot
 *     CLI's hook system runs commands, not HTTP, so a thin shell shim is
 *     required (Claude supports HTTP hooks natively).
 *  2. **Hook config** at `~/.copilot/hooks/agent-approver.json` — a single
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

    private const val PRE_TOOL_USE_ENDPOINT = "pre-tool-use-copilot"
    private const val PERMISSION_REQUEST_ENDPOINT = "approve-copilot"

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

    private const val HOOK_FILE_NAME = "agent-approver.json"

    private fun scriptDir(): File {
        val home = System.getProperty("user.home")
        return File(home, ".agent-approver")
    }

    private fun preToolUseScriptFile(): File = File(scriptDir(), PRE_TOOL_USE_SCRIPT_NAME)
    private fun permissionRequestScriptFile(): File = File(scriptDir(), PERMISSION_REQUEST_SCRIPT_NAME)

    private fun hookFile(): File {
        val home = System.getProperty("user.home")
        return File(home, ".copilot/hooks/$HOOK_FILE_NAME")
    }

    // The hook config references the scripts via `~`-expanded absolute paths
    // so Copilot CLI can resolve them regardless of cwd.
    private fun preToolUseScriptPath(): String = preToolUseScriptFile().absolutePath
    private fun permissionRequestScriptPath(): String = permissionRequestScriptFile().absolutePath

    /**
     * Renders a bridge script that POSTs the stdin payload to the given
     * Agent Approver endpoint and echoes the response. The configured port
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
    private fun buildScriptContent(endpoint: String, port: Int): String = """
        #!/usr/bin/env bash
        # Agent Approver bridge script for GitHub Copilot CLI
        # Reads hook JSON from stdin, POSTs to Agent Approver, returns response.
        # Fail-open: if server is unreachable, exits 0 so Copilot proceeds normally.

        set -uo pipefail

        URL="http://localhost:$port/$endpoint"
        INPUT=${'$'}(cat)

        RESPONSE=${'$'}(printf '%s' "${'$'}INPUT" | curl -sS --max-time 300 \
            -X POST \
            -H "Content-Type: application/json" \
            --data-binary @- \
            "${'$'}URL" 2>/dev/null)
        CURL_EXIT=${'$'}?

        if [ "${'$'}CURL_EXIT" -ne 0 ] || [ -z "${'$'}RESPONSE" ]; then
            # Server unreachable — fail open
            exit 0
        fi

        printf '%s' "${'$'}RESPONSE"
    """.trimIndent()

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
     */
    fun register(port: Int) {
        installScripts(port)
        writeHookFile()
        logger.i { "Registered Copilot user-scoped hook for port $port" }
    }

    /**
     * Removes the bridge scripts and the Agent Approver hook entries. If the
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
        if (hooks.exists()) {
            // Since the file is owned by Agent Approver (its name is
            // agent-approver.json), simply delete it. We don't need to
            // surgically rewrite — there's no risk of clobbering another
            // tool's hooks.
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
                "bash": "~/.agent-approver/$PRE_TOOL_USE_SCRIPT_NAME",
                "timeoutSec": 300
              }
            ],
            "permissionRequest": [
              {
                "type": "command",
                "bash": "~/.agent-approver/$PERMISSION_REQUEST_SCRIPT_NAME",
                "timeoutSec": 300
              }
            ]
          }
        }
    """.trimIndent()

    // ----- internals -----

    private fun installScripts(port: Int) {
        val dir = scriptDir()
        dir.mkdirs()

        val pre = preToolUseScriptFile()
        pre.writeText(buildScriptContent(PRE_TOOL_USE_ENDPOINT, port))
        pre.setExecutable(true)
        logger.i { "Installed bridge script ${pre.absolutePath}" }

        val perm = permissionRequestScriptFile()
        perm.writeText(buildScriptContent(PERMISSION_REQUEST_ENDPOINT, port))
        perm.setExecutable(true)
        logger.i { "Installed bridge script ${perm.absolutePath}" }
    }

    private fun writeHookFile() {
        val file = hookFile()
        file.parentFile.mkdirs()

        val root = buildJsonObject {
            put("version", 1)
            put("hooks", buildJsonObject {
                put(HOOK_PRE_TOOL_USE_KEY, buildJsonArray { add(buildHookEntry(preToolUseScriptPath())) })
                put(HOOK_PERMISSION_REQUEST_KEY, buildJsonArray { add(buildHookEntry(permissionRequestScriptPath())) })
            })
        }
        file.writeText(json.encodeToString(JsonElement.serializer(), root))
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

    private fun hasHookEntry(hooks: JsonObject, eventKey: String, scriptPath: String): Boolean {
        val entries = hooks[eventKey]?.jsonArray ?: return false
        return entries.any { entry ->
            val bash = entry.jsonObject["bash"]?.jsonPrimitive?.content
            bash == scriptPath
        }
    }
}
