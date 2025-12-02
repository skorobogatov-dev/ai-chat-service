package dev.skorobogatov.mcp.filesystem

import io.ktor.server.application.*
import io.ktor.server.engine.*
import io.ktor.server.netty.*
import io.ktor.server.routing.*
import io.ktor.server.websocket.*
import io.ktor.websocket.*
import io.modelcontextprotocol.kotlin.sdk.*
import io.modelcontextprotocol.kotlin.sdk.server.Server
import io.modelcontextprotocol.kotlin.sdk.server.ServerOptions
import io.modelcontextprotocol.kotlin.sdk.shared.Transport
import kotlinx.coroutines.channels.ClosedReceiveChannelException
import kotlinx.serialization.json.*
import org.slf4j.LoggerFactory
import java.io.File
import kotlin.time.Duration.Companion.seconds

/**
 * WebSocket MCP сервер для доступа к файловой системе проекта и git
 */
class FilesystemMCPServer(
    private val port: Int = 3002,
    private val host: String = "0.0.0.0",
    private val projectRoot: String = System.getenv("PROJECT_ROOT") ?: File(System.getProperty("user.dir")).parentFile.absolutePath
) {
    private val logger = LoggerFactory.getLogger(FilesystemMCPServer::class.java)

    /**
     * Запуск MCP сервера
     */
    fun start() {
        logger.info("Starting Filesystem MCP Server on ws://$host:$port/mcp")
        logger.info("Project root: $projectRoot")

        embeddedServer(Netty, port = port, host = host) {
            install(WebSockets) {
                pingPeriod = 15.seconds
                timeout = 15.seconds
                maxFrameSize = Long.MAX_VALUE
                masking = false
            }

            routing {
                webSocket("/mcp") {
                    logger.info("New MCP client connected")

                    try {
                        // Создаем MCP сервер
                        val mcpServer = Server(
                            serverInfo = Implementation(
                                name = "filesystem-mcp-server",
                                version = "1.0.0"
                            ),
                            options = ServerOptions(
                                capabilities = ServerCapabilities(
                                    tools = ServerCapabilities.Tools(listChanged = null)
                                )
                            )
                        )

                        // Регистрируем инструмент для получения списка файлов
                        mcpServer.addTool(
                            name = "list_files",
                            description = "Получить список файлов и директорий в указанной папке проекта",
                            inputSchema = Tool.Input(
                                properties = buildJsonObject {
                                    putJsonObject("path") {
                                        put("type", "string")
                                        put("description", "Путь относительно корня проекта (по умолчанию: '.')")
                                        put("default", ".")
                                    }
                                    putJsonObject("recursive") {
                                        put("type", "boolean")
                                        put("description", "Рекурсивный обход директорий (по умолчанию: false)")
                                        put("default", false)
                                    }
                                },
                                required = emptyList()
                            )
                        ) { request ->
                            handleListFiles(request)
                        }

                        // Регистрируем инструмент для чтения файла
                        mcpServer.addTool(
                            name = "read_file",
                            description = "Прочитать содержимое файла из проекта",
                            inputSchema = Tool.Input(
                                properties = buildJsonObject {
                                    putJsonObject("path") {
                                        put("type", "string")
                                        put("description", "Путь к файлу относительно корня проекта")
                                    }
                                },
                                required = listOf("path")
                            )
                        ) { request ->
                            handleReadFile(request)
                        }

                        // Регистрируем инструмент для получения текущей ветки git
                        mcpServer.addTool(
                            name = "get_git_branch",
                            description = "Получить текущую ветку git",
                            inputSchema = Tool.Input(
                                properties = buildJsonObject {},
                                required = emptyList()
                            )
                        ) { request ->
                            handleGetGitBranch(request)
                        }

                        // Регистрируем инструмент для получения статуса git
                        mcpServer.addTool(
                            name = "get_git_status",
                            description = "Получить статус git (измененные, добавленные, удаленные файлы)",
                            inputSchema = Tool.Input(
                                properties = buildJsonObject {},
                                required = emptyList()
                            )
                        ) { request ->
                            handleGetGitStatus(request)
                        }

                        // Регистрируем инструмент для получения git diff
                        mcpServer.addTool(
                            name = "get_git_diff",
                            description = "Получить diff изменений в рабочей директории. Показывает детальные изменения в файлах.",
                            inputSchema = Tool.Input(
                                properties = buildJsonObject {
                                    putJsonObject("staged") {
                                        put("type", "boolean")
                                        put("description", "Показать только staged изменения (по умолчанию: false)")
                                        put("default", false)
                                    }
                                    putJsonObject("file") {
                                        put("type", "string")
                                        put("description", "Путь к конкретному файлу (опционально)")
                                    }
                                },
                                required = emptyList()
                            )
                        ) { request ->
                            handleGetGitDiff(request)
                        }

                        // Регистрируем инструмент для получения git log
                        mcpServer.addTool(
                            name = "get_git_log",
                            description = "Получить историю коммитов git",
                            inputSchema = Tool.Input(
                                properties = buildJsonObject {
                                    putJsonObject("limit") {
                                        put("type", "number")
                                        put("description", "Количество последних коммитов (по умолчанию: 10)")
                                        put("default", 10)
                                        put("minimum", 1)
                                        put("maximum", 100)
                                    }
                                    putJsonObject("file") {
                                        put("type", "string")
                                        put("description", "Путь к файлу для просмотра его истории (опционально)")
                                    }
                                },
                                required = emptyList()
                            )
                        ) { request ->
                            handleGetGitLog(request)
                        }

                        // Регистрируем инструмент для получения содержимого коммита
                        mcpServer.addTool(
                            name = "get_git_commit",
                            description = "Получить детальную информацию о конкретном коммите (diff, автор, дата)",
                            inputSchema = Tool.Input(
                                properties = buildJsonObject {
                                    putJsonObject("commit_hash") {
                                        put("type", "string")
                                        put("description", "Hash коммита (полный или короткий)")
                                    }
                                },
                                required = listOf("commit_hash")
                            )
                        ) { request ->
                            handleGetGitCommit(request)
                        }

                        // Создаем WebSocket transport
                        var messageHandler: (suspend (JSONRPCMessage) -> Unit)? = null

                        val transport = object : Transport {
                            override suspend fun start() {
                                // Already started via WebSocket connection
                            }

                            override suspend fun send(message: JSONRPCMessage) {
                                val json = Json {
                                    ignoreUnknownKeys = true
                                    encodeDefaults = true
                                }
                                val messageText = json.encodeToString(JSONRPCMessage.serializer(), message)
                                send(Frame.Text(messageText))
                            }

                            override suspend fun close() {
                                this@webSocket.close(CloseReason(CloseReason.Codes.NORMAL, "Connection closed"))
                            }

                            override fun onClose(block: () -> Unit) {
                                // Handle close callbacks
                            }

                            override fun onError(block: (Throwable) -> Unit) {
                                // Handle error callbacks
                            }

                            override fun onMessage(block: suspend (JSONRPCMessage) -> Unit) {
                                // Save the message handler callback
                                messageHandler = block
                            }
                        }

                        // Подключаем MCP сервер к transport
                        mcpServer.connect(transport)

                        // Обрабатываем входящие сообщения
                        for (frame in incoming) {
                            when (frame) {
                                is Frame.Text -> {
                                    val messageText = frame.readText()
                                    logger.debug("Received message: $messageText")

                                    try {
                                        val json = Json { ignoreUnknownKeys = true }
                                        val message = json.decodeFromString(JSONRPCMessage.serializer(), messageText)
                                        // Передаем сообщение в MCP сервер через callback
                                        messageHandler?.invoke(message)
                                    } catch (e: Exception) {
                                        logger.error("Error parsing message: ${e.message}", e)
                                    }
                                }
                                else -> {
                                    logger.debug("Received non-text frame: ${frame.frameType}")
                                }
                            }
                        }
                    } catch (e: ClosedReceiveChannelException) {
                        logger.info("MCP client disconnected")
                    } catch (e: Exception) {
                        logger.error("Error in MCP WebSocket handler: ${e.message}", e)
                    }
                }
            }
        }.start(wait = true)
    }

    /**
     * Обработка запроса списка файлов
     */
    private fun handleListFiles(request: CallToolRequest): CallToolResult {
        return try {
            val arguments = request.arguments as? JsonObject
            val path = arguments?.get("path")?.jsonPrimitive?.contentOrNull ?: "."
            val recursive = arguments?.get("recursive")?.jsonPrimitive?.booleanOrNull ?: false

            val targetDir = File(projectRoot, path).canonicalFile

            // Проверка безопасности: файл должен быть внутри проекта
            if (!targetDir.canonicalPath.startsWith(File(projectRoot).canonicalPath)) {
                return CallToolResult(
                    content = listOf(
                        TextContent(
                            text = "Ошибка: доступ за пределы проекта запрещен"
                        )
                    ),
                    isError = true
                )
            }

            if (!targetDir.exists()) {
                return CallToolResult(
                    content = listOf(
                        TextContent(
                            text = "Ошибка: директория '$path' не найдена"
                        )
                    ),
                    isError = true
                )
            }

            if (!targetDir.isDirectory) {
                return CallToolResult(
                    content = listOf(
                        TextContent(
                            text = "Ошибка: '$path' не является директорией"
                        )
                    ),
                    isError = true
                )
            }

            val files = if (recursive) {
                targetDir.walkTopDown()
                    .filter { it != targetDir }
                    .map { getFileInfo(it, targetDir) }
                    .toList()
            } else {
                targetDir.listFiles()
                    ?.map { getFileInfo(it, targetDir) }
                    ?.toList()
                    ?: emptyList()
            }

            val response = buildString {
                appendLine("📁 Содержимое: $path")
                appendLine("Всего элементов: ${files.size}")
                appendLine()

                files.sortedBy { it.second }.forEach { (info, _) ->
                    appendLine(info)
                }
            }

            CallToolResult(
                content = listOf(
                    TextContent(text = response)
                )
            )
        } catch (e: Exception) {
            logger.error("Error handling list_files: ${e.message}", e)
            CallToolResult(
                content = listOf(
                    TextContent(
                        text = "Ошибка при получении списка файлов: ${e.message}"
                    )
                ),
                isError = true
            )
        }
    }

    /**
     * Получить информацию о файле
     */
    private fun getFileInfo(file: File, baseDir: File): Pair<String, String> {
        val relativePath = file.relativeTo(File(projectRoot)).path
        val icon = if (file.isDirectory) "📁" else "📄"
        val size = if (file.isFile) " (${formatFileSize(file.length())})" else ""
        return Pair("$icon $relativePath$size", relativePath)
    }

    /**
     * Форматировать размер файла
     */
    private fun formatFileSize(bytes: Long): String {
        return when {
            bytes < 1024 -> "$bytes B"
            bytes < 1024 * 1024 -> "${bytes / 1024} KB"
            else -> "${bytes / (1024 * 1024)} MB"
        }
    }

    /**
     * Обработка запроса чтения файла
     */
    private fun handleReadFile(request: CallToolRequest): CallToolResult {
        return try {
            val arguments = request.arguments as? JsonObject
            val path = arguments?.get("path")?.jsonPrimitive?.content ?: ""

            if (path.isBlank()) {
                return CallToolResult(
                    content = listOf(
                        TextContent(
                            text = "Ошибка: не указан путь к файлу"
                        )
                    ),
                    isError = true
                )
            }

            val targetFile = File(projectRoot, path).canonicalFile

            // Проверка безопасности: файл должен быть внутри проекта
            if (!targetFile.canonicalPath.startsWith(File(projectRoot).canonicalPath)) {
                return CallToolResult(
                    content = listOf(
                        TextContent(
                            text = "Ошибка: доступ за пределы проекта запрещен"
                        )
                    ),
                    isError = true
                )
            }

            if (!targetFile.exists()) {
                return CallToolResult(
                    content = listOf(
                        TextContent(
                            text = "Ошибка: файл '$path' не найден"
                        )
                    ),
                    isError = true
                )
            }

            if (!targetFile.isFile) {
                return CallToolResult(
                    content = listOf(
                        TextContent(
                            text = "Ошибка: '$path' не является файлом"
                        )
                    ),
                    isError = true
                )
            }

            val content = targetFile.readText()
            val response = buildString {
                appendLine("📄 Файл: $path")
                appendLine("Размер: ${formatFileSize(targetFile.length())}")
                appendLine()
                appendLine("Содержимое:")
                appendLine("```")
                appendLine(content)
                appendLine("```")
            }

            CallToolResult(
                content = listOf(
                    TextContent(text = response)
                )
            )
        } catch (e: Exception) {
            logger.error("Error handling read_file: ${e.message}", e)
            CallToolResult(
                content = listOf(
                    TextContent(
                        text = "Ошибка при чтении файла: ${e.message}"
                    )
                ),
                isError = true
            )
        }
    }

    /**
     * Обработка запроса получения текущей ветки git
     */
    private fun handleGetGitBranch(request: CallToolRequest): CallToolResult {
        return try {
            val process = ProcessBuilder("git", "branch", "--show-current")
                .directory(File(projectRoot))
                .redirectErrorStream(true)
                .start()

            val output = process.inputStream.bufferedReader().readText().trim()
            process.waitFor()

            if (process.exitValue() != 0 || output.isBlank()) {
                return CallToolResult(
                    content = listOf(
                        TextContent(
                            text = "Ошибка: не удалось получить текущую ветку git\n$output"
                        )
                    ),
                    isError = true
                )
            }

            val response = "🌿 Текущая ветка git: $output"

            CallToolResult(
                content = listOf(
                    TextContent(text = response)
                )
            )
        } catch (e: Exception) {
            logger.error("Error handling get_git_branch: ${e.message}", e)
            CallToolResult(
                content = listOf(
                    TextContent(
                        text = "Ошибка при получении ветки git: ${e.message}"
                    )
                ),
                isError = true
            )
        }
    }

    /**
     * Обработка запроса получения статуса git
     */
    private fun handleGetGitStatus(request: CallToolRequest): CallToolResult {
        return try {
            val process = ProcessBuilder("git", "status", "--short")
                .directory(File(projectRoot))
                .redirectErrorStream(true)
                .start()

            val output = process.inputStream.bufferedReader().readText().trim()
            process.waitFor()

            if (process.exitValue() != 0) {
                return CallToolResult(
                    content = listOf(
                        TextContent(
                            text = "Ошибка: не удалось получить статус git\n$output"
                        )
                    ),
                    isError = true
                )
            }

            val response = if (output.isBlank()) {
                "✅ Рабочая директория чистая (нет изменений)"
            } else {
                buildString {
                    appendLine("📊 Статус git:")
                    appendLine()
                    output.lines().forEach { line ->
                        val icon = when {
                            line.startsWith("M ") -> "📝" // Modified
                            line.startsWith("A ") -> "➕" // Added
                            line.startsWith("D ") -> "➖" // Deleted
                            line.startsWith("??") -> "❓" // Untracked
                            line.startsWith("R ") -> "🔄" // Renamed
                            else -> "📄"
                        }
                        appendLine("$icon $line")
                    }
                }
            }

            CallToolResult(
                content = listOf(
                    TextContent(text = response)
                )
            )
        } catch (e: Exception) {
            logger.error("Error handling get_git_status: ${e.message}", e)
            CallToolResult(
                content = listOf(
                    TextContent(
                        text = "Ошибка при получении статуса git: ${e.message}"
                    )
                ),
                isError = true
            )
        }
    }

    /**
     * Обработка запроса получения git diff
     */
    private fun handleGetGitDiff(request: CallToolRequest): CallToolResult {
        return try {
            val arguments = request.arguments as? JsonObject
            val staged = arguments?.get("staged")?.jsonPrimitive?.booleanOrNull ?: false
            val file = arguments?.get("file")?.jsonPrimitive?.contentOrNull

            val command = mutableListOf("git", "diff")
            if (staged) {
                command.add("--staged")
            }
            if (file != null && file.isNotBlank()) {
                command.add("--")
                command.add(file)
            }

            val process = ProcessBuilder(command)
                .directory(File(projectRoot))
                .redirectErrorStream(true)
                .start()

            val output = process.inputStream.bufferedReader().readText().trim()
            process.waitFor()

            if (process.exitValue() != 0) {
                return CallToolResult(
                    content = listOf(
                        TextContent(
                            text = "Ошибка: не удалось получить git diff\n$output"
                        )
                    ),
                    isError = true
                )
            }

            val response = if (output.isBlank()) {
                if (file != null) {
                    "✅ Нет изменений в файле '$file'"
                } else {
                    "✅ Нет изменений в рабочей директории"
                }
            } else {
                buildString {
                    appendLine("📝 Git diff${if (staged) " (staged)" else ""}${if (file != null) " для файла: $file" else ""}:")
                    appendLine()
                    appendLine("```diff")
                    appendLine(output)
                    appendLine("```")
                }
            }

            CallToolResult(
                content = listOf(
                    TextContent(text = response)
                )
            )
        } catch (e: Exception) {
            logger.error("Error handling get_git_diff: ${e.message}", e)
            CallToolResult(
                content = listOf(
                    TextContent(
                        text = "Ошибка при получении git diff: ${e.message}"
                    )
                ),
                isError = true
            )
        }
    }

    /**
     * Обработка запроса получения git log
     */
    private fun handleGetGitLog(request: CallToolRequest): CallToolResult {
        return try {
            val arguments = request.arguments as? JsonObject
            val limit = arguments?.get("limit")?.jsonPrimitive?.intOrNull ?: 10
            val file = arguments?.get("file")?.jsonPrimitive?.contentOrNull

            val command = mutableListOf(
                "git", "log",
                "--format=%H|%an|%ae|%ad|%s",
                "--date=iso",
                "-n", limit.toString()
            )
            if (file != null && file.isNotBlank()) {
                command.add("--")
                command.add(file)
            }

            val process = ProcessBuilder(command)
                .directory(File(projectRoot))
                .redirectErrorStream(true)
                .start()

            val output = process.inputStream.bufferedReader().readText().trim()
            process.waitFor()

            if (process.exitValue() != 0) {
                return CallToolResult(
                    content = listOf(
                        TextContent(
                            text = "Ошибка: не удалось получить git log\n$output"
                        )
                    ),
                    isError = true
                )
            }

            if (output.isBlank()) {
                return CallToolResult(
                    content = listOf(
                        TextContent(
                            text = if (file != null) {
                                "Нет коммитов для файла '$file'"
                            } else {
                                "Нет коммитов в репозитории"
                            }
                        )
                    )
                )
            }

            val response = buildString {
                appendLine("📜 История коммитов${if (file != null) " для файла: $file" else ""} (последние $limit):")
                appendLine()

                output.lines().forEach { line ->
                    val parts = line.split("|")
                    if (parts.size == 5) {
                        val (hash, author, email, date, message) = parts
                        appendLine("🔹 Коммит: ${hash.take(8)}")
                        appendLine("   👤 Автор: $author <$email>")
                        appendLine("   📅 Дата: $date")
                        appendLine("   💬 Сообщение: $message")
                        appendLine()
                    }
                }
            }

            CallToolResult(
                content = listOf(
                    TextContent(text = response)
                )
            )
        } catch (e: Exception) {
            logger.error("Error handling get_git_log: ${e.message}", e)
            CallToolResult(
                content = listOf(
                    TextContent(
                        text = "Ошибка при получении git log: ${e.message}"
                    )
                ),
                isError = true
            )
        }
    }

    /**
     * Обработка запроса получения информации о коммите
     */
    private fun handleGetGitCommit(request: CallToolRequest): CallToolResult {
        return try {
            val arguments = request.arguments as? JsonObject
            val commitHash = arguments?.get("commit_hash")?.jsonPrimitive?.content ?: ""

            if (commitHash.isBlank()) {
                return CallToolResult(
                    content = listOf(
                        TextContent(
                            text = "Ошибка: не указан hash коммита"
                        )
                    ),
                    isError = true
                )
            }

            // Получаем информацию о коммите
            val infoProcess = ProcessBuilder(
                "git", "show",
                "--format=%H|%an|%ae|%ad|%s|%b",
                "--date=iso",
                "--no-patch",
                commitHash
            )
                .directory(File(projectRoot))
                .redirectErrorStream(true)
                .start()

            val infoOutput = infoProcess.inputStream.bufferedReader().readText().trim()
            infoProcess.waitFor()

            if (infoProcess.exitValue() != 0) {
                return CallToolResult(
                    content = listOf(
                        TextContent(
                            text = "Ошибка: не удалось получить информацию о коммите\n$infoOutput"
                        )
                    ),
                    isError = true
                )
            }

            // Получаем diff коммита
            val diffProcess = ProcessBuilder(
                "git", "show",
                "--format=",
                commitHash
            )
                .directory(File(projectRoot))
                .redirectErrorStream(true)
                .start()

            val diffOutput = diffProcess.inputStream.bufferedReader().readText().trim()
            diffProcess.waitFor()

            val response = buildString {
                // Парсим информацию о коммите
                val parts = infoOutput.split("|")
                if (parts.size >= 5) {
                    val (hash, author, email, date, message) = parts.take(5)
                    val body = if (parts.size > 5) parts[5] else ""

                    appendLine("📦 Информация о коммите:")
                    appendLine()
                    appendLine("🔹 Hash: $hash")
                    appendLine("👤 Автор: $author <$email>")
                    appendLine("📅 Дата: $date")
                    appendLine("💬 Сообщение: $message")
                    if (body.isNotBlank()) {
                        appendLine()
                        appendLine("📝 Описание:")
                        appendLine(body)
                    }
                    appendLine()
                }

                // Добавляем diff
                if (diffOutput.isNotBlank()) {
                    appendLine("📝 Изменения:")
                    appendLine()
                    appendLine("```diff")
                    appendLine(diffOutput)
                    appendLine("```")
                }
            }

            CallToolResult(
                content = listOf(
                    TextContent(text = response)
                )
            )
        } catch (e: Exception) {
            logger.error("Error handling get_git_commit: ${e.message}", e)
            CallToolResult(
                content = listOf(
                    TextContent(
                        text = "Ошибка при получении информации о коммите: ${e.message}"
                    )
                ),
                isError = true
            )
        }
    }
}

/**
 * Main function для запуска Filesystem MCP сервера
 */
fun main() {
    val server = FilesystemMCPServer(port = 3002)
    server.start()
}
