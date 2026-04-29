package com.mikepenz.agentbelay.ui.shell

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mikepenz.agentbelay.VERSION
import com.mikepenz.agentbelay.model.ThemeMode
import com.mikepenz.agentbelay.ui.AgentRegistration
import com.mikepenz.agentbelay.ui.AppTab
import com.mikepenz.agentbelay.ui.components.LocalPreviewHoverOverride
import com.mikepenz.agentbelay.ui.components.SectionLabel
import com.mikepenz.agentbelay.ui.icons.LucideChart
import com.mikepenz.agentbelay.ui.icons.LucideGear
import com.mikepenz.agentbelay.ui.icons.LucideHistory
import com.mikepenz.agentbelay.ui.icons.LucideInbox
import com.mikepenz.agentbelay.ui.icons.LucideSearch
import com.mikepenz.agentbelay.ui.icons.LucideShield
import com.mikepenz.agentbelay.ui.theme.AccentEmerald
import com.mikepenz.agentbelay.ui.theme.AgentBelayColors
import com.mikepenz.agentbelay.ui.theme.AgentBelayTheme

data class NavItem(
    val tab: AppTab,
    val label: String,
    val icon: ImageVector,
    val kbd: String,
    val badge: Int = 0,
)

@Composable
fun AppSidebar(
    selectedTab: AppTab,
    onTabSelect: (AppTab) -> Unit,
    pendingCount: Int = 0,
    appVersion: String = "",
    serverPort: Int = 19532,
    agentRegistrations: List<AgentRegistration> = emptyList(),
    compact: Boolean = false,
    modifier: Modifier = Modifier,
) {
    val navItems = listOf(
        NavItem(AppTab.Approvals, "Approvals", LucideInbox, "1", badge = pendingCount),
        NavItem(AppTab.History, "History", LucideHistory, "2"),
        NavItem(AppTab.Statistics, "Stats", LucideChart, "3"),
        NavItem(AppTab.Settings, "Settings", LucideGear, "4"),
    )

    val sidebarWidth by animateDpAsState(
        targetValue = if (compact) 60.dp else 232.dp,
        animationSpec = tween(220),
        label = "sidebar-width",
    )
    val sidePad by animateDpAsState(
        targetValue = if (compact) 4.dp else 10.dp,
        animationSpec = tween(220),
        label = "sidebar-pad",
    )

    Row(
        modifier = modifier
            .width(sidebarWidth)
            .fillMaxHeight()
            .background(AgentBelayColors.surface),
    ) {
        Column(
            modifier = Modifier
                .weight(1f)
                .fillMaxHeight()
                .padding(start = sidePad, end = sidePad, top = 14.dp, bottom = 10.dp),
            horizontalAlignment = if (compact) Alignment.CenterHorizontally else Alignment.Start,
        ) {
        // Brand — icon only in compact, full brand row otherwise
        if (compact) {
            Box(
                modifier = Modifier
                    .size(32.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(
                        Brush.linearGradient(colors = listOf(AccentEmerald, Color(0xFF35B4C2))),
                    ),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = LucideShield,
                    contentDescription = "Agent Belay",
                    tint = AgentBelayColors.accentEmeraldInk,
                    modifier = Modifier.size(14.dp),
                )
            }
        } else {
            Row(
                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Box(
                    modifier = Modifier
                        .size(26.dp)
                        .clip(RoundedCornerShape(7.dp))
                        .background(
                            Brush.linearGradient(
                                colors = listOf(AccentEmerald, Color(0xFF35B4C2)),
                            ),
                        ),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = LucideShield,
                        contentDescription = null,
                        tint = AgentBelayColors.accentEmeraldInk,
                        modifier = Modifier.size(13.dp),
                    )
                }
                Column(modifier = Modifier.weight(1f, fill = false)) {
                    Text(
                        text = "Agent Belay",
                        color = AgentBelayColors.inkPrimary,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold,
                        letterSpacing = (-0.15).sp,
                        maxLines = 1,
                        overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                    )
                    Text(
                        text = buildString {
                            if (appVersion.isNotEmpty()) {
                                append("v").append(appVersion).append(" · ")
                            }
                            append("local · :").append(serverPort)
                        },
                        color = AgentBelayColors.inkMuted,
                        fontSize = 10.5.sp,
                        maxLines = 1,
                        overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                    )
                }
            }
        }

        Spacer(Modifier.height(14.dp))

        // Command search trigger — icon only in compact
        val paletteController = LocalCommandPaletteController.current
        if (compact) {
            CompactSearchButton(onClick = { paletteController.toggle() })
        } else {
            CommandSearchButton(onClick = { paletteController.toggle() })
        }

        Spacer(Modifier.height(14.dp))

        // Nav items
        Column(verticalArrangement = Arrangement.spacedBy(1.dp)) {
            navItems.forEach { item ->
                SidebarNavItem(
                    item = item,
                    isActive = selectedTab == item.tab,
                    compact = compact,
                    onClick = { onTabSelect(item.tab) },
                )
            }
        }

        Spacer(Modifier.weight(1f))

        if (!compact) {
            // Connected agents — only in full mode
            Spacer(Modifier.height(8.dp))
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(1.dp)
                    .background(MaterialTheme.colorScheme.outline),
            )
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 12.dp, bottom = 12.dp),
            ) {
                SectionLabel(
                    text = "Registered",
                    modifier = Modifier.padding(horizontal = 8.dp),
                )
                Spacer(Modifier.height(10.dp))
                agentRegistrations.forEachIndexed { index, agent ->
                    if (index > 0) Spacer(Modifier.height(7.dp))
                    AgentStatusRow(agent.name, registered = agent.registered)
                }
            }
        } // compact: registered section hidden
        }
        // Right border — matches design "border-right: 1px solid var(--line)"
        Box(
            modifier = Modifier
                .width(1.dp)
                .fillMaxHeight()
                .background(MaterialTheme.colorScheme.outline),
        )
    }
}

