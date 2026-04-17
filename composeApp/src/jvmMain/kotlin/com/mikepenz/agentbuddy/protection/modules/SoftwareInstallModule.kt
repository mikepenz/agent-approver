package com.mikepenz.agentbuddy.protection.modules

import com.mikepenz.agentbuddy.model.HookInput
import com.mikepenz.agentbuddy.model.ProtectionHit
import com.mikepenz.agentbuddy.model.ProtectionMode
import com.mikepenz.agentbuddy.protection.CommandParser
import com.mikepenz.agentbuddy.protection.ProtectionModule
import com.mikepenz.agentbuddy.protection.ProtectionRule
import com.mikepenz.agentbuddy.protection.parser.SimpleCommand
import com.mikepenz.agentbuddy.protection.parser.allSimpleCommands

object SoftwareInstallModule : ProtectionModule {
    override val id = "software_install"
    override val name = "Software Installation"
    override val description =
        "Flags package/software installation commands (brew, npm -g, npx, apt, cargo, …) so they can be reviewed before running."
    override val corrective = false
    override val defaultMode = ProtectionMode.ASK_AUTO_BLOCK
    override val applicableTools = setOf("Bash")

    override val rules: List<ProtectionRule> by lazy {
        listOf(
            BrewInstall,
            NpmGlobalInstall,
            YarnGlobalInstall,
            PnpmGlobalInstall,
            Npx,
            PipxInstall,
            CargoInstall,
            GoInstall,
            GemInstall,
            AptInstall,
            YumDnfInstall,
            PacmanInstall,
            SnapInstall,
            MasInstall,
            SdkmanInstall,
            WingetInstall,
            ChocoInstall,
        )
    }

    private fun hit(ruleId: String, message: String) = ProtectionHit(
        moduleId = id,
        ruleId = ruleId,
        message = message,
        mode = defaultMode,
    )

    private fun commands(hookInput: HookInput): Sequence<SimpleCommand> =
        CommandParser.parsedBash(hookInput)?.allSimpleCommands() ?: emptySequence()

    /** Check: command basename matches [name] and at least one of its literal args equals [subcommand]. */
    private fun simpleRule(
        ruleId: String,
        ruleName: String,
        ruleDescription: String,
        message: String,
        match: (SimpleCommand) -> Boolean,
    ): ProtectionRule = object : ProtectionRule {
        override val id = ruleId
        override val name = ruleName
        override val description = ruleDescription
        override fun evaluate(hookInput: HookInput): ProtectionHit? {
            val cmd = CommandParser.bashCommand(hookInput) ?: return null
            return if (commands(hookInput).any(match)) hit(id, "$message: $cmd") else null
        }
    }

    private val BrewInstall = simpleRule(
        ruleId = "brew_install",
        ruleName = "Homebrew install",
        ruleDescription = "Detects Homebrew package installation (brew install/reinstall/tap/cask).",
        message = "Homebrew install",
    ) { sc ->
        sc.commandName == "brew" && (
            sc.hasLiteralArg("install") || sc.hasLiteralArg("reinstall") ||
                sc.hasLiteralArg("tap") ||
                (sc.hasLiteralArg("cask") && sc.hasLiteralArg("install"))
            )
    }

    private val NpmGlobalInstall = simpleRule(
        ruleId = "npm_global_install",
        ruleName = "npm global install",
        ruleDescription = "Detects npm install -g / npm i -g / npm add -g.",
        message = "npm global install",
    ) { sc ->
        if (sc.commandName != "npm") return@simpleRule false
        val isInstall = sc.hasLiteralArg("install") || sc.hasLiteralArg("i") || sc.hasLiteralArg("add")
        val isGlobal = sc.hasFlag(short = 'g') || sc.hasLongFlag("--global")
        isInstall && isGlobal
    }

    private val YarnGlobalInstall = simpleRule(
        ruleId = "yarn_global_install",
        ruleName = "yarn global install",
        ruleDescription = "Detects yarn global add.",
        message = "yarn global install",
    ) { sc -> sc.commandName == "yarn" && sc.hasLiteralArg("global") && sc.hasLiteralArg("add") }

    private val PnpmGlobalInstall = simpleRule(
        ruleId = "pnpm_global_install",
        ruleName = "pnpm global install",
        ruleDescription = "Detects pnpm add -g / pnpm install -g.",
        message = "pnpm global install",
    ) { sc ->
        if (sc.commandName != "pnpm") return@simpleRule false
        val isAdd = sc.hasLiteralArg("add") || sc.hasLiteralArg("install") || sc.hasLiteralArg("i")
        val isGlobal = sc.hasFlag(short = 'g') || sc.hasLongFlag("--global")
        isAdd && isGlobal
    }

