package com.mikepenz.agentbelay.ui.insights

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mikepenz.agentbelay.insights.EstimatedSavings
import com.mikepenz.agentbelay.insights.Insight
import com.mikepenz.agentbelay.insights.InsightKind
import com.mikepenz.agentbelay.insights.InsightSeverity
import com.mikepenz.agentbelay.insights.ai.AiSuggestion
import com.mikepenz.agentbelay.model.Source
import com.mikepenz.agentbelay.storage.SessionSummary
import com.mikepenz.agentbelay.ui.components.AgentBelayCard
import com.mikepenz.agentbelay.ui.components.HorizontalHairline
import com.mikepenz.agentbelay.ui.components.sourceAccentColor
import com.mikepenz.agentbelay.ui.components.sourceDisplayName
import com.mikepenz.agentbelay.ui.theme.AccentEmerald
import com.mikepenz.agentbelay.ui.theme.AgentBelayColors
import com.mikepenz.agentbelay.ui.theme.DangerRed
import com.mikepenz.agentbelay.ui.theme.PreviewScaffold
import com.mikepenz.agentbelay.ui.theme.WarnYellow
import com.mikepenz.agentbelay.ui.theme.InfoBlue

/**
 * Stateless Insights screen. Wide layout: session list left rail, insight
 * cards on the right. Compact: stacked, sessions on top.
 */
@Composable
fun InsightsScreen(
    state: InsightsUiState,
    onSelectSession: (String) -> Unit,
    onRequestAi: (Insight) -> Unit,
    onSortChange: (SessionSort) -> Unit = {},
    onHarnessFilterChange: (Set<Source>?) -> Unit = {},
    modifier: Modifier = Modifier,
) {
    BoxWithConstraints(modifier = modifier.fillMaxSize().background(AgentBelayColors.background)) {
        val isCompact = maxWidth < 800.dp

        Column(modifier = Modifier.fillMaxSize()) {
            InsightsHeader()

            when {
                state.loadingSessions && state.sessions.isEmpty() ->
                    LoadingState("Scanning sessions for token-burn patterns…")

                state.allSessions.isEmpty() ->
                    EmptyState()

                isCompact -> {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .verticalScroll(rememberScrollState())
                            .padding(start = 28.dp, end = 28.dp, top = 18.dp, bottom = 28.dp),
                        verticalArrangement = Arrangement.spacedBy(14.dp),
                    ) {
                        SessionsToolbar(
                            sortBy = state.sortBy,
                            onSortChange = onSortChange,
                            harnessFilter = state.harnessFilter,
                            availableHarnesses = state.availableHarnesses,
                            onHarnessFilterChange = onHarnessFilterChange,
                        )
                        SessionList(
                            sessions = state.sessions,
                            selected = state.selectedSessionId,
                            onSelect = onSelectSession,
                            fillHeight = false,
                            modifier = Modifier.fillMaxWidth(),
                        )
                        InsightsList(state, onRequestAi)
                    }
                }

                else -> Row(
                    modifier = Modifier.fillMaxSize().padding(horizontal = 28.dp, vertical = 18.dp),
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                ) {
                    Column(
                        modifier = Modifier.width(280.dp).fillMaxHeight(),
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        SessionsToolbar(
                            sortBy = state.sortBy,
                            onSortChange = onSortChange,
                            harnessFilter = state.harnessFilter,
                            availableHarnesses = state.availableHarnesses,
                            onHarnessFilterChange = onHarnessFilterChange,
                        )
                        SessionList(
                            sessions = state.sessions,
                            selected = state.selectedSessionId,
                            onSelect = onSelectSession,
                            fillHeight = true,
                            modifier = Modifier.fillMaxWidth().weight(1f),
                        )
                    }
                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight()
                            .verticalScroll(rememberScrollState()),
                        verticalArrangement = Arrangement.spacedBy(14.dp),
                    ) {
                        InsightsList(state, onRequestAi)
                    }
                }
            }
        }
    }
}

// ── Header ──────────────────────────────────────────────────────────────────

