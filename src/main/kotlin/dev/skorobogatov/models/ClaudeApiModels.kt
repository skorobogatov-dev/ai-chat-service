package dev.skorobogatov.models

import kotlinx.serialization.Serializable

// Модели для работы с Anthropic API

@Serializable
data class ClaudeApiRequest(
    val model: String,
    val max_tokens: Int,
    val messages: List<ClaudeMessage>,
    val system: String? = null,
    val temperature: Double? = null
)

@Serializable
data class ClaudeMessage(
    val role: String,
    val content: String
)

@Serializable
data class ClaudeApiResponse(
    val id: String,
    val type: String,
    val role: String,
    val content: List<ClaudeContent>,
    val model: String,
    val stop_reason: String? = null,
    val usage: ClaudeUsage? = null
)

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
