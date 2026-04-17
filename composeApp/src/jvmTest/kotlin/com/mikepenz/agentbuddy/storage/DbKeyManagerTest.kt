package com.mikepenz.agentbuddy.storage

import java.io.File
import java.nio.file.Files
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class DbKeyManagerTest {

    private lateinit var tempDir: File

    @BeforeTest
    fun setUp() {
        tempDir = Files.createTempDirectory("db-key-test-").toFile()
    }

    @AfterTest
    fun tearDown() {
        tempDir.deleteRecursively()
    }

    @Test
    fun loadOrCreateGeneratesA256BitKeyOnFirstCall() {
        val key = DbKeyManager.loadOrCreate(tempDir.absolutePath, allowKeyring = false)
        assertEquals("AES", key.algorithm)
        assertEquals(32, key.encoded.size, "256-bit key should be 32 bytes")

        val keyFile = File(tempDir, "db.key")
        assertTrue(keyFile.exists(), "db.key should be persisted")
        assertEquals(32, keyFile.length().toInt())
    }

    @Test
    fun loadOrCreateReturnsTheSameBytesOnSubsequentCalls() {
        val first = DbKeyManager.loadOrCreate(tempDir.absolutePath, allowKeyring = false)
        val second = DbKeyManager.loadOrCreate(tempDir.absolutePath, allowKeyring = false)
        assertTrue(first.encoded.contentEquals(second.encoded), "key bytes must be stable across calls")
    }

    @Test
    fun loadOrCreateCreatesTheDataDirIfMissing() {
        val nested = File(tempDir, "nested/data/dir")
        DbKeyManager.loadOrCreate(nested.absolutePath, allowKeyring = false)
        assertTrue(File(nested, "db.key").exists())
    }
}