@Composable
private fun InsightsHeader() {
    Column(
        modifier = Modifier.fillMaxWidth().padding(start = 28.dp, end = 28.dp, top = 18.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(
                text = "Insights",
                color = AgentBelayColors.inkPrimary,
                fontSize = 20.sp,
                fontWeight = FontWeight.SemiBold,
                letterSpacing = (-0.4).sp,
            )
            ExperimentalBadge()
        }
        Spacer(Modifier.height(4.dp))
        Text(
            text = "AI-guided optimizations to cut token usage on connected harnesses",
            color = AgentBelayColors.inkTertiary,
            fontSize = 12.sp,
        )
        Spacer(Modifier.height(14.dp))
        HorizontalHairline()
    }
}

@Composable
private fun ExperimentalBadge() {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(4.dp))
            .background(WarnYellow.copy(alpha = 0.16f))
            .border(1.dp, WarnYellow.copy(alpha = 0.45f), RoundedCornerShape(4.dp))
            .padding(horizontal = 7.dp, vertical = 2.dp),
    ) {
        Text(
            text = "EXPERIMENTAL",
            color = WarnYellow,
            fontSize = 9.5.sp,
            fontWeight = FontWeight.SemiBold,
            letterSpacing = 0.6.sp,
        )
    }
}

// ── Session list ────────────────────────────────────────────────────────────

@Composable
private fun SessionList(
    sessions: List<SessionSummary>,
    selected: String?,
    onSelect: (String) -> Unit,
    modifier: Modifier = Modifier,
    fillHeight: Boolean = false,
) {
    // The card fills its parent in wide mode (so the bottom edge tracks the
    // parent column) and wraps to content in compact mode (so it doesn't push
    // the insights list off the screen). Empty trailing space inside the card
    // was the original "unnecessary spacing on the bottom" complaint.
    val cardMod = if (fillHeight) modifier.fillMaxHeight() else modifier
    AgentBelayCard(modifier = cardMod) {
        Column(modifier = if (fillHeight) Modifier.fillMaxSize() else Modifier) {
            Box(modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 10.dp)) {
                Text(
                    text = "SESSIONS",
                    color = AgentBelayColors.inkMuted,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.SemiBold,
                    letterSpacing = 0.5.sp,
                )
            }
            HorizontalHairline()
            // In compact mode the SessionList sits inside a parent
            // verticalScroll Column — a bare LazyColumn there would receive
            // infinite max height and crash at measure time. Render rows
            // inline as a regular Column in that case; only use the
            // virtualised LazyColumn when we actually have a bounded height
            // (wide-rail layout).
            if (fillHeight) {
                LazyColumn(modifier = Modifier.fillMaxWidth().weight(1f)) {
                    items(sessions) { s ->
                        SessionRow(s, isSelected = s.sessionId == selected, onClick = { onSelect(s.sessionId) })
                        HorizontalHairline()
                    }
                }
            } else {
                Column(modifier = Modifier.fillMaxWidth()) {
                    sessions.forEach { s ->
                        SessionRow(s, isSelected = s.sessionId == selected, onClick = { onSelect(s.sessionId) })
                        HorizontalHairline()
                    }
                }
            }
            if (sessions.isEmpty()) {
                Box(modifier = Modifier.fillMaxWidth().padding(20.dp), contentAlignment = Alignment.Center) {
                    Text(
                        text = "No sessions match this filter.",
                        color = AgentBelayColors.inkMuted,
                        fontSize = 12.sp,
                    )
                }
            }
        }
    }
}

/**
 * Sort + harness-filter toolbar pinned above the session list. Only renders
 * harnesses that actually appear in [availableHarnesses] so we never show
 * dead options.
 */
/**
 * Sort + filter toolbar above the session list. Delegates to
 * [com.mikepenz.agentbelay.ui.components.SortAndFilterRow] so it stays
 * visually identical to the History screen's filter row. FlowRow inside
 * the shared component handles wrapping when the rail is too narrow to
 * fit both controls on one line.
 */
