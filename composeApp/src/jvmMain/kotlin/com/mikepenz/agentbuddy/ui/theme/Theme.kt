package com.mikepenz.agentbuddy.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import co.touchlab.kermit.LogWriter
import co.touchlab.kermit.Logger
import co.touchlab.kermit.Severity
import com.mikepenz.agentbuddy.model.ThemeMode
import com.mikepenz.agentbuddy.model.ToolType
import io.github.kdroidfilter.nucleus.darkmodedetector.isSystemInDarkMode
import java.io.File
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

// Brand palette
val SeaTurtle = Color(0xFF339470)
val Dandelion = Color(0xFFDEEF33)
val PaleRose = Color(0xFFFFC2BA)
val Carmine = Color(0xFFE63845)
val WindowsBlue = Color(0xFF3082DB)

// Risk colors
val RiskSafe = Color(0xFF339470) // Sea Turtle
val RiskLow = Color(0xFF5BB88E)
val RiskMedium = Color(0xFFDEEF33) // Dandelion
val RiskHigh = Color(0xFFE6A233)
val RiskCritical = Color(0xFFE63845) // Carmine

// Tool badge colors
val ToolBashColor = Color(0xFFDEEF33) // Dandelion
val ToolAskColor = Color(0xFF3082DB) // Windows Blue
val ToolPlanColor = Color(0xFF339470) // Sea Turtle
val ToolDefaultColor = Color(0xFF78909C)
val ToolFileColor = Color(0xFFC792EA)   // Soft purple for file ops
val ToolSearchColor = Color(0xFF82AAFF) // Soft blue for search
val ToolWebColor = Color(0xFFFF9E64)    // Warm orange for web

// Source badge colors
val SourceClaudeColor = Color(0xFFD97757)   // Claude orange/terracotta
val SourceCopilotColor = Color(0xFF6E40C9)  // GitHub Copilot purple

fun sourceColor(source: com.mikepenz.agentbuddy.model.Source): Color = when (source) {
    com.mikepenz.agentbuddy.model.Source.CLAUDE_CODE -> SourceClaudeColor
    com.mikepenz.agentbuddy.model.Source.COPILOT -> SourceCopilotColor
}

fun sourceLabel(source: com.mikepenz.agentbuddy.model.Source): String = when (source) {
    com.mikepenz.agentbuddy.model.Source.CLAUDE_CODE -> "Claude Code"
    com.mikepenz.agentbuddy.model.Source.COPILOT -> "Copilot"
}

fun riskColor(risk: Int): Color = when (risk) {
    1 -> RiskSafe
    2 -> RiskLow
    3 -> RiskMedium
    4 -> RiskHigh
    5 -> RiskCritical
    else -> RiskMedium
}

fun riskLabel(risk: Int): String = when (risk) {
    1 -> "Safe"
    2 -> "Low"
    3 -> "Medium"
    4 -> "High"
    5 -> "Critical"
    else -> "Medium"
}

fun toolColor(toolName: String, toolType: ToolType): Color = when {
    toolName.equals("Bash", ignoreCase = true) -> ToolBashColor
    toolName.equals("Read", ignoreCase = true) ||
            toolName.equals("Edit", ignoreCase = true) ||
            toolName.equals("Write", ignoreCase = true) -> ToolFileColor

    toolName.equals("Grep", ignoreCase = true) ||
            toolName.equals("Glob", ignoreCase = true) -> ToolSearchColor

    toolName.equals("WebFetch", ignoreCase = true) -> ToolWebColor
    toolType == ToolType.ASK_USER_QUESTION -> ToolAskColor
    toolType == ToolType.PLAN -> ToolPlanColor
    else -> ToolDefaultColor
}

