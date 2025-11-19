package dev.skorobogatov.models

import kotlinx.serialization.Serializable

@Serializable
data class ConversationListItem(
    val sessionId: String,
    val title: String?,
    val messageCount: Int,
    val createdAt: Long,
    val lastAccessedAt: Long,
    val unreadCount: Int = 0
)

@Serializable
data class ConversationListResponse(
    val conversations: List<ConversationListItem>,
    val totalCount: Int
)
