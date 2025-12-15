package dev.skorobogatov.services

import dev.skorobogatov.models.MCPCallToolRequest
import kotlinx.serialization.json.JsonPrimitive
import org.slf4j.LoggerFactory

/**
 * Обработчик команд в чате
 * Поддерживает команды типа /help, /dev, /analytics и т.д.
 */
class CommandHandler(
    private val mcpService: MCPService,
    private val claudeService: ClaudeService,
    private val appsService: AppsService? = null,
    private val analyticsService: AnalyticsService? = null
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
            "/dev" -> handleDevCommand(args)
            "/analytics", "/a" -> handleAnalyticsCommand(args)
            else -> CommandResult.error("Неизвестная команда: $commandName\n\nДоступные команды:\n- /help [вопрос] - помощь по структуре проекта\n- /dev [описание] - создать веб-приложение\n- /analytics [вопрос] - аналитика по тикетам (или /a)\n- /analytics stats - статистика по тикетам")
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

    // ========== КОМАНДА /dev - СОЗДАНИЕ ВЕБ-ПРИЛОЖЕНИЙ ==========

    /**
     * Обрабатывает команду /dev
     * Создает, редактирует, удаляет веб-приложения через MCP filesystem
     */
    private suspend fun handleDevCommand(args: String): CommandResult {
        if (args.isBlank()) {
            return showDevHelp()
        }

        val parts = args.split(" ", limit = 2)
        val subCommand = parts[0].lowercase()

        return when (subCommand) {
            "list" -> handleDevList()
            "delete" -> handleDevDelete(if (parts.size > 1) parts[1] else "")
            "edit" -> handleDevEdit(if (parts.size > 1) parts[1] else "")
            "info" -> handleDevInfo(if (parts.size > 1) parts[1] else "")
            else -> handleDevCreate(args) // По умолчанию - создание нового приложения
        }
    }

    /**
     * Показать справку по команде /dev
     */
    private fun showDevHelp(): CommandResult {
        return CommandResult.success(
            """
            🛠️ Команда /dev - создание веб-приложений

            **Создание нового приложения:**
            `/dev <описание>` - создать новое веб-приложение

            Примеры:
            - `/dev Сделай игру 2048`
            - `/dev Создай калькулятор с красивым дизайном`
            - `/dev Сделай таймер Pomodoro`

            **Управление приложениями:**
            - `/dev list` - список всех приложений
            - `/dev info <имя>` - информация о приложении
            - `/dev edit <имя> <изменения>` - редактировать приложение
            - `/dev delete <имя>` - удалить приложение

            **Ограничения:**
            - Только веб-технологии: HTML, CSS, JavaScript/TypeScript
            - Приложения работают автономно в браузере
            - Без внешних зависимостей (CDN библиотек)
            """.trimIndent()
        )
    }

    /**
     * Список всех приложений
     */
    private suspend fun handleDevList(): CommandResult {
        try {
            val result = mcpService.callTool(
                toolName = "list_apps",
                arguments = emptyMap()
            )

            return if (result.success) {
                CommandResult.success(result.result)
            } else {
                CommandResult.error("Ошибка получения списка приложений: ${result.result}")
            }
        } catch (e: Exception) {
            logger.error("Error listing apps: ${e.message}", e)
            return CommandResult.error("Ошибка: ${e.message}")
        }
    }

    /**
     * Информация о приложении
     */
    private suspend fun handleDevInfo(appName: String): CommandResult {
        if (appName.isBlank()) {
            return CommandResult.error("Укажите имя приложения: /dev info <имя>")
        }

        try {
            // Получаем список файлов приложения
            val result = mcpService.callTool(
                toolName = "list_files",
                arguments = mapOf("path" to "server/src/main/resources/static/apps/$appName", "recursive" to "true")
            )

            return if (result.success) {
                CommandResult.success(
                    """
                    📱 Приложение: $appName

                    ${result.result}

                    🔗 URL: /apps/$appName/
                    """.trimIndent()
                )
            } else {
                CommandResult.error("Приложение '$appName' не найдено")
            }
        } catch (e: Exception) {
            logger.error("Error getting app info: ${e.message}", e)
            return CommandResult.error("Ошибка: ${e.message}")
        }
    }

    /**
     * Удаление приложения
     */
    private suspend fun handleDevDelete(appName: String): CommandResult {
        if (appName.isBlank()) {
            return CommandResult.error("Укажите имя приложения: /dev delete <имя>")
        }

        try {
            val result = mcpService.callTool(
                toolName = "delete_path",
                arguments = mapOf("path" to appName)
            )

            return if (result.success) {
                CommandResult.success("🗑️ Приложение '$appName' удалено")
            } else {
                CommandResult.error("Ошибка удаления: ${result.result}")
            }
        } catch (e: Exception) {
            logger.error("Error deleting app: ${e.message}", e)
            return CommandResult.error("Ошибка: ${e.message}")
        }
    }

    /**
     * Редактирование приложения
     */
    private suspend fun handleDevEdit(args: String): CommandResult {
        val parts = args.split(" ", limit = 2)
        val appName = parts[0]
        val changes = if (parts.size > 1) parts[1] else ""

        if (appName.isBlank() || changes.isBlank()) {
            return CommandResult.error("Используйте: /dev edit <имя_приложения> <описание_изменений>")
        }

        try {
            // Читаем текущие файлы приложения
            val indexResult = mcpService.callTool(
                toolName = "read_file",
                arguments = mapOf("path" to "server/src/main/resources/static/apps/$appName/index.html")
            )

            if (!indexResult.success) {
                return CommandResult.error("Приложение '$appName' не найдено")
            }

            // Создаем промпт для редактирования
            val systemPrompt = buildDevEditPrompt(appName, indexResult.result)

            // Отправляем запрос в Claude
            val response = claudeService.sendMessageWithTools(
                messages = listOf(
                    dev.skorobogatov.models.ClaudeMessage(
                        role = "user",
                        content = "Внеси следующие изменения в приложение:\n$changes"
                    )
                ),
                systemPrompt = systemPrompt,
                tools = getMcpToolsForDev(),
                requestModel = "claude-sonnet-4-20250514",
                onToolCall = { toolName, input ->
                    val args = input.mapValues { (_, value) ->
                        when (value) {
                            is JsonPrimitive -> if (value.isString) value.content else value.toString()
                            else -> value.toString()
                        }
                    }
                    val result = mcpService.callTool(toolName, args)
                    result.result
                }
            )

            return CommandResult.success(
                """
                ✏️ Приложение '$appName' обновлено!

                ${response.response}

                🔗 URL: /apps/$appName/
                """.trimIndent()
            )
        } catch (e: Exception) {
            logger.error("Error editing app: ${e.message}", e)
            return CommandResult.error("Ошибка редактирования: ${e.message}")
        }
    }

    /**
     * Создание нового приложения
     */
    private suspend fun handleDevCreate(description: String): CommandResult {
        try {
            logger.info("Creating new app with description: $description")

            // Генерируем имя приложения
            val appName = generateAppName(description)
            logger.info("Generated app name: $appName")

            // Создаем системный промпт
            val systemPrompt = buildDevCreatePrompt(appName)

            // Отправляем запрос в Claude с MCP инструментами
            val response = claudeService.sendMessageWithTools(
                messages = listOf(
                    dev.skorobogatov.models.ClaudeMessage(
                        role = "user",
                        content = "Создай веб-приложение по следующему описанию:\n$description"
                    )
                ),
                systemPrompt = systemPrompt,
                tools = getMcpToolsForDev(),
                requestModel = "claude-sonnet-4-20250514",
                onToolCall = { toolName, input ->
                    val args = input.mapValues { (_, value) ->
                        when (value) {
                            is JsonPrimitive -> if (value.isString) value.content else value.toString()
                            else -> value.toString()
                        }
                    }
                    val result = mcpService.callTool(toolName, args)
                    result.result
                }
            )

            return CommandResult.success(
                """
                ✅ Приложение создано!

                📁 Имя: $appName
                🔗 URL: /apps/$appName/

                ${response.response}
                """.trimIndent()
            )
        } catch (e: Exception) {
            logger.error("Error creating app: ${e.message}", e)
            return CommandResult.error("Ошибка создания приложения: ${e.message}")
        }
    }

    /**
     * Генерация имени приложения из описания
     */
    private fun generateAppName(description: String): String {
        // Извлекаем ключевые слова из описания
        val keywords = description
            .lowercase()
            .replace(Regex("[^a-zа-яё0-9\\s]"), "")
            .split(Regex("\\s+"))
            .filter { it.length > 2 }
            .take(3)
            .joinToString("-")

        val baseName = if (keywords.isNotBlank()) {
            // Транслитерация русских букв
            transliterate(keywords)
        } else {
            "app"
        }

        // Проверяем уникальность через MCP
        return baseName.take(30)
    }

    /**
     * Транслитерация русских букв в латиницу
     */
    private fun transliterate(text: String): String {
        val map = mapOf(
            'а' to "a", 'б' to "b", 'в' to "v", 'г' to "g", 'д' to "d",
            'е' to "e", 'ё' to "e", 'ж' to "zh", 'з' to "z", 'и' to "i",
            'й' to "y", 'к' to "k", 'л' to "l", 'м' to "m", 'н' to "n",
            'о' to "o", 'п' to "p", 'р' to "r", 'с' to "s", 'т' to "t",
            'у' to "u", 'ф' to "f", 'х' to "h", 'ц' to "ts", 'ч' to "ch",
            'ш' to "sh", 'щ' to "sch", 'ъ' to "", 'ы' to "y", 'ь' to "",
            'э' to "e", 'ю' to "yu", 'я' to "ya"
        )
        return text.map { map[it] ?: it.toString() }.joinToString("")
    }

    /**
     * Системный промпт для создания приложения
     */
    private fun buildDevCreatePrompt(appName: String): String = """
        Ты - опытный веб-разработчик. Создай веб-приложение по запросу пользователя.

        ВАЖНЫЕ ПРАВИЛА:
        1. Создавай ТОЛЬКО веб-приложения (HTML + CSS + JavaScript/TypeScript)
        2. Другие языки программирования (Python, Java, C++ и т.д.) НЕ поддерживаются
        3. Приложение должно работать полностью в браузере без серверной части

        ИНСТРУКЦИИ ПО СОЗДАНИЮ:
        1. Сначала вызови create_directory с path="$appName"
        2. Затем вызови write_file для создания index.html
        3. Для сложных приложений создавай отдельные файлы: script.js, style.css
        4. Для простых приложений - всё в одном index.html

        ТРЕБОВАНИЯ К КОДУ:
        - Полностью рабочий код без заглушек
        - Современный JavaScript (ES6+)
        - Адаптивный дизайн (работает на мобильных)
        - Красивый UI с CSS (gradients, shadows, animations)
        - Без внешних зависимостей (jQuery, React и т.д.)
        - Код должен работать автономно в браузере

        СТРУКТУРА index.html:
        ```html
        <!DOCTYPE html>
        <html lang="ru">
        <head>
            <meta charset="UTF-8">
            <meta name="viewport" content="width=device-width, initial-scale=1.0">
            <title>$appName</title>
            <style>
                /* CSS стили здесь */
            </style>
        </head>
        <body>
            <!-- HTML разметка -->
            <script>
                // JavaScript код
            </script>
        </body>
        </html>
        ```

        После создания файлов, напиши краткое описание что было создано и как пользоваться приложением.
    """.trimIndent()

    /**
     * Системный промпт для редактирования приложения
     */
    private fun buildDevEditPrompt(appName: String, currentCode: String): String = """
        Ты - опытный веб-разработчик. Отредактируй существующее веб-приложение.

        ВАЖНЫЕ ПРАВИЛА:
        1. Редактируй ТОЛЬКО веб-код (HTML + CSS + JavaScript)
        2. Сохраняй существующую функциональность, если не просят её убрать
        3. Приложение должно продолжать работать в браузере

        ТЕКУЩИЙ КОД ПРИЛОЖЕНИЯ ($appName):
        $currentCode

        ИНСТРУКЦИИ:
        1. Используй write_file для обновления файлов
        2. Путь к файлу: "$appName/index.html" (или другие файлы)
        3. Полностью перезаписывай файл с изменениями

        После внесения изменений, опиши что было изменено.
    """.trimIndent()

    /**
     * Получить список MCP инструментов для /dev
     */
    private suspend fun getMcpToolsForDev(): List<dev.skorobogatov.models.ClaudeTool> {
        val toolsResponse = mcpService.listTools()
        return toolsResponse.tools
            .filter { it.name in listOf("create_directory", "write_file", "delete_path", "read_file", "list_apps") }
            .mapNotNull { tool ->
                tool.inputSchema?.let { schema ->
                    dev.skorobogatov.models.ClaudeTool(
                        name = tool.name,
                        description = tool.description ?: "",
                        input_schema = schema
                    )
                }
            }
    }

    // ========== КОМАНДА /analytics - АНАЛИТИКА ТИКЕТОВ ==========

    /**
     * Обрабатывает команду /analytics
     * Позволяет задавать аналитические вопросы по тикетам
     */
    private suspend fun handleAnalyticsCommand(args: String): CommandResult {
        if (analyticsService == null) {
            return CommandResult.error("Сервис аналитики не инициализирован. Дождитесь загрузки данных.")
        }

        val status = analyticsService.getStatus()
        if (!status.initialized) {
            return CommandResult.error("Аналитика загружается... Попробуйте через несколько секунд.\nСтатус: ${status.vectorizedTickets}/${status.totalTickets} тикетов векторизовано.")
        }

        if (args.isBlank()) {
            return showAnalyticsHelp(status)
        }

        val subCommand = args.split(" ", limit = 2)[0].lowercase()

        return when (subCommand) {
            "stats", "статистика" -> handleAnalyticsStats()
            "status", "статус" -> handleAnalyticsStatus()
            else -> handleAnalyticsQuery(args)
        }
    }

    /**
     * Показать справку по команде /analytics
     */
    private fun showAnalyticsHelp(status: dev.skorobogatov.models.AnalyticsStatus): CommandResult {
        return CommandResult.success(
            """
            📊 Команда /analytics - аналитика тикетов

            **Использование:**
            `/analytics <вопрос>` или `/a <вопрос>` - задать вопрос

            **Примеры вопросов:**
            - `/a какая ошибка чаще всего?`
            - `/a топ-3 проблемы пользователей`
            - `/a где пользователи теряются в воронке?`
            - `/a проблемы premium пользователей`
            - `/a среднее время решения критических тикетов`

            **Подкоманды:**
            - `/analytics stats` - статистика по всем тикетам
            - `/analytics status` - статус системы

            **Текущий статус:**
            ✅ Загружено: ${status.totalTickets} тикетов
            🔢 Векторизовано: ${status.vectorizedTickets}
            📐 Размерность: ${status.embeddingDimension}
            """.trimIndent()
        )
    }

    /**
     * Статистика по тикетам
     */
    private fun handleAnalyticsStats(): CommandResult {
        val stats = analyticsService!!.getStats()

        val topErrors = stats.topErrorCodes.take(5).joinToString("\n") {
            "   • ${it.errorCode}: ${it.count} шт."
        }

        val byCategory = stats.byCategory.entries
            .sortedByDescending { it.value }
            .take(5)
            .joinToString("\n") { "   • ${it.key}: ${it.value}" }

        val byPriority = stats.byPriority.entries
            .joinToString(", ") { "${it.key}: ${it.value}" }

        val byStatus = stats.byStatus.entries
            .joinToString(", ") { "${it.key}: ${it.value}" }

        val avgTime = stats.avgResolutionTimeByPriority.entries
            .joinToString("\n") { "   • ${it.key}: %.1f ч".format(it.value) }

        return CommandResult.success(
            """
            📊 **Статистика по тикетам**

            **Всего тикетов:** ${stats.totalTickets}

            **Топ-5 категорий:**
            $byCategory

            **По приоритету:** $byPriority

            **По статусу:** $byStatus

            **Топ-5 ошибок:**
            $topErrors

            **Среднее время решения:**
            $avgTime
            """.trimIndent()
        )
    }

    /**
     * Статус системы аналитики
     */
    private fun handleAnalyticsStatus(): CommandResult {
        val status = analyticsService!!.getStatus()
        return CommandResult.success(
            """
            🔧 **Статус аналитической системы**

            • Инициализирована: ${if (status.initialized) "✅ Да" else "❌ Нет"}
            • Тикетов загружено: ${status.totalTickets}
            • Векторизовано: ${status.vectorizedTickets}
            • Размерность embedding: ${status.embeddingDimension}
            • Категорий: ${status.categories.size}
            """.trimIndent()
        )
    }

    /**
     * Обработка аналитического вопроса
     */
    private suspend fun handleAnalyticsQuery(query: String): CommandResult {
        return try {
            logger.info("Processing analytics query: $query")

            val request = dev.skorobogatov.models.AnalyticsQueryRequest(
                query = query,
                topK = 10,
                minSimilarity = 0.3
            )

            val response = analyticsService!!.analyzeQuery(request)

            val relevantInfo = if (response.relevantTickets.isNotEmpty()) {
                val tickets = response.relevantTickets.take(3).joinToString("\n") { result ->
                    "   • [${result.ticket.id}] ${result.ticket.title} (${result.ticket.category}, similarity: %.2f)".format(result.similarity)
                }
                "\n\n**Релевантные тикеты:**\n$tickets"
            } else ""

            CommandResult.success(
                """
                📊 **Аналитика:** $query

                ${response.answer}
                $relevantInfo

                _Проанализировано ${response.totalTicketsAnalyzed} тикетов за ${response.processingTimeMs}мс_
                """.trimIndent()
            )
        } catch (e: Exception) {
            logger.error("Error processing analytics query: ${e.message}", e)
            CommandResult.error("Ошибка аналитики: ${e.message}")
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
