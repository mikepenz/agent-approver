package com.mikepenz.agentbelay.model

import co.touchlab.kermit.Logger
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

data class PlanReviewData(
    val plan: String,
    val allowedPrompts: List<AllowedPrompt>,
)

data class AllowedPrompt(
    val tool: String,
    val prompt: String,
)

data class UserQuestionData(
    val questions: List<Question>,
)

data class Question(
    val question: String,
    val header: String,
    val options: List<QuestionOption>,
    val multiSelect: Boolean,
)

data class QuestionOption(
    val label: String,
    val description: String,
)

object SpecialToolParser {

    fun parsePlanReview(toolInput: Map<String, JsonElement>): PlanReviewData? {
        return try {
            val plan = toolInput["plan"]?.jsonPrimitive?.contentOrNull ?: return null
            val allowedPrompts = (toolInput["allowedPrompts"] as? JsonArray)?.map { element ->
                val obj = element.jsonObject
                AllowedPrompt(
                    tool = obj["tool"]?.jsonPrimitive?.contentOrNull ?: "",
                    prompt = obj["prompt"]?.jsonPrimitive?.contentOrNull
                        ?: obj["description"]?.jsonPrimitive?.contentOrNull ?: "",
                )
            } ?: emptyList()
            PlanReviewData(plan = plan, allowedPrompts = allowedPrompts)
        } catch (e: Exception) {
            Logger.w(e) { "Failed to parse PlanReview tool input" }
            null
        }
    }

    fun parseUserQuestion(toolInput: Map<String, JsonElement>): UserQuestionData? {
        return try {
            val questionsArray = (toolInput["questions"] as? JsonArray) ?: return null
            val questions = questionsArray.map { element ->
                val obj = element.jsonObject
                val options = (obj["options"] as? JsonArray)?.map { optElement ->
                    val optObj = optElement.jsonObject
                    QuestionOption(
                        label = optObj["label"]?.jsonPrimitive?.contentOrNull ?: "",
                        description = optObj["description"]?.jsonPrimitive?.contentOrNull ?: "",
                    )
                } ?: emptyList()
                Question(
                    question = obj["question"]?.jsonPrimitive?.contentOrNull ?: "",
                    header = obj["header"]?.jsonPrimitive?.contentOrNull ?: "",
                    options = options,
                    multiSelect = obj["multiSelect"]?.jsonPrimitive?.booleanOrNull ?: false,
                )
            }
            if (questions.isEmpty()) return null
            UserQuestionData(questions = questions)
        } catch (e: Exception) {
            Logger.w(e) { "Failed to parse UserQuestion tool input" }
            null
        }
    }
}
