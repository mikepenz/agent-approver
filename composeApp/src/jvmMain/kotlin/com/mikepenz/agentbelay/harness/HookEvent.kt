package com.mikepenz.agentbelay.harness

/**
 * Logical hook events shared across harnesses. Each harness's transport
 * maps these onto its native event names (`PermissionRequest`,
 * `permissionRequest`, `BeforeTool`, etc.) and HTTP route paths.
 *
 * Not every harness supports every event — the [HarnessTransport.endpoints]
 * map only contains entries for events the harness actually fires.
 */
enum class HookEvent {
    /** Interactive permission gate before tool execution. */
    PERMISSION_REQUEST,
    /** Programmatic pre-execution check (typically used for Protection Engine). */
    PRE_TOOL_USE,
    /** Post-execution callback; can return `updatedToolOutput` for redaction. */
    POST_TOOL_USE,
}
