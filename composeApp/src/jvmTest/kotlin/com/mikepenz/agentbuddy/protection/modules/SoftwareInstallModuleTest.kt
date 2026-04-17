package com.mikepenz.agentbuddy.protection.modules

import com.mikepenz.agentbuddy.model.HookInput
import com.mikepenz.agentbuddy.model.ProtectionMode
import kotlinx.serialization.json.JsonPrimitive
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class SoftwareInstallModuleTest {

    private val module = SoftwareInstallModule

    private fun bashHookInput(command: String) = HookInput(
        sessionId = "test-session",
        toolName = "Bash",
        toolInput = mapOf("command" to JsonPrimitive(command)),
        cwd = "/tmp",
    )

    private fun evaluateRule(ruleId: String, command: String) =
        module.rules.first { it.id == ruleId }.evaluate(bashHookInput(command))

    // --- Module metadata ---

    @Test
    fun moduleMetadata() {
        assertEquals("software_install", module.id)
        assertFalse(module.corrective)
        assertEquals(ProtectionMode.ASK_AUTO_BLOCK, module.defaultMode)
        assertEquals(setOf("Bash"), module.applicableTools)
        assertTrue(module.rules.isNotEmpty())
    }

    @Test
    fun ruleIdsUnique() {
        val ids = module.rules.map { it.id }
        assertEquals(ids.size, ids.toSet().size)
    }

    // --- brew ---

    @Test
    fun brewInstallBlocked() {
        assertNotNull(evaluateRule("brew_install", "brew install jq"))
    }

    @Test
    fun brewReinstallBlocked() {
        assertNotNull(evaluateRule("brew_install", "brew reinstall node"))
    }

    @Test
    fun brewTapBlocked() {
        assertNotNull(evaluateRule("brew_install", "brew tap homebrew/cask-versions"))
    }

    @Test
    fun brewCaskInstallBlocked() {
        assertNotNull(evaluateRule("brew_install", "brew cask install firefox"))
    }

    @Test
    fun brewListAllowed() {
        assertNull(evaluateRule("brew_install", "brew list"))
    }

    @Test
    fun brewInfoAllowed() {
        assertNull(evaluateRule("brew_install", "brew info jq"))
    }

    // --- npm global ---

    @Test
    fun npmInstallGlobalBlocked() {
        assertNotNull(evaluateRule("npm_global_install", "npm install -g typescript"))
    }

    @Test
    fun npmIGlobalBlocked() {
        assertNotNull(evaluateRule("npm_global_install", "npm i -g yarn"))
    }

    @Test
    fun npmAddGlobalBlocked() {
        assertNotNull(evaluateRule("npm_global_install", "npm add --global pnpm"))
    }

    @Test
    fun npmLocalInstallAllowed() {
        assertNull(evaluateRule("npm_global_install", "npm install"))
    }

    @Test
    fun npmLocalInstallPackageAllowed() {
        assertNull(evaluateRule("npm_global_install", "npm install react"))
    }

    // --- yarn global ---

    @Test
    fun yarnGlobalAddBlocked() {
        assertNotNull(evaluateRule("yarn_global_install", "yarn global add typescript"))
    }

    @Test
    fun yarnAddLocalAllowed() {
        assertNull(evaluateRule("yarn_global_install", "yarn add react"))
    }

    // --- pnpm global ---

    @Test
    fun pnpmAddGlobalBlocked() {
        assertNotNull(evaluateRule("pnpm_global_install", "pnpm add -g typescript"))
    }

    @Test
    fun pnpmInstallGlobalBlocked() {
        assertNotNull(evaluateRule("pnpm_global_install", "pnpm install --global typescript"))
    }

    @Test
    fun pnpmLocalAddAllowed() {
        assertNull(evaluateRule("pnpm_global_install", "pnpm add react"))
    }

    // --- npx ---

    @Test
    fun npxBlocked() {
        assertNotNull(evaluateRule("npx", "npx create-react-app myapp"))
    }

    @Test
    fun npxWithFlagBlocked() {
        assertNotNull(evaluateRule("npx", "npx --yes cowsay hello"))
    }

    @Test
    fun bareNpxBlocked() {
        assertNotNull(evaluateRule("npx", "npx"))
    }

    // --- pipx ---

    @Test
    fun pipxInstallBlocked() {
        assertNotNull(evaluateRule("pipx_install", "pipx install black"))
    }

    @Test
    fun pipxRunBlocked() {
        assertNotNull(evaluateRule("pipx_install", "pipx run black ."))
    }

    @Test
    fun pipxListAllowed() {
        assertNull(evaluateRule("pipx_install", "pipx list"))
    }

    // --- cargo install ---

    @Test
    fun cargoInstallBlocked() {
        assertNotNull(evaluateRule("cargo_install", "cargo install ripgrep"))
    }

    @Test
    fun cargoBuildAllowed() {
        assertNull(evaluateRule("cargo_install", "cargo build --release"))
    }

    // --- go install ---

    @Test
    fun goInstallBlocked() {
        assertNotNull(evaluateRule("go_install", "go install golang.org/x/tools/cmd/goimports@latest"))
    }

    @Test
    fun goBuildAllowed() {
        assertNull(evaluateRule("go_install", "go build ./..."))
    }

    // --- gem install ---

    @Test
    fun gemInstallBlocked() {
        assertNotNull(evaluateRule("gem_install", "gem install bundler"))
    }

    @Test
    fun gemListAllowed() {
        assertNull(evaluateRule("gem_install", "gem list"))
    }

    // --- apt / apt-get ---

    @Test
    fun aptInstallBlocked() {
        assertNotNull(evaluateRule("apt_install", "apt install curl"))
    }

    @Test
    fun aptGetInstallBlocked() {
        assertNotNull(evaluateRule("apt_install", "sudo apt-get install -y git"))
    }

    @Test
    fun aptListAllowed() {
        assertNull(evaluateRule("apt_install", "apt list --installed"))
    }

    // --- yum / dnf ---

    @Test
    fun yumInstallBlocked() {
        assertNotNull(evaluateRule("yum_dnf_install", "sudo yum install httpd"))
    }

    @Test
    fun dnfInstallBlocked() {
        assertNotNull(evaluateRule("yum_dnf_install", "dnf install git"))
    }

    // --- pacman ---

    @Test
    fun pacmanSyncBlocked() {
        assertNotNull(evaluateRule("pacman_install", "sudo pacman -S neovim"))
    }

    @Test
    fun pacmanSyncLongBlocked() {
        assertNotNull(evaluateRule("pacman_install", "pacman --sync neovim"))
    }

    @Test
    fun pacmanQueryAllowed() {
        assertNull(evaluateRule("pacman_install", "pacman -Q"))
    }

    @Test
    fun pacmanSyuBlocked() {
        assertNotNull(evaluateRule("pacman_install", "sudo pacman -Syu"))
    }

    @Test
    fun pacmanSyBlocked() {
        assertNotNull(evaluateRule("pacman_install", "sudo pacman -Sy neovim"))
    }

    @Test
    fun pacmanQuerySearchAllowed() {
        assertNull(evaluateRule("pacman_install", "pacman -Qs neovim"))
    }

    // --- snap ---

    @Test
    fun snapInstallBlocked() {
        assertNotNull(evaluateRule("snap_install", "sudo snap install code --classic"))
    }

    // --- mas ---

    @Test
    fun masInstallBlocked() {
        assertNotNull(evaluateRule("mas_install", "mas install 497799835"))
    }

    // --- sdkman ---

    @Test
    fun sdkInstallBlocked() {
        assertNotNull(evaluateRule("sdkman_install", "sdk install java 21.0.2-tem"))
    }

    @Test
    fun sdkListAllowed() {
        assertNull(evaluateRule("sdkman_install", "sdk list java"))
    }

    // --- winget / choco ---

    @Test
    fun wingetInstallBlocked() {
        assertNotNull(evaluateRule("winget_install", "winget install Git.Git"))
    }

    @Test
    fun chocoInstallBlocked() {
        assertNotNull(evaluateRule("choco_install", "choco install git"))
    }
}
