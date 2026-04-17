package com.mikepenz.agentbuddy.protection.modules

import com.mikepenz.agentbuddy.model.HookInput
import com.mikepenz.agentbuddy.model.ProtectionHit
import com.mikepenz.agentbuddy.model.ProtectionMode
import com.mikepenz.agentbuddy.protection.CommandParser
import com.mikepenz.agentbuddy.protection.ProtectionModule
import com.mikepenz.agentbuddy.protection.ProtectionRule
import com.mikepenz.agentbuddy.protection.parser.allLiteralPaths

object AbsolutePathsModule : ProtectionModule {
    override val id = "absolute_paths"
    override val name = "Absolute Paths"
    override val description = "Enforces use of relative paths instead of absolute paths for project and home directories."
    override val corrective = true
    override val defaultMode = ProtectionMode.AUTO_BLOCK
    override val applicableTools = setOf("Bash")

    override val rules: List<ProtectionRule> = listOf(
        ProjectAbsolute,
        HomeAbsolute,
    )

    private val whitelistedPrefixes = listOf(
        "/usr/", "/bin/", "/sbin/", "/lib/", "/opt/homebrew/",
        "/System/", "/Library/", "/tmp", "/dev/", "/proc/",
        "/var/", "/etc/", "/nix/", "/snap/",
    )

    private fun extractAbsolutePaths(hookInput: HookInput): List<String> {
        val parsed = CommandParser.parsedBash(hookInput) ?: return emptyList()
        return parsed.allLiteralPaths()
            .filter { it.startsWith("/") }
            .filter { path -> whitelistedPrefixes.none { prefix -> path.startsWith(prefix) } }
    }

    private fun hit(ruleId: String, message: String) = ProtectionHit(
        moduleId = id,
        ruleId = ruleId,
        message = message,
        mode = defaultMode,
    )

    private object ProjectAbsolute : ProtectionRule {
        override val id = "project_absolute"
        override val name = "Absolute path inside project"
        override val description = "Detects absolute paths that could be expressed as relative paths within the project."
        override val correctiveHint =
            "Use relative paths (e.g. ./src/file.kt) instead of absolute project paths. Relative paths are portable and don't trigger unnecessary permission prompts."

        override fun evaluate(hookInput: HookInput): ProtectionHit? {
            val cwd = hookInput.cwd.ifEmpty { return null }
            val projectPaths = extractAbsolutePaths(hookInput).filter { it.startsWith(cwd) }
            if (projectPaths.isEmpty()) return null
            return hit(id, "Use relative path ./... instead of absolute path ${projectPaths.first()}")
        }
    }

    private object HomeAbsolute : ProtectionRule {
        override val id = "home_absolute"
        override val name = "Absolute path inside home"
        override val description = "Detects absolute paths inside the user's home directory but outside the project."
        override val correctiveHint =
            "Absolute path inside home directory detected. Consider whether this path really needs to be accessed."

        override fun evaluate(hookInput: HookInput): ProtectionHit? {
            val cwd = hookInput.cwd.ifEmpty { return null }
            val home = System.getProperty("user.home") ?: return null
            val homePaths = extractAbsolutePaths(hookInput)
                .filter { it.startsWith(home) && !it.startsWith(cwd) }
            if (homePaths.isEmpty()) return null
            return hit(id, "Use relative path ./... instead of absolute path ${homePaths.first()}")
        }
    }
}
