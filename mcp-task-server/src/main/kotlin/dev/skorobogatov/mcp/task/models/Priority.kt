package dev.skorobogatov.mcp.task.models

import kotlinx.serialization.Serializable

@Serializable
enum class Priority {
    LOW,
    MEDIUM,
    HIGH,
    CRITICAL
}
