package dev.skorobogatov.mcp.support

import dev.skorobogatov.mcp.support.models.*
import dev.skorobogatov.mcp.support.storage.TicketStorage
import dev.skorobogatov.mcp.support.storage.UserStorage
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
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.UUID
import kotlin.time.Duration.Companion.seconds

/**
 * WebSocket MCP сервер для поддержки пользователей (CRM с тикетами)
 */
class SupportMCPServer(
    private val port: Int = 3003,
    private val host: String = "0.0.0.0"
) {
    private val logger = LoggerFactory.getLogger(SupportMCPServer::class.java)
    private val userStorage = UserStorage()
    private val ticketStorage = TicketStorage()

    fun start() {
        logger.info("Starting Support MCP Server on ws://$host:$port/mcp")

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
                        val mcpServer = Server(
                            serverInfo = Implementation(
                                name = "support-mcp-server",
                                version = "1.0.0"
                            ),
                            options = ServerOptions(
                                capabilities = ServerCapabilities(
                                    tools = ServerCapabilities.Tools(listChanged = null)
                                )
                            )
                        )

                        registerTools(mcpServer)

                        val transport = createWebSocketTransport()

                        mcpServer.connect(transport)

                        for (frame in incoming) {
                            when (frame) {
                                is Frame.Text -> {
                                    val messageText = frame.readText()
                                    try {
                                        val json = Json { ignoreUnknownKeys = true }
                                        val message = json.decodeFromString<JSONRPCMessage>(messageText)
                                        transport.messageHandler?.invoke(message)
                                    } catch (e: Exception) {
                                        logger.error("Error processing message: ${e.message}")
                                    }
                                }
                                else -> {}
                            }
                        }
                    } catch (e: ClosedReceiveChannelException) {
                        logger.info("MCP client disconnected")
                    } catch (e: Exception) {
                        logger.error("MCP error: ${e.message}", e)
                    }
                }
            }
        }.start(wait = true)
    }

    private fun registerTools(server: Server) {
        // Tool 1: get_user
        server.addTool(
            name = "get_user",
            description = "Получить информацию о пользователе по userId",
            inputSchema = Tool.Input(
                properties = buildJsonObject {
                    putJsonObject("userId") {
                        put("type", "string")
                        put("description", "ID пользователя (например, user-001)")
                    }
                },
                required = listOf("userId")
            )
        ) { request ->
            handleGetUser(request)
        }

        // Tool 2: search_users
        server.addTool(
            name = "search_users",
            description = "Поиск пользователей по имени или email",
            inputSchema = Tool.Input(
                properties = buildJsonObject {
                    putJsonObject("query") {
                        put("type", "string")
                        put("description", "Строка поиска (имя, email или userId)")
                    }
                },
                required = listOf("query")
            )
        ) { request ->
            handleSearchUsers(request)
        }

        // Tool 3: get_ticket
        server.addTool(
            name = "get_ticket",
            description = "Получить информацию о тикете по ID",
            inputSchema = Tool.Input(
                properties = buildJsonObject {
                    putJsonObject("ticketId") {
                        put("type", "string")
                        put("description", "ID тикета (например, ticket-001)")
                    }
                },
                required = listOf("ticketId")
            )
        ) { request ->
            handleGetTicket(request)
        }

        // Tool 4: search_tickets
        server.addTool(
            name = "search_tickets",
            description = "Поиск тикетов по теме/описанию с опциональной фильтрацией",
            inputSchema = Tool.Input(
                properties = buildJsonObject {
                    putJsonObject("query") {
                        put("type", "string")
                        put("description", "Строка поиска (тема или описание)")
                    }
                    putJsonObject("status") {
                        put("type", "string")
                        put("description", "Статус тикета (OPEN, IN_PROGRESS, WAITING_USER, RESOLVED, CLOSED)")
                    }
                    putJsonObject("userId") {
                        put("type", "string")
                        put("description", "ID пользователя для фильтрации")
                    }
                },
                required = listOf("query")
            )
        ) { request ->
            handleSearchTickets(request)
        }

        // Tool 5: get_user_tickets
        server.addTool(
            name = "get_user_tickets",
            description = "Получить все тикеты пользователя",
            inputSchema = Tool.Input(
                properties = buildJsonObject {
                    putJsonObject("userId") {
                        put("type", "string")
                        put("description", "ID пользователя")
                    }
                    putJsonObject("status") {
                        put("type", "string")
                        put("description", "Опциональная фильтрация по статусу")
                    }
                },
                required = listOf("userId")
            )
        ) { request ->
            handleGetUserTickets(request)
        }

        // Tool 6: create_ticket
        server.addTool(
            name = "create_ticket",
            description = "Создать новый тикет",
            inputSchema = Tool.Input(
                properties = buildJsonObject {
                    putJsonObject("userId") {
                        put("type", "string")
                        put("description", "ID пользователя")
                    }
                    putJsonObject("subject") {
                        put("type", "string")
                        put("description", "Тема тикета")
                    }
                    putJsonObject("description") {
                        put("type", "string")
                        put("description", "Описание проблемы")
                    }
                    putJsonObject("priority") {
                        put("type", "string")
                        put("description", "Приоритет (LOW, MEDIUM, HIGH, URGENT)")
                        put("default", "MEDIUM")
                    }
                },
                required = listOf("userId", "subject", "description")
            )
        ) { request ->
            handleCreateTicket(request)
        }

        // Tool 7: update_ticket
        server.addTool(
            name = "update_ticket",
            description = "Обновить существующий тикет",
            inputSchema = Tool.Input(
                properties = buildJsonObject {
                    putJsonObject("ticketId") {
                        put("type", "string")
                        put("description", "ID тикета")
                    }
                    putJsonObject("status") {
                        put("type", "string")
                        put("description", "Новый статус тикета")
                    }
                    putJsonObject("assignedTo") {
                        put("type", "string")
                        put("description", "Назначить тикет агенту")
                    }
                    putJsonObject("notes") {
                        put("type", "string")
                        put("description", "Заметки или обновление")
                    }
                },
                required = listOf("ticketId")
            )
        ) { request ->
            handleUpdateTicket(request)
        }
    }

    private fun handleGetUser(request: CallToolRequest): CallToolResult {
        return try {
            val arguments = request.arguments as? JsonObject
            val userId = arguments?.get("userId")?.jsonPrimitive?.content ?: ""
            val user = userStorage.getUser(userId)

            if (user != null) {
                val json = Json { prettyPrint = true }
                CallToolResult(
                    content = listOf(TextContent(text = json.encodeToString(User.serializer(), user))),
                    isError = false
                )
            } else {
                CallToolResult(
                    content = listOf(TextContent(text = "Пользователь с ID '$userId' не найден")),
                    isError = true
                )
            }
        } catch (e: Exception) {
            CallToolResult(
                content = listOf(TextContent(text = "Error: ${e.message}")),
                isError = true
            )
        }
    }

    private fun handleSearchUsers(request: CallToolRequest): CallToolResult {
        return try {
            val arguments = request.arguments as? JsonObject
            val query = arguments?.get("query")?.jsonPrimitive?.content ?: ""
            val users = userStorage.searchUsers(query)

            val json = Json { prettyPrint = true }
            val result = buildJsonObject {
                put("totalFound", users.size)
                putJsonArray("users") {
                    users.forEach { user ->
                        addJsonObject {
                            put("userId", user.userId)
                            put("name", user.name)
                            put("email", user.email)
                            put("role", user.role)
                        }
                    }
                }
            }

            CallToolResult(
                content = listOf(TextContent(text = json.encodeToString(JsonObject.serializer(), result))),
                isError = false
            )
        } catch (e: Exception) {
            CallToolResult(
                content = listOf(TextContent(text = "Error: ${e.message}")),
                isError = true
            )
        }
    }

    private fun handleGetTicket(request: CallToolRequest): CallToolResult {
        return try {
            val arguments = request.arguments as? JsonObject
            val ticketId = arguments?.get("ticketId")?.jsonPrimitive?.content ?: ""
            val ticket = ticketStorage.getTicket(ticketId)

            if (ticket != null) {
                val json = Json { prettyPrint = true }
                CallToolResult(
                    content = listOf(TextContent(text = json.encodeToString(Ticket.serializer(), ticket))),
                    isError = false
                )
            } else {
                CallToolResult(
                    content = listOf(TextContent(text = "Тикет с ID '$ticketId' не найден")),
                    isError = true
                )
            }
        } catch (e: Exception) {
            CallToolResult(
                content = listOf(TextContent(text = "Error: ${e.message}")),
                isError = true
            )
        }
    }

    private fun handleSearchTickets(request: CallToolRequest): CallToolResult {
        return try {
            val arguments = request.arguments as? JsonObject
            val query = arguments?.get("query")?.jsonPrimitive?.content ?: ""
            val statusStr = arguments?.get("status")?.jsonPrimitive?.content
            val userId = arguments?.get("userId")?.jsonPrimitive?.content

            val status = statusStr?.let { TicketStatus.valueOf(it) }
            val tickets = ticketStorage.searchTickets(query, status, userId)

            val json = Json { prettyPrint = true }
            val result = buildJsonObject {
                put("totalFound", tickets.size)
                putJsonArray("tickets") {
                    tickets.forEach { ticket ->
                        addJsonObject {
                            put("id", ticket.id)
                            put("userId", ticket.userId)
                            put("subject", ticket.subject)
                            put("status", ticket.status.name)
                            put("priority", ticket.priority.name)
                            put("createdAt", ticket.createdAt)
                        }
                    }
                }
            }

            CallToolResult(
                content = listOf(TextContent(text = json.encodeToString(JsonObject.serializer(), result))),
                isError = false
            )
        } catch (e: Exception) {
            CallToolResult(
                content = listOf(TextContent(text = "Error: ${e.message}")),
                isError = true
            )
        }
    }

    private fun handleGetUserTickets(request: CallToolRequest): CallToolResult {
        return try {
            val arguments = request.arguments as? JsonObject
            val userId = arguments?.get("userId")?.jsonPrimitive?.content ?: ""
            val statusStr = arguments?.get("status")?.jsonPrimitive?.content
            val status = statusStr?.let { TicketStatus.valueOf(it) }

            val tickets = ticketStorage.getUserTickets(userId, status)

            val json = Json { prettyPrint = true }
            val result = buildJsonObject {
                put("userId", userId)
                put("totalTickets", tickets.size)
                putJsonArray("tickets") {
                    tickets.forEach { ticket ->
                        addJsonObject {
                            put("id", ticket.id)
                            put("subject", ticket.subject)
                            put("status", ticket.status.name)
                            put("priority", ticket.priority.name)
                            put("createdAt", ticket.createdAt)
                            put("messagesCount", ticket.messages.size)
                        }
                    }
                }
            }

            CallToolResult(
                content = listOf(TextContent(text = json.encodeToString(JsonObject.serializer(), result))),
                isError = false
            )
        } catch (e: Exception) {
            CallToolResult(
                content = listOf(TextContent(text = "Error: ${e.message}")),
                isError = true
            )
        }
    }

    private fun handleCreateTicket(request: CallToolRequest): CallToolResult {
        return try {
            val arguments = request.arguments as? JsonObject
            val userId = arguments?.get("userId")?.jsonPrimitive?.content ?: ""
            val subject = arguments?.get("subject")?.jsonPrimitive?.content ?: ""
            val description = arguments?.get("description")?.jsonPrimitive?.content ?: ""
            val priorityStr = arguments?.get("priority")?.jsonPrimitive?.content ?: "MEDIUM"

            val priority = try {
                Priority.valueOf(priorityStr)
            } catch (e: Exception) {
                Priority.MEDIUM
            }

            val now = LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME)
            val ticketId = "ticket-${UUID.randomUUID().toString().substring(0, 8)}"

            val initialMessage = TicketMessage(
                id = "msg-${UUID.randomUUID().toString().substring(0, 8)}",
                author = userStorage.getUser(userId)?.name ?: "Unknown User",
                content = description,
                timestamp = now,
                isFromSupport = false
            )

            val ticket = Ticket(
                id = ticketId,
                userId = userId,
                subject = subject,
                description = description,
                status = TicketStatus.OPEN,
                priority = priority,
                createdAt = now,
                updatedAt = now,
                messages = listOf(initialMessage)
            )

            ticketStorage.addTicket(ticket)

            val json = Json { prettyPrint = true }
            val result = buildJsonObject {
                put("success", true)
                put("ticketId", ticketId)
                put("message", "Тикет успешно создан")
            }

            CallToolResult(
                content = listOf(TextContent(text = json.encodeToString(JsonObject.serializer(), result))),
                isError = false
            )
        } catch (e: Exception) {
            CallToolResult(
                content = listOf(TextContent(text = "Error: ${e.message}")),
                isError = true
            )
        }
    }

    private fun handleUpdateTicket(request: CallToolRequest): CallToolResult {
        return try {
            val arguments = request.arguments as? JsonObject
            val ticketId = arguments?.get("ticketId")?.jsonPrimitive?.content ?: ""
            val statusStr = arguments?.get("status")?.jsonPrimitive?.content
            val assignedTo = arguments?.get("assignedTo")?.jsonPrimitive?.content
            val notes = arguments?.get("notes")?.jsonPrimitive?.content

            val ticket = ticketStorage.getTicket(ticketId)

            if (ticket != null) {
                val now = LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME)

                val updatedTicket = ticket.copy(
                    status = statusStr?.let { TicketStatus.valueOf(it) } ?: ticket.status,
                    assignedTo = assignedTo ?: ticket.assignedTo,
                    updatedAt = now,
                    messages = if (notes != null) {
                        ticket.messages + TicketMessage(
                            id = "msg-${UUID.randomUUID().toString().substring(0, 8)}",
                            author = "Support Agent",
                            content = notes,
                            timestamp = now,
                            isFromSupport = true
                        )
                    } else {
                        ticket.messages
                    }
                )

                ticketStorage.updateTicket(updatedTicket)

                val json = Json { prettyPrint = true }
                val result = buildJsonObject {
                    put("success", true)
                    put("ticketId", ticketId)
                    put("message", "Тикет успешно обновлен")
                }

                CallToolResult(
                    content = listOf(TextContent(text = json.encodeToString(JsonObject.serializer(), result))),
                    isError = false
                )
            } else {
                CallToolResult(
                    content = listOf(TextContent(text = "Тикет с ID '$ticketId' не найден")),
                    isError = true
                )
            }
        } catch (e: Exception) {
            CallToolResult(
                content = listOf(TextContent(text = "Error: ${e.message}")),
                isError = true
            )
        }
    }

    private fun DefaultWebSocketServerSession.createWebSocketTransport(): WebSocketTransport {
        return WebSocketTransport(this)
    }

    inner class WebSocketTransport(private val session: DefaultWebSocketServerSession) : Transport {
        var messageHandler: (suspend (JSONRPCMessage) -> Unit)? = null

        override suspend fun start() {
            // Already started via WebSocket connection
        }

        override suspend fun send(message: JSONRPCMessage) {
            val json = Json {
                ignoreUnknownKeys = true
                encodeDefaults = true
            }
            val messageText = json.encodeToString(JSONRPCMessage.serializer(), message)
            session.send(Frame.Text(messageText))
        }

        override suspend fun close() {
            session.close(CloseReason(CloseReason.Codes.NORMAL, "Connection closed"))
        }

        override fun onClose(block: () -> Unit) {
            // Handle close callbacks
        }

        override fun onError(block: (Throwable) -> Unit) {
            // Handle error callbacks
        }

        override fun onMessage(block: suspend (JSONRPCMessage) -> Unit) {
            messageHandler = block
        }
    }
}

fun main() {
    val server = SupportMCPServer()
    server.start()
}
