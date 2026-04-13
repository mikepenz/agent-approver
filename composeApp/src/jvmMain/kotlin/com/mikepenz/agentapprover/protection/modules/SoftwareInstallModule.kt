package com.mikepenz.agentapprover.protection.modules

import com.mikepenz.agentapprover.model.HookInput
import com.mikepenz.agentapprover.model.ProtectionHit
import com.mikepenz.agentapprover.model.ProtectionMode
import com.mikepenz.agentapprover.protection.CommandParser
import com.mikepenz.agentapprover.protection.ProtectionModule
import com.mikepenz.agentapprover.protection.ProtectionRule

object SoftwareInstallModule : ProtectionModule {
    override val id = "software_install"
    override val name = "Software Installation"
    override val description =
        "Flags package/software installation commands (brew, npm -g, npx, apt, cargo, …) so they can be reviewed before running."
    override val corrective = false
    override val defaultMode = ProtectionMode.ASK_AUTO_BLOCK
    override val applicableTools = setOf("Bash")

    override val rules: List<ProtectionRule> = listOf(
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

    private fun hit(ruleId: String, message: String) = ProtectionHit(
        moduleId = id,
        ruleId = ruleId,
        message = message,
        mode = defaultMode,
    )

    private object BrewInstall : ProtectionRule {
        override val id = "brew_install"
        override val name = "Homebrew install"
        override val description = "Detects Homebrew package installation (brew install/reinstall/tap/cask)."
        private val pattern = Regex("""\bbrew\s+(install|reinstall|tap|cask\s+install)\b""")

        override fun evaluate(hookInput: HookInput): ProtectionHit? {
            val cmd = CommandParser.bashCommand(hookInput) ?: return null
            if (!pattern.containsMatchIn(cmd)) return null
            return hit(id, "Homebrew install: $cmd")
        }
    }

    private object NpmGlobalInstall : ProtectionRule {
        override val id = "npm_global_install"
        override val name = "npm global install"
        override val description = "Detects npm install -g / npm i -g / npm add -g."
        private val pattern = Regex("""\bnpm\s+(install|i|add)\b[^|;&]*\s(-g|--global)\b""")

        override fun evaluate(hookInput: HookInput): ProtectionHit? {
            val cmd = CommandParser.bashCommand(hookInput) ?: return null
            if (!pattern.containsMatchIn(cmd)) return null
            return hit(id, "npm global install: $cmd")
        }
    }

    private object YarnGlobalInstall : ProtectionRule {
        override val id = "yarn_global_install"
        override val name = "yarn global install"
        override val description = "Detects yarn global add."
        private val pattern = Regex("""\byarn\s+global\s+add\b""")

        override fun evaluate(hookInput: HookInput): ProtectionHit? {
            val cmd = CommandParser.bashCommand(hookInput) ?: return null
            if (!pattern.containsMatchIn(cmd)) return null
            return hit(id, "yarn global install: $cmd")
        }
    }

    private object PnpmGlobalInstall : ProtectionRule {
        override val id = "pnpm_global_install"
        override val name = "pnpm global install"
        override val description = "Detects pnpm add -g / pnpm install -g."
        private val pattern = Regex("""\bpnpm\s+(add|install|i)\b[^|;&]*\s(-g|--global)\b""")

        override fun evaluate(hookInput: HookInput): ProtectionHit? {
            val cmd = CommandParser.bashCommand(hookInput) ?: return null
            if (!pattern.containsMatchIn(cmd)) return null
            return hit(id, "pnpm global install: $cmd")
        }
    }

    private object Npx : ProtectionRule {
        override val id = "npx"
        override val name = "npx package execution"
        override val description = "Detects npx, which downloads and runs arbitrary npm packages."
        private val pattern = Regex("""\bnpx\b""")

        override fun evaluate(hookInput: HookInput): ProtectionHit? {
            val cmd = CommandParser.bashCommand(hookInput) ?: return null
            if (!pattern.containsMatchIn(cmd)) return null
            return hit(id, "npx package execution: $cmd")
        }
    }

    private object PipxInstall : ProtectionRule {
        override val id = "pipx_install"
        override val name = "pipx install"
        override val description = "Detects pipx install."
        private val pattern = Regex("""\bpipx\s+(install|run)\b""")

        override fun evaluate(hookInput: HookInput): ProtectionHit? {
            val cmd = CommandParser.bashCommand(hookInput) ?: return null
            if (!pattern.containsMatchIn(cmd)) return null
            return hit(id, "pipx install: $cmd")
        }
    }

    private object CargoInstall : ProtectionRule {
        override val id = "cargo_install"
        override val name = "cargo install"
        override val description = "Detects cargo install, which fetches and builds a crate globally."
        private val pattern = Regex("""\bcargo\s+install\b""")

        override fun evaluate(hookInput: HookInput): ProtectionHit? {
            val cmd = CommandParser.bashCommand(hookInput) ?: return null
            if (!pattern.containsMatchIn(cmd)) return null
            return hit(id, "cargo install: $cmd")
        }
    }

    private object GoInstall : ProtectionRule {
        override val id = "go_install"
        override val name = "go install"
        override val description = "Detects go install, which fetches and installs Go binaries."
        private val pattern = Regex("""\bgo\s+install\b""")

        override fun evaluate(hookInput: HookInput): ProtectionHit? {
            val cmd = CommandParser.bashCommand(hookInput) ?: return null
            if (!pattern.containsMatchIn(cmd)) return null
            return hit(id, "go install: $cmd")
        }
    }

    private object GemInstall : ProtectionRule {
        override val id = "gem_install"
        override val name = "gem install"
        override val description = "Detects RubyGems install."
        private val pattern = Regex("""\bgem\s+install\b""")

        override fun evaluate(hookInput: HookInput): ProtectionHit? {
            val cmd = CommandParser.bashCommand(hookInput) ?: return null
            if (!pattern.containsMatchIn(cmd)) return null
            return hit(id, "gem install: $cmd")
        }
    }

    private object AptInstall : ProtectionRule {
        override val id = "apt_install"
        override val name = "apt install"
        override val description = "Detects apt/apt-get install."
        private val pattern = Regex("""\bapt(-get)?\s+(install|full-upgrade|dist-upgrade)\b""")

        override fun evaluate(hookInput: HookInput): ProtectionHit? {
            val cmd = CommandParser.bashCommand(hookInput) ?: return null
            if (!pattern.containsMatchIn(cmd)) return null
            return hit(id, "apt install: $cmd")
        }
    }

    private object YumDnfInstall : ProtectionRule {
        override val id = "yum_dnf_install"
        override val name = "yum/dnf install"
        override val description = "Detects yum or dnf install."
        private val pattern = Regex("""\b(yum|dnf)\s+(install|upgrade|group\s+install)\b""")

        override fun evaluate(hookInput: HookInput): ProtectionHit? {
            val cmd = CommandParser.bashCommand(hookInput) ?: return null
            if (!pattern.containsMatchIn(cmd)) return null
            return hit(id, "yum/dnf install: $cmd")
        }
    }

    private object PacmanInstall : ProtectionRule {
        override val id = "pacman_install"
        override val name = "pacman -S"
        override val description = "Detects pacman -S / -Syu / --sync (including combined short flags)."
        // pacman's operation is the first capital letter after `-`; -S* = sync, so match any
        // short-flag token whose first letter is S (e.g. -S, -Sy, -Syu, -Syyu, -Su).
        private val pattern = Regex("""\bpacman\s+(-S[a-zA-Z]*\b|--sync\b)""")

        override fun evaluate(hookInput: HookInput): ProtectionHit? {
            val cmd = CommandParser.bashCommand(hookInput) ?: return null
            if (!pattern.containsMatchIn(cmd)) return null
            return hit(id, "pacman install: $cmd")
        }
    }

    private object SnapInstall : ProtectionRule {
        override val id = "snap_install"
        override val name = "snap install"
        override val description = "Detects snap install."
        private val pattern = Regex("""\bsnap\s+install\b""")

        override fun evaluate(hookInput: HookInput): ProtectionHit? {
            val cmd = CommandParser.bashCommand(hookInput) ?: return null
            if (!pattern.containsMatchIn(cmd)) return null
            return hit(id, "snap install: $cmd")
        }
    }

    private object MasInstall : ProtectionRule {
        override val id = "mas_install"
        override val name = "mas install"
        override val description = "Detects Mac App Store install via mas."
        private val pattern = Regex("""\bmas\s+install\b""")

        override fun evaluate(hookInput: HookInput): ProtectionHit? {
            val cmd = CommandParser.bashCommand(hookInput) ?: return null
            if (!pattern.containsMatchIn(cmd)) return null
            return hit(id, "Mac App Store install: $cmd")
        }
    }

    private object SdkmanInstall : ProtectionRule {
        override val id = "sdkman_install"
        override val name = "sdk install"
        override val description = "Detects SDKMAN install."
        private val pattern = Regex("""\bsdk\s+install\b""")

        override fun evaluate(hookInput: HookInput): ProtectionHit? {
            val cmd = CommandParser.bashCommand(hookInput) ?: return null
            if (!pattern.containsMatchIn(cmd)) return null
            return hit(id, "SDKMAN install: $cmd")
        }
    }

    private object WingetInstall : ProtectionRule {
        override val id = "winget_install"
        override val name = "winget install"
        override val description = "Detects Windows Package Manager install."
        private val pattern = Regex("""\bwinget\s+install\b""")

        override fun evaluate(hookInput: HookInput): ProtectionHit? {
            val cmd = CommandParser.bashCommand(hookInput) ?: return null
            if (!pattern.containsMatchIn(cmd)) return null
            return hit(id, "winget install: $cmd")
        }
    }

    private object ChocoInstall : ProtectionRule {
        override val id = "choco_install"
        override val name = "choco install"
        override val description = "Detects Chocolatey install."
        private val pattern = Regex("""\bchoco\s+install\b""")

        override fun evaluate(hookInput: HookInput): ProtectionHit? {
            val cmd = CommandParser.bashCommand(hookInput) ?: return null
            if (!pattern.containsMatchIn(cmd)) return null
            return hit(id, "choco install: $cmd")
        }
    }
}
