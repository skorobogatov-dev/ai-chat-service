package dev.skorobogatov.mcp.task.models

import kotlinx.serialization.Serializable

@Serializable
data class Task(
    val id: String,
    val title: String,
    val description: String,
    val status: TaskStatus,
    val priority: Priority,
    val assignee: String? = null,
    val tags: List<String> = emptyList(),
    val dueDate: String? = null,         // ISO-8601 format
    val createdAt: String,
    val updatedAt: String,
    val project: String? = null,
    val dependencies: List<String> = emptyList(),  // task IDs
    val estimatedHours: Int? = null,
    val actualHours: Int? = null
)
