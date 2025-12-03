package dev.skorobogatov.mcp.support.models

import kotlinx.serialization.Serializable

@Serializable
enum class Priority {
    LOW,
    MEDIUM,
    HIGH,
    URGENT
}