@Composable
private fun CompactSearchButton(onClick: () -> Unit) {
    val interactionSource = remember { MutableInteractionSource() }
    val liveHovered by interactionSource.collectIsHoveredAsState()
    val isHovered = LocalPreviewHoverOverride.current ?: liveHovered

    Box(
        modifier = Modifier
            .size(40.dp)
            .clip(RoundedCornerShape(7.dp))
            .background(
                if (isHovered) AgentBelayColors.surface2
                else MaterialTheme.colorScheme.background,
            )
            .border(
                1.dp,
                if (isHovered) MaterialTheme.colorScheme.outlineVariant
                else MaterialTheme.colorScheme.outline,
                RoundedCornerShape(7.dp),
            )
            .hoverable(interactionSource)
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                role = Role.Button,
                onClickLabel = "Open command palette",
            ) { onClick() },
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = LucideSearch,
            contentDescription = "Search commands",
            tint = AgentBelayColors.inkTertiary,
            modifier = Modifier.size(14.dp),
        )
    }
}

@Composable
private fun CommandSearchButton(onClick: () -> Unit) {
    val interactionSource = remember { MutableInteractionSource() }
    val liveHovered by interactionSource.collectIsHoveredAsState()
    val isHovered = LocalPreviewHoverOverride.current ?: liveHovered

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(34.dp)
            .clip(RoundedCornerShape(7.dp))
            .background(
                if (isHovered) AgentBelayColors.surface2
                else MaterialTheme.colorScheme.background,
            )
            .border(
                1.dp,
                if (isHovered) MaterialTheme.colorScheme.outlineVariant
                else MaterialTheme.colorScheme.outline,
                RoundedCornerShape(7.dp),
            )
            .hoverable(interactionSource)
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                role = Role.Button,
                onClickLabel = "Open command palette",
            ) { onClick() }
            .padding(horizontal = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Icon(
            imageVector = LucideSearch,
            contentDescription = "Search commands",
            tint = AgentBelayColors.inkTertiary,
            modifier = Modifier.size(13.dp),
        )
        Text(
            text = "Search commands…",
            color = AgentBelayColors.inkTertiary,
            fontSize = 12.sp,
            modifier = Modifier.weight(1f),
        )
        KbdKey("⌘K")
    }
}