    private val Npx = simpleRule(
        ruleId = "npx",
        ruleName = "npx package execution",
        ruleDescription = "Detects npx, which downloads and runs arbitrary npm packages.",
        message = "npx package execution",
    ) { sc -> sc.commandName == "npx" }

    private val PipxInstall = simpleRule(
        ruleId = "pipx_install",
        ruleName = "pipx install",
        ruleDescription = "Detects pipx install.",
        message = "pipx install",
    ) { sc -> sc.commandName == "pipx" && (sc.hasLiteralArg("install") || sc.hasLiteralArg("run")) }

    private val CargoInstall = simpleRule(
        ruleId = "cargo_install",
        ruleName = "cargo install",
        ruleDescription = "Detects cargo install, which fetches and builds a crate globally.",
        message = "cargo install",
    ) { sc -> sc.commandName == "cargo" && sc.hasLiteralArg("install") }

    private val GoInstall = simpleRule(
        ruleId = "go_install",
        ruleName = "go install",
        ruleDescription = "Detects go install, which fetches and installs Go binaries.",
        message = "go install",
    ) { sc -> sc.commandName == "go" && sc.hasLiteralArg("install") }

    private val GemInstall = simpleRule(
        ruleId = "gem_install",
        ruleName = "gem install",
        ruleDescription = "Detects RubyGems install.",
        message = "gem install",
    ) { sc -> sc.commandName == "gem" && sc.hasLiteralArg("install") }

    private val AptInstall = simpleRule(
        ruleId = "apt_install",
        ruleName = "apt install",
        ruleDescription = "Detects apt/apt-get install.",
        message = "apt install",
    ) { sc ->
        val name = sc.commandName
        (name == "apt" || name == "apt-get") &&
            (sc.hasLiteralArg("install") || sc.hasLiteralArg("full-upgrade") || sc.hasLiteralArg("dist-upgrade"))
    }

    private val YumDnfInstall = simpleRule(
        ruleId = "yum_dnf_install",
        ruleName = "yum/dnf install",
        ruleDescription = "Detects yum or dnf install.",
        message = "yum/dnf install",
    ) { sc ->
        val name = sc.commandName
        (name == "yum" || name == "dnf") &&
            (sc.hasLiteralArg("install") || sc.hasLiteralArg("upgrade") || (sc.hasLiteralArg("group") && sc.hasLiteralArg("install")))
    }

    private val PacmanInstall = simpleRule(
        ruleId = "pacman_install",
        ruleName = "pacman -S",
        ruleDescription = "Detects pacman -S / -Syu / --sync (including combined short flags).",
        message = "pacman install",
    ) { sc ->
        if (sc.commandName != "pacman") return@simpleRule false
        if (sc.hasLongFlag("--sync")) return@simpleRule true
        // Any short-flag argument whose first letter is `S` (e.g. -S, -Sy, -Syu, -Syyu).
        sc.args.any { a ->
            val lit = a.literal ?: return@any false
            lit.length >= 2 && lit[0] == '-' && lit[1] == 'S'
        }
    }

    private val SnapInstall = simpleRule(
        ruleId = "snap_install",
        ruleName = "snap install",
        ruleDescription = "Detects snap install.",
        message = "snap install",
    ) { sc -> sc.commandName == "snap" && sc.hasLiteralArg("install") }

    private val MasInstall = simpleRule(
        ruleId = "mas_install",
        ruleName = "mas install",
        ruleDescription = "Detects Mac App Store install via mas.",
        message = "Mac App Store install",
    ) { sc -> sc.commandName == "mas" && sc.hasLiteralArg("install") }

    private val SdkmanInstall = simpleRule(
        ruleId = "sdkman_install",
        ruleName = "sdk install",
        ruleDescription = "Detects SDKMAN install.",
        message = "SDKMAN install",
    ) { sc -> sc.commandName == "sdk" && sc.hasLiteralArg("install") }

    private val WingetInstall = simpleRule(
        ruleId = "winget_install",
        ruleName = "winget install",
        ruleDescription = "Detects Windows Package Manager install.",
        message = "winget install",
    ) { sc -> sc.commandName == "winget" && sc.hasLiteralArg("install") }

    private val ChocoInstall = simpleRule(
        ruleId = "choco_install",
        ruleName = "choco install",
        ruleDescription = "Detects Chocolatey install.",
        message = "choco install",
    ) { sc -> sc.commandName == "choco" && sc.hasLiteralArg("install") }
}
