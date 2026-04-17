package com.mikepenz.agentbuddy.protection.modules

import com.mikepenz.agentbuddy.model.HookInput
import com.mikepenz.agentbuddy.model.ProtectionMode
import kotlinx.serialization.json.JsonPrimitive
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class DestructiveCommandsModuleTest {

    private val module = DestructiveCommandsModule

    private fun bashHookInput(command: String) = HookInput(
        sessionId = "test-session",
        toolName = "Bash",
        toolInput = mapOf("command" to JsonPrimitive(command)),
        cwd = "/tmp",
    )

    private fun evaluateAll(command: String) =
        module.rules.mapNotNull { it.evaluate(bashHookInput(command)) }

    private fun evaluateRule(ruleId: String, command: String) =
        module.rules.first { it.id == ruleId }.evaluate(bashHookInput(command))

    // --- Module metadata ---

    @Test
    fun moduleMetadata() {
        assertEquals("destructive_commands", module.id)
        assertEquals("Destructive Commands", module.name)
        assertEquals(false, module.corrective)
        assertEquals(ProtectionMode.ASK_AUTO_BLOCK, module.defaultMode)
        assertEquals(setOf("Bash"), module.applicableTools)
        assertEquals(10, module.rules.size)
    }

    // --- rm_rf ---

    @Test
    fun rmRfBlocked() {
        assertNotNull(evaluateRule("rm_rf", "rm -rf src/"))
    }

    @Test
    fun rmRfTmpAllowed() {
        assertNull(evaluateRule("rm_rf", "rm -rf /tmp/build"))
    }

    @Test
    fun rmPlainFileAllowed() {
        assertNull(evaluateRule("rm_rf", "rm file.txt"))
    }

    @Test
    fun rmFrBlocked() {
        assertNotNull(evaluateRule("rm_rf", "rm -fr src/"))
    }

    @Test
    fun rmRecursiveForceBlocked() {
        assertNotNull(evaluateRule("rm_rf", "rm --recursive --force src/"))
    }

    // --- find_delete ---

    @Test
    fun findDeleteBlocked() {
        assertNotNull(evaluateRule("find_delete", "find . -name '*.tmp' -delete"))
    }

    @Test
    fun findExecRmBlocked() {
        assertNotNull(evaluateRule("find_delete", "find . -name '*.tmp' -exec rm {} \\;"))
    }

    @Test
    fun findPlainAllowed() {
        assertNull(evaluateRule("find_delete", "find . -name '*.txt'"))
    }

    // --- xargs_rm ---

    @Test
    fun xargsRmBlocked() {
        assertNotNull(evaluateRule("xargs_rm", "find . | xargs rm"))
    }

    @Test
    fun xargsUnlinkBlocked() {
        assertNotNull(evaluateRule("xargs_rm", "find . | xargs unlink"))
    }

    // --- git_reset_hard ---

    @Test
    fun gitResetHardBlocked() {
        assertNotNull(evaluateRule("git_reset_hard", "git reset --hard HEAD~1"))
    }

    @Test
    fun gitResetSoftAllowed() {
        assertNull(evaluateRule("git_reset_hard", "git reset --soft HEAD~1"))
    }

    // --- git_checkout_files ---

    @Test
    fun gitCheckoutDashDashBlocked() {
        assertNotNull(evaluateRule("git_checkout_files", "git checkout -- src/main.kt"))
    }

    @Test
    fun gitCheckoutDotBlocked() {
        assertNotNull(evaluateRule("git_checkout_files", "git checkout ."))
    }

    @Test
    fun gitCheckoutNewBranchAllowed() {
        assertNull(evaluateRule("git_checkout_files", "git checkout -b new-branch"))
    }

    // --- git_clean_force ---

    @Test
    fun gitCleanForceBlocked() {
        assertNotNull(evaluateRule("git_clean_force", "git clean -fd"))
    }

    @Test
    fun gitCleanDryRunAllowed() {
        assertNull(evaluateRule("git_clean_force", "git clean -n"))
    }

    // --- git_push_force ---

    @Test
    fun gitPushForceBlocked() {
        assertNotNull(evaluateRule("git_push_force", "git push --force origin main"))
    }

    @Test
    fun gitPushForceFlagBlocked() {
        assertNotNull(evaluateRule("git_push_force", "git push -f"))
    }

    @Test
    fun gitPushForceWithLeaseAllowed() {
        assertNull(evaluateRule("git_push_force", "git push --force-with-lease origin main"))
    }

    @Test
    fun gitPushNormalAllowed() {
        assertNull(evaluateRule("git_push_force", "git push origin main"))
    }

    // --- git_branch_force_delete ---

    @Test
    fun gitBranchForceDeleteBlocked() {
        assertNotNull(evaluateRule("git_branch_force_delete", "git branch -D feature"))
    }

    @Test
    fun gitBranchSoftDeleteAllowed() {
        assertNull(evaluateRule("git_branch_force_delete", "git branch -d feature"))
    }

    // --- git_stash_drop ---

    @Test
    fun gitStashDropBlocked() {
        assertNotNull(evaluateRule("git_stash_drop", "git stash drop"))
    }

    @Test
    fun gitStashClearBlocked() {
        assertNotNull(evaluateRule("git_stash_drop", "git stash clear"))
    }

    @Test
    fun gitStashApplyAllowed() {
        assertNull(evaluateRule("git_stash_drop", "git stash apply"))
    }

    // --- truncate_dd ---

    @Test
    fun truncateBlocked() {
        assertNotNull(evaluateRule("truncate_dd", "truncate -s 0 file.log"))
    }

    @Test
    fun ddOfBlocked() {
        assertNotNull(evaluateRule("truncate_dd", "dd if=/dev/zero of=disk.img"))
    }

    // --- Non-Bash tool is ignored ---

    @Test
    fun nonBashToolIgnored() {
        val input = HookInput(
            sessionId = "test-session",
            toolName = "Write",
            toolInput = mapOf("file_path" to JsonPrimitive("/tmp/foo.txt")),
            cwd = "/tmp",
        )
        val hits = module.rules.mapNotNull { it.evaluate(input) }
        assertTrue(hits.isEmpty())
    }
}
