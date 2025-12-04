package dev.skorobogatov.mcp.task

import dev.skorobogatov.mcp.task.models.*
import dev.skorobogatov.mcp.task.storage.TaskStorage
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
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.*
import org.slf4j.LoggerFactory
import java.io.File
import kotlin.time.Duration.Companion.seconds

private val logger = LoggerFactory.getLogger("TaskMCPServer")

fun main() {
    val server = TaskMCPServer()
    server.start()
}

class TaskMCPServer(
    private val port: Int = 3004,
    private val host: String = "0.0.0.0"
) {
    private val logger = LoggerFactory.getLogger(TaskMCPServer::class.java)
    private val storage: TaskStorage

    init {
        val resourcePath = this::class.java.classLoader.getResource("data/tasks.json")
        val dataFile = if (resourcePath != null) {
            File(resourcePath.toURI())
        } else {
            // Fallback to relative path for development
            File("mcp-task-server/src/main/resources/data/tasks.json")
        }
        storage = TaskStorage(dataFile)
        logger.info("TaskStorage initialized with ${storage.getAllTasks().size} tasks from ${dataFile.absolutePath}")
    }

    fun start() {
        logger.info("Starting Task MCP Server on ws://$host:$port/mcp")

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
                                name = "task-mcp-server",
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
                                        transport.handleMessage(message)
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

    private fun DefaultWebSocketServerSession.createWebSocketTransport(): WebSocketTransport {
        return object : WebSocketTransport {
            private var messageHandler: (suspend (JSONRPCMessage) -> Unit)? = null

            override suspend fun start() {}

            override suspend fun send(message: JSONRPCMessage) {
                try {
                    val json = Json { encodeDefaults = true; ignoreUnknownKeys = true }
                    val messageText = json.encodeToString(JSONRPCMessage.serializer(), message)
                    send(Frame.Text(messageText))
                } catch (e: Exception) {
                    logger.error("Error sending message: ${e.message}")
                }
            }

            override suspend fun close() {
                try {
                    close(CloseReason(CloseReason.Codes.NORMAL, "Connection closed"))
                } catch (e: Exception) {
                    logger.error("Error closing connection: ${e.message}")
                }
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

            override suspend fun handleMessage(message: JSONRPCMessage) {
                messageHandler?.invoke(message)
            }
        }
    }

    // Custom Transport interface extension with message handling
    private interface WebSocketTransport : Transport {
        suspend fun handleMessage(message: JSONRPCMessage)
    }

    private fun registerTools(server: Server) {
        val json = Json { prettyPrint = true }

        // Tool 1: create_task
        server.addTool(
            name = "create_task",
            description = "Create a new task with specified parameters",
            inputSchema = Tool.Input(
                properties = buildJsonObject {
                    putJsonObject("title") {
                        put("type", "string")
                        put("description", "Task title")
                    }
                    putJsonObject("description") {
                        put("type", "string")
                        put("description", "Task description")
                    }
                    putJsonObject("priority") {
                        put("type", "string")
                        put("description", "Task priority (LOW, MEDIUM, HIGH, CRITICAL)")
                        put("enum", JsonArray(listOf("LOW", "MEDIUM", "HIGH", "CRITICAL").map { JsonPrimitive(it) }))
                    }
                    putJsonObject("assignee") {
                        put("type", "string")
                        put("description", "Task assignee (optional)")
                    }
                    putJsonObject("dueDate") {
                        put("type", "string")
                        put("description", "Due date in ISO-8601 format (optional)")
                    }
                    putJsonObject("tags") {
                        put("type", "array")
                        putJsonObject("items") {
                            put("type", "string")
                        }
                        put("description", "Task tags (optional)")
                    }
                    putJsonObject("project") {
                        put("type", "string")
                        put("description", "Project name (optional)")
                    }
                    putJsonObject("estimatedHours") {
                        put("type", "integer")
                        put("description", "Estimated hours (optional)")
                    }
                },
                required = listOf("title", "description", "priority")
            )
        ) { request ->
            val arguments = request.arguments ?: JsonObject(emptyMap())
            val title = arguments["title"]?.jsonPrimitive?.content ?: error("title is required")
            val description = arguments["description"]?.jsonPrimitive?.content ?: error("description is required")
            val priorityStr = arguments["priority"]?.jsonPrimitive?.content ?: "MEDIUM"
            val priority = Priority.valueOf(priorityStr)
            val assignee = arguments["assignee"]?.jsonPrimitive?.contentOrNull
            val dueDate = arguments["dueDate"]?.jsonPrimitive?.contentOrNull
            val tags = arguments["tags"]?.jsonArray?.map { it.jsonPrimitive.content } ?: emptyList()
            val project = arguments["project"]?.jsonPrimitive?.contentOrNull
            val estimatedHours = arguments["estimatedHours"]?.jsonPrimitive?.intOrNull

            val task = storage.createTask(
                title = title,
                description = description,
                priority = priority,
                assignee = assignee,
                dueDate = dueDate,
                tags = tags,
                project = project,
                estimatedHours = estimatedHours
            )

            val result = json.encodeToString(Task.serializer(), task)
            CallToolResult(content = listOf(TextContent(text = result)))
        }

        // Tool 2: update_task
        server.addTool(
            name = "update_task",
            description = "Update an existing task",
            inputSchema = Tool.Input(
                properties = buildJsonObject {
                    putJsonObject("taskId") {
                        put("type", "string")
                        put("description", "Task ID to update")
                    }
                    putJsonObject("status") {
                        put("type", "string")
                        put("description", "New status (optional)")
                        put("enum", JsonArray(listOf("TODO", "IN_PROGRESS", "DONE", "BLOCKED").map { JsonPrimitive(it) }))
                    }
                    putJsonObject("priority") {
                        put("type", "string")
                        put("description", "New priority (optional)")
                        put("enum", JsonArray(listOf("LOW", "MEDIUM", "HIGH", "CRITICAL").map { JsonPrimitive(it) }))
                    }
                    putJsonObject("assignee") {
                        put("type", "string")
                        put("description", "New assignee (optional)")
                    }
                    putJsonObject("dueDate") {
                        put("type", "string")
                        put("description", "New due date (optional)")
                    }
                    putJsonObject("tags") {
                        put("type", "array")
                        putJsonObject("items") {
                            put("type", "string")
                        }
                        put("description", "New tags (optional)")
                    }
                    putJsonObject("actualHours") {
                        put("type", "integer")
                        put("description", "Actual hours spent (optional)")
                    }
                },
                required = listOf("taskId")
            )
        ) { request ->
            val arguments = request.arguments ?: JsonObject(emptyMap())
            val taskId = arguments["taskId"]?.jsonPrimitive?.content ?: error("taskId is required")
            val status = arguments["status"]?.jsonPrimitive?.contentOrNull?.let { TaskStatus.valueOf(it) }
            val priority = arguments["priority"]?.jsonPrimitive?.contentOrNull?.let { Priority.valueOf(it) }
            val assignee = arguments["assignee"]?.jsonPrimitive?.contentOrNull
            val dueDate = arguments["dueDate"]?.jsonPrimitive?.contentOrNull
            val tags = arguments["tags"]?.jsonArray?.map { it.jsonPrimitive.content }
            val actualHours = arguments["actualHours"]?.jsonPrimitive?.intOrNull

            val task = storage.updateTask(
                taskId = taskId,
                status = status,
                priority = priority,
                assignee = assignee,
                dueDate = dueDate,
                tags = tags,
                actualHours = actualHours
            )

            if (task == null) {
                CallToolResult(content = listOf(TextContent(text = "Error: Task with ID $taskId not found")), isError = true)
            } else {
                val result = json.encodeToString(Task.serializer(), task)
                CallToolResult(content = listOf(TextContent(text = result)))
            }
        }

        // Tool 3: get_task
        server.addTool(
            name = "get_task",
            description = "Get a specific task by ID",
            inputSchema = Tool.Input(
                properties = buildJsonObject {
                    putJsonObject("taskId") {
                        put("type", "string")
                        put("description", "Task ID")
                    }
                },
                required = listOf("taskId")
            )
        ) { request ->
            val arguments = request.arguments ?: JsonObject(emptyMap())
            val taskId = arguments["taskId"]?.jsonPrimitive?.content ?: error("taskId is required")
            val task = storage.getTask(taskId)

            if (task == null) {
                CallToolResult(content = listOf(TextContent(text = "Error: Task with ID $taskId not found")), isError = true)
            } else {
                val result = json.encodeToString(Task.serializer(), task)
                CallToolResult(content = listOf(TextContent(text = result)))
            }
        }

        // Tool 4: list_tasks
        server.addTool(
            name = "list_tasks",
            description = "List tasks with optional filters",
            inputSchema = Tool.Input(
                properties = buildJsonObject {
                    putJsonObject("status") {
                        put("type", "string")
                        put("description", "Filter by status (optional)")
                        put("enum", JsonArray(listOf("TODO", "IN_PROGRESS", "DONE", "BLOCKED").map { JsonPrimitive(it) }))
                    }
                    putJsonObject("priority") {
                        put("type", "string")
                        put("description", "Filter by priority (optional)")
                        put("enum", JsonArray(listOf("LOW", "MEDIUM", "HIGH", "CRITICAL").map { JsonPrimitive(it) }))
                    }
                    putJsonObject("assignee") {
                        put("type", "string")
                        put("description", "Filter by assignee (optional)")
                    }
                    putJsonObject("project") {
                        put("type", "string")
                        put("description", "Filter by project (optional)")
                    }
                    putJsonObject("limit") {
                        put("type", "integer")
                        put("description", "Maximum number of tasks to return (optional)")
                    }
                },
                required = emptyList()
            )
        ) { request ->
            val arguments = request.arguments ?: JsonObject(emptyMap())
            val status = arguments["status"]?.jsonPrimitive?.contentOrNull?.let { TaskStatus.valueOf(it) }
            val priority = arguments["priority"]?.jsonPrimitive?.contentOrNull?.let { Priority.valueOf(it) }
            val assignee = arguments["assignee"]?.jsonPrimitive?.contentOrNull
            val project = arguments["project"]?.jsonPrimitive?.contentOrNull
            val limit = arguments["limit"]?.jsonPrimitive?.intOrNull

            val tasks = storage.listTasks(
                status = status,
                priority = priority,
                assignee = assignee,
                project = project,
                limit = limit
            )

            @Serializable
            data class TaskListResponse(val tasks: List<Task>, val totalCount: Int)
            
            val response = TaskListResponse(tasks = tasks, totalCount = tasks.size)
            val result = json.encodeToString(TaskListResponse.serializer(), response)
            CallToolResult(content = listOf(TextContent(text = result)))
        }

        // Tool 5: search_tasks
        server.addTool(
            name = "search_tasks",
            description = "Search tasks by query string (searches in title, description, and tags)",
            inputSchema = Tool.Input(
                properties = buildJsonObject {
                    putJsonObject("query") {
                        put("type", "string")
                        put("description", "Search query")
                    }
                    putJsonObject("status") {
                        put("type", "string")
                        put("description", "Filter by status (optional)")
                        put("enum", JsonArray(listOf("TODO", "IN_PROGRESS", "DONE", "BLOCKED").map { JsonPrimitive(it) }))
                    }
                    putJsonObject("priority") {
                        put("type", "string")
                        put("description", "Filter by priority (optional)")
                        put("enum", JsonArray(listOf("LOW", "MEDIUM", "HIGH", "CRITICAL").map { JsonPrimitive(it) }))
                    }
                },
                required = listOf("query")
            )
        ) { request ->
            val arguments = request.arguments ?: JsonObject(emptyMap())
            val query = arguments["query"]?.jsonPrimitive?.content ?: error("query is required")
            val status = arguments["status"]?.jsonPrimitive?.contentOrNull?.let { TaskStatus.valueOf(it) }
            val priority = arguments["priority"]?.jsonPrimitive?.contentOrNull?.let { Priority.valueOf(it) }

            val tasks = storage.searchTasks(
                query = query,
                status = status,
                priority = priority
            )

            @Serializable
            data class SearchResponse(val tasks: List<Task>, val totalCount: Int, val query: String)
            
            val response = SearchResponse(tasks = tasks, totalCount = tasks.size, query = query)
            val result = json.encodeToString(SearchResponse.serializer(), response)
            CallToolResult(content = listOf(TextContent(text = result)))
        }

        // Tool 6: get_stats
        server.addTool(
            name = "get_stats",
            description = "Get project statistics (total tasks, by status, by priority, overdue, completion rate, etc.)",
            inputSchema = Tool.Input(
                properties = buildJsonObject {
                    putJsonObject("project") {
                        put("type", "string")
                        put("description", "Filter by project (optional)")
                    }
                },
                required = emptyList()
            )
        ) { request ->
            val arguments = request.arguments ?: JsonObject(emptyMap())
            val project = arguments["project"]?.jsonPrimitive?.contentOrNull
            val stats = storage.getStats(project = project)

            val result = json.encodeToString(ProjectStats.serializer(), stats)
            CallToolResult(content = listOf(TextContent(text = result)))
        }

        // Tool 7: get_recommendations
        server.addTool(
            name = "get_recommendations",
            description = "Get recommended tasks to work on next (top 5 tasks based on priority, due dates, and dependencies)",
            inputSchema = Tool.Input(
                properties = buildJsonObject {
                    putJsonObject("assignee") {
                        put("type", "string")
                        put("description", "Filter by assignee (optional)")
                    }
                },
                required = emptyList()
            )
        ) { request ->
            val arguments = request.arguments ?: JsonObject(emptyMap())
            val assignee = arguments["assignee"]?.jsonPrimitive?.contentOrNull
            val tasks = storage.getRecommendations(assignee = assignee)

            @Serializable
            data class RecommendationsResponse(
                val recommendations: List<Task>,
                val totalCount: Int,
                val message: String
            )
            
            val response = RecommendationsResponse(
                recommendations = tasks,
                totalCount = tasks.size,
                message = "Top ${tasks.size} recommended tasks based on priority, due dates, and dependencies"
            )
            val result = json.encodeToString(RecommendationsResponse.serializer(), response)
            CallToolResult(content = listOf(TextContent(text = result)))
        }

        logger.info("Registered 7 MCP tools: create_task, update_task, get_task, list_tasks, search_tasks, get_stats, get_recommendations")
    }
}
