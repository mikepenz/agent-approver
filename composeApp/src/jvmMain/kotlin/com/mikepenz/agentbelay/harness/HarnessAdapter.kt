package com.mikepenz.agentbelay.harness

import com.mikepenz.agentbelay.model.ApprovalRequest
import com.mikepenz.agentbelay.model.PermissionSuggestion
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject

/**
 * Owns one harness's wire envelope on both ends:
 *
 *  - Parses incoming request JSON into the canonical [ApprovalRequest].
 *  - Builds outgoing response JSON in the harness's native shape.
 *
 * Each harness has its own envelope conventions (Claude Code wraps under
 * `hookSpecificOutput.decision.behavior`, Copilot CLI emits a flat
 * `{permissionDecision, modifiedArgs}` single-line JSON, Cursor uses
 * `{decision, modifiedInput}`, …) — implementations encapsulate those
 * differences so callers (route handlers, redaction wiring) work in
 * envelope-agnostic terms.
 *
 * Response builders return [String] (already-serialized JSON) rather than
 * [JsonElement] because some harnesses (Copilot CLI) require the response
 * on a single line — emitting through the implementation lets each adapter
 * pick its serialization policy without leaking that detail upward.
 */
interface HarnessAdapter {
    /** Parses a `PermissionRequest`-style payload (interactive approval). */
    fun parsePermissionRequest(rawJson: String): ApprovalRequest?

    /** Parses a `PreToolUse`-style payload (Protection Engine pre-check). */
    fun parsePreToolUse(rawJson: String): ApprovalRequest?

    /** Allow response with optional rewritten tool args. */
    fun buildPermissionAllowResponse(
        request: ApprovalRequest,
        updatedInput: Map<String, JsonElement>?,
    ): String

    /** Allow response that asks the harness to persist a list of permission suggestions. */
    fun buildPermissionAlwaysAllowResponse(
        request: ApprovalRequest,
        suggestions: List<PermissionSuggestion>,
    ): String

    /** Deny response with a user-facing reason. */
    fun buildPermissionDenyResponse(
        request: ApprovalRequest,
        message: String,
    ): String

    /** Pre-tool-use allow (Protection Engine pass). */
    fun buildPreToolUseAllowResponse(): String

    /** Pre-tool-use deny (Protection Engine block). */
    fun buildPreToolUseDenyResponse(reason: String): String

    /**
     * Post-tool-use response that replaces the tool's output. The
     * [updatedOutput] shape must match the original tool's response
     * schema or the harness will reject it (e.g. Bash expects
     * `{stdout, stderr, interrupted, isImage}`).
     *
     * Returns null when the harness does not support output redaction
     * (capability flag off) — callers should pass-through the original
     * response in that case.
     */
    fun buildPostToolUseRedactedResponse(updatedOutput: JsonObject): String?
}