// Dark theme — deep cool surfaces, Sea Turtle primary, warm accents
private val DarkColorScheme = darkColorScheme(
    primary = SeaTurtle,
    onPrimary = Color.White,
    primaryContainer = Color(0xFF1A4D3A),
    onPrimaryContainer = Color(0xFFB8E6D4),
    secondary = WindowsBlue,
    onSecondary = Color.White,
    secondaryContainer = Color(0xFF1A3D5C),
    onSecondaryContainer = Color(0xFFB8D4F0),
    tertiary = Dandelion,
    onTertiary = Color(0xFF2A2A00),
    tertiaryContainer = Color(0xFF3A3A00),
    onTertiaryContainer = Dandelion,
    background = Color(0xFF0D1117),
    onBackground = Color(0xFFE6EDF3),
    surface = Color(0xFF161B22),
    onSurface = Color(0xFFE6EDF3),
    surfaceVariant = Color(0xFF21262D),
    onSurfaceVariant = Color(0xFF8B949E),
    surfaceContainerLowest = Color(0xFF0D1117),
    surfaceContainerLow = Color(0xFF161B22),
    surfaceContainer = Color(0xFF1C2128),
    surfaceContainerHigh = Color(0xFF21262D),
    surfaceContainerHighest = Color(0xFF2D333B),
    outline = Color(0xFF30363D),
    outlineVariant = Color(0xFF21262D),
    inverseSurface = Color(0xFFE6EDF3),
    inverseOnSurface = Color(0xFF161B22),
    error = Carmine,
    onError = Color.White,
    errorContainer = Color(0xFF4D0F14),
    onErrorContainer = Color(0xFFFFC2BA), // Pale Rose
)

// Light theme — clean warm surfaces, Sea Turtle primary
private val LightColorScheme = lightColorScheme(
    primary = Color(0xFF2A7D5F), // Slightly deeper Sea Turtle for contrast
    onPrimary = Color.White,
    primaryContainer = Color(0xFFD4F0E4),
    onPrimaryContainer = Color(0xFF0D3322),
    secondary = Color(0xFF2872C2), // Deeper Windows Blue for contrast
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFD4E8F8),
    onSecondaryContainer = Color(0xFF0D2D4D),
    tertiary = Color(0xFF8B9900), // Deeper Dandelion for readability
    onTertiary = Color.White,
    tertiaryContainer = Color(0xFFF0F4CC),
    onTertiaryContainer = Color(0xFF2A2D00),
    background = Color(0xFFFBFCFD),
    onBackground = Color(0xFF1A1D21),
    surface = Color(0xFFFFFFFF),
    onSurface = Color(0xFF1A1D21),
    surfaceVariant = Color(0xFFF0F3F6),
    onSurfaceVariant = Color(0xFF4A5568),
    surfaceContainerLowest = Color(0xFFFFFFFF),
    surfaceContainerLow = Color(0xFFF7F9FB),
    surfaceContainer = Color(0xFFF0F3F6),
    surfaceContainerHigh = Color(0xFFE8ECF0),
    surfaceContainerHighest = Color(0xFFDDE3E8),
    outline = Color(0xFFCBD5E1),
    outlineVariant = Color(0xFFE2E8F0),
    inverseSurface = Color(0xFF1A1D21),
    inverseOnSurface = Color(0xFFF0F3F6),
    error = Color(0xFFCC2E38), // Deeper Carmine for light bg
    onError = Color.White,
    errorContainer = Color(0xFFFFC2BA), // Pale Rose
    onErrorContainer = Color(0xFF4D0F14),
)

@Composable
fun AgentBuddyTheme(
    themeMode: ThemeMode = ThemeMode.SYSTEM,
    content: @Composable () -> Unit,
) {
    val isDark = when (themeMode) {
        ThemeMode.SYSTEM -> isSystemInDarkMode()
        ThemeMode.DARK -> true
        ThemeMode.LIGHT -> false
    }
    val colorScheme = if (isDark) DarkColorScheme else LightColorScheme
    MaterialTheme(
        colorScheme = colorScheme,
        content = content,
    )
}

/**
 * Simple file log writer that appends log entries to a file in the app data directory.
 */
class FileLogWriter(
    private val logDir: File = File(System.getProperty("user.home"), ".agent-buddy/logs"),
) : LogWriter() {
    private val logFile: File by lazy {
        logDir.mkdirs()
        File(logDir, "agent-buddy.log")
    }

    private val formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss.SSS")

    override fun log(severity: Severity, message: String, tag: String, throwable: Throwable?) {
        try {
            val timestamp = LocalDateTime.now().format(formatter)
            val line = buildString {
                append("$timestamp [$severity] $tag: $message")
                if (throwable != null) {
                    append("\n${throwable.stackTraceToString()}")
                }
                append("\n")
            }
            logFile.appendText(line)
        } catch (_: Exception) {
            // Silently ignore file write failures to avoid recursive logging
        }
    }
}

/**
 * Configures Kermit with the file log writer.
 */
fun configureLogging() {
    Logger.addLogWriter(FileLogWriter())
}
