package dev.skorobogatov.services

import dev.skorobogatov.models.*
import kotlinx.coroutines.*
import kotlinx.serialization.json.JsonPrimitive
import org.slf4j.LoggerFactory
import java.time.Duration
import java.time.LocalDateTime
import java.time.temporal.ChronoUnit
import java.util.concurrent.ConcurrentHashMap

/**
 * Сервис планировщика задач
 */
class SchedulerService(
    private val taskStorage: TaskStorageService,
    private val claudeService: ClaudeService,
    private val historyService: ConversationHistoryService,
    private val mcpService: MCPService
) {
    private val logger = LoggerFactory.getLogger(SchedulerService::class.java)
    private val tasks = ConcurrentHashMap<String, ScheduledTask>()
    private val scheduledJobs = ConcurrentHashMap<String, Job>()
    private val scope = CoroutineScope(Dispatchers.Default + SupervisorJob())

    init {
        // Загрузить все задачи из хранилища
        val loadedTasks = taskStorage.loadAllTasks()
        loadedTasks.forEach { task ->
            tasks[task.id] = task
            if (task.enabled) {
                scheduleTask(task)
            }
        }
        logger.info("Initialized scheduler with ${tasks.size} tasks (${scheduledJobs.size} active)")
    }

    /**
     * Создать новую задачу
     */
    fun createTask(request: CreateTaskRequest): ScheduledTask {
        val task = ScheduledTask(
            name = request.name,
            description = request.description,
            question = request.question,
            sessionId = request.sessionId,
            schedule = request.schedule,
            nextExecutionAt = calculateNextExecution(request.schedule)
        )

        tasks[task.id] = task
        taskStorage.saveTask(task)

        if (task.enabled) {
            scheduleTask(task)
        }

        logger.info("Created task: ${task.id} - ${task.name}")
        return task
    }

    /**
     * Обновить задачу
     */
    fun updateTask(taskId: String, request: UpdateTaskRequest): ScheduledTask? {
        val existingTask = tasks[taskId] ?: return null

        val updatedTask = existingTask.copy(
            name = request.name ?: existingTask.name,
            description = request.description ?: existingTask.description,
            question = request.question ?: existingTask.question,
            sessionId = request.sessionId ?: existingTask.sessionId,
            schedule = request.schedule ?: existingTask.schedule,
            enabled = request.enabled ?: existingTask.enabled,
            updatedAt = LocalDateTime.now(),
            nextExecutionAt = if (request.schedule != null) {
                calculateNextExecution(request.schedule)
            } else {
                existingTask.nextExecutionAt
            }
        )

        tasks[taskId] = updatedTask
        taskStorage.saveTask(updatedTask)

        // Перепланировать задачу
        cancelScheduledJob(taskId)
        if (updatedTask.enabled) {
            scheduleTask(updatedTask)
        }

        logger.info("Updated task: $taskId")
        return updatedTask
    }

    /**
     * Удалить задачу
     */
    fun deleteTask(taskId: String): Boolean {
        cancelScheduledJob(taskId)
        tasks.remove(taskId)
        taskStorage.deleteTask(taskId)
        taskStorage.deleteExecutions(taskId)
        logger.info("Deleted task: $taskId")
        return true
    }

    /**
     * Получить задачу по ID
     */
    fun getTask(taskId: String): ScheduledTask? {
        return tasks[taskId]
    }

    /**
     * Получить все задачи
     */
    fun getAllTasks(): List<ScheduledTask> {
        return tasks.values.sortedByDescending { it.createdAt }
    }

    /**
     * Получить выполнения задачи
     */
    fun getExecutions(taskId: String, limit: Int = 100): List<TaskExecution> {
        return taskStorage.loadExecutions(taskId, limit)
    }

    /**
     * Запланировать задачу
     */
    private fun scheduleTask(task: ScheduledTask) {
        val delay = calculateDelay(task)
        if (delay == null) {
            logger.warn("Cannot schedule task ${task.id}: invalid schedule")
            return
        }

        val job = scope.launch {
            delay(delay.toMillis())
            executeTask(task)
        }

        scheduledJobs[task.id] = job
        logger.info("Scheduled task ${task.id} - ${task.name} to run in ${delay.toMinutes()} minutes")
    }

    /**
     * Отменить запланированную задачу
     */
    private fun cancelScheduledJob(taskId: String) {
        scheduledJobs[taskId]?.cancel()
        scheduledJobs.remove(taskId)
    }

    /**
     * Выполнить задачу
     */
    private suspend fun executeTask(task: ScheduledTask) {
        logger.info("Executing task: ${task.id} - ${task.name}")
        val startTime = System.currentTimeMillis()

        try {
            // Определяем sessionId для записи результата
            val targetSessionId = task.sessionId ?: run {
                // Создаем новую сессию если не указана
                historyService.getOrCreateSession(null).sessionId
            }

            // Добавляем вопрос пользователя в историю
            historyService.addUserMessage(targetSessionId, task.question)

            // Получаем сессию и конвертируем в формат Claude
            val session = historyService.getSession(targetSessionId)
                ?: throw IllegalStateException("Session not found: $targetSessionId")
            val allMessages = session.toClaudeMessages()

            // Проверяем наличие MCP инструментов и отправляем вопрос в Claude
            val connectionStatus = mcpService.getConnectionStatus()
            val response = if (connectionStatus.connected) {
                logger.info("MCP server connected, fetching tools for task execution")
                val mcpToolsResponse = mcpService.listTools()

                if (mcpToolsResponse.tools.isNotEmpty()) {
                    logger.info("Found ${mcpToolsResponse.tools.size} MCP tools, using tool-enabled mode")

                    // Конвертировать MCP инструменты в формат Claude
                    val claudeTools = mcpToolsResponse.tools.map { mcpTool ->
                        ClaudeTool(
                            name = mcpTool.name,
                            description = mcpTool.description ?: "No description",
                            input_schema = dev.skorobogatov.routes.convertMcpSchemaToJson(mcpTool.inputSchema)
                        )
                    }

                    // Отправить запрос с поддержкой инструментов
                    claudeService.sendMessageWithTools(
                        messages = allMessages,
                        tools = claudeTools,
                        systemPrompt = null,
                        requestModel = null,
                        onToolCall = { toolName, input ->
                            logger.info("Executing MCP tool in scheduled task: $toolName")
                            // Конвертировать JsonObject в Map<String, String>
                            val arguments = input.entries.associate { (key, value) ->
                                key to when (value) {
                                    is JsonPrimitive -> value.content
                                    else -> value.toString()
                                }
                            }
                            val result = mcpService.callTool(toolName, arguments)
                            if (result.success) {
                                result.result
                            } else {
                                throw Exception(result.error ?: "Unknown error calling tool")
                            }
                        }
                    )
                } else {
                    logger.debug("MCP server connected but no tools available, using standard mode")
                    claudeService.sendMessage(allMessages, systemPrompt = null)
                }
            } else {
                logger.debug("MCP server not connected, using standard mode")
                claudeService.sendMessage(allMessages, systemPrompt = null)
            }

            // Добавляем ответ ассистента в историю
            historyService.addAssistantMessage(targetSessionId, response.response)

            // Сохраняем результат выполнения
            val execution = TaskExecution(
                taskId = task.id,
                taskName = task.name,
                question = task.question,
                response = response.response,
                sessionId = targetSessionId,
                success = true,
                executionTimeMs = System.currentTimeMillis() - startTime
            )
            taskStorage.saveExecution(execution)

            // Обновляем задачу
            val updatedTask = task.copy(
                lastExecutedAt = LocalDateTime.now(),
                nextExecutionAt = calculateNextExecution(task.schedule, LocalDateTime.now())
            )
            tasks[task.id] = updatedTask
            taskStorage.saveTask(updatedTask)

            logger.info("Successfully executed task: ${task.id}")

            // Перепланируем для периодических задач
            if (task.schedule.type != ScheduleType.ONCE && task.enabled) {
                scheduleTask(updatedTask)
            }

        } catch (e: Exception) {
            logger.error("Failed to execute task: ${task.id}", e)

            val execution = TaskExecution(
                taskId = task.id,
                taskName = task.name,
                question = task.question,
                response = "",
                sessionId = task.sessionId ?: "",
                success = false,
                errorMessage = e.message,
                executionTimeMs = System.currentTimeMillis() - startTime
            )
            taskStorage.saveExecution(execution)

            // Даже при ошибке перепланируем периодические задачи
            if (task.schedule.type != ScheduleType.ONCE && task.enabled) {
                val updatedTask = task.copy(
                    lastExecutedAt = LocalDateTime.now(),
                    nextExecutionAt = calculateNextExecution(task.schedule, LocalDateTime.now())
                )
                tasks[task.id] = updatedTask
                taskStorage.saveTask(updatedTask)
                scheduleTask(updatedTask)
            }
        }
    }

    /**
     * Рассчитать задержку до следующего выполнения
     */
    private fun calculateDelay(task: ScheduledTask): Duration? {
        val nextExecution = task.nextExecutionAt ?: calculateNextExecution(task.schedule)
        ?: return null

        val now = LocalDateTime.now()
        if (nextExecution.isBefore(now)) {
            // Если время уже прошло
            if (task.schedule.type == ScheduleType.ONCE) {
                // Для одноразовых задач не планируем если время прошло
                logger.warn("Task ${task.id} has past execution time, skipping")
                return null
            }
            // Для периодических задач планируем на следующий раз
            val newNextExecution = calculateNextExecution(task.schedule, now) ?: return null
            return Duration.between(now, newNextExecution)
        }

        return Duration.between(now, nextExecution)
    }

    /**
     * Рассчитать следующее время выполнения
     */
    private fun calculateNextExecution(schedule: TaskSchedule, from: LocalDateTime = LocalDateTime.now()): LocalDateTime? {
        return when (schedule.type) {
            ScheduleType.ONCE -> {
                schedule.startTime
            }
            ScheduleType.DAILY -> {
                val hour = schedule.hour ?: return null
                val minute = schedule.minute ?: 0
                var next = from.withHour(hour).withMinute(minute).withSecond(0).withNano(0)
                if (next.isBefore(from) || next.isEqual(from)) {
                    next = next.plusDays(1)
                }
                next
            }
            ScheduleType.WEEKLY -> {
                val hour = schedule.hour ?: return null
                val minute = schedule.minute ?: 0
                val dayOfWeek = schedule.dayOfWeek ?: return null

                var next = from.withHour(hour).withMinute(minute).withSecond(0).withNano(0)

                // Находим следующий нужный день недели
                val currentDayOfWeek = from.dayOfWeek.value
                val daysUntilTarget = if (dayOfWeek >= currentDayOfWeek) {
                    dayOfWeek - currentDayOfWeek
                } else {
                    7 - (currentDayOfWeek - dayOfWeek)
                }

                next = next.plusDays(daysUntilTarget.toLong())

                // Если это тот же день но время уже прошло, добавляем неделю
                if (next.isBefore(from) || next.isEqual(from)) {
                    next = next.plusWeeks(1)
                }
                next
            }
        }
    }

    /**
     * Остановить планировщик
     */
    fun shutdown() {
        scope.cancel()
        logger.info("Scheduler service shutdown")
    }
}
