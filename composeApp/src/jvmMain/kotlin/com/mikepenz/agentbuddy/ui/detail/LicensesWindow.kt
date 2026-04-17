package com.mikepenz.agentbuddy.ui.detail

import agentbuddy.composeapp.generated.resources.Res
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
import com.mikepenz.aboutlibraries.Libs
import com.mikepenz.aboutlibraries.ui.compose.m3.LibrariesContainer
import com.mikepenz.agentbuddy.ui.theme.AgentBuddyTheme
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

    AgentBuddyTheme {
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
            Surface(
                modifier = Modifier.fillMaxSize(),
                color = MaterialTheme.colorScheme.background,
            ) {
                LibrariesContainer(
                    libraries = libraries,
                    modifier = Modifier.fillMaxSize(),
                )
            }
        }
    }
}
