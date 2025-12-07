package dev.skorobogatov

import dev.skorobogatov.plugins.*
import dev.skorobogatov.services.ClaudeService
import io.ktor.client.*
import io.ktor.client.engine.cio.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.client.plugins.logging.*
import io.ktor.serialization.kotlinx.json.*
import io.ktor.server.application.*
import io.ktor.server.netty.*
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import java.io.File

fun main(args: Array<String>) {
    EngineMain.main(args)
}

fun Application.module() {
    // Чтение конфигурации
    val apiKey = environment.config.property("claude.apiKey").getString()
    val apiUrl = environment.config.property("claude.apiUrl").getString()
    val model = environment.config.property("claude.model").getString()
    val maxTokens = environment.config.property("claude.maxTokens").getString().toInt()

    // Системный промпт с дефолтным значением
    val defaultSystemPrompt = """
        You must respond ONLY with valid JSON in this exact format:
        {"question": "user's question", "answer": "your detailed answer", "tags": ["tag1", "tag2", "tag3"]}

        Rules:
        - question: repeat the user's question
        - answer: provide a comprehensive answer
        - tags: 3-5 relevant keywords related to the question and answer
        - Never include any text outside the JSON structure
    """.trimIndent()
    val systemPrompt = environment.config.propertyOrNull("claude.systemPrompt")?.getString() ?: defaultSystemPrompt

    // Специальный системный промпт для технической поддержки
    val supportSystemPrompt = environment.config.propertyOrNull("support.systemPrompt")?.getString() ?: """
        You are a technical support assistant for TaskMaster Pro, a task and project management platform.

        Your role:
        - Help users solve problems with the product
        - Answer questions about features, billing, and integrations
        - Search for relevant information in documentation (RAG provides context automatically)
        - Look up user information and ticket history (use MCP tools when needed)
        - Provide clear, friendly, and professional answers

        Guidelines:
        - Always be polite and helpful
        - Use documentation to provide accurate answers
        - Check user's ticket history for context if user ID is provided
        - If unsure, refer to documentation or ask for clarification
        - Suggest creating a support ticket for complex issues

        Format responses in a friendly, professional manner.
    """.trimIndent()

    // Создание HTTP клиента для запросов к Anthropic API и Ollama
    val httpClient = HttpClient(CIO) {
        install(ContentNegotiation) {
            json(Json {
                prettyPrint = true
                isLenient = true
                ignoreUnknownKeys = true
            })
        }
        install(Logging) {
            logger = Logger.DEFAULT
            level = LogLevel.INFO
        }
        // Увеличенные timeouts для Ollama (первый запрос может быть медленным)
        engine {
            requestTimeout = 120_000 // 2 минуты на весь запрос
            endpoint {
                connectTimeout = 30_000 // 30 секунд на подключение
                socketTimeout = 120_000 // 2 минуты на чтение/запись
            }
        }
    }

    // Создание сервиса для работы с Claude
    val claudeService = ClaudeService(
        httpClient = httpClient,
        apiKey = apiKey,
        apiUrl = apiUrl,
        model = model,
        maxTokens = maxTokens,
        defaultSystemPrompt = systemPrompt
    )

    // Создание сервиса для сохранения диалогов в файлы
    val fileStorageService = dev.skorobogatov.services.FileStorageService(
        storageDirectory = "chat_sessions"
    )

    // Создание сервиса для управления историей диалогов
    val historyService = dev.skorobogatov.services.ConversationHistoryService(
        compressionThreshold = 3,  // Сжимать каждые 3 пары сообщений
        fileStorageService = fileStorageService
    )

    // Создание сервиса для работы с MCP (Model Context Protocol)
    val mcpService = dev.skorobogatov.services.MCPService()

    // Создание сервиса для работы с Ollama (векторизация текста)
    val ollamaBaseUrl = environment.config.propertyOrNull("ollama.baseUrl")?.getString() ?: "http://localhost:11434"
    val ollamaModel = environment.config.propertyOrNull("ollama.model")?.getString() ?: "nomic-embed-text"
    val ollamaService = dev.skorobogatov.services.OllamaService(
        httpClient = httpClient,
        baseUrl = ollamaBaseUrl,
        model = ollamaModel
    )

    // Создание сервиса для разбиения текста на чанки
    val chunkerService = dev.skorobogatov.services.TextChunkerService(
        defaultChunkSize = 750,
        defaultOverlap = 75
    )

    // Создание сервиса для векторного хранилища (RAG)
    val vectorStoreService = dev.skorobogatov.services.VectorStoreService(
        storageDirectory = "embeddings_output"
    )

    // Создание сервиса для хранения задач
    val taskStorageService = dev.skorobogatov.services.TaskStorageService(
        storageDirectory = "scheduled_tasks"
    )

    // Создание планировщика задач
    val schedulerService = dev.skorobogatov.services.SchedulerService(
        taskStorage = taskStorageService,
        claudeService = claudeService,
        historyService = historyService,
        mcpService = mcpService
    )

    // Создание сервиса для управления веб-приложениями
    val appsService = dev.skorobogatov.services.AppsService(
        appsDirectory = "src/main/resources/static/apps"
    )

    // Создание обработчика команд
    val commandHandler = dev.skorobogatov.services.CommandHandler(
        mcpService = mcpService,
        claudeService = claudeService,
        appsService = appsService
    )

    // Конфигурация плагинов
    configureSerialization()
    configureHTTP()
    configureStatusPages()
    configureStaticContent()
    configureOpenAPI()
    configureRouting(claudeService, historyService, mcpService, schedulerService, ollamaService, chunkerService, vectorStoreService, commandHandler, appsService, supportSystemPrompt)

    // Автоматическое подключение к MCP серверам при старте
    val mcpServerUrl = environment.config.propertyOrNull("mcp.serverUrl")?.getString()
    val mcpFilesystemUrl = environment.config.propertyOrNull("mcp.filesystemServerUrl")?.getString() ?: "ws://localhost:3002/mcp"

    if (!mcpServerUrl.isNullOrBlank()) {
        val transportType = environment.config.propertyOrNull("mcp.transportType")?.getString() ?: "websocket"
        environment.log.info("MCP server URL configured: $mcpServerUrl (transport: $transportType)")

        // Подключение будет выполнено асинхронно при старте
        launch {
            try {
                environment.log.info("Connecting to MCP server: $mcpServerUrl")
                val status = mcpService.connect(mcpServerUrl, transportType)
                if (status.connected) {
                    environment.log.info("Successfully connected to MCP server: $mcpServerUrl")
                    // Получить список доступных инструментов
                    val tools = mcpService.listTools()
                    environment.log.info("Available MCP tools: ${tools.tools.joinToString(", ") { it.name }}")
                } else {
                    environment.log.warn("Failed to connect to MCP server $mcpServerUrl: ${status.error}")
                }
            } catch (e: Exception) {
                environment.log.error("Error connecting to MCP server $mcpServerUrl: ${e.message}", e)
            }
        }
    } else {
        environment.log.info("MCP server URL not configured. Set MCP_SERVER_URL environment variable to enable.")
    }

    // Автоматическое подключение к MCP Filesystem Server
    environment.log.info("MCP Filesystem server URL configured: $mcpFilesystemUrl")
    launch {
        try {
            // Даем время серверу запуститься, если он запускается одновременно
            kotlinx.coroutines.delay(2000)

            environment.log.info("Connecting to MCP Filesystem server: $mcpFilesystemUrl")
            val status = mcpService.connect(mcpFilesystemUrl, "websocket")
            if (status.connected) {
                environment.log.info("Successfully connected to MCP Filesystem server")
                // Получить список доступных инструментов
                val tools = mcpService.listTools()
                environment.log.info("Available MCP tools: ${tools.tools.joinToString(", ") { it.name }}")
            } else {
                environment.log.warn("Failed to connect to MCP Filesystem server: ${status.error}")
                environment.log.info("Make sure to start the filesystem MCP server: ./gradlew :mcp-filesystem-server:run")
            }
        } catch (e: Exception) {
            environment.log.error("Error connecting to MCP Filesystem server: ${e.message}", e)
            environment.log.info("Make sure to start the filesystem MCP server: ./gradlew :mcp-filesystem-server:run")
        }
    }

    // Автоматическое подключение к MCP Support Server
    val mcpSupportUrl = environment.config.propertyOrNull("mcp.supportServerUrl")?.getString() ?: "ws://localhost:3003/mcp"
    environment.log.info("MCP Support server URL configured: $mcpSupportUrl")
    launch {
        try {
            // Даем время серверу запуститься
            kotlinx.coroutines.delay(3000)

            environment.log.info("Connecting to MCP Support server: $mcpSupportUrl")
            val status = mcpService.connect(mcpSupportUrl, "websocket")
            if (status.connected) {
                environment.log.info("Successfully connected to MCP Support server")
                // Получить список доступных инструментов
                val tools = mcpService.listTools()
                environment.log.info("Available Support MCP tools: ${tools.tools.joinToString(", ") { it.name }}")
            } else {
                environment.log.warn("Failed to connect to MCP Support server: ${status.error}")
                environment.log.info("Make sure to start the support MCP server: ./gradlew :mcp-support-server:run")
            }
        } catch (e: Exception) {
            environment.log.error("Error connecting to MCP Support server: ${e.message}", e)
            environment.log.info("Make sure to start the support MCP server: ./gradlew :mcp-support-server:run")
        }
    }

    // Автоматическое подключение к MCP Task Server
    val mcpTaskUrl = environment.config.propertyOrNull("mcp.taskServerUrl")?.getString() ?: "ws://localhost:3004/mcp"
    environment.log.info("MCP Task server URL configured: $mcpTaskUrl")
    launch {
        try {
            // Даем время серверу запуститься
            kotlinx.coroutines.delay(4000)

            environment.log.info("Connecting to MCP Task server: $mcpTaskUrl")
            val status = mcpService.connect(mcpTaskUrl, "websocket")
            if (status.connected) {
                environment.log.info("Successfully connected to MCP Task server")
                // Получить список доступных инструментов
                val tools = mcpService.listTools()
                environment.log.info("Available Task MCP tools: ${tools.tools.joinToString(", ") { it.name }}")
            } else {
                environment.log.warn("Failed to connect to MCP Task server: ${status.error}")
                environment.log.info("Make sure to start the task MCP server: ./gradlew :mcp-task-server:run")
            }
        } catch (e: Exception) {
            environment.log.error("Error connecting to MCP Task server: ${e.message}", e)
            environment.log.info("Make sure to start the task MCP server: ./gradlew :mcp-task-server:run")
        }
    }

    // Логирование при старте
    environment.monitor.subscribe(ApplicationStarted) {
        environment.log.info("Application started successfully")
        environment.log.info("Server running on: http://0.0.0.0:${environment.config.property("ktor.deployment.port").getString()}")
        environment.log.info("Using Claude model: $model")
        environment.log.info("Ollama service configured: $ollamaBaseUrl (model: $ollamaModel)")
        environment.log.info("RAG system initialized: ${vectorStoreService.getDocumentsCount()} documents, ${vectorStoreService.getTotalChunksCount()} chunks")

        // Прогрев Ollama модели (загрузка в память)
        launch {
            try {
                environment.log.info("Warming up Ollama model...")
                ollamaService.getEmbedding("test")
                environment.log.info("Ollama model warmed up successfully")
            } catch (e: Exception) {
                environment.log.warn("Failed to warm up Ollama model: ${e.message}")
            }
        }

        // Автовекторизация документов из папки docs/
        launch {
            try {
                environment.log.info("Starting auto-vectorization of docs/ directory...")
                val docsDir = java.io.File("docs")
                if (!docsDir.exists()) {
                    environment.log.warn("docs/ directory does not exist, skipping auto-vectorization")
                    return@launch
                }

                // Рекурсивно найти все .md файлы в docs/ и поддиректориях
                fun File.findMarkdownFiles(): List<java.io.File> {
                    val result = mutableListOf<java.io.File>()
                    if (this.isFile && this.extension == "md") {
                        result.add(this)
                    } else if (this.isDirectory) {
                        this.listFiles()?.forEach { child ->
                            result.addAll(child.findMarkdownFiles())
                        }
                    }
                    return result
                }

                val mdFiles = docsDir.findMarkdownFiles()
                if (mdFiles.isEmpty()) {
                    environment.log.warn("No .md files found in docs/ directory")
                    return@launch
                }

                environment.log.info("Found ${mdFiles.size} .md files in docs/ directory")
                var successCount = 0
                var skipCount = 0

                for (file in mdFiles) {
                    try {
                        val outputFileName = "${file.nameWithoutExtension}_embeddings.json"
                        val outputFile = java.io.File("embeddings_output/$outputFileName")

                        // Пропустить, если embeddings уже существуют
                        if (outputFile.exists()) {
                            environment.log.info("Skipping ${file.name} - embeddings already exist")
                            skipCount++
                            continue
                        }

                        environment.log.info("Vectorizing ${file.name}...")
                        val startTime = System.currentTimeMillis()
                        val text = file.readText()

                        // Разбить текст на чанки
                        val chunks = chunkerService.chunkText(text, 750, 75)
                        environment.log.info("Split ${file.name} into ${chunks.size} chunks")

                        // Векторизовать чанки
                        val chunkTexts = chunks.map { it.text }
                        val embeddingsResponse = ollamaService.getBatchEmbeddings(chunkTexts)
                        val embeddings = embeddingsResponse.embeddings

                        // Создать документ для векторного хранилища
                        val vectorizedChunks = chunks.mapIndexed { index, chunk ->
                            dev.skorobogatov.models.VectorizedChunkInfo(
                                chunkId = chunk.chunkId,
                                text = chunk.text,
                                startWord = chunk.startWord,
                                endWord = chunk.endWord,
                                estimatedTokens = chunk.estimatedTokens,
                                wordCount = chunk.wordCount,
                                embedding = embeddings[index],
                                dimension = embeddings[index].size,
                                processingTimeMs = 0
                            )
                        }

                        val totalTime = System.currentTimeMillis() - startTime
                        val document = dev.skorobogatov.models.VectorizeTextResponse(
                            metadata = dev.skorobogatov.models.VectorizationMetadata(
                                timestamp = java.time.LocalDateTime.now().toString(),
                                totalChunks = chunks.size,
                                chunkSizeTokens = 750,
                                overlapTokens = 75,
                                totalTextTokens = chunkerService.estimateTokens(text),
                                model = ollamaModel,
                                embeddingDimension = embeddingsResponse.dimension,
                                totalProcessingTimeMs = totalTime,
                                savedToFile = outputFile.absolutePath
                            ),
                            chunks = vectorizedChunks
                        )

                        // Сохранить в файл
                        outputFile.parentFile?.mkdirs()
                        outputFile.writeText(kotlinx.serialization.json.Json { prettyPrint = true }.encodeToString(dev.skorobogatov.models.VectorizeTextResponse.serializer(), document))
                        environment.log.info("Successfully vectorized and saved ${file.name}")
                        successCount++
                    } catch (e: Exception) {
                        environment.log.error("Failed to vectorize ${file.name}: ${e.message}", e)
                    }
                }

                environment.log.info("Auto-vectorization completed: $successCount vectorized, $skipCount skipped, ${mdFiles.size - successCount - skipCount} failed")

                // Перезагрузить индекс, если были новые векторизованные документы
                if (successCount > 0) {
                    vectorStoreService.reloadIndex()
                }

                environment.log.info("RAG system updated: ${vectorStoreService.getDocumentsCount()} documents, ${vectorStoreService.getTotalChunksCount()} chunks")
            } catch (e: Exception) {
                environment.log.error("Error during auto-vectorization: ${e.message}", e)
            }
        }
    }

    // Закрытие HTTP клиента при остановке приложения
    environment.monitor.subscribe(ApplicationStopped) {
        httpClient.close()
        environment.log.info("Application stopped")
    }
}
