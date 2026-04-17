package com.mikepenz.agentbuddy.server

import co.touchlab.kermit.Logger
import com.mikepenz.agentbuddy.capability.AgentTarget
import com.mikepenz.agentbuddy.capability.CapabilityEngine
import com.mikepenz.agentbuddy.capability.HookEvent
import io.ktor.http.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

private val logger = Logger.withTag("CapabilityRoute")

/**
 * Receives Claude Code `UserPromptSubmit` hook events and returns
 * `additionalContext` computed from enabled [CapabilityEngine] modules.
 *
 * The handler never parses the prompt body — it only needs to know the
 * event name to pick the right injection text. The response shape follows
 * Claude Code's documented hook schema:
 *
 *   {
 *     "hookSpecificOutput": {
 *       "hookEventName": "UserPromptSubmit",
 *       "additionalContext": "..."
 *     }
 *   }
 *
 * If no capability contributes context, an empty object is returned so
 * Claude Code treats the call as a no-op.
 */
fun Route.capabilityRoute(engine: CapabilityEngine) {
    post("/capability/inject") {
        // Drain the body so Netty can recycle the connection, but we don't
        // parse it — the injection is independent of the prompt content.
        runCatching { call.receiveText() }

        val text = engine.injectionFor(HookEvent.USER_PROMPT_SUBMIT, AgentTarget.CLAUDE_CODE)
        if (text.isBlank()) {
            call.respondText("{}", contentType = ContentType.Application.Json)
            return@post
        }
        val body = buildJsonObject {
            put("hookSpecificOutput", buildJsonObject {
                put("hookEventName", "UserPromptSubmit")
                put("additionalContext", text)
            })
        }.toString()
        call.respondText(body, contentType = ContentType.Application.Json)
    }

    post("/capability/session-start") {
        runCatching { call.receiveText() }

        val text = engine.injectionFor(HookEvent.SESSION_START, AgentTarget.CLAUDE_CODE)
        if (text.isBlank()) {
            call.respondText("{}", contentType = ContentType.Application.Json)
            return@post
        }
        val body = buildJsonObject {
            put("hookSpecificOutput", buildJsonObject {
                put("hookEventName", "SessionStart")
                put("additionalContext", text)
            })
        }.toString()
        call.respondText(body, contentType = ContentType.Application.Json)
    }

    post("/capability/inject-copilot") {
        runCatching { call.receiveText() }

        val parts = listOf(
            engine.injectionFor(HookEvent.USER_PROMPT_SUBMIT, AgentTarget.COPILOT_CLI),
            engine.injectionFor(HookEvent.SESSION_START, AgentTarget.COPILOT_CLI),
        ).filter { it.isNotBlank() }
        val text = parts.joinToString("\n\n")
        if (text.isBlank()) {
            call.respondText("{}", contentType = ContentType.Application.Json)
            return@post
        }
        // Copilot CLI's `sessionStart` command-type hook reads a flat
        // `{ additionalContext }` from the bridge script's stdout. We
        // intentionally do NOT bind this to `userPromptSubmitted`: in the
        // bundled Copilot runtime, that event's output parser is literally
        // `a => {}` (returns undefined), so any text we print there is
        // discarded. `sessionStart` fires once per session and its parser
        // honors `additionalContext`, which is prepended as a user message
        // for the whole session — matching "inject once, stay in effect".
        val body = buildJsonObject {
            put("additionalContext", text)
        }.toString()
        call.respondText(body, contentType = ContentType.Application.Json)
        logger.v { "Served capability injection (${text.length} chars) to Copilot CLI" }
    }
}