@Composable
private fun SidebarNavItem(
    item: NavItem,
    isActive: Boolean,
    compact: Boolean = false,
    onClick: () -> Unit,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val liveHovered by interactionSource.collectIsHoveredAsState()
    val isHovered = LocalPreviewHoverOverride.current ?: liveHovered

    val bgColor = when {
        isActive -> AgentBelayColors.surface3
        isHovered -> AgentBelayColors.surface2
        else -> Color.Transparent
    }
    val textColor = if (isActive) AgentBelayColors.inkPrimary else AgentBelayColors.inkSecondary
    val iconColor = if (isActive) AccentEmerald else AgentBelayColors.inkTertiary

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(36.dp)
            .clip(RoundedCornerShape(6.dp))
            .background(bgColor)
            .hoverable(interactionSource)
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                role = Role.Tab,
                onClickLabel = "Open ${item.label}",
            ) { onClick() }
            .semantics { selected = isActive },
        contentAlignment = Alignment.Center,
    ) {
        if (compact) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    imageVector = item.icon,
                    contentDescription = item.label,
                    tint = iconColor,
                    modifier = Modifier.size(16.dp),
                )
                // Badge dot in compact mode
                if (item.badge > 0) {
                    Box(
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .size(7.dp)
                            .clip(RoundedCornerShape(4.dp))
                            .background(AccentEmerald),
                    )
                }
            }
        } else {
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Icon(
                    imageVector = item.icon,
                    contentDescription = item.label,
                    tint = iconColor,
                    modifier = Modifier.size(15.dp),
                )
                Text(
                    text = item.label,
                    color = textColor,
                    fontSize = 13.sp,
                    fontWeight = if (isActive) FontWeight.Medium else FontWeight.Normal,
                    letterSpacing = (-0.05).sp,
                    maxLines = 1,
                    overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                if (item.badge > 0) {
                    val label = if (item.badge > 99) "99+" else item.badge.toString()
                    Box(
                        modifier = Modifier
                            .heightIn(min = 18.dp)
                            .widthIn(min = 18.dp)
                            .clip(RoundedCornerShape(9.dp))
                            .background(AccentEmerald)
                            .padding(horizontal = 6.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            text = label,
                            color = AgentBelayColors.accentEmeraldInk,
                            fontSize = 10.5.sp,
                            fontWeight = FontWeight.SemiBold,
                        )
                    }
                } else {
                    KbdKey(item.kbd)
                }
            }
        }
    }
}

@Composable
private fun AgentStatusRow(name: String, registered: Boolean) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Box(
            modifier = Modifier
                .size(6.dp)
                .clip(CircleShape)
                .background(
                    if (registered) AccentEmerald else AgentBelayColors.inkSubtle,
                ),
        )
        Text(
            text = name,
            color = if (registered) AgentBelayColors.inkSecondary else AgentBelayColors.inkMuted,
            fontSize = 12.sp,
            maxLines = 1,
            overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        Text(
            text = if (registered) "Registered" else "Not registered",
            color = AgentBelayColors.inkMuted,
            fontSize = 10.sp,
            maxLines = 1,
        )
    }
}

