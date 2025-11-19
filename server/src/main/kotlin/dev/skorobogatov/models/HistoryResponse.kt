package dev.skorobogatov.models

import kotlinx.serialization.Serializable

@Serializable
data class HistoryResponse(
    val sessionId: String,
    val messages: List<HistoryMessageDto>,
    val messageCount: Int,
    val pairsCount: Int,
    val createdAt: Long,
    val lastAccessedAt: Long
)

@Serializable
data class HistoryMessageDto(
    val type: String,
    val content: String,
    val timestamp: Long
)
