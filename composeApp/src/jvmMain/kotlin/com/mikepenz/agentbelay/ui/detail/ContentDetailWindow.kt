package com.mikepenz.agentbelay.ui.detail

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.WindowPosition
import androidx.compose.ui.window.rememberWindowState
import androidx.compose.ui.tooling.preview.Preview
import com.mikepenz.agentbelay.ui.theme.AgentBelayTheme
import com.mikepenz.agentbelay.ui.theme.PreviewScaffold
import com.mikepenz.markdown.compose.components.markdownComponents
import com.mikepenz.markdown.compose.elements.highlightedCodeBlock
import com.mikepenz.markdown.compose.elements.highlightedCodeFence
import com.mikepenz.markdown.m3.Markdown
import io.github.kdroidfilter.nucleus.window.material.MaterialDecoratedWindow
import io.github.kdroidfilter.nucleus.window.material.MaterialTitleBar

@Composable
fun ContentDetailWindow(
    title: String,
    content: String,
    onClose: () -> Unit,
) {
    val windowState = rememberWindowState(
        size = DpSize(700.dp, 600.dp),
        position = WindowPosition(Alignment.Center),
    )

    AgentBelayTheme {
        MaterialDecoratedWindow(
            onCloseRequest = onClose,
            state = windowState,
            title = title,
        ) {
            MaterialTitleBar {
                Text(
                    title,
                    modifier = Modifier.align(Alignment.CenterHorizontally),
                    color = MaterialTheme.colorScheme.onSurface,
                    style = MaterialTheme.typography.titleSmall,
                )
            }
            ContentDetailBody(content = content)
        }
    }
}

/**
 * Pure content portion of [ContentDetailWindow] — extracted so it can be
 * rendered in `@Preview` without needing a native window host. Used by both
 * the real window and the preview functions below.
 */
@Composable
fun ContentDetailBody(content: String) {
    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background,
    ) {
        Markdown(
            content = content,
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            components = markdownComponents(
                codeFence = highlightedCodeFence,
                codeBlock = highlightedCodeBlock,
            ),
        )
    }
}

// ── Previews (iter 3) ──────────────────────────────────────────────────────

private const val SAMPLE_MARKDOWN = """# Implementation Plan

Agent Belay reviews the following steps before proceeding:

1. Add data models
2. Wire the hook server
3. Persist approvals to disk

```kotlin
fun hello() = "world"
```

> Note: all side effects gated on user approval.
"""

private const val LONG_MARKDOWN = """# Detailed migration notes

This is a dense multi-paragraph document used to verify scroll, line-height and
typography rendering inside the popped-out detail window.

## Background

The migration replaces the legacy JSON persistence layer with a small SQLite
database. Historical entries are preserved by a one-time import.

## Steps

1. Open the database on first launch.
2. Import existing `history.json` rows.
3. Verify counts match.

```kotlin
class HistoryStorage(private val db: Database) {
    suspend fun insert(entry: HistoryEntry) { /* … */ }
}
```

### Caveats

- Import is idempotent but slow on first run.
- Rollback requires deleting the database file.
- Future versions will migrate in-place.

## Acceptance

- [ ] Existing history visible
- [ ] New approvals persist
- [ ] Statistics unchanged
"""

@Preview(widthDp = 700, heightDp = 600)
@Composable
private fun PreviewContentDetailBody() {
    PreviewScaffold {
        ContentDetailBody(content = SAMPLE_MARKDOWN)
    }
}

@Preview(widthDp = 700, heightDp = 600)
@Composable
private fun PreviewContentDetailBodyLong() {
    PreviewScaffold {
        ContentDetailBody(content = LONG_MARKDOWN)
    }
}

@Preview(widthDp = 700, heightDp = 600)
@Composable
private fun PreviewContentDetailBodyLight() {
    PreviewScaffold(themeMode = com.mikepenz.agentbelay.model.ThemeMode.LIGHT) {
        ContentDetailBody(content = SAMPLE_MARKDOWN)
    }
}
