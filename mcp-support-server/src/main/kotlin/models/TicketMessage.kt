package dev.skorobogatov.mcp.support.models

import kotlinx.serialization.Serializable

@Serializable
data class TicketMessage(
    val id: String,
    val author: String,
    val content: String,
    val timestamp: String,
    val isFromSupport: Boolean
)
