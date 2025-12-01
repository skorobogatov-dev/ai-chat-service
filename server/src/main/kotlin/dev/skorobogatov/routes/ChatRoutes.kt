package dev.skorobogatov.routes

import dev.skorobogatov.models.*
import dev.skorobogatov.services.ClaudeService
import dev.skorobogatov.services.ConversationHistoryService
import dev.skorobogatov.services.MCPService
import dev.skorobogatov.services.VectorStoreService
import dev.skorobogatov.services.OllamaService
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import kotlinx.serialization.json.*
import org.slf4j.LoggerFactory

private val logger = LoggerFactory.getLogger("ChatRoutes")

/**
 * Конвертирует MCP схему в JsonObject для Claude API
 */
internal fun convertMcpSchemaToJson(inputSchema: JsonObject?): JsonObject {
    // Схема уже приходит в правильном формате из MCPService
    return inputSchema ?: buildJsonObject {
        put("type", "object")
        put("properties", buildJsonObject {})
    }
}

fun Route.chatRoutes(
    claudeService: ClaudeService,
    historyService: ConversationHistoryService,
    mcpService: MCPService,
    vectorStoreService: VectorStoreService? = null,
    ollamaService: OllamaService? = null,
    commandHandler: dev.skorobogatov.services.CommandHandler? = null
) {
    route("/api/chat") {
        post {
            try {
                val request = call.receive<ChatRequest>()

                if (request.message.isBlank()) {
                    call.respond(HttpStatusCode.BadRequest, mapOf("error" to "Message cannot be empty"))
                    return@post
                }

                logger.info("Received chat request with message length: ${request.message.length}")
                if (request.model != null) {
                    logger.info("Using custom model: ${request.model}")
                }

                // Проверка на команды
                if (commandHandler != null && commandHandler.isCommand(request.message)) {
                    logger.info("Processing command: ${request.message.take(20)}...")

                    val commandResult = commandHandler.handleCommand(request.message)

                    // Получаем или создаем сессию для команды
                    val session = historyService.getOrCreateSession(request.sessionId)
                    val isNewSession = request.sessionId == null

                    // Добавляем команду в историю как сообщение пользователя
                    historyService.addUserMessage(session.sessionId, request.message)

                    // Добавляем результат команды в историю как ответ ассистента
                    historyService.addAssistantMessage(session.sessionId, commandResult.message)

                    // Генерируем название для нового диалога
                    if (isNewSession) {
                        val title = claudeService.generateConversationTitle(request.message)
                        historyService.setConversationTitle(session.sessionId, title)
                    }

                    // Возвращаем ответ
                    val response = ChatResponse(
                        response = commandResult.message,
                        sessionId = session.sessionId,
                        model = "command-handler",
                        inputTokens = 0,
                        outputTokens = 0,
                        totalTokens = 0,
                        responseTimeMs = 0,
                        historyCompressed = false,
                        ragUsed = false,
                        ragChunksFound = 0,
                        ragSources = emptyList(),
                        ragChunks = emptyList(),
                        rerankingUsed = false,
                        rerankingTimeMs = 0
                    )

                    call.respond(HttpStatusCode.OK, response)
                    return@post
                }

                // Получить или создать сессию
                val session = historyService.getOrCreateSession(request.sessionId)
                val isNewSession = request.sessionId == null
                logger.debug("Using session: ${session.sessionId}, isNew: $isNewSession")

                // Добавить сообщение пользователя в историю
                historyService.addUserMessage(session.sessionId, request.message)

                // RAG: поиск похожих документов
                var ragUsed = false
                var ragChunksFound = 0
                val ragSources = mutableListOf<String>()
                val ragChunks = mutableListOf<RAGChunkReference>()
                var enrichedSystemPrompt = request.systemPrompt
                var rerankingUsed = false
                var rerankingTimeMs = 0L

                if (request.useRAG && vectorStoreService != null && ollamaService != null) {
                    try {
                        logger.info("RAG enabled: searching for relevant context (topK=${request.ragTopK}, minSimilarity=${request.ragMinSimilarity}, reranking=${request.useReranking})")

                        // Векторизуем запрос пользователя
                        val queryEmbedding = ollamaService.getEmbedding(request.message)
                        logger.debug("Query vectorized: dimension=${queryEmbedding.dimension}")

                        // Ищем похожие чанки
                        // Если используем reranking, берем больше кандидатов (topK * 2), чтобы было из чего выбирать
                        val initialTopK = if (request.useReranking) request.ragTopK * 2 else request.ragTopK
                        var similarChunks = vectorStoreService.searchSimilarChunks(
                            queryEmbedding = queryEmbedding.embedding,
                            topK = initialTopK,
                            minSimilarity = request.ragMinSimilarity
                        )

                        // Reranking: переранжируем результаты с помощью Claude AI
                        if (request.useReranking && similarChunks.isNotEmpty()) {
                            try {
                                logger.info("Reranking enabled: reranking ${similarChunks.size} candidates")
                                val rerankStartTime = System.currentTimeMillis()

                                val rerankedResults = claudeService.rerankDocuments(
                                    query = request.message,
                                    chunks = similarChunks
                                )

                                rerankingTimeMs = System.currentTimeMillis() - rerankStartTime
                                rerankingUsed = true

                                // Берем топ-K после reranking
                                similarChunks = rerankedResults
                                    .take(request.ragTopK)
                                    .map { (chunk, score) ->
                                        // Обновляем similarity score на reranking score
                                        chunk.copy(similarity = score)
                                    }

                                logger.info("Reranking completed in ${rerankingTimeMs}ms, selected top ${similarChunks.size} chunks")
                            } catch (e: Exception) {
                                logger.error("Reranking failed: ${e.message}, falling back to original ranking", e)
                                // При ошибке используем оригинальные результаты (топ-K без reranking)
                                similarChunks = similarChunks.take(request.ragTopK)
                            }
                        }

                        if (similarChunks.isNotEmpty()) {
                            ragUsed = true
                            ragChunksFound = similarChunks.size
                            ragSources.addAll(similarChunks.map { it.fileName }.distinct())

                            // Сохраняем информацию о найденных чанках для возврата в ответе
                            ragChunks.addAll(similarChunks.map { result ->
                                RAGChunkReference(
                                    fileName = result.fileName,
                                    chunkId = result.chunkInfo.chunkId,
                                    text = result.chunkInfo.text,
                                    similarity = result.similarity,
                                    wordCount = result.chunkInfo.wordCount,
                                    startWord = result.chunkInfo.startWord,
                                    endWord = result.chunkInfo.endWord,
                                    estimatedTokens = result.chunkInfo.estimatedTokens
                                )
                            })

                            // Формируем контекст из найденных чанков
                            val contextText = buildString {
                                appendLine("RELEVANT CONTEXT FROM KNOWLEDGE BASE:")
                                appendLine()
                                similarChunks.forEachIndexed { index, result ->
                                    val scoreLabel = if (rerankingUsed) "relevance" else "similarity"
                                    appendLine("--- Context ${index + 1} ($scoreLabel: ${"%.4f".format(result.similarity)}, source: ${result.fileName}) ---")
                                    appendLine(result.chunkInfo.text)
                                    appendLine()
                                }
                                appendLine("--- END OF CONTEXT ---")
                                appendLine()
                                appendLine("Use the above context to answer the user's question. If the context is relevant, reference it in your answer.")
                            }

                            // Добавляем контекст к системному промпту
                            enrichedSystemPrompt = if (request.systemPrompt != null) {
                                "$contextText\n\n${request.systemPrompt}"
                            } else {
                                contextText
                            }

                            logger.info("RAG context added: ${ragChunksFound} chunks from ${ragSources.size} documents")
                        } else {
                            logger.info("RAG: no relevant context found")
                        }
                    } catch (e: Exception) {
                        logger.error("RAG search failed: ${e.message}", e)
                        // Продолжаем без RAG в случае ошибки
                    }
                }

                // Проверить, нужно ли сжать историю
                var historyCompressed = false
                if (historyService.shouldCompressHistory(session.sessionId)) {
                    logger.info("Compressing history for session ${session.sessionId}")

                    // Получить сообщения для сжатия
                    val messagesToCompress = historyService.getMessagesForCompression(session.sessionId, 3)

                    // Создать список ClaudeMessage для создания summary
                    val claudeMessages = messagesToCompress.map { msg ->
                        val role = when (msg.type) {
                            dev.skorobogatov.models.MessageType.USER -> "user"
                            dev.skorobogatov.models.MessageType.ASSISTANT -> "assistant"
                            dev.skorobogatov.models.MessageType.SUMMARY -> "user"
                        }
                        ClaudeMessage(role = role, content = msg.content)
                    }

                    // Создать summary
                    val summary = claudeService.createSummary(claudeMessages)

                    // Сжать историю
                    historyService.compressHistory(session.sessionId, summary, messagesToCompress.size)
                    historyCompressed = true
                }

                // Получить все сообщения для отправки в Claude API
                val allMessages = session.toClaudeMessages()

                // Проверить, подключен ли MCP сервер и получить инструменты
                val connectionStatus = mcpService.getConnectionStatus()
                val apiResponse = if (connectionStatus.connected) {
                    logger.info("MCP server connected, fetching tools")
                    val mcpToolsResponse = mcpService.listTools()

                    if (mcpToolsResponse.tools.isNotEmpty()) {
                        logger.info("Found ${mcpToolsResponse.tools.size} MCP tools, using tool-enabled mode")

                        // Конвертировать MCP инструменты в формат Claude
                        val claudeTools = mcpToolsResponse.tools.map { mcpTool ->
                            ClaudeTool(
                                name = mcpTool.name,
                                description = mcpTool.description ?: "No description",
                                input_schema = convertMcpSchemaToJson(mcpTool.inputSchema)
                            )
                        }

                        // Отправить запрос с поддержкой инструментов
                        claudeService.sendMessageWithTools(
                            messages = allMessages,
                            tools = claudeTools,
                            systemPrompt = enrichedSystemPrompt,
                            requestModel = request.model,
                            onToolCall = { toolName, input ->
                                logger.info("Executing MCP tool: $toolName")
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
                        claudeService.sendMessage(allMessages, enrichedSystemPrompt, request.model)
                    }
                } else {
                    logger.debug("MCP server not connected, using standard mode")
                    claudeService.sendMessage(allMessages, enrichedSystemPrompt, request.model)
                }

                // Добавить ответ ассистента в историю (с RAG чанками если они есть)
                historyService.addAssistantMessage(
                    session.sessionId,
                    apiResponse.response,
                    ragChunks = if (ragChunks.isNotEmpty()) ragChunks else null
                )

                // Сгенерировать название для нового диалога
                if (isNewSession && session.title == null) {
                    try {
                        val title = claudeService.generateConversationTitle(request.message)
                        historyService.setConversationTitle(session.sessionId, title)
                        logger.debug("Generated title for new conversation: $title")
                    } catch (e: Exception) {
                        logger.warn("Failed to generate title for conversation: ${e.message}")
                    }
                }

                // Создать ответ с sessionId, RAG и reranking информацией
                val response = apiResponse.copy(
                    sessionId = session.sessionId,
                    historyCompressed = historyCompressed,
                    ragUsed = ragUsed,
                    ragChunksFound = ragChunksFound,
                    ragSources = ragSources,
                    ragChunks = ragChunks,
                    rerankingUsed = rerankingUsed,
                    rerankingTimeMs = rerankingTimeMs
                )

                call.respond(HttpStatusCode.OK, response)
            } catch (e: Exception) {
                logger.error("Error processing chat request", e)
                call.respond(
                    HttpStatusCode.InternalServerError,
                    mapOf("error" to (e.message ?: "Unknown error occurred"))
                )
            }
        }
    }

    // Get session history endpoint
    get("/api/chat/history/{sessionId}") {
        try {
            val sessionId = call.parameters["sessionId"]
            if (sessionId.isNullOrBlank()) {
                call.respond(HttpStatusCode.BadRequest, mapOf("error" to "Session ID is required"))
                return@get
            }

            val session = historyService.getSession(sessionId)
            if (session == null) {
                call.respond(HttpStatusCode.NotFound, mapOf("error" to "Session not found"))
                return@get
            }

            // Prepare history data for response
            val historyMessages = session.messages.map { msg ->
                dev.skorobogatov.models.HistoryMessageDto(
                    type = msg.type.name,
                    content = msg.content,
                    timestamp = msg.timestamp,
                    fromScheduledTask = msg.fromScheduledTask,
                    ragChunks = msg.ragChunks
                )
            }

            val response = dev.skorobogatov.models.HistoryResponse(
                sessionId = session.sessionId,
                messages = historyMessages,
                messageCount = session.messages.size,
                pairsCount = session.getMessagePairsCount(),
                createdAt = session.createdAt,
                lastAccessedAt = session.lastAccessedAt
            )

            call.respond(HttpStatusCode.OK, response)
        } catch (e: Exception) {
            logger.error("Error retrieving session history", e)
            call.respond(
                HttpStatusCode.InternalServerError,
                mapOf("error" to (e.message ?: "Unknown error occurred"))
            )
        }
    }

    // Get all conversations endpoint
    get("/api/chat/conversations") {
        try {
            val conversations = historyService.getAllConversations()
            val conversationList = conversations.map { session ->
                dev.skorobogatov.models.ConversationListItem(
                    sessionId = session.sessionId,
                    title = session.title,
                    messageCount = session.messages.size,
                    createdAt = session.createdAt,
                    lastAccessedAt = session.lastAccessedAt,
                    unreadCount = session.getUnreadCount()
                )
            }

            val response = dev.skorobogatov.models.ConversationListResponse(
                conversations = conversationList,
                totalCount = conversationList.size
            )

            call.respond(HttpStatusCode.OK, response)
        } catch (e: Exception) {
            logger.error("Error retrieving conversations list", e)
            call.respond(
                HttpStatusCode.InternalServerError,
                mapOf("error" to (e.message ?: "Unknown error occurred"))
            )
        }
    }

    // Delete conversation endpoint
    delete("/api/chat/conversations/{sessionId}") {
        try {
            val sessionId = call.parameters["sessionId"]
            if (sessionId.isNullOrBlank()) {
                call.respond(HttpStatusCode.BadRequest, mapOf("error" to "Session ID is required"))
                return@delete
            }

            val deleted = historyService.deleteConversation(sessionId)
            if (deleted) {
                call.respond(HttpStatusCode.OK, DeleteConversationResponse(
                    success = true,
                    message = "Conversation deleted"
                ))
            } else {
                call.respond(HttpStatusCode.NotFound, mapOf("error" to "Conversation not found"))
            }
        } catch (e: Exception) {
            logger.error("Error deleting conversation", e)
            call.respond(
                HttpStatusCode.InternalServerError,
                mapOf("error" to (e.message ?: "Unknown error occurred"))
            )
        }
    }

    // Mark messages as read endpoint
    post("/api/chat/mark-read/{sessionId}") {
        try {
            val sessionId = call.parameters["sessionId"]
            if (sessionId.isNullOrBlank()) {
                call.respond(HttpStatusCode.BadRequest, mapOf("error" to "Session ID is required"))
                return@post
            }

            historyService.markAllAsRead(sessionId)
            call.respond(HttpStatusCode.OK, mapOf("success" to true))
        } catch (e: IllegalArgumentException) {
            logger.error("Session not found for mark-read", e)
            call.respond(HttpStatusCode.NotFound, mapOf("error" to e.message))
        } catch (e: Exception) {
            logger.error("Error marking messages as read", e)
            call.respond(
                HttpStatusCode.InternalServerError,
                mapOf("error" to (e.message ?: "Unknown error occurred"))
            )
        }
    }

    // Health check endpoint
    get("/api/health") {
        call.respond(HttpStatusCode.OK, mapOf("status" to "UP"))
    }
}
