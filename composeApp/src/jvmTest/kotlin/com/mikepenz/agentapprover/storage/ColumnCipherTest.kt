package com.mikepenz.agentapprover.storage

import javax.crypto.AEADBadTagException
import javax.crypto.KeyGenerator
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFails
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

class ColumnCipherTest {

    private fun newCipher(): ColumnCipher {
        val key = KeyGenerator.getInstance("AES").apply { init(256) }.generateKey()
        return ColumnCipher(key)
    }

    @Test
    fun roundTripsArbitraryStrings() {
        val cipher = newCipher()
        val samples = listOf(
            "",
            "hello",
            """{"command":"rm -rf /","cwd":"/tmp"}""",
            "unicode — 漢字 — 🔐",
        )
        for (s in samples) {
            val encrypted = cipher.encrypt(s)
            assertTrue(encrypted.startsWith("v1:"), "encrypted value must carry v1: prefix")
            assertNotEquals(s, encrypted)
            assertEquals(s, cipher.decrypt(encrypted))
        }
    }

    @Test
    fun encryptUsesFreshIvPerCall() {
        // Same plaintext should encrypt to different ciphertexts because of
        // the random IV. Without this property, GCM would be catastrophically
        // weak under key reuse.
        val cipher = newCipher()
        val a = cipher.encrypt("same plaintext")
        val b = cipher.encrypt("same plaintext")
        assertNotEquals(a, b)
    }

    @Test
    fun tamperingTriggersAuthFailure() {
        val cipher = newCipher()
        val encrypted = cipher.encrypt("sensitive command")
        // Flip a byte in the base64 payload (skip the v1: prefix and pick a
        // character we know decodes to something different).
        val payload = encrypted.removePrefix("v1:")
        val mutatedChar = if (payload[10] == 'A') 'B' else 'A'
        val tampered = "v1:" + payload.substring(0, 10) + mutatedChar + payload.substring(11)
        val ex = assertFails { cipher.decrypt(tampered) }
        // GCM auth failure surfaces as AEADBadTagException (sometimes wrapped).
        assertTrue(
            ex is AEADBadTagException || ex.cause is AEADBadTagException || ex.message?.contains("tag", ignoreCase = true) == true || ex.message?.contains("Input", ignoreCase = true) == true,
            "expected auth failure, got ${ex::class.simpleName}: ${ex.message}",
        )
    }

    @Test
    fun decryptPassesThroughLegacyUnprefixedValues() {
        // Backward-compat path: rows persisted before column encryption was
        // introduced have no v1: prefix and must be returned as-is.
        val cipher = newCipher()
        val legacy = """{"raw":"value"}"""
        assertEquals(legacy, cipher.decrypt(legacy))
    }

    @Test
    fun nullableHelpersReturnNullForNull() {
        val cipher = newCipher()
        assertEquals(null, cipher.encryptNullable(null))
        assertEquals(null, cipher.decryptNullable(null))
        val enc = cipher.encryptNullable("x")
        assertTrue(enc?.startsWith("v1:") == true)
        assertEquals("x", cipher.decryptNullable(enc))
    }
}
