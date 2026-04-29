package com.mikepenz.agentbelay.storage

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.io.File
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class LegacyDataMigrationTest {

    private lateinit var fakeHome: File
    private lateinit var originalHome: String
    private val json = Json { ignoreUnknownKeys = true; prettyPrint = true }

    /**
     * Test fixture for the Approver → Belay migration step. Real callers
     * (Main.kt) chain Approver → Buddy → Belay, but for migration unit tests
     * we collapse to one step so each test exercises a single rename.
     */
    private fun approverToBelayStep(legacyDataDir: String, newDataDir: String) =
        LegacyDataMigration.Step(
            legacyDataDir = legacyDataDir,
            newDataDir = newDataDir,
            legacyDbFile = "agent-approver.db",
            newDbFile = "agent-belay.db",
            legacyHookDirName = ".agent-approver",
            newHookDirName = ".agent-belay",
            legacyCopilotHookFile = "agent-approver.json",
            newCopilotHookFile = "agent-belay.json",
        )

    @BeforeTest
    fun setUp() {
        originalHome = System.getProperty("user.home")
        fakeHome = File(System.getProperty("java.io.tmpdir"), "migration-test-${System.nanoTime()}")
        fakeHome.mkdirs()
        System.setProperty("user.home", fakeHome.absolutePath)
    }

    @AfterTest
    fun tearDown() {
        System.setProperty("user.home", originalHome)
        fakeHome.deleteRecursively()
    }

    @Test
    fun `cleanInstall_doesNothing`() {
        val legacyDataDir = File(fakeHome, "legacy-data").absolutePath
        val newDataDir = File(fakeHome, "new-data").absolutePath
        LegacyDataMigration.run(approverToBelayStep(legacyDataDir, newDataDir))
        assertFalse(File(legacyDataDir).exists())
        assertFalse(File(newDataDir).exists())
    }

    @Test
    fun `dataDir_isRenamedAndDbFileRenamed`() {
        val legacyDir = File(fakeHome, "Library/Application Support/AgentApprover").also { it.mkdirs() }
        File(legacyDir, "agent-approver.db").writeText("hello")
        File(legacyDir, "settings.json").writeText("{}")
        val newDir = File(fakeHome, "Library/Application Support/AgentBelay")

        LegacyDataMigration.run(approverToBelayStep(legacyDir.absolutePath, newDir.absolutePath))

        assertFalse(legacyDir.exists())
        assertTrue(newDir.isDirectory)
        assertTrue(File(newDir, "agent-belay.db").exists())
        assertFalse(File(newDir, "agent-approver.db").exists())
        assertEquals("hello", File(newDir, "agent-belay.db").readText())
        assertTrue(File(newDir, "settings.json").exists())
    }

    @Test
    fun `newDataDirAlreadyExists_leavesLegacyAlone`() {
        val legacyDir = File(fakeHome, "legacy-data").also { it.mkdirs() }
        File(legacyDir, "agent-approver.db").writeText("legacy")
        val newDir = File(fakeHome, "new-data").also { it.mkdirs() }
        File(newDir, "agent-belay.db").writeText("fresh")

        LegacyDataMigration.run(approverToBelayStep(legacyDir.absolutePath, newDir.absolutePath))

        assertTrue(legacyDir.exists(), "legacy dir must be preserved when new already exists")
        assertEquals("fresh", File(newDir, "agent-belay.db").readText())
    }

    @Test
    fun `hookBridgeDir_isRenamedAndScriptContentsRewritten`() {
        val legacyHook = File(fakeHome, ".agent-approver").also { it.mkdirs() }
        val legacyScriptPath = legacyHook.absolutePath + "/copilot-hook.sh"
        File(legacyHook, "copilot-hook.sh").apply {
            writeText("#!/usr/bin/env bash\nURL=\"http://localhost:19532/approve\"\n# Path: $legacyScriptPath\n")
            setExecutable(true)
        }

        LegacyDataMigration.run(approverToBelayStep("/tmp/nonexistent-legacy", "/tmp/nonexistent-new"))

        val newHook = File(fakeHome, ".agent-belay")
        assertFalse(legacyHook.exists())
        assertTrue(newHook.isDirectory)
        val migrated = File(newHook, "copilot-hook.sh")
        assertTrue(migrated.exists())
        val content = migrated.readText()
        assertTrue(content.contains(newHook.absolutePath), "script should reference new hook dir: $content")
        assertFalse(content.contains(legacyHook.absolutePath), "script should not reference legacy hook dir: $content")
    }

    @Test
    fun `copilotHookFile_isMovedAndPathsRewritten`() {
        val copilotDir = File(fakeHome, ".copilot/hooks").also { it.mkdirs() }
        val legacyCopilotPath = File(fakeHome, ".agent-approver").absolutePath
        val newCopilotPath = File(fakeHome, ".agent-belay").absolutePath
        val legacyJson = """
            {
              "version": 1,
              "hooks": {
                "preToolUse": [
                  { "type": "command", "bash": "$legacyCopilotPath/copilot-hook.sh", "timeoutSec": 300 }
                ]
              }
            }
        """.trimIndent()
        File(copilotDir, "agent-approver.json").writeText(legacyJson)

        LegacyDataMigration.run(approverToBelayStep("/tmp/no-legacy", "/tmp/no-new"))

        assertFalse(File(copilotDir, "agent-approver.json").exists())
        val belayFile = File(copilotDir, "agent-belay.json")
        assertTrue(belayFile.exists())
        val rewritten = belayFile.readText()
        assertTrue(rewritten.contains(newCopilotPath), "rewritten json should reference new path: $rewritten")
        assertFalse(rewritten.contains(legacyCopilotPath), "rewritten json should not reference legacy path: $rewritten")
    }

    @Test
    fun `claudeSettings_sessionStartCommandPaths_rewritten`() {
        val claudeDir = File(fakeHome, ".claude").also { it.mkdirs() }
        val legacyScript = File(fakeHome, ".agent-approver/claude-session-start.sh").absolutePath
        val settings = """
            {
              "hooks": {
                "PermissionRequest": [
                  { "matcher": "", "hooks": [ { "type": "http", "url": "http://localhost:19532/approve" } ] }
                ],
                "SessionStart": [
                  { "matcher": "", "hooks": [ { "type": "command", "command": "$legacyScript" } ] }
                ]
              }
            }
        """.trimIndent()
        File(claudeDir, "settings.json").writeText(settings)

        LegacyDataMigration.run(approverToBelayStep("/tmp/no-legacy", "/tmp/no-new"))

        val newScript = File(fakeHome, ".agent-belay/claude-session-start.sh").absolutePath
        val root = json.parseToJsonElement(File(claudeDir, "settings.json").readText()).jsonObject
        val ss = root["hooks"]!!.jsonObject["SessionStart"]!!.jsonArray
        val cmd = ss[0].jsonObject["hooks"]!!.jsonArray[0].jsonObject["command"]!!.jsonPrimitive.content
        assertEquals(newScript, cmd)

        // Unrelated events must be preserved untouched.
        val perm = root["hooks"]!!.jsonObject["PermissionRequest"]!!.jsonArray
        assertEquals(1, perm.size)
    }
}
