package dev.skorobogatov.mcp.support.models

import kotlinx.serialization.Serializable

@Serializable
enum class TicketStatus {
    OPEN,
    IN_PROGRESS,
    WAITING_USER,
    RESOLVED,
    CLOSED
}