@Composable
fun KbdKey(text: String) {
    Box(
        modifier = Modifier
            .heightIn(min = 20.dp)
            .widthIn(min = 20.dp)
            .clip(RoundedCornerShape(4.dp))
            .background(AgentBelayColors.surface3)
            .border(1.dp, AgentBelayColors.line2, RoundedCornerShape(4.dp))
            .padding(horizontal = 6.dp, vertical = 1.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = text,
            color = AgentBelayColors.inkPrimary,
            fontSize = 11.sp,
            fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace,
            fontWeight = FontWeight.SemiBold,
        )
    }
}

// ── Previews ───────────────────────────────────────────────────────────────

@Composable
private fun SidebarPreviewScaffold(
    themeMode: ThemeMode = ThemeMode.DARK,
    content: @Composable () -> Unit,
) {
    AgentBelayTheme(themeMode = themeMode) {
        CompositionLocalProvider(
            LocalCommandPaletteController provides remember { CommandPaletteController() },
        ) {
            content()
        }
    }
}

@Preview(widthDp = 232, heightDp = 860)
@Composable
private fun PreviewSidebar() {
    SidebarPreviewScaffold {
        AppSidebar(
            selectedTab = AppTab.Approvals,
            onTabSelect = {},
            pendingCount = 3,
            appVersion = VERSION,
            serverPort = 19532,
            agentRegistrations = listOf(
                AgentRegistration("Claude Code", true),
                AgentRegistration("GitHub Copilot", true),
            ),
        )
    }
}

@Preview(widthDp = 232, heightDp = 860)
@Composable
private fun PreviewSidebarHistory() {
    SidebarPreviewScaffold {
        AppSidebar(
            selectedTab = AppTab.History,
            onTabSelect = {},
            pendingCount = 0,
            appVersion = VERSION,
            serverPort = 19532,
            agentRegistrations = listOf(
                AgentRegistration("Claude Code", true),
                AgentRegistration("GitHub Copilot", false),
            ),
        )
    }
}

@Preview(widthDp = 232, heightDp = 860)
@Composable
private fun PreviewSidebarHovered() {
    SidebarPreviewScaffold {
        CompositionLocalProvider(LocalPreviewHoverOverride provides true) {
            AppSidebar(
                selectedTab = AppTab.Approvals,
                onTabSelect = {},
                pendingCount = 3,
                appVersion = VERSION,
                serverPort = 19532,
                agentRegistrations = listOf(
                    AgentRegistration("Claude Code", true),
                    AgentRegistration("GitHub Copilot", true),
                ),
            )
        }
    }
}

@Preview(widthDp = 232, heightDp = 860)
@Composable
private fun PreviewSidebarUnregistered() {
    SidebarPreviewScaffold {
        AppSidebar(
            selectedTab = AppTab.Approvals,
            onTabSelect = {},
            pendingCount = 0,
            appVersion = VERSION,
            serverPort = 19532,
            agentRegistrations = listOf(
                AgentRegistration("Claude Code", false),
                AgentRegistration("GitHub Copilot", false),
            ),
        )
    }
}

@Preview(widthDp = 232, heightDp = 860)
@Composable
private fun PreviewSidebarLight() {
    SidebarPreviewScaffold(themeMode = ThemeMode.LIGHT) {
        AppSidebar(
            selectedTab = AppTab.Approvals,
            onTabSelect = {},
            pendingCount = 3,
            appVersion = VERSION,
            serverPort = 19532,
            agentRegistrations = listOf(
                AgentRegistration("Claude Code", true),
                AgentRegistration("GitHub Copilot", true),
            ),
        )
    }
}

@Preview(widthDp = 232, heightDp = 860)
@Composable
private fun PreviewSidebarLargeBadge() {
    SidebarPreviewScaffold {
        AppSidebar(
            selectedTab = AppTab.Approvals,
            onTabSelect = {},
            pendingCount = 99,
            appVersion = VERSION,
            serverPort = 19532,
            agentRegistrations = listOf(
                AgentRegistration("Claude Code", true),
                AgentRegistration("GitHub Copilot", true),
            ),
        )
    }
}
