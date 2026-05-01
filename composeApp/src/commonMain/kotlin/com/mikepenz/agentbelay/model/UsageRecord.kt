package com.mikepenz.agentbelay.model

import kotlinx.datetime.Instant
import kotlinx.serialization.Serializable

/**
 * One assistant turn's worth of token-usage telemetry, parsed out of a harness
 * session file. Persisted into the `usage_records` SQLite table.
 *
 * `dedupKey` is the natural-key used to skip-on-conflict when re-scanning the
 * same file: combining harness, source file, and a per-turn ts/cumulative-total
 * marker mirrors the codeburn approach so the same turn is never billed twice.
 *
 * Token fields are normalized to Anthropic-style semantics:
 *   - [inputTokens]: tokens *not* served from cache.
 *   - [cacheReadTokens]: tokens served from a prompt cache (cheaper).
 *   - [cacheWriteTokens]: tokens that populated the prompt cache (slightly
 *     pricier than uncached input).
 *   - [outputTokens]: assistant-emitted tokens.
 *   - [reasoningTokens]: opaque reasoning tokens (Codex / o-series); folded
 *     into output tokens for cost.
 */
@Serializable
data class UsageRecord(
    val harness: Source,
    val sessionId: String,
    val timestamp: Instant,
    val model: String?,
    val inputTokens: Long,
    val outputTokens: Long,
    val cacheReadTokens: Long = 0L,
    val cacheWriteTokens: Long = 0L,
    val reasoningTokens: Long = 0L,
    val costUsd: Double? = null,
    val durationMs: Long? = null,
    val sourceFile: String,
    val sourceOffset: Long,
    val dedupKey: String,
)
