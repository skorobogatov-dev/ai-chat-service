package dev.skorobogatov.models

import kotlinx.serialization.Serializable

@Serializable
data class SupportChatRequest(
    val message: String,
    val userId: String? = null,  // для контекста пользователя
    val ticketId: String? = null,  // для контекста тикета
    val sessionId: String? = null,
    val useRAG: Boolean = true,  // автоматически включать RAG для поиска в документации
    val ragTopK: Int = 5,
    val ragMinSimilarity: Double = 0.7
)

@Serializable
data class SupportChatResponse(
    val response: String,
    val sessionId: String,
    val model: String,
    val inputTokens: Int,
    val outputTokens: Int,
    val totalTokens: Int,
    val responseTimeMs: Long,
    val ragUsed: Boolean = false,
    val ragChunksFound: Int = 0,
    val ragSources: List<String> = emptyList(),
    val userContext: UserContext? = null,
    val ticketContext: TicketContext? = null
)

@Serializable
data class UserContext(
    val userId: String,
    val name: String,
    val email: String,
    val role: String
)

@Serializable
data class TicketContext(
    val ticketId: String,
    val subject: String,
    val status: String
)

@Serializable
data class CreateTicketRequest(
    val userId: String,
    val subject: String,
    val description: String,
    val priority: String = "MEDIUM"  // LOW, MEDIUM, HIGH, URGENT
)

@Serializable
data class UpdateTicketRequest(
    val status: String? = null,      // OPEN, IN_PROGRESS, WAITING_USER, RESOLVED, CLOSED
    val assignedTo: String? = null,
    val notes: String? = null
)

@Serializable
data class TicketResponse(
    val id: String,
    val userId: String,
    val subject: String,
    val description: String,
    val status: String,
    val priority: String,
    val createdAt: String,
    val updatedAt: String,
    val assignedTo: String? = null,
    val messages: List<TicketMessageResponse> = emptyList()
)

@Serializable
data class TicketMessageResponse(
    val id: String,
    val author: String,
    val content: String,
    val timestamp: String,
    val isFromSupport: Boolean
)

@Serializable
data class UserResponse(
    val userId: String,
    val name: String,
    val email: String,
    val role: String,
    val registeredAt: String
)

@Serializable
data class TicketListResponse(
    val tickets: List<TicketResponse>,
    val totalCount: Int,
    val userId: String? = null
)
