package dev.skorobogatov.models

import kotlinx.serialization.Serializable

/**
 * Запрос на создание задачи
 */
@Serializable
data class CreateTaskRequest(
    val name: String,
    val description: String = "",
    val question: String,
    val sessionId: String? = null,
    val schedule: TaskSchedule
)

/**
 * Запрос на обновление задачи
 */
@Serializable
data class UpdateTaskRequest(
    val name: String? = null,
    val description: String? = null,
    val question: String? = null,
    val sessionId: String? = null,
    val schedule: TaskSchedule? = null,
    val enabled: Boolean? = null
)

/**
 * Ответ со списком задач
 */
@Serializable
data class TaskListResponse(
    val tasks: List<ScheduledTask>,
    val totalCount: Int
)

/**
 * Ответ со списком выполнений задачи
 */
@Serializable
data class TaskExecutionListResponse(
    val executions: List<TaskExecution>,
    val totalCount: Int,
    val taskId: String
)

/**
 * Ответ о выполнении задачи
 */
@Serializable
data class TaskExecutionResponse(
    val taskId: String,
    val executionId: String,
    val success: Boolean,
    val message: String
)
