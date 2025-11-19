package dev.skorobogatov.services

import dev.skorobogatov.models.ScheduledTask
import dev.skorobogatov.models.TaskExecution
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.slf4j.LoggerFactory
import java.io.File
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import kotlin.io.path.exists

/**
 * Сервис для хранения задач и их выполнений в JSON файлах
 */
class TaskStorageService(
    private val storageDirectory: String = "scheduled_tasks"
) {
    private val logger = LoggerFactory.getLogger(TaskStorageService::class.java)
    private val json = Json {
        prettyPrint = true
        ignoreUnknownKeys = true
    }

    private val tasksDir: Path
    private val executionsDir: Path

    init {
        tasksDir = Paths.get(storageDirectory, "tasks")
        executionsDir = Paths.get(storageDirectory, "executions")

        // Создаем директории если не существуют
        if (!tasksDir.exists()) {
            Files.createDirectories(tasksDir)
            logger.info("Created tasks directory: $tasksDir")
        }
        if (!executionsDir.exists()) {
            Files.createDirectories(executionsDir)
            logger.info("Created executions directory: $executionsDir")
        }
    }

    /**
     * Сохранить задачу
     */
    fun saveTask(task: ScheduledTask) {
        try {
            val file = tasksDir.resolve("${task.id}.json").toFile()
            val jsonString = json.encodeToString(task)
            file.writeText(jsonString)
            logger.debug("Saved task: ${task.id}")
        } catch (e: Exception) {
            logger.error("Failed to save task: ${task.id}", e)
            throw e
        }
    }

    /**
     * Загрузить задачу по ID
     */
    fun loadTask(taskId: String): ScheduledTask? {
        return try {
            val file = tasksDir.resolve("$taskId.json").toFile()
            if (!file.exists()) {
                logger.debug("Task not found: $taskId")
                return null
            }
            val jsonString = file.readText()
            json.decodeFromString<ScheduledTask>(jsonString)
        } catch (e: Exception) {
            logger.error("Failed to load task: $taskId", e)
            null
        }
    }

    /**
     * Загрузить все задачи
     */
    fun loadAllTasks(): List<ScheduledTask> {
        return try {
            val tasks = mutableListOf<ScheduledTask>()
            val files = tasksDir.toFile().listFiles { file ->
                file.isFile && file.extension == "json"
            } ?: emptyArray()

            for (file in files) {
                try {
                    val jsonString = file.readText()
                    val task = json.decodeFromString<ScheduledTask>(jsonString)
                    tasks.add(task)
                } catch (e: Exception) {
                    logger.error("Failed to load task from file: ${file.name}", e)
                }
            }

            logger.info("Loaded ${tasks.size} tasks from storage")
            tasks
        } catch (e: Exception) {
            logger.error("Failed to load tasks", e)
            emptyList()
        }
    }

    /**
     * Удалить задачу
     */
    fun deleteTask(taskId: String): Boolean {
        return try {
            val file = tasksDir.resolve("$taskId.json").toFile()
            if (file.exists()) {
                file.delete()
                logger.info("Deleted task: $taskId")
                true
            } else {
                logger.warn("Task not found for deletion: $taskId")
                false
            }
        } catch (e: Exception) {
            logger.error("Failed to delete task: $taskId", e)
            false
        }
    }

    /**
     * Сохранить выполнение задачи
     */
    fun saveExecution(execution: TaskExecution) {
        try {
            // Создаем поддиректорию для задачи если не существует
            val taskExecutionsDir = executionsDir.resolve(execution.taskId)
            if (!taskExecutionsDir.exists()) {
                Files.createDirectories(taskExecutionsDir)
            }

            val file = taskExecutionsDir.resolve("${execution.id}.json").toFile()
            val jsonString = json.encodeToString(execution)
            file.writeText(jsonString)
            logger.debug("Saved execution: ${execution.id} for task: ${execution.taskId}")
        } catch (e: Exception) {
            logger.error("Failed to save execution: ${execution.id}", e)
            throw e
        }
    }

    /**
     * Загрузить выполнения задачи
     */
    fun loadExecutions(taskId: String, limit: Int = 100): List<TaskExecution> {
        return try {
            val executions = mutableListOf<TaskExecution>()
            val taskExecutionsDir = executionsDir.resolve(taskId)

            if (!taskExecutionsDir.exists()) {
                return emptyList()
            }

            val files = taskExecutionsDir.toFile().listFiles { file ->
                file.isFile && file.extension == "json"
            } ?: emptyArray()

            for (file in files.sortedByDescending { it.lastModified() }.take(limit)) {
                try {
                    val jsonString = file.readText()
                    val execution = json.decodeFromString<TaskExecution>(jsonString)
                    executions.add(execution)
                } catch (e: Exception) {
                    logger.error("Failed to load execution from file: ${file.name}", e)
                }
            }

            executions
        } catch (e: Exception) {
            logger.error("Failed to load executions for task: $taskId", e)
            emptyList()
        }
    }

    /**
     * Удалить все выполнения задачи
     */
    fun deleteExecutions(taskId: String): Boolean {
        return try {
            val taskExecutionsDir = executionsDir.resolve(taskId)
            if (taskExecutionsDir.exists()) {
                taskExecutionsDir.toFile().deleteRecursively()
                logger.info("Deleted all executions for task: $taskId")
                true
            } else {
                false
            }
        } catch (e: Exception) {
            logger.error("Failed to delete executions for task: $taskId", e)
            false
        }
    }

    /**
     * Проверить существование задачи
     */
    fun exists(taskId: String): Boolean {
        return tasksDir.resolve("$taskId.json").exists()
    }

    /**
     * Получить количество задач
     */
    fun getTaskCount(): Int {
        return tasksDir.toFile().listFiles { file ->
            file.isFile && file.extension == "json"
        }?.size ?: 0
    }
}
