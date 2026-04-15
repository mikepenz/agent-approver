package com.mikepenz.agentapprover.protection.modules

import com.mikepenz.agentapprover.model.HookInput
import com.mikepenz.agentapprover.model.ProtectionHit
import com.mikepenz.agentapprover.model.ProtectionMode
import kotlinx.serialization.json.JsonPrimitive
import java.io.File
import java.nio.file.Files
import java.util.UUID
import java.util.concurrent.TimeUnit
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import org.junit.Assume.assumeTrue

class GitAwareGuardModuleTest {

    private val module = GitAwareGuardModule

    private val createdDirs = mutableListOf<File>()

    private fun newTempDir(): File {
        val dir = Files.createTempDirectory("git-aware-guard-${UUID.randomUUID()}-").toFile()
        createdDirs.add(dir)
        return dir
    }

    @AfterTest
    fun cleanup() {
        createdDirs.forEach { d -> d.walkBottomUp().forEach { it.delete() } }
        createdDirs.clear()
    }

    private fun bashHookInput(command: String, cwd: String) = HookInput(
        sessionId = "test-session",
        toolName = "Bash",
        toolInput = mapOf("command" to JsonPrimitive(command)),
        cwd = cwd,
    )

    private fun writeHookInput(filePath: String, cwd: String) = HookInput(
        sessionId = "test-session",
        toolName = "Write",
        toolInput = mapOf("file_path" to JsonPrimitive(filePath)),
        cwd = cwd,
    )

    private fun evaluateAll(input: HookInput): List<ProtectionHit> =
        module.rules.mapNotNull { it.evaluate(input) }

    private fun gitAvailable(): Boolean = try {
        val p = ProcessBuilder("git", "--version").redirectErrorStream(true).start()
        p.waitFor(5, TimeUnit.SECONDS) && p.exitValue() == 0
    } catch (_: Exception) {
        false
    }

    private fun gitInit(dir: File) {
        val process = ProcessBuilder("git", "init", "-q")
            .directory(dir)
            .redirectErrorStream(true)
            .start()
        assertTrue(process.waitFor(10, TimeUnit.SECONDS), "git init timed out")
        assertEquals(0, process.exitValue(), "git init failed: ${process.inputStream.bufferedReader().readText()}")
        // Disable any global GPG signing or hooks that might fail in CI sandboxes by setting
        // a local config that won't interfere with status reporting.
        ProcessBuilder("git", "config", "user.email", "test@example.com").directory(dir).start().waitFor()
        ProcessBuilder("git", "config", "user.name", "Test").directory(dir).start().waitFor()
        ProcessBuilder("git", "config", "commit.gpgsign", "false").directory(dir).start().waitFor()
    }

    // --- Module metadata -------------------------------------------------------

    @Test
    fun moduleMetadata() {
        assertEquals("git_aware_guard", module.id)
        assertEquals("Git-aware Guard", module.name)
        assertEquals(false, module.corrective)
        assertEquals(ProtectionMode.ASK, module.defaultMode)
        assertEquals(setOf("Bash", "Write", "Edit"), module.applicableTools)
        assertEquals(3, module.rules.size)
        assertEquals(
            setOf("destructive_outside_git", "write_outside_git", "destructive_in_dirty_repo"),
            module.rules.map { it.id }.toSet(),
        )
    }

    // --- destructive_outside_git ----------------------------------------------

    @Test
    fun rmRfInNonGitDirectory_hits() {
        val cwd = newTempDir().absolutePath
        val hits = evaluateAll(bashHookInput("rm -rf build/", cwd))
        val outsideHit = hits.firstOrNull { it.ruleId == "destructive_outside_git" }
        assertNotNull(outsideHit, "expected destructive_outside_git hit, got $hits")
        assertTrue(outsideHit.message.contains(cwd))
    }

    @Test
    fun rmRfInCleanGitRepo_noHit() {
        assumeTrue("git not available on PATH", gitAvailable())
        val dir = newTempDir()
        gitInit(dir)
        // No working tree changes -> `git status --porcelain` is empty -> clean.
        val hits = evaluateAll(bashHookInput("rm -rf build/", dir.absolutePath))
        assertNull(
            hits.firstOrNull { it.ruleId == "destructive_outside_git" },
            "should not flag clean git repo as outside-git",
        )
        assertNull(
            hits.firstOrNull { it.ruleId == "destructive_in_dirty_repo" },
            "should not flag clean git repo as dirty",
        )
    }