@Composable
private fun SessionsToolbar(
    sortBy: SessionSort,
    onSortChange: (SessionSort) -> Unit,
    harnessFilter: Set<Source>?,
    availableHarnesses: List<Source>,
    onHarnessFilterChange: (Set<Source>?) -> Unit,
) {
    com.mikepenz.agentbelay.ui.components.SortAndFilterRow(
        sortOptions = SessionSort.entries.map { it to it.label },
        sortSelected = sortBy,
        onSortChange = onSortChange,
        harnessOptions = availableHarnesses.map { it to sourceDisplayName(it) },
        harnessSelected = harnessFilter,
        onHarnessChange = onHarnessFilterChange,
        harnessLeadingDot = ::sourceAccentColor,
    )
}

@Composable
private fun SessionRow(s: SessionSummary, isSelected: Boolean, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(if (isSelected) AgentBelayColors.surface2 else Color.Transparent)
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(modifier = Modifier.size(7.dp).clip(CircleShape).background(sourceAccentColor(s.harness)))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = sourceDisplayName(s.harness),
                color = AgentBelayColors.inkPrimary,
                fontSize = 12.5.sp,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = "${s.model ?: "—"} · ${s.turnCount} turns",
                color = AgentBelayColors.inkMuted,
                fontSize = 10.5.sp,
                fontFamily = FontFamily.Monospace,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Text(
            text = "$" + "%.2f".format(s.totalCostUsd),
            color = AgentBelayColors.inkSecondary,
            fontSize = 12.sp,
            fontWeight = FontWeight.Medium,
        )
    }
}

// ── Insight cards ───────────────────────────────────────────────────────────

@Composable
private fun InsightsList(state: InsightsUiState, onRequestAi: (Insight) -> Unit) {
    if (state.loadingInsights) {
        LoadingState("Analyzing session…")
        return
    }
    if (state.insights.isEmpty()) {
        AgentBelayCard(modifier = Modifier.fillMaxWidth()) {
            Box(modifier = Modifier.fillMaxWidth().padding(28.dp), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = "No optimization opportunities found",
                        color = AgentBelayColors.inkSecondary,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Medium,
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = "This session looks healthy — cache hits, model choice, and tool patterns all check out.",
                        color = AgentBelayColors.inkMuted,
                        fontSize = 12.sp,
                        textAlign = TextAlign.Center,
                    )
                }
            }
        }
        return
    }
    state.insights.forEach { insight ->
        val key = "${insight.sessionId}:${insight.kind.name}"
        InsightCard(
            insight = insight,
            aiSuggestion = state.aiSuggestions[key],
            aiInflight = key in state.aiInflight,
            aiError = state.aiErrors[key],
            aiEnabled = state.aiEnabled,
            onRequestAi = { onRequestAi(insight) },
        )
    }
}

@Composable
private fun InsightCard(
    insight: Insight,
    aiSuggestion: AiSuggestion?,
    aiInflight: Boolean,
    aiError: String?,
    aiEnabled: Boolean,
    onRequestAi: () -> Unit,
) {
    AgentBelayCard(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            // Top row keeps severity / title / savings together so the eye
            // can scan headlines vertically without the description wrapping
            // around chips. The description hangs below at full width.
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                SeverityChip(insight.severity)
                Text(
                    text = insight.title,
                    color = AgentBelayColors.inkPrimary,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold,
                    letterSpacing = (-0.1).sp,
                    modifier = Modifier.weight(1f),
                )
                SavingsChip(insight.savings)
            }
            Text(
                text = insight.description,
                color = AgentBelayColors.inkSecondary,
                fontSize = 12.5.sp,
                modifier = Modifier.fillMaxWidth(),
            )
            if (insight.evidence.isNotEmpty()) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(6.dp))
                        .background(AgentBelayColors.surface2)
                        .padding(horizontal = 12.dp, vertical = 10.dp),
                    verticalArrangement = Arrangement.spacedBy(3.dp),
                ) {
                    insight.evidence.take(6).forEach { line ->
                        Text(
                            text = "· $line",
                            color = AgentBelayColors.inkTertiary,
                            fontSize = 11.5.sp,
                            fontFamily = FontFamily.Monospace,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
            }

            if (insight.aiEligible && aiEnabled) {
                AiSuggestionBlock(
                    suggestion = aiSuggestion,
                    inflight = aiInflight,
                    error = aiError,
                    onRequest = onRequestAi,
                )
            }
        }
    }
}

