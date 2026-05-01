package com.mikepenz.agentbelay.insights

import com.mikepenz.agentbelay.model.ApprovalRequest
import com.mikepenz.agentbelay.model.ApprovalResult
import com.mikepenz.agentbelay.model.Decision
import com.mikepenz.agentbelay.model.HookInput
import com.mikepenz.agentbelay.model.Source
import com.mikepenz.agentbelay.model.ToolType
import com.mikepenz.agentbelay.model.UsageRecord
import kotlin.time.Instant
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonPrimitive

/**
 * Tiny builders so detector tests stay readable. Each helper takes the bare
 * minimum to reproduce a scenario; everything else gets sane defaults.
 */
internal object InsightFixtures {

    fun turn(
        sessionId: String = "s",
        offsetMillis: Long = 0,
        input: Long = 0,
        output: Long = 0,
        cacheRead: Long = 0,
        cacheWrite: Long = 0,
        reasoning: Long = 0,
        cost: Double = 0.0,
        model: String? = "claude-sonnet-4-5",
        harness: Source = Source.CLAUDE_CODE,
        dedup: String = "k-${offsetMillis}-${input}-${output}",
    ): UsageRecord = UsageRecord(
        harness = harness,
        sessionId = sessionId,
        timestamp = Instant.fromEpochMilliseconds(START_TS + offsetMillis),
        model = model,
        inputTokens = input,
        outputTokens = output,
        cacheReadTokens = cacheRead,
        cacheWriteTokens = cacheWrite,
        reasoningTokens = reasoning,
        costUsd = cost,
        durationMs = null,
        sourceFile = "/tmp/x.jsonl",
        sourceOffset = 0L,
        dedupKey = dedup,
    )

    fun approval(
        toolName: String,
        toolInput: Map<String, JsonElement> = emptyMap(),
        sessionId: String = "s",
        decision: Decision = Decision.APPROVED,
        offsetMillis: Long = 0,
    ): ApprovalResult = ApprovalResult(
        request = ApprovalRequest(
            id = "req-${toolName}-${offsetMillis}",
            source = Source.CLAUDE_CODE,
            toolType = ToolType.DEFAULT,
            hookInput = HookInput(
                sessionId = sessionId,
                toolName = toolName,
                toolInput = toolInput,
                cwd = "/tmp/proj",
            ),
            timestamp = Instant.fromEpochMilliseconds(START_TS + offsetMillis),
            rawRequestJson = "{}",
        ),
        decision = decision,
        feedback = null,
        riskAnalysis = null,
        rawResponseJson = null,
        decidedAt = Instant.fromEpochMilliseconds(START_TS + offsetMillis + 100),
        protectionModule = null,
        protectionRule = null,
        protectionDetail = null,
        redactionHits = emptyList(),
    )

    fun bash(command: String, sessionId: String = "s", offsetMillis: Long = 0): ApprovalResult =
        approval(
            toolName = "Bash",
            toolInput = mapOf("command" to JsonPrimitive(command)),
            sessionId = sessionId,
            offsetMillis = offsetMillis,
        )

    fun read(path: String, sessionId: String = "s", offsetMillis: Long = 0): ApprovalResult =
        approval(
            toolName = "Read",
            toolInput = mapOf("file_path" to JsonPrimitive(path)),
            sessionId = sessionId,
            offsetMillis = offsetMillis,
        )

    fun edit(path: String, sessionId: String = "s", offsetMillis: Long = 0): ApprovalResult =
        approval(
            toolName = "Edit",
            toolInput = mapOf("file_path" to JsonPrimitive(path)),
            sessionId = sessionId,
            offsetMillis = offsetMillis,
        )

    fun task(sessionId: String = "s", offsetMillis: Long = 0): ApprovalResult =
        approval("Task", sessionId = sessionId, offsetMillis = offsetMillis)

    fun session(
        turns: List<UsageRecord>,
        history: List<ApprovalResult> = emptyList(),
        cwd: String? = "/tmp/proj",
        sessionId: String = "s",
        harness: Source = Source.CLAUDE_CODE,
        model: String? = "claude-sonnet-4-5",
    ) = SessionMetrics(
        harness = harness,
        sessionId = sessionId,
        cwd = cwd,
        model = model,
        turns = turns,
        history = history,
    )

    private const val START_TS = 1_745_001_600_000L // 2026-04-30T10:00:00Z-ish
}