    @Test
    fun rmRfInDirtyGitRepo_dirtyHit() {
        assumeTrue("git not available on PATH", gitAvailable())
        val dir = newTempDir()
        gitInit(dir)
        File(dir, "untracked.txt").writeText("hello")
        val hits = evaluateAll(bashHookInput("rm -rf build/", dir.absolutePath))
        val dirtyHit = hits.firstOrNull { it.ruleId == "destructive_in_dirty_repo" }
        assertNotNull(dirtyHit, "expected destructive_in_dirty_repo hit, got $hits")
        // Outside-git rule should NOT fire here.
        assertNull(hits.firstOrNull { it.ruleId == "destructive_outside_git" })
    }

    @Test
    fun nonDestructiveCommand_noHit() {
        val cwd = newTempDir().absolutePath
        val hits = evaluateAll(bashHookInput("ls -la", cwd))
        assertTrue(hits.isEmpty(), "expected no hits for ls -la, got $hits")
    }

    /**
     * `rm -i foo.txt` is interactive and non-recursive — it should NOT trip the
     * destructive detector. This documents intentional behaviour.
     */
    @Test
    fun nonRecursiveInteractiveRm_noHit() {
        val cwd = newTempDir().absolutePath
        val hits = evaluateAll(bashHookInput("rm -i foo.txt", cwd))
        assertTrue(
            hits.none { it.ruleId == "destructive_outside_git" || it.ruleId == "destructive_in_dirty_repo" },
            "rm -i should not be considered destructive for this module, got $hits",
        )
    }

    // --- write_outside_git -----------------------------------------------------

    @Test
    fun writeUnderNonGitCwd_hits() {
        val cwd = newTempDir().absolutePath
        val hits = evaluateAll(writeHookInput("$cwd/notes.md", cwd))
        val hit = hits.firstOrNull { it.ruleId == "write_outside_git" }
        assertNotNull(hit, "expected write_outside_git hit, got $hits")
    }

    @Test
    fun writeRelativePathUnderNonGitCwd_hits() {
        val cwd = newTempDir().absolutePath
        val hits = evaluateAll(writeHookInput("notes.md", cwd))
        val hit = hits.firstOrNull { it.ruleId == "write_outside_git" }
        assertNotNull(hit, "expected write_outside_git hit for relative path, got $hits")
    }

    @Test
    fun writeInGitRepo_noWriteOutsideHit() {
        assumeTrue("git not available on PATH", gitAvailable())
        val dir = newTempDir()
        gitInit(dir)
        val cwd = dir.absolutePath
        val hits = evaluateAll(writeHookInput("$cwd/notes.md", cwd))
        assertNull(hits.firstOrNull { it.ruleId == "write_outside_git" })
    }

    @Test
    fun writeOutsideCwd_noHit() {
        val cwd = newTempDir().absolutePath
        // Target lives elsewhere — let other modules (e.g. AbsolutePathsModule) handle it.
        val hits = evaluateAll(writeHookInput("/var/tmp/elsewhere.txt", cwd))
        assertNull(hits.firstOrNull { it.ruleId == "write_outside_git" })
    }

    // --- helpers ---------------------------------------------------------------

    @Test
    fun findGitRoot_walksUp() {
        assumeTrue("git not available on PATH", gitAvailable())
        val root = newTempDir()
        gitInit(root)
        val nested = File(root, "a/b/c").apply { mkdirs() }
        val found = module.findGitRoot(nested.absolutePath)
        assertNotNull(found)
        assertEquals(root.canonicalFile, found.canonicalFile)
    }

    /**
     * Linked worktrees and git submodules use a `.git` *file* (containing e.g.
     * `gitdir: /path/to/real/gitdir`) instead of a `.git` directory. `findGitRoot`
     * documents support for this — verify it without requiring the git binary.
     */
    @Test
    fun findGitRoot_supportsDotGitAsFile() {
        val root = newTempDir()
        File(root, ".git").writeText("gitdir: /tmp/fake/.git/worktrees/wt\n")
        val nested = File(root, "sub/dir").apply { mkdirs() }
        val found = module.findGitRoot(nested.absolutePath)
        assertNotNull(found, "expected findGitRoot to treat .git file as repo root")
        assertEquals(root.canonicalFile, found.canonicalFile)
    }

    @Test
    fun findGitRoot_returnsNullOutsideRepo() {
        val nested = File(newTempDir(), "a/b").apply { mkdirs() }
        assertNull(module.findGitRoot(nested.absolutePath))
    }
}