@Composable
private fun SeverityChip(severity: InsightSeverity) {
    val (label, color) = when (severity) {
        InsightSeverity.CRITICAL -> "CRITICAL" to DangerRed
        InsightSeverity.WARNING -> "WARN" to WarnYellow
        InsightSeverity.INFO -> "INFO" to InfoBlue
    }
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(4.dp))
            .background(color.copy(alpha = 0.15f))
            .border(1.dp, color.copy(alpha = 0.4f), RoundedCornerShape(4.dp))
            .padding(horizontal = 7.dp, vertical = 3.dp),
    ) {
        Text(
            text = label,
            color = color,
            fontSize = 9.5.sp,
            fontWeight = FontWeight.SemiBold,
            letterSpacing = 0.6.sp,
        )
    }
}

@Composable
private fun SavingsChip(savings: EstimatedSavings) {
    val text = when {
        savings.usd != null && savings.usd >= 0.01 -> "save ~$" + "%.2f".format(savings.usd)
        savings.tokens != null && savings.tokens > 0 -> "save ~${formatTokens(savings.tokens)}"
        else -> return
    }
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(4.dp))
            .background(AccentEmerald.copy(alpha = 0.14f))
            .border(1.dp, AccentEmerald.copy(alpha = 0.35f), RoundedCornerShape(4.dp))
            .padding(horizontal = 8.dp, vertical = 4.dp),
    ) {
        Text(
            text = text,
            color = AccentEmerald,
            fontSize = 11.sp,
            fontWeight = FontWeight.SemiBold,
        )
    }
}

@Composable
private fun AiSuggestionBlock(
    suggestion: AiSuggestion?,
    inflight: Boolean,
    error: String?,
    onRequest: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(6.dp))
            .background(AgentBelayColors.surface2)
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(
                text = "AI suggestion",
                color = AgentBelayColors.inkSecondary,
                fontSize = 11.sp,
                fontWeight = FontWeight.SemiBold,
                letterSpacing = 0.4.sp,
            )
            Spacer(Modifier.weight(1f))
            when {
                inflight -> Text(
                    text = "Thinking…",
                    color = AccentEmerald,
                    fontSize = 11.sp,
                )
                suggestion == null -> AiActionButton(label = "Get AI suggestion", onClick = onRequest)
                else -> AiActionButton(label = "Re-run", onClick = onRequest)
            }
        }
        AnimatedVisibility(visible = error != null) {
            Text(
                text = error ?: "",
                color = DangerRed,
                fontSize = 11.5.sp,
            )
        }
        AnimatedVisibility(visible = suggestion != null) {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    text = suggestion?.title.orEmpty(),
                    color = AgentBelayColors.inkPrimary,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Medium,
                )
                Text(
                    text = suggestion?.body.orEmpty(),
                    color = AgentBelayColors.inkSecondary,
                    fontSize = 12.sp,
                )
                val action = suggestion?.action
                if (!action.isNullOrBlank()) {
                    Text(
                        text = "→ $action",
                        color = AccentEmerald,
                        fontSize = 11.5.sp,
                        fontFamily = FontFamily.Monospace,
                    )
                }
            }
        }
    }
}

@Composable
private fun AiActionButton(label: String, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(5.dp))
            .background(AccentEmerald.copy(alpha = 0.18f))
            .border(1.dp, AccentEmerald.copy(alpha = 0.45f), RoundedCornerShape(5.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 5.dp),
    ) {
        Text(text = label, color = AccentEmerald, fontSize = 11.5.sp, fontWeight = FontWeight.Medium)
    }
}

// ── States ──────────────────────────────────────────────────────────────────

@Composable
private fun LoadingState(label: String) {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                repeat(3) { Box(modifier = Modifier.size(7.dp).clip(CircleShape).background(AccentEmerald.copy(alpha = 0.6f))) }
            }
            Text(label, color = AgentBelayColors.inkSecondary, fontSize = 13.sp)
        }
    }
}

