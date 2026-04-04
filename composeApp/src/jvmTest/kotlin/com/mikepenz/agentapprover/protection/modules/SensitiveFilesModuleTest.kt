package com.mikepenz.agentapprover.protection.modules

import com.mikepenz.agentapprover.model.HookInput
import com.mikepenz.agentapprover.model.ProtectionMode
import kotlinx.serialization.json.JsonPrimitive
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class SensitiveFilesModuleTest {

    private val module = SensitiveFilesModule

    private fun bashHookInput(command: String) = HookInput(
        sessionId = "test-session",
        toolName = "Bash",
        toolInput = mapOf("command" to JsonPrimitive(command)),
        cwd = "/tmp",
    )

    private fun writeHookInput(filePath: String) = HookInput(
        sessionId = "test-session",
        toolName = "Write",
        toolInput = mapOf("file_path" to JsonPrimitive(filePath)),
        cwd = "/tmp",
    )

    private fun editHookInput(filePath: String) = HookInput(
        sessionId = "test-session",
        toolName = "Edit",
        toolInput = mapOf("file_path" to JsonPrimitive(filePath)),
        cwd = "/tmp",
    )

    private fun readHookInput(filePath: String) = HookInput(
        sessionId = "test-session",
        toolName = "Read",
        toolInput = mapOf("file_path" to JsonPrimitive(filePath)),
        cwd = "/tmp",
    )

    private fun evaluateAll(input: HookInput) =
        module.rules.mapNotNull { it.evaluate(input) }

    // --- Module metadata ---

    @Test
    fun moduleMetadata() {
        assertEquals("sensitive_files", module.id)
        assertEquals("Sensitive File Protection", module.name)
        assertEquals(false, module.corrective)
        assertEquals(ProtectionMode.AUTO_BLOCK, module.defaultMode)
        assertEquals(setOf("Bash", "Write", "Edit"), module.applicableTools)
        assertEquals(11, module.rules.size)
    }

    // --- env_files ---

    @Test
    fun envFileBlocked() {
        assertNotNull(evaluateAll(writeHookInput("/project/.env")).firstOrNull())
    }

    @Test
    fun envProductionBlocked() {
        assertNotNull(evaluateAll(writeHookInput("/project/.env.production")).firstOrNull())
    }

    @Test
    fun envExampleAllowed() {
        assertTrue(evaluateAll(writeHookInput("/project/.env.example")).isEmpty())
    }

    @Test
    fun envSampleAllowed() {
        assertTrue(evaluateAll(writeHookInput("/project/.env.sample")).isEmpty())
    }

    @Test
    fun envTemplateAllowed() {
        assertTrue(evaluateAll(writeHookInput("/project/.env.template")).isEmpty())
    }

    // --- crypto_keys ---

    @Test
    fun pemBlocked() {
        assertNotNull(evaluateAll(writeHookInput("/certs/server.pem")).firstOrNull())
    }

    @Test
    fun privateKeyBlocked() {
        assertNotNull(evaluateAll(writeHookInput("/keys/private.key")).firstOrNull())
    }

    @Test
    fun p12Blocked() {
        assertNotNull(evaluateAll(writeHookInput("/certs/cert.p12")).firstOrNull())
    }

    // --- ssh_dir ---

    @Test
    fun sshIdRsaBlocked() {
        assertNotNull(evaluateAll(writeHookInput("/home/user/.ssh/id_rsa")).firstOrNull())
    }

    @Test
    fun sshConfigBlocked() {
        assertNotNull(evaluateAll(writeHookInput("/home/user/.ssh/config")).firstOrNull())
    }

    // --- cloud_creds ---

    @Test
    fun awsCredentialsBlocked() {
        assertNotNull(evaluateAll(writeHookInput("/home/user/.aws/credentials")).firstOrNull())
    }

    @Test
    fun gcpKeyBlocked() {
        assertNotNull(evaluateAll(writeHookInput("/home/user/.gcp/key.json")).firstOrNull())
    }

    @Test
    fun azureConfigBlocked() {
        assertNotNull(evaluateAll(writeHookInput("/home/user/.azure/config")).firstOrNull())
    }

    // --- secret_files ---

    @Test
    fun secretJsonBlocked() {
        assertNotNull(evaluateAll(writeHookInput("/project/secret.json")).firstOrNull())
    }

    @Test
    fun credentialsYamlBlocked() {
        assertNotNull(evaluateAll(writeHookInput("/project/credentials.yaml")).firstOrNull())
    }

    // --- auth_configs ---

    @Test
    fun netrcBlocked() {
        assertNotNull(evaluateAll(writeHookInput("/home/user/.netrc")).firstOrNull())
    }

    @Test
    fun npmrcBlocked() {
        assertNotNull(evaluateAll(writeHookInput("/home/user/.npmrc")).firstOrNull())
    }

    // --- terraform_state ---

    @Test
    fun terraformStateBlocked() {
        assertNotNull(evaluateAll(writeHookInput("/infra/terraform.tfstate")).firstOrNull())
    }

    // --- kube_config ---

    @Test
    fun kubeConfigBlocked() {
        assertNotNull(evaluateAll(writeHookInput("/home/user/.kube/config")).firstOrNull())
    }

    // --- docker_config ---

    @Test
    fun dockerConfigBlocked() {
        assertNotNull(evaluateAll(writeHookInput("/home/user/.docker/config.json")).firstOrNull())
    }

    // --- Bash detection ---

    @Test
    fun bashCatEnvBlocked() {
        assertNotNull(evaluateAll(bashHookInput("cat .env")).firstOrNull())
    }

    @Test
    fun bashSedSshConfigBlocked() {
        assertNotNull(evaluateAll(bashHookInput("sed -i 's/old/new/' ~/.ssh/config")).firstOrNull())
    }

    // --- Edit tool ---

    @Test
    fun editEnvBlocked() {
        assertNotNull(evaluateAll(editHookInput("/project/.env")).firstOrNull())
    }

    // --- Normal file allowed ---

    @Test
    fun normalFileAllowed() {
        assertTrue(evaluateAll(writeHookInput("/project/src/main.kt")).isEmpty())
    }

    // --- Read tool NOT blocked ---

    @Test
    fun readToolEnvNotBlocked() {
        assertTrue(evaluateAll(readHookInput("/project/.env")).isEmpty())
    }
}
