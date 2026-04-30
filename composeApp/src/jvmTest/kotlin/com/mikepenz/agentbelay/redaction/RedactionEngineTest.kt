package com.mikepenz.agentbelay.redaction

import com.mikepenz.agentbelay.model.RedactionMode
import com.mikepenz.agentbelay.model.RedactionModuleSettings
import com.mikepenz.agentbelay.model.RedactionSettings
import com.mikepenz.agentbelay.redaction.modules.ApiKeyRedactionModule
import com.mikepenz.agentbelay.redaction.modules.EnvVarRedactionModule
import com.mikepenz.agentbelay.redaction.modules.JwtRedactionModule
import com.mikepenz.agentbelay.redaction.modules.PrivateKeyRedactionModule
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class RedactionEngineTest {

    private fun engine(
        settings: RedactionSettings = RedactionSettings(),
    ): RedactionEngine = RedactionEngine(
        modules = builtInRedactionModules,
        settingsProvider = { settings },
    )

    @Test
    fun `bash stdout AWS access key is redacted`() {
        val response = buildJsonObject {
            put("stdout", JsonPrimitive("export AWS_ACCESS_KEY_ID=AKIAIOSFODNN7EXAMPLE"))
            put("stderr", JsonPrimitive(""))
            put("interrupted", JsonPrimitive(false))
            put("isImage", JsonPrimitive(false))
        }

        val result = engine().scan("Bash", response)

        // Two hits expected: env-var rule on the assignment + api-key rule on AKIA literal.
        assertTrue(result.hits.any { it.moduleId == "api-keys" && it.ruleId == "aws-access-key" })
        val redacted = assertNotNull(result.redactedOutput)
        val stdout = redacted["stdout"]!!.jsonPrimitive.content
        assertTrue("AKIAIOSFODNN7EXAMPLE" !in stdout, "Original key must be replaced; got: $stdout")
        assertTrue("[REDACTED:" in stdout)
        // Schema preserved
        assertEquals(false, redacted["interrupted"]!!.jsonPrimitive.content.toBoolean())
    }

    @Test
    fun `read content jwt is redacted`() {
        val jwt = "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJzdWIiOiIxMjM0NTY3ODkwIn0.SflKxwRJSMeKKF2QT4fwpMeJf36POk6yJV_adQssw5c"
        val response = buildJsonObject {
            put("content", JsonPrimitive("Authorization: Bearer $jwt"))
        }

        val result = engine().scan("Read", response)

        assertTrue(result.hits.any { it.moduleId == "jwt" })
        val redacted = assertNotNull(result.redactedOutput)
        assertTrue(jwt !in redacted["content"]!!.jsonPrimitive.content)
    }

    @Test
    fun `private key block is redacted across newlines`() {
        val key = """
            -----BEGIN RSA PRIVATE KEY-----
            MIIEpAIBAAKCAQEA1234567890abcdefABCDEF
            -----END RSA PRIVATE KEY-----
        """.trimIndent()
        val response = buildJsonObject { put("content", JsonPrimitive(key)) }
        val result = engine().scan("Read", response)

        assertTrue(result.hits.any { it.moduleId == "private-keys" })
        val redacted = assertNotNull(result.redactedOutput)
        assertTrue("MIIEpAIBA" !in redacted["content"]!!.jsonPrimitive.content)
    }

    @Test
    fun `env var module preserves the credential key name`() {
        val response = buildJsonObject {
            put("stdout", JsonPrimitive("DATABASE_PASSWORD=hunter2"))
            put("stderr", JsonPrimitive(""))
        }
        val result = engine().scan("Bash", response)

        val redacted = assertNotNull(result.redactedOutput)
        val stdout = redacted["stdout"]!!.jsonPrimitive.content
        assertTrue(stdout.startsWith("DATABASE_PASSWORD="), "Key should still be visible: $stdout")
        assertTrue("hunter2" !in stdout, "Value should be redacted: $stdout")
    }

    @Test
    fun `disabled module does not redact`() {
        val response = buildJsonObject {
            put("stdout", JsonPrimitive("token=ghp_aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa"))
            put("stderr", JsonPrimitive(""))
        }

        val result = engine(
            RedactionSettings(
                modules = mapOf(
                    ApiKeyRedactionModule.id to RedactionModuleSettings(mode = RedactionMode.DISABLED),
                    EnvVarRedactionModule.id to RedactionModuleSettings(mode = RedactionMode.DISABLED),
                    JwtRedactionModule.id to RedactionModuleSettings(mode = RedactionMode.DISABLED),
                    PrivateKeyRedactionModule.id to RedactionModuleSettings(mode = RedactionMode.DISABLED),
                ),
            )
        ).scan("Bash", response)

        assertTrue(result.hits.isEmpty())
        assertNull(result.redactedOutput)
    }

    @Test
    fun `log only mode records hit but does not modify output`() {
        val response = buildJsonObject {
            put("stdout", JsonPrimitive("AKIAIOSFODNN7EXAMPLE"))
            put("stderr", JsonPrimitive(""))
        }

        val result = engine(
            RedactionSettings(
                modules = mapOf(
                    ApiKeyRedactionModule.id to RedactionModuleSettings(mode = RedactionMode.LOG_ONLY),
                    EnvVarRedactionModule.id to RedactionModuleSettings(mode = RedactionMode.DISABLED),
                ),
            )
        ).scan("Bash", response)

        assertTrue(result.hits.any { it.ruleId == "aws-access-key" })
        // No ENABLED hits, so output is not rebuilt.
        assertNull(result.redactedOutput)
    }

    @Test
    fun `master switch off short-circuits the engine`() {
        val response = buildJsonObject {
            put("stdout", JsonPrimitive("AKIAIOSFODNN7EXAMPLE"))
            put("stderr", JsonPrimitive(""))
        }
        val result = engine(RedactionSettings(enabled = false)).scan("Bash", response)
        assertTrue(result.hits.isEmpty())
        assertNull(result.redactedOutput)
    }

    @Test
    fun `unsupported tool is a no-op`() {
        val response = buildJsonObject { put("file_path", JsonPrimitive("/etc/passwd")) }
        val result = engine().scan("Edit", response)
        assertTrue(result.hits.isEmpty())
        assertNull(result.redactedOutput)
    }

    @Test
    fun `clean output produces empty result`() {
        val response = buildJsonObject {
            put("stdout", JsonPrimitive("hello world"))
            put("stderr", JsonPrimitive(""))
        }
        val result = engine().scan("Bash", response)
        assertTrue(result.hits.isEmpty())
        assertNull(result.redactedOutput)
    }
}
