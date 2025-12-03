package dev.skorobogatov.mcp.support.models

import kotlinx.serialization.Serializable

@Serializable
data class Ticket(
    val id: String,
    val userId: String,
    val subject: String,
    val description: String,
    val status: TicketStatus,
    val priority: Priority,
    val createdAt: String,
    val updatedAt: String,
    val assignedTo: String? = null,
    val messages: List<TicketMessage> = emptyList()
)