@Composable
private fun EmptyState() {
    Box(modifier = Modifier.fillMaxSize().padding(28.dp), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = "No sessions to analyze yet",
                color = AgentBelayColors.inkSecondary,
                fontSize = 14.sp,
                fontWeight = FontWeight.Medium,
            )
            Spacer(Modifier.height(6.dp))
            Text(
                text = "Once usage tracking has captured at least 5 turns from a session, optimizations show up here.",
                color = AgentBelayColors.inkMuted,
                fontSize = 12.sp,
                textAlign = TextAlign.Center,
                modifier = Modifier.widthIn(max = 380.dp),
            )
        }
    }
}

// ── Helpers ─────────────────────────────────────────────────────────────────

private fun formatTokens(n: Long): String = when {
    n >= 1_000_000 -> "${"%.2f".format(n / 1_000_000.0)}M"
    n >= 1_000 -> "${(n / 1_000.0).toInt()}k"
    else -> n.toString()
}

// ── Previews ────────────────────────────────────────────────────────────────

private fun sampleSessions() = listOf(
    SessionSummary(
        harness = Source.CLAUDE_CODE,
        sessionId = "sess-1",
        model = "claude-sonnet-4-5",
        firstTsMillis = 0,
        lastTsMillis = 60_000,
        turnCount = 42,
        totalInputTokens = 280_000,
        totalOutputTokens = 38_000,
        totalCacheReadTokens = 1_120_000,
        totalCostUsd = 9.84,
    ),
    SessionSummary(
        harness = Source.CODEX,
        sessionId = "sess-2",
        model = "gpt-5-codex",
        firstTsMillis = 0,
        lastTsMillis = 60_000,
        turnCount = 18,
        totalInputTokens = 60_000,
        totalOutputTokens = 12_000,
        totalCacheReadTokens = 0,
        totalCostUsd = 1.42,
    ),
    SessionSummary(
        harness = Source.COPILOT,
        sessionId = "sess-3",
        model = "gpt-5",
        firstTsMillis = 0,
        lastTsMillis = 60_000,
        turnCount = 9,
        totalInputTokens = 12_000,
        totalOutputTokens = 4_000,
        totalCacheReadTokens = 0,
        totalCostUsd = 0.32,
    ),
)

private fun sampleInsights(): List<Insight> = listOf(
    Insight(
        kind = InsightKind.COLD_CACHE_THRASH,
        severity = InsightSeverity.CRITICAL,
        title = "Prompt cache is missing — only 8% of inputs hit cache",
        description = "Healthy Claude Code sessions cache 70–90% of inputs. Yours is at 8%, which usually means something near the top of the system prompt is changing between turns.",
        evidence = listOf(
            "Cache-read tokens: 1,120,000",
            "Uncached input tokens: 280,000",
            "Across 42 turns",
        ),
        savings = EstimatedSavings(tokens = 240_000, usd = 0.72),
        harness = Source.CLAUDE_CODE,
        sessionId = "sess-1",
        aiEligible = true,
    ),
    Insight(
        kind = InsightKind.MCP_SCHEMA_BLOAT,
        severity = InsightSeverity.WARNING,
        title = "First-turn prompt is heavy (~30k tokens) with little MCP use",
        description = "Loaded MCP servers inject their full tool schemas into every session's system prompt. The first turn used 30,000 tokens but only 1 MCP tool actually fired.",
        evidence = listOf(
            "First-turn input: 30,000 tokens",
            "MCP tools invoked: mcp__github__create_issue",
            "Across 42 turns",
        ),
        savings = EstimatedSavings(tokens = 25_000 * 42, usd = 0.50),
        harness = Source.CLAUDE_CODE,
        sessionId = "sess-1",
    ),
    Insight(
        kind = InsightKind.SUBAGENT_OVERSPAWN,
        severity = InsightSeverity.INFO,
        title = "4 Task subagents spawned",
        description = "Each Task call bootstraps a fresh ~20k-token context. For short lookups, inline `Grep` / `Read` is dramatically cheaper.",
        evidence = listOf(
            "Task invocations: 4",
            "Approx. bootstrap overhead: ~80k tokens",
        ),
        savings = EstimatedSavings(tokens = 80_000, usd = 0.24),
        harness = Source.CLAUDE_CODE,
        sessionId = "sess-1",
    ),
)

