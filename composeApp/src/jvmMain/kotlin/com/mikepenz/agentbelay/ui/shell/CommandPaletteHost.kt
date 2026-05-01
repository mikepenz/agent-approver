package com.mikepenz.agentbelay.ui.shell

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.key.*
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mikepenz.agentbelay.ui.AppTab
import com.mikepenz.agentbelay.ui.icons.*
import com.mikepenz.agentbelay.ui.theme.*

@Composable
fun CommandPaletteHost(
    controller: CommandPaletteController,
    onNavigate: (AppTab) -> Unit,
    onShowLicenses: () -> Unit,
) {
    if (!controller.isOpen) return

    val focusRequester = remember { FocusRequester() }
    var searchQuery by remember { mutableStateOf("") }
    var selectedIndex by remember { mutableStateOf(0) }
    val listState = rememberLazyListState()

    val commands = remember(searchQuery) {
        listOf(
            CommandItem("Go to Approvals", LucideInbox, "Jump to pending tool requests") { onNavigate(AppTab.Approvals) },
            CommandItem("Go to History", LucideHistory, "View past tool decisions") { onNavigate(AppTab.History) },
            CommandItem("Go to Stats", LucideChart, "View usage statistics") { onNavigate(AppTab.Statistics) },
            CommandItem("Go to Usage", LucideZap, "Token usage, cost & performance") { onNavigate(AppTab.Usage) },
            CommandItem("Go to Settings", LucideGear, "Configure app and integrations") { onNavigate(AppTab.Settings) },
            CommandItem("Show Licenses", LucideShield, "View open source licenses") { onShowLicenses() },
        ).filter { it.label.contains(searchQuery, ignoreCase = true) }
    }

    LaunchedEffect(searchQuery) {
        selectedIndex = 0
    }

    LaunchedEffect(selectedIndex, commands.size) {
        if (commands.isNotEmpty() && selectedIndex in commands.indices) {
            listState.animateScrollToItem(selectedIndex)
        }
    }

    val inPreview = androidx.compose.ui.platform.LocalInspectionMode.current
    LaunchedEffect(Unit) {
        if (!inPreview) {
            // Wait one frame so the BasicTextField's focus node is attached
            // before requesting focus — otherwise requestFocus() silently
            // no-ops and the user has to click the input before ↑/↓/Enter
            // start working.
            withFrameNanos { }
            focusRequester.requestFocus()
        }
    }

    val keyHandler: (KeyEvent) -> Boolean = handler@{ event ->
        if (event.type != KeyEventType.KeyDown) return@handler false
        when (event.key) {
            Key.Escape -> {
                controller.close(); true
            }
            Key.DirectionDown -> {
                if (commands.isNotEmpty()) {
                    selectedIndex = (selectedIndex + 1) % commands.size
                }
                true
            }
            Key.DirectionUp -> {
                if (commands.isNotEmpty()) {
                    selectedIndex = (selectedIndex - 1 + commands.size) % commands.size
                }
                true
            }
            Key.Enter, Key.NumPadEnter -> {
                if (commands.isNotEmpty()) {
                    commands[selectedIndex].action()
                    controller.close()
                }
                true
            }
            else -> false
        }
    }

    BoxWithConstraints(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.4f))
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null
            ) { controller.close() },
        contentAlignment = Alignment.Center
    ) {
        val paletteWidth = maxWidth.times(0.6f).coerceIn(440.dp, 640.dp)
        Column(
            modifier = Modifier
                .width(paletteWidth)
                .clip(RoundedCornerShape(12.dp))
                .background(AgentBelayColors.surface)
                .clickable(enabled = false) {} // Prevent click-through to background
        ) {
            // Search Header
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Icon(
                    imageVector = LucideSearch,
                    contentDescription = null,
                    tint = AgentBelayColors.inkTertiary,
                    modifier = Modifier.size(18.dp)
                )
                BasicTextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    modifier = Modifier
                        .weight(1f)
                        .focusRequester(focusRequester)
                        .onPreviewKeyEvent(keyHandler),
                    textStyle = TextStyle(
                        color = AgentBelayColors.inkPrimary,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Normal
                    ),
                    cursorBrush = SolidColor(AccentEmerald),
                    decorationBox = { innerTextField ->
                        if (searchQuery.isEmpty()) {
                            Text(
                                "Type a command or search…",
                                color = AgentBelayColors.inkSubtle,
                                fontSize = 16.sp
                            )
                        }
                        innerTextField()
                    },
                    singleLine = true
                )
            }

            Divider(color = MaterialTheme.colorScheme.outline, thickness = 1.dp)

            // Results
            Box(modifier = Modifier.heightIn(max = 300.dp)) {
                if (commands.isEmpty()) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(32.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text("No commands found", color = AgentBelayColors.inkSubtle, fontSize = 14.sp)
                    }
                } else {
                    LazyColumn(state = listState) {
                        itemsIndexed(commands) { index, item ->
                            CommandRow(
                                item = item,
                                isSelected = index == selectedIndex,
                                onClick = {
                                    item.action()
                                    controller.close()
                                }
                            )
                        }
                    }
                }
            }

            // Footer
            Divider(color = MaterialTheme.colorScheme.outline, thickness = 1.dp)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 10.dp),
                horizontalArrangement = Arrangement.spacedBy(16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                FooterHint("↑↓", "to navigate")
                FooterHint("↵", "to select")
                FooterHint("esc", "to dismiss")
            }
        }
    }
}

