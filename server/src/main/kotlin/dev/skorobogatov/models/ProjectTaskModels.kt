package dev.skorobogatov.models

import kotlinx.serialization.Serializable

/**
 * Task status enum
 */
@Serializable
enum class TaskStatus {
    TODO,
    IN_PROGRESS,
    DONE,
    BLOCKED
}

/**
 * Task priority enum
 */
@Serializable
enum class Priority {
    LOW,
    MEDIUM,
    HIGH,
    CRITICAL
}

/**
 * Project task model - represents a project task from MCP Task Server
 */
@Serializable
data class ProjectTask(
    val id: String,
    val title: String,
    val description: String,
    val status: TaskStatus,
    val priority: Priority,
    val assignee: String? = null,
    val tags: List<String> = emptyList(),
    val dueDate: String? = null,
    val createdAt: String,
    val updatedAt: String,
    val project: String? = null,
    val dependencies: List<String> = emptyList(),
    val estimatedHours: Int? = null,
    val actualHours: Int? = null
)

/**
 * Request to create a new project task
 */
@Serializable
data class CreateProjectTaskRequest(
    val title: String,
    val description: String,
    val priority: Priority,
    val assignee: String? = null,
    val tags: List<String> = emptyList(),
    val dueDate: String? = null,
    val project: String? = null,
    val estimatedHours: Int? = null
)

/**
 * Request to update an existing project task
 */
@Serializable
data class UpdateProjectTaskRequest(
    val status: TaskStatus? = null,
    val priority: Priority? = null,
    val assignee: String? = null,
    val tags: List<String>? = null,
    val dueDate: String? = null,
    val actualHours: Int? = null
)

/**
 * Response containing a list of project tasks
 */
@Serializable
data class ProjectTaskListResponse(
    val tasks: List<ProjectTask>,
    val totalCount: Int
)

/**
 * Response containing search results for project tasks
 */
@Serializable
data class ProjectTaskSearchResponse(
    val tasks: List<ProjectTask>,
    val totalCount: Int,
    val query: String
)

/**
 * Project statistics
 */
@Serializable
data class ProjectStats(
    val total: Int,
    val byStatus: Map<String, Int>,
    val byPriority: Map<String, Int>,
    val overdue: Int,
    val completionRate: Double,
    val avgCompletionTimeHours: Double?
)

/**
 * Project task recommendations response
 */
@Serializable
data class ProjectTaskRecommendationsResponse(
    val recommendations: List<ProjectTask>,
    val totalCount: Int,
    val message: String
)
