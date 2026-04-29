package com.mikepenz.agentbelay.platform

import co.touchlab.kermit.Logger
import java.io.File

/**
 * Manages registering/unregistering the app to launch on system boot.
 * Platform-specific implementations for macOS, Windows, and Linux.
 */
object StartupManager {
    private val log = Logger.withTag("StartupManager")
    private val osName = System.getProperty("os.name").lowercase()

    fun setStartOnBoot(enabled: Boolean) {
        try {
            when {
                osName.contains("mac") -> setMacOSStartup(enabled)
                osName.contains("win") -> setWindowsStartup(enabled)
                else -> setLinuxStartup(enabled)
            }
            log.i { "Start on boot ${if (enabled) "enabled" else "disabled"}" }
        } catch (e: Exception) {
            log.e(e) { "Failed to ${if (enabled) "enable" else "disable"} start on boot" }
        }
    }

    fun isStartOnBootEnabled(): Boolean {
        return try {
            when {
                osName.contains("mac") -> isMacOSStartupEnabled()
                osName.contains("win") -> isWindowsStartupEnabled()
                else -> isLinuxStartupEnabled()
            }
        } catch (e: Exception) {
            log.w(e) { "Failed to check start on boot status" }
            false
        }
    }

    // --- macOS: LaunchAgent plist ---

    private val macPlistDir = File(System.getProperty("user.home"), "Library/LaunchAgents")
    private val macPlistFile = File(macPlistDir, "com.mikepenz.agentbelay.plist")

    private fun findAppExecutable(): String {
        // Try to find the app jar or native executable
        val javaHome = System.getProperty("java.home")
        val java = "$javaHome/bin/java"
        val classPath = System.getProperty("java.class.path") ?: ""
        return if (classPath.isNotBlank()) {
            "$java -cp $classPath com.mikepenz.agentbelay.MainKt"
        } else {
            // Fallback: use the gradlew run command from project dir
            java
        }
    }

    private fun setMacOSStartup(enabled: Boolean) {
        if (enabled) {
            macPlistDir.mkdirs()
            val execCommand = findAppExecutable()
            // Split the command into program + arguments for the plist array
            val parts = execCommand.split(" ")
            val programArgs = parts.joinToString("\n") { "        <string>$it</string>" }
            macPlistFile.writeText("""<?xml version="1.0" encoding="UTF-8"?>
<!DOCTYPE plist PUBLIC "-//Apple//DTD PLIST 1.0//EN" "http://www.apple.com/DTDs/PropertyList-1.0.dtd">
<plist version="1.0">
<dict>
    <key>Label</key>
    <string>com.mikepenz.agentbelay</string>
    <key>ProgramArguments</key>
    <array>
$programArgs
    </array>
    <key>RunAtLoad</key>
    <true/>
    <key>KeepAlive</key>
    <false/>
</dict>
</plist>
""")
            log.d { "Created LaunchAgent plist at ${macPlistFile.absolutePath}" }
        } else {
            if (macPlistFile.exists()) {
                macPlistFile.delete()
                log.d { "Removed LaunchAgent plist" }
            }
        }
    }

    private fun isMacOSStartupEnabled(): Boolean = macPlistFile.exists()

    // --- Windows: Registry via reg.exe ---

    private const val WIN_REG_KEY = """HKCU\Software\Microsoft\Windows\CurrentVersion\Run"""
    private const val WIN_REG_NAME = "AgentBelay"

    private fun setWindowsStartup(enabled: Boolean) {
        val javaHome = System.getProperty("java.home")
        val javaw = "$javaHome\\bin\\javaw.exe"
        val classPath = System.getProperty("java.class.path") ?: ""

        if (enabled) {
            val command = if (classPath.isNotBlank()) {
                "\"$javaw\" -cp \"$classPath\" com.mikepenz.agentbelay.MainKt"
            } else {
                "\"$javaw\""
            }
            ProcessBuilder("reg", "add", WIN_REG_KEY, "/v", WIN_REG_NAME, "/t", "REG_SZ", "/d", command, "/f")
                .redirectErrorStream(true)
                .start()
                .waitFor()
        } else {
            ProcessBuilder("reg", "delete", WIN_REG_KEY, "/v", WIN_REG_NAME, "/f")
                .redirectErrorStream(true)
                .start()
                .waitFor()
        }
    }

    private fun isWindowsStartupEnabled(): Boolean {
        val process = ProcessBuilder("reg", "query", WIN_REG_KEY, "/v", WIN_REG_NAME)
            .redirectErrorStream(true)
            .start()
        val output = process.inputStream.bufferedReader().readText()
        process.waitFor()
        return output.contains(WIN_REG_NAME)
    }

    // --- Linux: .desktop file in autostart ---

    private val linuxAutostartDir = File(System.getProperty("user.home"), ".config/autostart")
    private val linuxDesktopFile = File(linuxAutostartDir, "agent-belay.desktop")

    private fun setLinuxStartup(enabled: Boolean) {
        if (enabled) {
            linuxAutostartDir.mkdirs()
            val execCommand = findAppExecutable()
            linuxDesktopFile.writeText("""[Desktop Entry]
Type=Application
Name=Agent Belay
Exec=$execCommand
Hidden=false
NoDisplay=false
X-GNOME-Autostart-enabled=true
Comment=AI Agent Approval Manager
""")
            log.d { "Created autostart desktop file at ${linuxDesktopFile.absolutePath}" }
        } else {
            if (linuxDesktopFile.exists()) {
                linuxDesktopFile.delete()
                log.d { "Removed autostart desktop file" }
            }
        }
    }

    private fun isLinuxStartupEnabled(): Boolean = linuxDesktopFile.exists()
}
