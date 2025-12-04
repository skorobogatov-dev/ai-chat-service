package dev.skorobogatov.mcp.task.models

import kotlinx.serialization.Serializable

@Serializable
data class ProjectStats(
    val total: Int,
    val byStatus: Map<String, Int>,
    val byPriority: Map<String, Int>,
    val overdue: Int,
    val completionRate: Double,
    val avgCompletionTimeHours: Double?
)
