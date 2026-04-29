package com.mikepenz.agentbelay.storage

import java.security.SecureRandom
import java.util.Base64
import javax.crypto.Cipher
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/**
 * Application-level AES/GCM column encryption helper.
 *
 * Stored format: `v1:<base64(iv || ciphertext || tag)>`.
 *
 * - `v1:` is a version prefix that lets future migrations distinguish
 *   ciphertexts written under different parameters (key rotation, algorithm
 *   bump, …) without having to walk the schema.
 * - The IV is 12 bytes (NIST-recommended for GCM) and is freshly randomized
 *   per value — never reused with the same key.
 * - The 16-byte GCM auth tag is appended by the JCE implementation and is
 *   verified on decrypt; tampering with any byte causes [decrypt] to throw.
 *
 * Backward compatibility: rows persisted by earlier installs (before
 * encryption was introduced) store plaintext directly. [decrypt] returns
 * any value WITHOUT the `v1:` prefix as-is, so legacy data continues to
 * load. Such values are upgraded to ciphertext the next time they are
 * written through [encrypt] (e.g. on `INSERT OR REPLACE`).
 */
class ColumnCipher(private val key: SecretKey) {

    private val random = SecureRandom()

    fun encrypt(plaintext: String): String {
        val iv = ByteArray(IV_BYTES).also { random.nextBytes(it) }
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, key, GCMParameterSpec(TAG_BITS, iv))
        val ciphertextAndTag = cipher.doFinal(plaintext.toByteArray(Charsets.UTF_8))
        val combined = ByteArray(iv.size + ciphertextAndTag.size).also {
            System.arraycopy(iv, 0, it, 0, iv.size)
            System.arraycopy(ciphertextAndTag, 0, it, iv.size, ciphertextAndTag.size)
        }
        return PREFIX + Base64.getEncoder().encodeToString(combined)
    }

    fun decrypt(stored: String): String {
        // Legacy / backward-compat path: values written before column encryption
        // was introduced have no version prefix — return them as-is. New writes
        // through `encrypt()` will upgrade them in place on the next persist.
        if (!stored.startsWith(PREFIX)) return stored

        val combined = Base64.getDecoder().decode(stored.substring(PREFIX.length))
        require(combined.size > IV_BYTES) { "Encrypted payload too short" }
        val iv = combined.copyOfRange(0, IV_BYTES)
        val ciphertextAndTag = combined.copyOfRange(IV_BYTES, combined.size)
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(TAG_BITS, iv))
        val plaintext = cipher.doFinal(ciphertextAndTag)
        return String(plaintext, Charsets.UTF_8)
    }

    /** Encrypts [plaintext] when non-null. Convenience for nullable column writes. */
    fun encryptNullable(plaintext: String?): String? = plaintext?.let { encrypt(it) }

    /** Decrypts [stored] when non-null. Convenience for nullable column reads. */
    fun decryptNullable(stored: String?): String? = stored?.let { decrypt(it) }

    companion object {
        private const val TRANSFORMATION = "AES/GCM/NoPadding"
        private const val IV_BYTES = 12
        private const val TAG_BITS = 128
        const val PREFIX = "v1:"
    }
}
