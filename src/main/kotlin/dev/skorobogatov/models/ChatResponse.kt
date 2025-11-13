package dev.skorobogatov.models

import kotlinx.serialization.Serializable

@Serializable
data class ChatResponse(
    val response: String,
    val sessionId: String = "",  // ID сессии для продолжения диалога
    val model: String,
    val inputTokens: Int,
    val outputTokens: Int,
    val totalTokens: Int,
    val responseTimeMs: Long,
    val historyCompressed: Boolean = false  // Была ли сжата история в этом запросе
)
