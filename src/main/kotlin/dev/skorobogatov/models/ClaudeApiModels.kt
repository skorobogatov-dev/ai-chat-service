package dev.skorobogatov.models

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject

// Модели для работы с Anthropic API

@Serializable
data class ClaudeApiRequest(
    val model: String,
    val max_tokens: Int,
    val messages: List<ClaudeMessageRequest>,
    val system: String? = null,
    val tools: List<ClaudeTool>? = null
)

// Модели для запросов с поддержкой различных типов content
@Serializable
data class ClaudeMessageRequest(
    val role: String,
    val content: List<ClaudeContentRequest>
)

@Serializable
data class ClaudeContentRequest(
    val type: String,
    val text: String? = null,
    val id: String? = null,
    val name: String? = null,
    val input: JsonObject? = null,
    val tool_use_id: String? = null,
    val content: String? = null
)

// Старая модель для обратной совместимости
@Serializable
data class ClaudeMessage(
    val role: String,
    val content: String
)

// Определение инструмента для Claude
@Serializable
data class ClaudeTool(
    val name: String,
    val description: String,
    val input_schema: JsonObject
)

@Serializable
data class ClaudeApiResponse(
    val id: String,
    val type: String,
    val role: String,
    val content: List<ClaudeContentResponse>,
    val model: String,
    val stop_reason: String? = null,
    val usage: ClaudeUsage? = null
)

// Контент в ответе от Claude (может быть text или tool_use)
@Serializable
data class ClaudeContentResponse(
    val type: String,
    val text: String? = null,
    val id: String? = null,
    val name: String? = null,
    val input: JsonObject? = null
)

// Старая модель для обратной совместимости
@Serializable
data class ClaudeContent(
    val type: String,
    val text: String
)

@Serializable
data class ClaudeUsage(
    val input_tokens: Int,
    val output_tokens: Int
)

@Serializable
data class ClaudeErrorResponse(
    val type: String,
    val error: ClaudeError
)

@Serializable
data class ClaudeError(
    val type: String,
    val message: String
)

@Serializable
data class ClaudeJsonResponse(
    val question: String? = null,
    val answer: String,
    val tags: List<String>? = null
)
