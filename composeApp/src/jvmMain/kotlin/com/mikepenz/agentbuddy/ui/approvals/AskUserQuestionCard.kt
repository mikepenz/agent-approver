package com.mikepenz.agentbuddy.ui.approvals

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import co.touchlab.kermit.Logger
import com.mikepenz.agentbuddy.model.ApprovalRequest
import com.mikepenz.agentbuddy.model.HookInput
import com.mikepenz.agentbuddy.model.Question
import com.mikepenz.agentbuddy.model.QuestionOption
import com.mikepenz.agentbuddy.model.Source
import com.mikepenz.agentbuddy.model.ToolType
import com.mikepenz.agentbuddy.model.UserQuestionData
import com.mikepenz.agentbuddy.ui.components.SlimAllowButton
import com.mikepenz.agentbuddy.ui.components.SlimDenyButton
import com.mikepenz.agentbuddy.ui.theme.PreviewScaffold
import kotlinx.datetime.Clock
import com.mikepenz.agentbuddy.util.asArrayOrNull
import com.mikepenz.agentbuddy.util.asObjectOrNull
import com.mikepenz.agentbuddy.util.asStringOrNull
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

@Composable
fun AskUserQuestionCard(
    request: ApprovalRequest,
    questionData: UserQuestionData,
    onApproveWithInput: (updatedInput: Map<String, JsonElement>) -> Unit,
    onDismiss: () -> Unit,
    slimButtons: Boolean = false,
) {
    // question index -> selected option indices
    val selections = remember { mutableStateMapOf<Int, Set<Int>>() }
    // question index -> custom answer text
    val customAnswers = remember { mutableStateMapOf<Int, String>() }

    val allAnswered = questionData.questions.indices.all { qIdx ->
        val question = questionData.questions[qIdx]
        val hasCustom = (customAnswers[qIdx] ?: "").isNotBlank()
        if (question.options.isEmpty()) hasCustom
        else hasCustom || (selections[qIdx]?.isNotEmpty() == true)
    }

    Column(modifier = Modifier.fillMaxWidth()) {
        Text("No timeout", fontSize = 10.sp, color = Color.Gray)
        Spacer(Modifier.height(8.dp))

        if (request.hookInput.cwd.isNotBlank()) {
            Text(
                text = request.hookInput.cwd,
                fontSize = 10.sp,
                color = Color.Gray,
                maxLines = 1,
            )
            Spacer(Modifier.height(4.dp))
        }

        questionData.questions.forEachIndexed { qIdx, question ->
            if (question.header.isNotBlank()) {
                Text(
                    text = question.header,
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Spacer(Modifier.height(2.dp))
            }

            if (question.question.isNotBlank()) {
                Text(
                    text = question.question,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Spacer(Modifier.height(4.dp))
            }

            val selected = selections[qIdx] ?: emptySet()
            val customAnswer = customAnswers[qIdx] ?: ""
            val hasCustomAnswer = customAnswer.isNotBlank()

            question.options.forEachIndexed { optIdx, option ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    if (question.multiSelect) {
                        Checkbox(
                            checked = optIdx in selected,
                            enabled = !hasCustomAnswer,
                            onCheckedChange = { checked ->
                                selections[qIdx] = if (checked) {
                                    selected + optIdx
                                } else {
                                    selected - optIdx
                                }
                            },
                        )
                    } else {
                        RadioButton(
                            selected = optIdx in selected,
                            enabled = !hasCustomAnswer,
                            onClick = {
                                selections[qIdx] = setOf(optIdx)
                            },
                        )
                    }
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = option.label,
                            style = MaterialTheme.typography.bodyMedium,
                            color = if (hasCustomAnswer) {
                                MaterialTheme.colorScheme.onSurfaceVariant
                            } else {
                                MaterialTheme.colorScheme.onSurface
                            },
                        )
                        if (option.description.isNotBlank()) {
                            Text(
                                text = option.description,
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            }

            Spacer(Modifier.height(4.dp))

            OutlinedTextField(
                value = customAnswer,
                onValueChange = { customAnswers[qIdx] = it.replace("\n", "") },
                placeholder = { Text("Or type a custom answer...", maxLines = 1) },
                modifier = Modifier.fillMaxWidth(),
                textStyle = MaterialTheme.typography.bodySmall,
                singleLine = true,
            )

            if (qIdx < questionData.questions.lastIndex) {
                Spacer(Modifier.height(8.dp))
            }
        }

        Spacer(Modifier.height(8.dp))

        // Buttons
        val submitAction = {
            val updatedInput = buildUpdatedInput(
                request.hookInput.toolInput,
                selections,
                customAnswers,
            )
            onApproveWithInput(updatedInput)
        }
        if (slimButtons) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                SlimDenyButton(
                    modifier = Modifier.weight(1f),
                    label = "Dismiss",
                    onClick = onDismiss,
                )
                SlimAllowButton(
                    modifier = Modifier.weight(1f),
                    label = "Submit",
                    enabled = allAnswered,
                    onClick = submitAction,
                )
            }
        } else {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                OutlinedButton(
                    onClick = onDismiss,
                    modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error),
                ) {
                    Text("Dismiss")
                }
                Button(
                    onClick = submitAction,
                    modifier = Modifier.weight(1f),
                    enabled = allAnswered,
                ) {
                    Text("Submit")
                }
            }
        }
    }
}

