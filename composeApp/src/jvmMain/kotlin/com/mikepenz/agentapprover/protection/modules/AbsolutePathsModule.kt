package com.mikepenz.agentapprover.protection.modules

import com.mikepenz.agentapprover.model.HookInput
import com.mikepenz.agentapprover.model.ProtectionHit
import com.mikepenz.agentapprover.model.ProtectionMode
import com.mikepenz.agentapprover.protection.CommandParser
import com.mikepenz.agentapprover.protection.ProtectionModule
import com.mikepenz.agentapprover.protection.ProtectionRule

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

    private val absolutePathPattern = Regex("""(?:^|\s)(/[^\s;|&<>]+)""")

    private fun extractAbsolutePaths(command: String): List<String> {
        return absolutePathPattern.findAll(command)
            .map { it.groupValues[1] }
            .filter { path -> whitelistedPrefixes.none { prefix -> path.startsWith(prefix) } }
            .toList()
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

        override fun evaluate(hookInput: HookInput): ProtectionHit? {
            val cmd = CommandParser.bashCommand(hookInput) ?: return null
            val cwd = hookInput.cwd.ifEmpty { return null }
            val paths = extractAbsolutePaths(cmd)
            val projectPaths = paths.filter { it.startsWith(cwd) }
            if (projectPaths.isEmpty()) return null
            val first = projectPaths.first()
            return hit(id, "Use relative path ./... instead of absolute path $first")
        }
    }

    private object HomeAbsolute : ProtectionRule {
        override val id = "home_absolute"
        override val name = "Absolute path inside home"
        override val description = "Detects absolute paths inside the user's home directory but outside the project."

        override fun evaluate(hookInput: HookInput): ProtectionHit? {
            val cmd = CommandParser.bashCommand(hookInput) ?: return null
            val cwd = hookInput.cwd.ifEmpty { return null }
            val home = System.getProperty("user.home") ?: return null
            val paths = extractAbsolutePaths(cmd)
            val homePaths = paths.filter { it.startsWith(home) && !it.startsWith(cwd) }
            if (homePaths.isEmpty()) return null
            val first = homePaths.first()
            return hit(id, "Use relative path ./... instead of absolute path $first")
        }
    }
}