@Composable
private fun CommandRow(
    item: CommandItem,
    isSelected: Boolean,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(48.dp)
            .background(if (isSelected) AgentBelayColors.surface2 else Color.Transparent)
            .clickable { onClick() }
            .padding(horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Icon(
            imageVector = item.icon,
            contentDescription = null,
            tint = if (isSelected) AccentEmerald else AgentBelayColors.inkTertiary,
            modifier = Modifier.size(18.dp)
        )
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = item.label,
                color = if (isSelected) AgentBelayColors.inkPrimary else AgentBelayColors.inkSecondary,
                fontSize = 14.sp,
                fontWeight = FontWeight.Medium
            )
            Text(
                text = item.description,
                color = AgentBelayColors.inkSubtle,
                fontSize = 11.sp,
                maxLines = 1
            )
        }
        if (isSelected) {
            Text(
                text = "ENTER",
                color = AgentBelayColors.inkSubtle,
                fontSize = 10.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 0.5.sp
            )
        }
    }
}

@Composable
private fun FooterHint(key: String, label: String) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Text(
            text = key,
            color = AgentBelayColors.inkSecondary,
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold,
            fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace
        )
        Text(
            text = label,
            color = AgentBelayColors.inkSubtle,
            fontSize = 11.sp
        )
    }
}

private data class CommandItem(
    val label: String,
    val icon: ImageVector,
    val description: String,
    val action: () -> Unit,
)

// ── Previews (iter 3) ──────────────────────────────────────────────────────

@androidx.compose.ui.tooling.preview.Preview(widthDp = 960, heightDp = 640)
@Composable
private fun PreviewCommandPaletteOpen() {
    val controller = remember { CommandPaletteController().also { it.open() } }
    PreviewScaffold {
        CommandPaletteHost(
            controller = controller,
            onNavigate = {},
            onShowLicenses = {},
        )
    }
}

@androidx.compose.ui.tooling.preview.Preview(widthDp = 960, heightDp = 640)
@Composable
private fun PreviewCommandPaletteOpenLight() {
    val controller = remember { CommandPaletteController().also { it.open() } }
    PreviewScaffold(themeMode = com.mikepenz.agentbelay.model.ThemeMode.LIGHT) {
        CommandPaletteHost(
            controller = controller,
            onNavigate = {},
            onShowLicenses = {},
        )
    }
}

@androidx.compose.ui.tooling.preview.Preview(widthDp = 960, heightDp = 640)
@Composable
private fun PreviewCommandPaletteClosed() {
    val controller = remember { CommandPaletteController() } // not opened
    PreviewScaffold {
        // With the controller closed the host short-circuits to an empty
        // composition — useful for verifying "no backdrop" in screenshots.
        CommandPaletteHost(
            controller = controller,
            onNavigate = {},
            onShowLicenses = {},
        )
        androidx.compose.material3.Text(
            "CommandPaletteHost closed (renders nothing by design)",
            color = AgentBelayColors.inkSubtle,
            modifier = Modifier.padding(16.dp),
        )
    }
}
