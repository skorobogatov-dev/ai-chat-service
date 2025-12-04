package dev.skorobogatov.mcp.task.models

import kotlinx.serialization.Serializable

@Serializable
enum class TaskStatus {
    TODO,
    IN_PROGRESS,
    DONE,
    BLOCKED
}