@Preview(widthDp = 1200, heightDp = 900)
@Composable
private fun PreviewInsightsScreen() {
    PreviewScaffold {
        InsightsScreen(
            state = InsightsUiState(
                allSessions = sampleSessions(),
                sessions = sampleSessions(),
                selectedSessionId = "sess-1",
                insights = sampleInsights(),
                loadingSessions = false,
                aiEnabled = true,
            ),
            onSelectSession = {},
            onRequestAi = {},
        )
    }
}

@Preview(widthDp = 1200, heightDp = 900)
@Composable
private fun PreviewInsightsScreenWithAiSuggestion() {
    val key = "sess-1:COLD_CACHE_THRASH"
    val suggestion = AiSuggestion(
        title = "Trim the 'Today is …' line at the top of CLAUDE.md",
        body = "Your project CLAUDE.md begins with a date stamp that invalidates the system-prompt prefix on every turn. Move it under '## Context' or remove it entirely; cache-read should jump back to ~80% within a few turns.",
        action = "rg -n '^Today is' CLAUDE.md && remove the line",
        rawText = "",
    )
    PreviewScaffold {
        InsightsScreen(
            state = InsightsUiState(
                allSessions = sampleSessions(),
                sessions = sampleSessions(),
                selectedSessionId = "sess-1",
                insights = sampleInsights(),
                loadingSessions = false,
                aiEnabled = true,
                aiSuggestions = mapOf(key to suggestion),
            ),
            onSelectSession = {},
            onRequestAi = {},
        )
    }
}

@Preview(widthDp = 1200, heightDp = 900)
@Composable
private fun PreviewInsightsScreenAiInflight() {
    val key = "sess-1:COLD_CACHE_THRASH"
    PreviewScaffold {
        InsightsScreen(
            state = InsightsUiState(
                allSessions = sampleSessions(),
                sessions = sampleSessions(),
                selectedSessionId = "sess-1",
                insights = sampleInsights(),
                loadingSessions = false,
                aiEnabled = true,
                aiInflight = setOf(key),
            ),
            onSelectSession = {},
            onRequestAi = {},
        )
    }
}

@Preview(widthDp = 720, heightDp = 1200)
@Composable
private fun PreviewInsightsScreenCompact() {
    PreviewScaffold {
        InsightsScreen(
            state = InsightsUiState(
                allSessions = sampleSessions(),
                sessions = sampleSessions(),
                selectedSessionId = "sess-1",
                insights = sampleInsights(),
                loadingSessions = false,
                aiEnabled = false,
            ),
            onSelectSession = {},
            onRequestAi = {},
        )
    }
}

@Preview(widthDp = 1200, heightDp = 600)
@Composable
private fun PreviewInsightsScreenEmpty() {
    PreviewScaffold {
        InsightsScreen(
            state = InsightsUiState(loadingSessions = false),
            onSelectSession = {},
            onRequestAi = {},
        )
    }
}

@Preview(widthDp = 1200, heightDp = 600)
@Composable
private fun PreviewInsightsScreenLoading() {
    PreviewScaffold {
        InsightsScreen(
            state = InsightsUiState(loadingSessions = true),
            onSelectSession = {},
            onRequestAi = {},
        )
    }
}

@Preview(widthDp = 1200, heightDp = 600)
@Composable
private fun PreviewInsightsScreenHealthySession() {
    PreviewScaffold {
        InsightsScreen(
            state = InsightsUiState(
                allSessions = sampleSessions(),
                sessions = sampleSessions(),
                selectedSessionId = "sess-3",
                insights = emptyList(),
                loadingSessions = false,
            ),
            onSelectSession = {},
            onRequestAi = {},
        )
    }
}