internal fun buildUpdatedInput(
    originalInput: Map<String, JsonElement>,
    selections: Map<Int, Set<Int>>,
    customAnswers: Map<Int, String>,
): Map<String, JsonElement> {
    val result = originalInput.toMutableMap()

    val questionsElement = originalInput["questions"] ?: return result

    try {
        val questionsArray = questionsElement.asArrayOrNull() ?: return result
        val answersMap = mutableMapOf<String, JsonElement>()

        questionsArray.forEachIndexed { qIdx, questionElement ->
            val obj = questionElement.asObjectOrNull() ?: return@forEachIndexed
            val questionText = obj["question"].asStringOrNull() ?: return@forEachIndexed

            val customAnswer = customAnswers[qIdx]?.takeIf { it.isNotBlank() }
            val answerValue = if (customAnswer != null) {
                customAnswer
            } else {
                val selectedIndices = selections[qIdx] ?: emptySet()
                val options = obj["options"].asArrayOrNull()
                selectedIndices.sorted().mapNotNull { idx ->
                    options?.getOrNull(idx)?.asObjectOrNull()?.get("label").asStringOrNull()
                }.joinToString(", ")
            }

            if (answerValue.isNotBlank()) {
                answersMap[questionText] = JsonPrimitive(answerValue)
            }
        }

        result["answers"] = JsonObject(answersMap)
    } catch (e: Exception) {
        Logger.w(e) { "Failed to build updated input for AskUserQuestion" }
    }

    return result
}

@Preview
@Composable
private fun PreviewAskUserQuestionWithOptions() {
    PreviewScaffold {
        Box(modifier = Modifier.width(350.dp).padding(12.dp)) {
            AskUserQuestionCard(
                request = ApprovalRequest(
                    id = "preview-ask",
                    source = Source.CLAUDE_CODE,
                    toolType = ToolType.ASK_USER_QUESTION,
                    hookInput = HookInput(
                        sessionId = "sess-abc123",
                        toolName = "AskUserQuestion",
                        toolInput = emptyMap(),
                        cwd = "/home/user/project",
                    ),
                    timestamp = Clock.System.now(),
                    rawRequestJson = "{}",
                ),
                questionData = UserQuestionData(
                    questions = listOf(
                        Question(
                            header = "Database Choice",
                            question = "Which database?",
                            options = listOf(
                                QuestionOption(label = "PostgreSQL", description = "Robust relational DB"),
                                QuestionOption(label = "SQLite", description = "Lightweight embedded DB"),
                            ),
                            multiSelect = false,
                        ),
                    ),
                ),
                onApproveWithInput = {},
                onDismiss = {},
            )
        }
    }
}

@Preview
@Composable
private fun PreviewAskUserQuestionSlimButtons() {
    // Verifies the slim-style buttons (Dismiss = outlined-red-on-hover,
    // Submit = emerald fill, disabled state when no answer chosen).
    PreviewScaffold {
        Box(modifier = Modifier.width(350.dp).padding(12.dp)) {
            AskUserQuestionCard(
                request = ApprovalRequest(
                    id = "preview-ask-slim",
                    source = Source.CLAUDE_CODE,
                    toolType = ToolType.ASK_USER_QUESTION,
                    hookInput = HookInput(
                        sessionId = "sess-slim",
                        toolName = "AskUserQuestion",
                        toolInput = emptyMap(),
                        cwd = "/home/user/project",
                    ),
                    timestamp = Clock.System.now(),
                    rawRequestJson = "{}",
                ),
                questionData = UserQuestionData(
                    questions = listOf(
                        Question(
                            header = "Database Choice",
                            question = "Which database?",
                            options = listOf(
                                QuestionOption(label = "PostgreSQL", description = "Robust relational DB"),
                                QuestionOption(label = "SQLite", description = "Lightweight embedded DB"),
                            ),
                            multiSelect = false,
                        ),
                    ),
                ),
                onApproveWithInput = {},
                onDismiss = {},
                slimButtons = true,
            )
        }
    }
}

@Preview
@Composable
private fun PreviewAskUserQuestionMultiSelect() {
    PreviewScaffold {
        Box(modifier = Modifier.width(350.dp).padding(12.dp)) {
            AskUserQuestionCard(
                request = ApprovalRequest(
                    id = "preview-ask-multi",
                    source = Source.CLAUDE_CODE,
                    toolType = ToolType.ASK_USER_QUESTION,
                    hookInput = HookInput(
                        sessionId = "sess-abc123",
                        toolName = "AskUserQuestion",
                        toolInput = emptyMap(),
                        cwd = "/home/user/project",
                    ),
                    timestamp = Clock.System.now(),
                    rawRequestJson = "{}",
                ),
                questionData = UserQuestionData(
                    questions = listOf(
                        Question(
                            header = "Select features",
                            question = "Which features should we enable?",
                            options = listOf(
                                QuestionOption(label = "Auth", description = "User login / signup"),
                                QuestionOption(label = "Billing", description = "Stripe integration"),
                                QuestionOption(label = "Search", description = "Full-text search"),
                                QuestionOption(label = "Analytics", description = "Event tracking"),
                            ),
                            multiSelect = true,
                        ),
                    ),
                ),
                onApproveWithInput = {},
                onDismiss = {},
            )
        }
    }
}
