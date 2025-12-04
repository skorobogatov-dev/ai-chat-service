package dev.skorobogatov.mcp.task.storage

import dev.skorobogatov.mcp.task.models.*
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.encodeToString
import org.slf4j.LoggerFactory
import java.io.File
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import java.util.concurrent.ConcurrentHashMap

private val logger = LoggerFactory.getLogger("TaskStorage")

@Serializable
data class TasksData(
    val tasks: List<Task>
)

class TaskStorage(private val dataFile: File) {
    private val tasks = ConcurrentHashMap<String, Task>()
    private val json = Json {
        prettyPrint = true
        ignoreUnknownKeys = true
    }

    init {
        loadTasks()
    }

    private fun loadTasks() {
        try {
            if (dataFile.exists()) {
                val jsonText = dataFile.readText()
                val tasksData = json.decodeFromString<TasksData>(jsonText)
                tasksData.tasks.forEach { task ->
                    tasks[task.id] = task
                }
                logger.info("Loaded ${tasks.size} tasks from ${dataFile.name}")
            } else {
                logger.warn("Tasks file ${dataFile.name} not found")
            }
        } catch (e: Exception) {
            logger.error("Failed to load tasks", e)
        }
    }

    private fun saveTasks() {
        try {
            val tasksData = TasksData(tasks.values.toList())
            val jsonText = json.encodeToString(tasksData)
            dataFile.writeText(jsonText)
            logger.debug("Saved ${tasks.size} tasks to ${dataFile.name}")
        } catch (e: Exception) {
            logger.error("Failed to save tasks", e)
        }
    }

    fun getAllTasks(): List<Task> {
        return tasks.values.toList().sortedByDescending { it.createdAt }
    }

    fun getTask(taskId: String): Task? {
        return tasks[taskId]
    }

    fun createTask(
        title: String,
        description: String,
        priority: Priority,
        assignee: String? = null,
        dueDate: String? = null,
        tags: List<String> = emptyList(),
        project: String? = null,
        estimatedHours: Int? = null
    ): Task {
        val taskId = "TASK-${(tasks.size + 1).toString().padStart(3, '0')}"
        val now = LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME)

        val task = Task(
            id = taskId,
            title = title,
            description = description,
            status = TaskStatus.TODO,
            priority = priority,
            assignee = assignee,
            tags = tags,
            dueDate = dueDate,
            createdAt = now,
            updatedAt = now,
            project = project,
            estimatedHours = estimatedHours
        )

        tasks[taskId] = task
        saveTasks()
        logger.info("Created task: $taskId - $title")

