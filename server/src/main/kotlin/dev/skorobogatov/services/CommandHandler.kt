package dev.skorobogatov.services

import dev.skorobogatov.models.MCPCallToolRequest
import org.slf4j.LoggerFactory

/**
 * Обработчик команд в чате
 * Поддерживает команды типа /help, /info и т.д.
 */
class CommandHandler(
    private val mcpService: MCPService,
    private val claudeService: ClaudeService
) {
    private val logger = LoggerFactory.getLogger(CommandHandler::class.java)

    /**
     * Проверяет, является ли сообщение командой
     */
    fun isCommand(message: String): Boolean {
        return message.trim().startsWith("/")
    }

    /**
     * Обрабатывает команду и возвращает результат
     */
    suspend fun handleCommand(command: String): CommandResult {
        val trimmed = command.trim()

        // Разбираем команду и аргументы
        val parts = trimmed.split(" ", limit = 2)
        val commandName = parts[0].lowercase()
        val args = if (parts.size > 1) parts[1] else ""

        logger.info("Processing command: $commandName with args: ${args.take(50)}...")

        return when (commandName) {
            "/help" -> handleHelpCommand(args)
            else -> CommandResult.error("Неизвестная команда: $commandName\n\nДоступные команды:\n- /help [вопрос] - помощь по структуре проекта")
        }
    }

    /**
     * Обрабатывает команду /help
     * Использует MCP filesystem для получения информации о проекте
     */
    private suspend fun handleHelpCommand(query: String): CommandResult {
        if (query.isBlank()) {
            return CommandResult.success(
                """
                📚 Команда /help - помощь по структуре проекта

                Используйте: /help [ваш вопрос]

                Примеры:
                - /help какая структура проекта?
                - /help где находится конфигурация сервера?
                - /help как работает RAG система?
                - /help на какой ветке я сейчас?
                - /help какие файлы были изменены?

                Эта команда использует MCP filesystem server для анализа структуры проекта и отвечает на ваши вопросы.
                """.trimIndent()
            )
        }

        try {
            // Собираем контекст о проекте через MCP инструменты
            val context = buildString {
                appendLine("=== Контекст проекта ===")
                appendLine()

                // Получаем текущую ветку git
                try {
                    val branchResult = mcpService.callTool(
                        toolName = "get_git_branch",
                        arguments = emptyMap()
                    )
                    if (branchResult.success) {
                        appendLine("🌿 Git ветка: ${branchResult.result}")
                        appendLine()
                    }
                } catch (e: Exception) {
                    logger.warn("Failed to get git branch: ${e.message}")
                }

                // Получаем статус git
                try {
                    val statusResult = mcpService.callTool(
                        toolName = "get_git_status",
                        arguments = emptyMap()
                    )
                    if (statusResult.success) {
                        appendLine("📊 Git статус:")
                        appendLine(statusResult.result)
                        appendLine()
                    }
                } catch (e: Exception) {
                    logger.warn("Failed to get git status: ${e.message}")
                }

                // Получаем структуру проекта
                try {
                    val filesResult = mcpService.callTool(
                        toolName = "list_files",
                        arguments = mapOf("path" to ".", "recursive" to "false")
                    )
                    if (filesResult.success) {
                        appendLine("📁 Структура корневой директории:")
                        appendLine(filesResult.result)
                        appendLine()
                    }
                } catch (e: Exception) {
                    logger.warn("Failed to list files: ${e.message}")
                }

                // Получаем список документации
                try {
                    val docsResult = mcpService.callTool(
                        toolName = "list_files",
                        arguments = mapOf("path" to "docs", "recursive" to "false")
                    )
                    if (docsResult.success) {
                        appendLine("📚 Доступная документация:")
                        appendLine(docsResult.result)
                        appendLine()
                    }
                } catch (e: Exception) {
                    logger.warn("Failed to list docs: ${e.message}")
                }

                // Получаем список модулей
                try {
                    val modulesResult = mcpService.callTool(
                        toolName = "list_files",
                        arguments = mapOf("path" to ".", "recursive" to "false")
                    )
                    if (modulesResult.success) {
                        appendLine("🔧 Модули проекта:")
                        appendLine(modulesResult.result)
                        appendLine()
                    }
                } catch (e: Exception) {
                    logger.warn("Failed to list modules: ${e.message}")
                }
            }

            // Формируем системный промпт для Claude
            val systemPrompt = """
                Ты - помощник по навигации в проекте AI Chat Service (Kotlin + Ktor).
                Используй предоставленный контекст о структуре проекта для ответа на вопросы пользователя.

                Отвечай кратко и по существу. Если нужно прочитать конкретный файл, скажи об этом.
                Используй эмодзи для наглядности.

                Доступные MCP инструменты для дополнительного анализа:
                - list_files - список файлов в директории
                - read_file - чтение содержимого файла
                - get_git_branch - текущая ветка
                - get_git_status - статус изменений

                Контекст проекта:
                $context
            """.trimIndent()

            // Отправляем запрос в Claude для анализа
            val userMessage = "Вопрос пользователя: $query"

            val response = claudeService.sendMessage(
                messages = listOf(
                    dev.skorobogatov.models.ClaudeMessage(
                        role = "user",
                        content = userMessage
                    )
                ),
                systemPrompt = systemPrompt,
                requestModel = null // Используем дефолтную модель
            )

            return CommandResult.success(response.response)

        } catch (e: Exception) {
            logger.error("Error processing /help command: ${e.message}", e)
            return CommandResult.error("Ошибка при обработке команды: ${e.message}")
        }
    }

    /**
     * Результат выполнения команды
     */
    data class CommandResult(
        val success: Boolean,
        val message: String,
        val isCommand: Boolean = true
    ) {
        companion object {
            fun success(message: String) = CommandResult(true, message)
            fun error(message: String) = CommandResult(false, message)
        }
    }
}
