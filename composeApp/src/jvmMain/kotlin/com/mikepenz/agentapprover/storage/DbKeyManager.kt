package com.mikepenz.agentapprover.storage

import co.touchlab.kermit.Logger
import com.github.javakeyring.BackendNotSupportedException
import com.github.javakeyring.Keyring
import com.github.javakeyring.PasswordAccessException
import java.io.File
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.nio.file.attribute.FileAttribute
import java.nio.file.attribute.PosixFilePermissions
import java.util.Base64
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.SecretKeySpec

/**
 * Manages the long-lived AES-256 key used by [ColumnCipher] to encrypt
 * sensitive history columns at rest.
 *
 * Storage strategy, in order of preference:
 *   1. Platform keyring — macOS Keychain, Windows Credential Manager, or
 *      freedesktop Secret Service on Linux. The OS gates access by user
 *      login session, so this resists offline disk reads in a way a flat
 *      file cannot.
 *   2. Fallback file at `<dataDir>/db.key` (POSIX `rw-------`; on Windows
 *      the file inherits the user's default ACL — documented limitation in
 *      SECURITY.md). Used when the platform keyring is unavailable (headless
 *      Linux session, locked keyring, missing Secret Service daemon, etc.).
 */
object DbKeyManager {

    private val logger = Logger.withTag("DbKeyManager")
    private const val KEY_FILE_NAME = "db.key"
    private const val KEY_BITS = 256
    private const val KEY_BYTES = KEY_BITS / 8
    private const val ALGORITHM = "AES"
    private const val KEYRING_SERVICE = "com.mikepenz.agentapprover"
    private const val KEYRING_ACCOUNT = "db.key"

    /**
     * Returns the AES key, generating and persisting a new 256-bit random
     * key on first call. Subsequent calls return the same key.
     *
     * @param dataDir location of the fallback `db.key` file
     * @param allowKeyring set to false in tests to force file-backed storage
     */
    fun loadOrCreate(dataDir: String, allowKeyring: Boolean = true): SecretKey {
        val dir = File(dataDir).also { if (!it.exists()) it.mkdirs() }
        val keyFile = File(dir, KEY_FILE_NAME)

        if (allowKeyring) {
            val fromKeyring = tryKeyring()
            if (fromKeyring != null) return fromKeyring
        }

        return loadOrCreateFile(dir, keyFile)
    }

    /**
     * Returns the AES key via the platform keyring, or null if the keyring
     * backend is unavailable / unusable on this host (caller falls back to
     * the file path).
     */
    private fun tryKeyring(): SecretKey? {
        val keyring = try {
            Keyring.create()
        } catch (e: BackendNotSupportedException) {
            logger.i { "Platform keyring unavailable (${e.message}); using file-based key storage" }
            return null
        } catch (e: Throwable) {
            logger.w { "Unexpected error creating keyring: ${e.message}; using file-based key storage" }
            return null
        }

        return keyring.use { kr ->
            val existing = try {
                kr.getPassword(KEYRING_SERVICE, KEYRING_ACCOUNT)
            } catch (_: PasswordAccessException) {
                null
            } catch (e: Throwable) {
                logger.w { "Unexpected error reading keyring: ${e.message}; using file-based key storage" }
                return@use null
            }

            if (existing != null) {
                val bytes = decodeKeyringValue(existing)
                if (bytes != null) return@use SecretKeySpec(bytes, ALGORITHM)
                logger.w { "Keyring entry has unexpected format; regenerating" }
            }

            val key = generateKey()
            try {
                kr.setPassword(KEYRING_SERVICE, KEYRING_ACCOUNT, encodeKeyringValue(key.encoded))
                logger.i { "Generated new database key (stored in platform keyring)" }
                key
            } catch (e: Throwable) {
                logger.w { "Failed to write key to keyring: ${e.message}; using file-based key storage" }
                null
            }
        }
    }

    private fun loadOrCreateFile(dir: File, keyFile: File): SecretKey {
        if (keyFile.exists()) {
            val bytes = keyFile.readBytes()
            require(bytes.size == KEY_BYTES) {
                "Existing $KEY_FILE_NAME has unexpected length ${bytes.size} (expected $KEY_BYTES)"
            }
            return SecretKeySpec(bytes, ALGORITHM)
        }

        val key = generateKey()
        writeKeyAtomically(dir, keyFile, key.encoded)
        logger.i { "Generated new database key (stored in $KEY_FILE_NAME)" }
        return key
    }

    private fun generateKey(): SecretKey =
        KeyGenerator.getInstance(ALGORITHM).apply { init(KEY_BITS) }.generateKey()

    private fun encodeKeyringValue(bytes: ByteArray): String =
        Base64.getEncoder().encodeToString(bytes)

    private fun decodeKeyringValue(value: String): ByteArray? = try {
        val decoded = Base64.getDecoder().decode(value)
        if (decoded.size == KEY_BYTES) decoded else null
    } catch (_: IllegalArgumentException) {
        null
    }

    /**
     * Writes [keyBytes] to [keyFile] via a temp file + atomic rename so a crash
     * or partial write cannot leave a truncated key behind. On POSIX the temp
     * file is created with `rw-------` up front (so there is no observable
     * window where the key is world-readable). On Windows the file inherits
     * the user's default ACL — documented limitation in SECURITY.md.
     */
    private fun writeKeyAtomically(dir: File, keyFile: File, keyBytes: ByteArray) {
        val dirPath = dir.toPath()
        val posixAttr: FileAttribute<*>? = try {
            PosixFilePermissions.asFileAttribute(PosixFilePermissions.fromString("rw-------"))
        } catch (_: UnsupportedOperationException) {
            null
        }
        val temp = if (posixAttr != null) {
            Files.createTempFile(dirPath, "db.key", ".tmp", posixAttr)
        } else {
            Files.createTempFile(dirPath, "db.key", ".tmp")
        }
        try {
            Files.write(temp, keyBytes)
            try {
                Files.move(temp, keyFile.toPath(), StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING)
            } catch (_: UnsupportedOperationException) {
                Files.move(temp, keyFile.toPath(), StandardCopyOption.REPLACE_EXISTING)
            }
            // Re-assert permissions on the final path (ATOMIC_MOVE preserves them
            // on most POSIX filesystems, but some tmpfs implementations drop
            // them; this is a defensive no-op on platforms where they're kept).
            applyOwnerOnlyPermissions(keyFile)
        } catch (e: Exception) {
            try { Files.deleteIfExists(temp) } catch (_: Exception) { /* best effort */ }
            throw e
        }
    }

    private fun applyOwnerOnlyPermissions(file: File) {
        try {
            val perms = PosixFilePermissions.fromString("rw-------")
            Files.setPosixFilePermissions(file.toPath(), perms)
        } catch (_: UnsupportedOperationException) {
            // Windows or another non-POSIX filesystem. Documented limitation —
            // the file inherits the user's default ACL instead.
        } catch (e: Exception) {
            logger.w { "Failed to set 0600 permissions on db key file: ${e.message}" }
        }
    }

}