        return task
    }

    fun updateTask(
        taskId: String,
        status: TaskStatus? = null,
        priority: Priority? = null,
        assignee: String? = null,
        dueDate: String? = null,
        tags: List<String>? = null,
        actualHours: Int? = null
    ): Task? {
        val task = tasks[taskId] ?: return null
        val now = LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME)

        val updatedTask = task.copy(
            status = status ?: task.status,
            priority = priority ?: task.priority,
            assignee = assignee ?: task.assignee,
            dueDate = dueDate ?: task.dueDate,
            tags = tags ?: task.tags,
            actualHours = actualHours ?: task.actualHours,
            updatedAt = now
        )

        tasks[taskId] = updatedTask
        saveTasks()
        logger.info("Updated task: $taskId")

        return updatedTask
    }

    fun listTasks(
        status: TaskStatus? = null,
        priority: Priority? = null,
        assignee: String? = null,
        project: String? = null,
        limit: Int? = null
    ): List<Task> {
        var filteredTasks = tasks.values.toList()

        if (status != null) {
            filteredTasks = filteredTasks.filter { it.status == status }
        }
        if (priority != null) {
            filteredTasks = filteredTasks.filter { it.priority == priority }
        }
        if (assignee != null) {
            filteredTasks = filteredTasks.filter { it.assignee?.equals(assignee, ignoreCase = true) == true }
        }
        if (project != null) {
            filteredTasks = filteredTasks.filter { it.project?.equals(project, ignoreCase = true) == true }
        }

        // Sort by priority (CRITICAL > HIGH > MEDIUM > LOW) and then by due date
        filteredTasks = filteredTasks.sortedWith(
            compareBy<Task> {
                when (it.priority) {
                    Priority.CRITICAL -> 0
                    Priority.HIGH -> 1
                    Priority.MEDIUM -> 2
                    Priority.LOW -> 3
                }
            }.thenBy { it.dueDate ?: "9999-12-31" }
        )

        return if (limit != null) filteredTasks.take(limit) else filteredTasks
    }

    fun searchTasks(query: String, status: TaskStatus? = null, priority: Priority? = null): List<Task> {
        val queryLower = query.lowercase()
        var filteredTasks = tasks.values.filter { task ->
            task.title.lowercase().contains(queryLower) ||
            task.description.lowercase().contains(queryLower) ||
            task.tags.any { it.lowercase().contains(queryLower) }
        }

        if (status != null) {
            filteredTasks = filteredTasks.filter { it.status == status }
        }
        if (priority != null) {
            filteredTasks = filteredTasks.filter { it.priority == priority }
        }

        return filteredTasks.sortedByDescending { it.createdAt }
    }

    fun getStats(project: String? = null): ProjectStats {
        val filteredTasks = if (project != null) {
            tasks.values.filter { it.project?.equals(project, ignoreCase = true) == true }
        } else {
            tasks.values
        }

        val byStatus = filteredTasks.groupingBy { it.status.name }.eachCount()
        val byPriority = filteredTasks.groupingBy { it.priority.name }.eachCount()

        val now = LocalDateTime.now()
        val overdue = filteredTasks.count { task ->
            task.dueDate != null &&
            task.status != TaskStatus.DONE &&
            LocalDateTime.parse(task.dueDate).isBefore(now)
        }

        val completedTasks = filteredTasks.filter { it.status == TaskStatus.DONE }
        val completionRate = if (filteredTasks.isNotEmpty()) {
            (completedTasks.size.toDouble() / filteredTasks.size) * 100
        } else {
            0.0
        }

        val avgCompletionTimeHours = if (completedTasks.isNotEmpty()) {
            completedTasks.mapNotNull { task ->
                try {
                    val created = LocalDateTime.parse(task.createdAt)
                    val updated = LocalDateTime.parse(task.updatedAt)
                    ChronoUnit.HOURS.between(created, updated).toDouble()
                } catch (e: Exception) {
                    null
                }
            }.average()
        } else {
            null
        }

        return ProjectStats(
            total = filteredTasks.size,
            byStatus = byStatus,
            byPriority = byPriority,
            overdue = overdue,
            completionRate = completionRate,
            avgCompletionTimeHours = avgCompletionTimeHours
        )
    }

    fun getRecommendations(assignee: String? = null): List<Task> {
        val now = LocalDateTime.now()

        var todoTasks = tasks.values.filter { it.status == TaskStatus.TODO }
        if (assignee != null) {
            todoTasks = todoTasks.filter { it.assignee?.equals(assignee, ignoreCase = true) == true }
        }

        // Score tasks based on priority, due date, and dependencies
        val scoredTasks = todoTasks.map { task ->
            var score = 0.0

            // Priority scoring
            score += when (task.priority) {
                Priority.CRITICAL -> 100.0
                Priority.HIGH -> 70.0
                Priority.MEDIUM -> 40.0
                Priority.LOW -> 10.0
            }

            // Due date scoring (urgent tasks get higher score)
            if (task.dueDate != null) {
                try {
                    val dueDate = LocalDateTime.parse(task.dueDate)
                    val daysUntilDue = ChronoUnit.DAYS.between(now, dueDate)
                    score += when {
                        daysUntilDue < 0 -> 50.0 // Overdue
                        daysUntilDue <= 1 -> 40.0 // Due today or tomorrow
                        daysUntilDue <= 3 -> 20.0 // Due within 3 days
                        daysUntilDue <= 7 -> 10.0 // Due within a week
                        else -> 0.0
                    }
                } catch (e: Exception) {
                    // Invalid date format, ignore
                }
            }

            // Dependency scoring (tasks that block others get higher score)
            val blockingCount = tasks.values.count { it.dependencies.contains(task.id) }
            score += blockingCount * 15.0

            Pair(task, score)
        }

        return scoredTasks
            .sortedByDescending { it.second }
            .take(5)
            .map { it.first }
    }
}
