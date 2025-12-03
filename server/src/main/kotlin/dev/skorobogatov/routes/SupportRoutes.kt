package dev.skorobogatov.routes

import dev.skorobogatov.models.*
import dev.skorobogatov.services.*
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import kotlinx.serialization.json.*
import org.slf4j.LoggerFactory

private val logger = LoggerFactory.getLogger("SupportRoutes")

fun Route.supportRoutes(
    claudeService: ClaudeService,
    historyService: ConversationHistoryService,
    mcpService: MCPService,
    vectorStoreService: VectorStoreService,
    ollamaService: OllamaService,
    supportSystemPrompt: String
) {
    route("/api/support") {
        // POST /api/support/chat - специальный чат для поддержки с RAG и MCP
        post("/chat") {
            try {
                val request = call.receive<SupportChatRequest>()
                logger.info("Support chat request: userId=${request.userId}, ticketId=${request.ticketId}, useRAG=${request.useRAG}")

                // Получить или создать сессию
                val sessionId = request.sessionId ?: java.util.UUID.randomUUID().toString()
                val session = historyService.getOrCreateSession(sessionId)

                // Собрать контекст из MCP (пользователь и тикет)
                var userContext: UserContext? = null
                var ticketContext: TicketContext? = null
                var additionalContext = ""

                // Получить информацию о пользователе через MCP
                if (request.userId != null) {
                    try {
                        val userResult = mcpService.callTool("get_user", mapOf("userId" to request.userId))
                        if (userResult.success) {
                            val userJson = Json.parseToJsonElement(userResult.result).jsonObject
                            userContext = UserContext(
                                userId = request.userId,
                                name = userJson["name"]?.jsonPrimitive?.content ?: "Unknown",
                                email = userJson["email"]?.jsonPrimitive?.content ?: "Unknown",
                                role = userJson["role"]?.jsonPrimitive?.content ?: "basic"
                            )
                            additionalContext += "\n\nUser Context:\n- Name: ${userContext.name}\n- Email: ${userContext.email}\n- Role: ${userContext.role}\n"

                            // Получить историю тикетов пользователя
                            val ticketsResult = mcpService.callTool("get_user_tickets", mapOf("userId" to request.userId))
                            if (ticketsResult.success) {
                                val ticketsJson = Json.parseToJsonElement(ticketsResult.result).jsonArray
                                if (ticketsJson.isNotEmpty()) {
                                    additionalContext += "\nRecent tickets (${ticketsJson.size} total):\n"
                                    ticketsJson.take(3).forEach { ticket ->
                                        val t = ticket.jsonObject
                                        additionalContext += "- [${t["id"]?.jsonPrimitive?.content}] ${t["subject"]?.jsonPrimitive?.content} (${t["status"]?.jsonPrimitive?.content})\n"
                                    }
                                }
                            }
                        }
                    } catch (e: Exception) {
                        logger.warn("Failed to get user context: ${e.message}")
                    }
                }

                // Получить информацию о конкретном тикете через MCP
                if (request.ticketId != null) {
                    try {
                        val ticketResult = mcpService.callTool("get_ticket", mapOf("ticketId" to request.ticketId))
                        if (ticketResult.success) {
                            val ticketJson = Json.parseToJsonElement(ticketResult.result).jsonObject
                            ticketContext = TicketContext(
                                ticketId = request.ticketId,
                                subject = ticketJson["subject"]?.jsonPrimitive?.content ?: "Unknown",
                                status = ticketJson["status"]?.jsonPrimitive?.content ?: "OPEN"
                            )
                            additionalContext += "\n\nTicket Context:\n- ID: ${ticketContext.ticketId}\n- Subject: ${ticketContext.subject}\n- Status: ${ticketContext.status}\n"

                            // Добавить историю сообщений тикета
                            val messages = ticketJson["messages"]?.jsonArray
                            if (messages != null && messages.isNotEmpty()) {
                                additionalContext += "\nTicket history:\n"
                                messages.forEach { msg ->
                                    val m = msg.jsonObject
                                    val author = m["author"]?.jsonPrimitive?.content ?: "Unknown"
                                    val content = m["content"]?.jsonPrimitive?.content ?: ""
                                    additionalContext += "- $author: $content\n"
                                }
                            }
                        }
                    } catch (e: Exception) {
                        logger.warn("Failed to get ticket context: ${e.message}")
                    }
                }

                // Добавить сообщение пользователя в историю
                historyService.addUserMessage(session.sessionId, request.message)

                // Подготовить системный промпт с дополнительным контекстом
                var systemPrompt = supportSystemPrompt
                if (additionalContext.isNotBlank()) {
                    systemPrompt += "\n\n=== Context from CRM ===\n$additionalContext"
                }

                // RAG: поиск релевантной документации
                var ragContext = ""
                var ragUsed = false
                var ragChunksFound = 0
                val ragSources = mutableListOf<String>()

                if (request.useRAG) {
                    try {
                        // Векторизовать запрос пользователя
                        val queryEmbedding = ollamaService.getEmbedding(request.message).embedding

                        // Поиск похожих чанков в документации
                        val searchResults = vectorStoreService.searchSimilarChunks(
                            queryEmbedding = queryEmbedding,
                            topK = request.ragTopK,
                            minSimilarity = request.ragMinSimilarity
                        )

                        if (searchResults.isNotEmpty()) {
                            ragUsed = true
                            ragChunksFound = searchResults.size
                            ragSources.addAll(searchResults.map { it.fileName }.distinct())

                            ragContext = "\n\n=== Relevant Documentation ===\n"
                            searchResults.forEachIndexed { index, result ->
                                ragContext += "\n[Source: ${result.fileName}, Similarity: ${"%.2f".format(result.similarity)}]\n${result.chunkInfo.text}\n"
                            }

                            systemPrompt += ragContext
                            logger.info("RAG found ${searchResults.size} relevant chunks (sources: ${ragSources.joinToString(", ")})")
                        }
                    } catch (e: Exception) {
                        logger.warn("Failed to perform RAG search: ${e.message}")
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
                            MessageType.USER -> "user"
                            MessageType.ASSISTANT -> "assistant"
                            MessageType.SUMMARY -> "user"
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

                // Отправить запрос в Claude с использованием MCP tools
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
                            systemPrompt = systemPrompt,
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
                        claudeService.sendMessage(allMessages, systemPrompt)
                    }
                } else {
                    logger.debug("MCP server not connected, using standard mode")
                    claudeService.sendMessage(allMessages, systemPrompt)
                }

                // Добавить ответ ассистента в историю
                historyService.addAssistantMessage(session.sessionId, apiResponse.response)

                // Генерация названия диалога (только для новой сессии)
                if (request.sessionId == null && session.messages.size <= 2) {
                    try {
                        val title = claudeService.generateConversationTitle(request.message)
                        historyService.setConversationTitle(session.sessionId, title)
                        logger.info("Generated title for session ${session.sessionId}: $title")
                    } catch (e: Exception) {
                        logger.warn("Failed to generate title: ${e.message}")
                    }
                }

                // Формирование ответа
                val response = SupportChatResponse(
                    response = apiResponse.response,
                    sessionId = session.sessionId,
                    model = apiResponse.model,
                    inputTokens = apiResponse.inputTokens,
                    outputTokens = apiResponse.outputTokens,
                    totalTokens = apiResponse.totalTokens,
                    responseTimeMs = apiResponse.responseTimeMs,
                    ragUsed = ragUsed,
                    ragChunksFound = ragChunksFound,
                    ragSources = ragSources,
                    userContext = userContext,
                    ticketContext = ticketContext
                )

                call.respond(HttpStatusCode.OK, response)
            } catch (e: Exception) {
                logger.error("Error processing support chat request", e)
                call.respond(HttpStatusCode.InternalServerError, mapOf("error" to (e.message ?: "Unknown error")))
            }
        }

        // GET /api/support/user/:userId - информация о пользователе
        get("/user/{userId}") {
            try {
                val userId = call.parameters["userId"] ?: return@get call.respond(
                    HttpStatusCode.BadRequest,
                    mapOf("error" to "userId is required")
                )

                val result = mcpService.callTool("get_user", mapOf("userId" to userId))
                if (!result.success) {
                    return@get call.respond(
                        HttpStatusCode.NotFound,
                        mapOf("error" to (result.error ?: "User not found"))
                    )
                }

                val userJson = Json.parseToJsonElement(result.result).jsonObject

                val user = UserResponse(
                    userId = userJson["userId"]?.jsonPrimitive?.content ?: userId,
                    name = userJson["name"]?.jsonPrimitive?.content ?: "Unknown",
                    email = userJson["email"]?.jsonPrimitive?.content ?: "Unknown",
                    role = userJson["role"]?.jsonPrimitive?.content ?: "basic",
                    registeredAt = userJson["registeredAt"]?.jsonPrimitive?.content ?: ""
                )

                call.respond(HttpStatusCode.OK, user)
            } catch (e: Exception) {
                logger.error("Error getting user info", e)
                call.respond(HttpStatusCode.InternalServerError, mapOf("error" to (e.message ?: "Unknown error")))
            }
        }

        // GET /api/support/tickets/:userId - тикеты пользователя
        get("/tickets/{userId}") {
            try {
                val userId = call.parameters["userId"] ?: return@get call.respond(
                    HttpStatusCode.BadRequest,
                    mapOf("error" to "userId is required")
                )

                val result = mcpService.callTool("get_user_tickets", mapOf("userId" to userId))
                if (!result.success) {
                    return@get call.respond(
                        HttpStatusCode.NotFound,
                        mapOf("error" to (result.error ?: "Tickets not found"))
                    )
                }

                val responseJson = Json.parseToJsonElement(result.result).jsonObject
                val ticketsJson = responseJson["tickets"]?.jsonArray ?: JsonArray(emptyList())

                val tickets = ticketsJson.map { ticketElement ->
                    val ticket = ticketElement.jsonObject
                    TicketResponse(
                        id = ticket["id"]?.jsonPrimitive?.content ?: "",
                        userId = ticket["userId"]?.jsonPrimitive?.content ?: userId,
                        subject = ticket["subject"]?.jsonPrimitive?.content ?: "",
                        description = ticket["description"]?.jsonPrimitive?.content ?: "",
                        status = ticket["status"]?.jsonPrimitive?.content ?: "OPEN",
                        priority = ticket["priority"]?.jsonPrimitive?.content ?: "MEDIUM",
                        createdAt = ticket["createdAt"]?.jsonPrimitive?.content ?: "",
                        updatedAt = ticket["updatedAt"]?.jsonPrimitive?.content ?: "",
                        assignedTo = ticket["assignedTo"]?.jsonPrimitive?.contentOrNull,
                        messages = ticket["messages"]?.jsonArray?.map { msgElement ->
                            val msg = msgElement.jsonObject
                            TicketMessageResponse(
                                id = msg["id"]?.jsonPrimitive?.content ?: "",
                                author = msg["author"]?.jsonPrimitive?.content ?: "",
                                content = msg["content"]?.jsonPrimitive?.content ?: "",
                                timestamp = msg["timestamp"]?.jsonPrimitive?.content ?: "",
                                isFromSupport = msg["isFromSupport"]?.jsonPrimitive?.boolean ?: false
                            )
                        } ?: emptyList()
                    )
                }

                call.respond(HttpStatusCode.OK, TicketListResponse(tickets, tickets.size, userId))
            } catch (e: Exception) {
                logger.error("Error getting user tickets", e)
                call.respond(HttpStatusCode.InternalServerError, mapOf("error" to (e.message ?: "Unknown error")))
            }
        }

        // GET /api/support/ticket/:ticketId - конкретный тикет
        get("/ticket/{ticketId}") {
            try {
                val ticketId = call.parameters["ticketId"] ?: return@get call.respond(
                    HttpStatusCode.BadRequest,
                    mapOf("error" to "ticketId is required")
                )

                val result = mcpService.callTool("get_ticket", mapOf("ticketId" to ticketId))
                if (!result.success) {
                    return@get call.respond(
                        HttpStatusCode.NotFound,
                        mapOf("error" to (result.error ?: "Ticket not found"))
                    )
                }

                val ticketJson = Json.parseToJsonElement(result.result).jsonObject

                val ticket = TicketResponse(
                    id = ticketJson["id"]?.jsonPrimitive?.content ?: ticketId,
                    userId = ticketJson["userId"]?.jsonPrimitive?.content ?: "",
                    subject = ticketJson["subject"]?.jsonPrimitive?.content ?: "",
                    description = ticketJson["description"]?.jsonPrimitive?.content ?: "",
                    status = ticketJson["status"]?.jsonPrimitive?.content ?: "OPEN",
                    priority = ticketJson["priority"]?.jsonPrimitive?.content ?: "MEDIUM",
                    createdAt = ticketJson["createdAt"]?.jsonPrimitive?.content ?: "",
                    updatedAt = ticketJson["updatedAt"]?.jsonPrimitive?.content ?: "",
                    assignedTo = ticketJson["assignedTo"]?.jsonPrimitive?.contentOrNull,
                    messages = ticketJson["messages"]?.jsonArray?.map { msgElement ->
                        val msg = msgElement.jsonObject
                        TicketMessageResponse(
                            id = msg["id"]?.jsonPrimitive?.content ?: "",
                            author = msg["author"]?.jsonPrimitive?.content ?: "",
                            content = msg["content"]?.jsonPrimitive?.content ?: "",
                            timestamp = msg["timestamp"]?.jsonPrimitive?.content ?: "",
                            isFromSupport = msg["isFromSupport"]?.jsonPrimitive?.boolean ?: false
                        )
                    } ?: emptyList()
                )

                call.respond(HttpStatusCode.OK, ticket)
            } catch (e: Exception) {
                logger.error("Error getting ticket", e)
                call.respond(HttpStatusCode.InternalServerError, mapOf("error" to (e.message ?: "Unknown error")))
            }
        }

        // POST /api/support/tickets - создать тикет
        post("/tickets") {
            try {
                val request = call.receive<CreateTicketRequest>()

                val arguments = mapOf(
                    "userId" to request.userId,
                    "subject" to request.subject,
                    "description" to request.description,
                    "priority" to request.priority
                )

                val result = mcpService.callTool("create_ticket", arguments)
                if (!result.success) {
                    return@post call.respond(
                        HttpStatusCode.InternalServerError,
                        mapOf("error" to (result.error ?: "Failed to create ticket"))
                    )
                }

                val ticketJson = Json.parseToJsonElement(result.result).jsonObject

                val ticket = TicketResponse(
                    id = ticketJson["id"]?.jsonPrimitive?.content ?: "",
                    userId = ticketJson["userId"]?.jsonPrimitive?.content ?: request.userId,
                    subject = ticketJson["subject"]?.jsonPrimitive?.content ?: request.subject,
                    description = ticketJson["description"]?.jsonPrimitive?.content ?: request.description,
                    status = ticketJson["status"]?.jsonPrimitive?.content ?: "OPEN",
                    priority = ticketJson["priority"]?.jsonPrimitive?.content ?: request.priority,
                    createdAt = ticketJson["createdAt"]?.jsonPrimitive?.content ?: "",
                    updatedAt = ticketJson["updatedAt"]?.jsonPrimitive?.content ?: "",
                    assignedTo = ticketJson["assignedTo"]?.jsonPrimitive?.contentOrNull,
                    messages = emptyList()
                )

                call.respond(HttpStatusCode.Created, ticket)
            } catch (e: Exception) {
                logger.error("Error creating ticket", e)
                call.respond(HttpStatusCode.InternalServerError, mapOf("error" to (e.message ?: "Unknown error")))
            }
        }

        // PUT /api/support/ticket/:ticketId - обновить тикет
        put("/ticket/{ticketId}") {
            try {
                val ticketId = call.parameters["ticketId"] ?: return@put call.respond(
                    HttpStatusCode.BadRequest,
                    mapOf("error" to "ticketId is required")
                )

                val request = call.receive<UpdateTicketRequest>()

                val arguments = mutableMapOf<String, String>("ticketId" to ticketId)
                if (request.status != null) arguments["status"] = request.status
                if (request.assignedTo != null) arguments["assignedTo"] = request.assignedTo
                if (request.notes != null) arguments["notes"] = request.notes

                val result = mcpService.callTool("update_ticket", arguments)
                if (!result.success) {
                    return@put call.respond(
                        HttpStatusCode.InternalServerError,
                        mapOf("error" to (result.error ?: "Failed to update ticket"))
                    )
                }

                val ticketJson = Json.parseToJsonElement(result.result).jsonObject

                val ticket = TicketResponse(
                    id = ticketJson["id"]?.jsonPrimitive?.content ?: ticketId,
                    userId = ticketJson["userId"]?.jsonPrimitive?.content ?: "",
                    subject = ticketJson["subject"]?.jsonPrimitive?.content ?: "",
                    description = ticketJson["description"]?.jsonPrimitive?.content ?: "",
                    status = ticketJson["status"]?.jsonPrimitive?.content ?: "OPEN",
                    priority = ticketJson["priority"]?.jsonPrimitive?.content ?: "MEDIUM",
                    createdAt = ticketJson["createdAt"]?.jsonPrimitive?.content ?: "",
                    updatedAt = ticketJson["updatedAt"]?.jsonPrimitive?.content ?: "",
                    assignedTo = ticketJson["assignedTo"]?.jsonPrimitive?.contentOrNull,
                    messages = ticketJson["messages"]?.jsonArray?.map { msgElement ->
                        val msg = msgElement.jsonObject
                        TicketMessageResponse(
                            id = msg["id"]?.jsonPrimitive?.content ?: "",
                            author = msg["author"]?.jsonPrimitive?.content ?: "",
                            content = msg["content"]?.jsonPrimitive?.content ?: "",
                            timestamp = msg["timestamp"]?.jsonPrimitive?.content ?: "",
                            isFromSupport = msg["isFromSupport"]?.jsonPrimitive?.boolean ?: false
                        )
                    } ?: emptyList()
                )

                call.respond(HttpStatusCode.OK, ticket)
            } catch (e: Exception) {
                logger.error("Error updating ticket", e)
                call.respond(HttpStatusCode.InternalServerError, mapOf("error" to (e.message ?: "Unknown error")))
            }
        }
    }
}
