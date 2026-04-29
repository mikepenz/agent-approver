package com.mikepenz.agentbelay.ui.theme

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.mikepenz.agentbelay.model.ThemeMode

/**
 * Wraps a preview in [AgentBelayTheme]. Host `isSystemInDarkMode` is unreliable in
 * headless renderers so we force the theme explicitly — dark by default since the
 * Agent Belay design system is dark-first. Pass [themeMode] = [ThemeMode.LIGHT] to
 * render the light variant.
 */
@Composable
fun PreviewScaffold(
    themeMode: ThemeMode = ThemeMode.DARK,
    background: Color? = null,
    content: @Composable () -> Unit,
) {
    AgentBelayTheme(themeMode = themeMode) {
        val bg = background ?: MaterialTheme.colorScheme.background
        Box(modifier = Modifier.fillMaxSize().background(bg)) {
            content()
        }
    }
}

/**
 * Renders [content] twice — once in dark mode, once in light mode — side-by-side
 * so a single preview PNG captures both theme variants. Used from
 * `@Preview`-annotated helpers named `PreviewFooLightDark` to broaden light-theme
 * coverage without explosion of individual preview functions.
 */
@Composable
fun PreviewLightDarkScaffold(
    content: @Composable () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxSize(),
        horizontalArrangement = Arrangement.spacedBy(0.dp),
    ) {
        Box(modifier = Modifier.weight(1f)) {
            PreviewScaffold(themeMode = ThemeMode.DARK) {
                Box(Modifier.fillMaxSize().padding(16.dp)) { content() }
            }
        }
        Box(modifier = Modifier.weight(1f)) {
            PreviewScaffold(themeMode = ThemeMode.LIGHT) {
                Box(Modifier.fillMaxSize().padding(16.dp)) { content() }
            }
        }
    }
}

/**
 * Convenience wrapper that vertically stacks light-then-dark scaffolds. Prefer
 * [PreviewLightDarkScaffold] for wide surfaces; this one is better for tall
 * list-style surfaces where horizontal splitting would crop content.
 */
@Composable
fun PreviewLightDarkStacked(
    content: @Composable () -> Unit,
) {
    Column(modifier = Modifier.fillMaxSize()) {
        Box(modifier = Modifier.weight(1f)) {
            PreviewScaffold(themeMode = ThemeMode.LIGHT) {
                Box(Modifier.fillMaxSize().padding(16.dp)) { content() }
            }
        }
        Box(modifier = Modifier.weight(1f)) {
            PreviewScaffold(themeMode = ThemeMode.DARK) {
                Box(Modifier.fillMaxSize().padding(16.dp)) { content() }
            }
        }
    }
}
