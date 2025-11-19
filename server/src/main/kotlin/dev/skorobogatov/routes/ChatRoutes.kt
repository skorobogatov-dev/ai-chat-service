package dev.skorobogatov.routes

import dev.skorobogatov.models.*
import dev.skorobogatov.services.ClaudeService
import dev.skorobogatov.services.ConversationHistoryService
import dev.skorobogatov.services.MCPService
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
private fun convertMcpSchemaToJson(inputSchema: JsonObject?): JsonObject {
    // Схема уже приходит в правильном формате из MCPService
    return inputSchema ?: buildJsonObject {
        put("type", "object")
        put("properties", buildJsonObject {})
    }
}

fun Route.chatRoutes(
    claudeService: ClaudeService,
    historyService: ConversationHistoryService,
    mcpService: MCPService
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

                // Получить или создать сессию
                val session = historyService.getOrCreateSession(request.sessionId)
                val isNewSession = request.sessionId == null
                logger.debug("Using session: ${session.sessionId}, isNew: $isNewSession")

                // Добавить сообщение пользователя в историю
                historyService.addUserMessage(session.sessionId, request.message)

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
                            systemPrompt = request.systemPrompt,
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
                        claudeService.sendMessage(allMessages, request.systemPrompt, request.model)
                    }
                } else {
                    logger.debug("MCP server not connected, using standard mode")
                    claudeService.sendMessage(allMessages, request.systemPrompt, request.model)
                }

                // Добавить ответ ассистента в историю
                historyService.addAssistantMessage(session.sessionId, apiResponse.response)

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

                // Создать ответ с sessionId
                val response = apiResponse.copy(
                    sessionId = session.sessionId,
                    historyCompressed = historyCompressed
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
                    timestamp = msg.timestamp
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
                    lastAccessedAt = session.lastAccessedAt
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

    // Health check endpoint
    get("/api/health") {
        call.respond(HttpStatusCode.OK, mapOf("status" to "UP"))
    }
}