@Preview(widthDp = 1200, heightDp = 900)
@Composable
private fun PreviewInsightsScreenSortedByCost() {
    PreviewScaffold {
        InsightsScreen(
            state = InsightsUiState(
                allSessions = sampleSessions(),
                sessions = sampleSessions().sortedByDescending { it.totalCostUsd },
                selectedSessionId = "sess-1",
                insights = sampleInsights(),
                loadingSessions = false,
                aiEnabled = true,
                sortBy = SessionSort.Cost,
            ),
            onSelectSession = {},
            onRequestAi = {},
        )
    }
}

@Preview(widthDp = 1200, heightDp = 900)
@Composable
private fun PreviewInsightsScreenHarnessFiltered() {
    PreviewScaffold {
        InsightsScreen(
            state = InsightsUiState(
                allSessions = sampleSessions(),
                sessions = sampleSessions().filter { it.harness == Source.CODEX },
                selectedSessionId = "sess-2",
                insights = emptyList(),
                loadingSessions = false,
                aiEnabled = true,
                harnessFilter = setOf(Source.CODEX),
            ),
            onSelectSession = {},
            onRequestAi = {},
        )
    }
}

@Preview(widthDp = 460, heightDp = 80)
@Composable
private fun PreviewSessionsToolbar() {
    PreviewScaffold {
        Box(modifier = Modifier.padding(14.dp)) {
            SessionsToolbar(
                sortBy = SessionSort.Recent,
                onSortChange = {},
                harnessFilter = null,
                availableHarnesses = listOf(Source.CLAUDE_CODE, Source.CODEX, Source.COPILOT),
                onHarnessFilterChange = {},
            )
        }
    }
}

@Preview(widthDp = 460, heightDp = 80)
@Composable
private fun PreviewSessionsToolbarFiltered() {
    PreviewScaffold {
        Box(modifier = Modifier.padding(14.dp)) {
            SessionsToolbar(
                sortBy = SessionSort.Cost,
                onSortChange = {},
                harnessFilter = setOf(Source.CLAUDE_CODE, Source.CODEX),
                availableHarnesses = listOf(Source.CLAUDE_CODE, Source.CODEX, Source.COPILOT),
                onHarnessFilterChange = {},
            )
        }
    }
}

@Preview(widthDp = 320, heightDp = 280)
@Composable
private fun PreviewMultiSelectDropdownOpen() {
    // Renders the dropdown in its expanded state so we can eyeball the
    // checkmarks, harness color dots, and All-row alignment in both themes.
    PreviewScaffold {
        Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            com.mikepenz.agentbelay.ui.components.MultiSelectDropdown(
                options = listOf(
                    Source.CLAUDE_CODE to "Claude Code",
                    Source.CODEX to "Codex",
                    Source.COPILOT to "GitHub Copilot",
                    Source.OPENCODE to "OpenCode",
                ),
                selected = setOf(Source.CLAUDE_CODE, Source.CODEX),
                onChange = {},
                allLabel = "All harnesses",
                leadingDot = ::sourceAccentColor,
                initiallyOpen = true,
            )
        }
    }
}

@Preview(widthDp = 320, heightDp = 280)
@Composable
private fun PreviewMultiSelectDropdownLight() {
    PreviewScaffold(themeMode = com.mikepenz.agentbelay.model.ThemeMode.LIGHT) {
        Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            com.mikepenz.agentbelay.ui.components.MultiSelectDropdown(
                options = listOf(
                    Source.CLAUDE_CODE to "Claude Code",
                    Source.CODEX to "Codex",
                    Source.COPILOT to "GitHub Copilot",
                ),
                selected = null,
                onChange = {},
                allLabel = "All harnesses",
                leadingDot = ::sourceAccentColor,
                initiallyOpen = true,
            )
        }
    }
}

@Preview(widthDp = 1200, heightDp = 900)
@Composable
private fun PreviewInsightsScreenLight() {
    PreviewScaffold(themeMode = com.mikepenz.agentbelay.model.ThemeMode.LIGHT) {
        InsightsScreen(
            state = InsightsUiState(
                allSessions = sampleSessions(),
                sessions = sampleSessions(),
                selectedSessionId = "sess-1",
                insights = sampleInsights(),
                loadingSessions = false,
                aiEnabled = true,
            ),
            onSelectSession = {},
            onRequestAi = {},
        )
    }
}
