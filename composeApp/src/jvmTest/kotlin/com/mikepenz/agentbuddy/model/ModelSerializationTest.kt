package com.mikepenz.agentbuddy.model

import kotlinx.datetime.Clock
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonPrimitive
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class ModelSerializationTest {

    private val json = Json { encodeDefaults = true }

    @Test
    fun approvalRequestRoundTrip() {
        val request = ApprovalRequest(
            id = "test-id",
            source = Source.CLAUDE_CODE,
            toolType = ToolType.DEFAULT,
            hookInput = HookInput(
                sessionId = "session-1",
                toolName = "Bash",
                toolInput = mapOf("command" to JsonPrimitive("npm test")),
                cwd = "/tmp",
            ),
            timestamp = Clock.System.now(),
            rawRequestJson = "{}",
        )
        val encoded = json.encodeToString(ApprovalRequest.serializer(), request)
        val decoded = json.decodeFromString(ApprovalRequest.serializer(), encoded)
        assertEquals(request, decoded)
    }

    @Test
    fun approvalResultRoundTrip() {
        val request = ApprovalRequest(
            id = "test-id",
            source = Source.CLAUDE_CODE,
            toolType = ToolType.DEFAULT,
            hookInput = HookInput(
                sessionId = "session-1",
                toolName = "Bash",
                toolInput = mapOf("command" to JsonPrimitive("npm test")),
                cwd = "/tmp",
            ),
            timestamp = Clock.System.now(),
            rawRequestJson = "{}",
        )
        val result = ApprovalResult(
            request = request,
            decision = Decision.APPROVED,
            feedback = "Looks good",
            riskAnalysis = RiskAnalysis(risk = 3, label = "Moderate", message = "Medium risk"),
            rawResponseJson = """{"approved":true}""",
            decidedAt = Clock.System.now(),
        )
        val encoded = json.encodeToString(ApprovalResult.serializer(), result)
        val decoded = json.decodeFromString(ApprovalResult.serializer(), encoded)
        assertEquals(result, decoded)
    }

    @Test
    fun approvalResultWithNullFields() {
        val request = ApprovalRequest(
            id = "test-id-2",
            source = Source.CLAUDE_CODE,
            toolType = ToolType.ASK_USER_QUESTION,
            hookInput = HookInput(
                sessionId = "session-2",
                toolName = "Ask",
                cwd = "/home",
            ),
            timestamp = Clock.System.now(),
            rawRequestJson = "{}",
        )
        val result = ApprovalResult(
            request = request,
            decision = Decision.CANCELLED_BY_CLIENT,
            feedback = null,
            riskAnalysis = null,
            rawResponseJson = null,
            decidedAt = Clock.System.now(),
        )
        val encoded = json.encodeToString(ApprovalResult.serializer(), result)
        val decoded = json.decodeFromString(ApprovalResult.serializer(), encoded)
        assertEquals(result, decoded)
        assertNull(decoded.feedback)
        assertNull(decoded.riskAnalysis)
        assertNull(decoded.rawResponseJson)
    }

    @Test
    fun hookInputWithPermissionSuggestions() {
        val input = HookInput(
            sessionId = "session-1",
            toolName = "Bash",
            toolInput = mapOf("command" to JsonPrimitive("ls -la")),
            permissionSuggestions = listOf(
                PermissionSuggestion(
                    type = "addRules",
                    rules = listOf(RuleEntry(toolName = "Bash", ruleContent = "ls *")),
                    behavior = "allow",
                    destination = "projectSettings",
                ),
            ),
            cwd = "/tmp",
        )
        val encoded = json.encodeToString(HookInput.serializer(), input)
        val decoded = json.decodeFromString(HookInput.serializer(), encoded)
        assertEquals(input, decoded)
        assertEquals(1, decoded.permissionSuggestions.size)
        assertEquals("Bash", decoded.permissionSuggestions[0].rules?.first()?.toolName)
        assertEquals("ls *", decoded.permissionSuggestions[0].rules?.first()?.ruleContent)
    }

    @Test
    fun riskAnalysisSourceFieldRoundTrip() {
        val analysis = RiskAnalysis(risk = 2, label = "Low", message = "Minor edit", source = "copilot")
        val encoded = json.encodeToString(RiskAnalysis.serializer(), analysis)
        val decoded = json.decodeFromString(RiskAnalysis.serializer(), encoded)
        assertEquals("copilot", decoded.source)
    }

    @Test
    fun riskAnalysisSourceDefaultsToEmpty() {
        val oldJson = """{"risk":3,"label":"Moderate","message":"Medium risk"}"""
        val lenientJson = Json { ignoreUnknownKeys = true }
        val decoded = lenientJson.decodeFromString(RiskAnalysis.serializer(), oldJson)
        assertEquals("", decoded.source)
    }

    @Test
    fun appSettingsWithCopilotBackend() {
        val settings = AppSettings(
            riskAnalysisBackend = RiskAnalysisBackend.COPILOT,
            riskAnalysisCopilotModel = "gpt-4.1",
        )
        val encoded = json.encodeToString(AppSettings.serializer(), settings)
        val decoded = json.decodeFromString(AppSettings.serializer(), encoded)
        assertEquals(RiskAnalysisBackend.COPILOT, decoded.riskAnalysisBackend)
        assertEquals("gpt-4.1", decoded.riskAnalysisCopilotModel)
    }

    @Test
    fun appSettingsBackwardCompatDefaultsClaude() {
        val oldJson = """{"themeMode":"SYSTEM","serverPort":19532,"alwaysOnTop":true,"defaultTimeoutSeconds":240,"startOnBoot":false,"riskAnalysisEnabled":true,"riskAnalysisModel":"haiku","riskAnalysisCustomPrompt":"","autoApproveRisk1":false,"autoDenyRisk5":false,"awayMode":false,"newestApprovalFirst":false,"windowX":null,"windowY":null,"windowWidth":null,"windowHeight":null}"""
        val lenientJson = Json { ignoreUnknownKeys = true }
        val decoded = lenientJson.decodeFromString(AppSettings.serializer(), oldJson)
        assertEquals(RiskAnalysisBackend.CLAUDE, decoded.riskAnalysisBackend)
        assertEquals("gpt-4.1-mini", decoded.riskAnalysisCopilotModel)
        assertEquals(0, decoded.autoApproveLevel)
        assertEquals(0, decoded.autoDenyLevel)
    }

    @Test
    fun appSettingsAutoLevelsRoundTrip() {
        val settings = AppSettings(autoApproveLevel = 3, autoDenyLevel = 4)
        val encoded = json.encodeToString(AppSettings.serializer(), settings)
        val decoded = json.decodeFromString(AppSettings.serializer(), encoded)
        assertEquals(3, decoded.autoApproveLevel)
        assertEquals(4, decoded.autoDenyLevel)
    }

    @Test
    fun appSettingsBackwardCompatDefaultsProminentAlwaysAllow() {
        val oldJson = """{"themeMode":"SYSTEM","serverPort":19532,"alwaysOnTop":true,"defaultTimeoutSeconds":240,"startOnBoot":false,"riskAnalysisEnabled":true,"riskAnalysisModel":"haiku","riskAnalysisCustomPrompt":"","autoApproveRisk1":false,"autoDenyRisk5":false,"awayMode":false,"newestApprovalFirst":false,"windowX":null,"windowY":null,"windowWidth":null,"windowHeight":null}"""
        val lenientJson = Json { ignoreUnknownKeys = true }
        val decoded = lenientJson.decodeFromString(AppSettings.serializer(), oldJson)
        assertEquals(false, decoded.prominentAlwaysAllow)
    }

    @Test
    fun appSettingsProminentAlwaysAllowRoundTrip() {
        val settings = AppSettings(prominentAlwaysAllow = true)
        val encoded = json.encodeToString(AppSettings.serializer(), settings)
        val decoded = json.decodeFromString(AppSettings.serializer(), encoded)
        assertEquals(true, decoded.prominentAlwaysAllow)
    }

    @Test
    fun appSettingsRoundTrip() {
        val settings = AppSettings()
        val encoded = json.encodeToString(AppSettings.serializer(), settings)
        val decoded = json.decodeFromString(AppSettings.serializer(), encoded)
        assertEquals(settings, decoded)
        assertEquals(19532, decoded.serverPort)
        assertEquals(true, decoded.alwaysOnTop)
        assertEquals(240, decoded.defaultTimeoutSeconds)
    }
}
