package com.mikepenz.agentbelay.ui.detail

import agentbelay.composeapp.generated.resources.Res
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.WindowPosition
import androidx.compose.ui.window.rememberWindowState
import androidx.compose.ui.tooling.preview.Preview
import com.mikepenz.aboutlibraries.Libs
import com.mikepenz.aboutlibraries.ui.compose.m3.LibrariesContainer
import com.mikepenz.agentbelay.ui.theme.AgentBelayTheme
import com.mikepenz.agentbelay.ui.theme.PreviewScaffold
import io.github.kdroidfilter.nucleus.window.material.MaterialDecoratedWindow
import io.github.kdroidfilter.nucleus.window.material.MaterialTitleBar
import org.jetbrains.compose.resources.ExperimentalResourceApi

@OptIn(ExperimentalResourceApi::class)
@Composable
fun LicensesWindow(
    onClose: () -> Unit,
) {
    val windowState = rememberWindowState(
        size = DpSize(600.dp, 700.dp),
        position = WindowPosition(Alignment.Center),
    )

    val libraries by produceState<Libs?>(null) {
        value = runCatching {
            Libs.Builder()
                .withJson(Res.readBytes("files/aboutlibraries.json").decodeToString())
                .build()
        }.getOrNull()
    }

    AgentBelayTheme {
        MaterialDecoratedWindow(
            onCloseRequest = onClose,
            state = windowState,
            title = "Open Source Libraries",
        ) {
            MaterialTitleBar {
                Text(
                    "Open Source Libraries",
                    modifier = Modifier.align(Alignment.CenterHorizontally),
                    color = MaterialTheme.colorScheme.onSurface,
                    style = MaterialTheme.typography.titleSmall,
                )
            }
            LicensesBody(libraries = libraries)
        }
    }
}

/**
 * Pure content portion of [LicensesWindow] — pulled out so we can preview the
 * body without needing to mount a native window. [libraries] is nullable to
 * mirror the async loading path in the real window.
 */
@Composable
fun LicensesBody(libraries: Libs?) {
    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background,
    ) {
        if (libraries == null) {
            androidx.compose.foundation.layout.Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = "Loading licenses…",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        } else {
            LibrariesContainer(
                libraries = libraries,
                modifier = Modifier.fillMaxSize(),
            )
        }
    }
}

// ── Previews (iter 3) ──────────────────────────────────────────────────────

@OptIn(ExperimentalResourceApi::class)
@Preview(widthDp = 600, heightDp = 700)
@Composable
private fun PreviewLicensesBodyLoading() {
    PreviewScaffold {
        LicensesBody(libraries = null)
    }
}

@OptIn(ExperimentalResourceApi::class)
@Preview(widthDp = 600, heightDp = 700)
@Composable
private fun PreviewLicensesBodyLoadingLight() {
    PreviewScaffold(themeMode = com.mikepenz.agentbelay.model.ThemeMode.LIGHT) {
        LicensesBody(libraries = null)
    }
}
