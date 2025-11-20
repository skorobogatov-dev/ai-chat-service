package dev.skorobogatov.services

import dev.skorobogatov.models.*
import io.modelcontextprotocol.kotlin.sdk.client.Client
import io.modelcontextprotocol.kotlin.sdk.client.WebSocketClientTransport
import io.modelcontextprotocol.kotlin.sdk.Implementation
import io.modelcontextprotocol.kotlin.sdk.Tool
import io.ktor.client.*
import io.ktor.client.engine.cio.*
import io.ktor.client.plugins.websocket.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.json.*
import org.slf4j.LoggerFactory

/**
 * Сервис для работы с Model Context Protocol (MCP)
 * Управляет подключением к нескольким MCP серверам и взаимодействием с инструментами
 */
class MCPService {
    private val logger = LoggerFactory.getLogger(MCPService::class.java)
    private val mcpClients = mutableMapOf<String, MCPConnection>()
    private val connectionMutex = Mutex()

    /**
     * Информация о подключении к MCP серверу
     */
    data class MCPConnection(
        val client: Client,
        val serverUrl: String,
        val httpClient: HttpClient
    )

    /**
     * Подключение к MCP серверу
     */
    suspend fun connect(serverUrl: String, transportType: String = "websocket"): MCPConnectionStatus {
        return connectionMutex.withLock {
            try {
                // Проверяем, не подключены ли мы уже к этому серверу
                if (mcpClients.containsKey(serverUrl)) {
                    logger.warn("Already connected to MCP server: $serverUrl")
                    return MCPConnectionStatus(
                        connected = true,
                        serverUrl = serverUrl
                    )
                }

                logger.info("Attempting to connect to MCP server: $serverUrl")

                // Создаем нового клиента
                val client = Client(
                    clientInfo = Implementation(
                        name = "ai-chat-service",
                        version = "1.0.0"
                    )
                )

                // Создаем HTTP клиент для WebSocket
                val httpClient = HttpClient(CIO) {
                    install(WebSockets)
                }

                // Подключаемся через WebSocket
                when (transportType.lowercase()) {
                    "websocket" -> {
                        val transport = WebSocketClientTransport(httpClient, serverUrl)
                        client.connect(transport)
                    }
                    else -> {
                        httpClient.close()
                        throw IllegalArgumentException("Unsupported transport type: $transportType. Only 'websocket' is currently supported.")
                    }
                }

                // Сохраняем клиент и URL
                mcpClients[serverUrl] = MCPConnection(client, serverUrl, httpClient)

                logger.info("Successfully connected to MCP server: $serverUrl")
                MCPConnectionStatus(
                    connected = true,
                    serverUrl = serverUrl
                )
            } catch (e: Exception) {
                logger.error("Failed to connect to MCP server: ${e.message}", e)
                MCPConnectionStatus(
                    connected = false,
                    serverUrl = serverUrl,
                    error = e.message ?: "Unknown connection error"
                )
            }
        }
    }

    /**
     * Получение списка доступных инструментов со всех подключенных серверов
     */
    suspend fun listTools(): MCPToolsResponse {
        return connectionMutex.withLock {
            if (mcpClients.isEmpty()) {
                logger.warn("Attempted to list tools without active connections")
                return MCPToolsResponse(
                    tools = emptyList(),
                    totalCount = 0,
                    serverUrl = null,
                    connected = false
                )
            }

            try {
                logger.info("Fetching tools list from ${mcpClients.size} MCP server(s)")
                val allTools = mutableListOf<MCPToolInfo>()

                // Получаем инструменты со всех подключенных серверов
                mcpClients.forEach { (serverUrl, connection) ->
                    try {
                        val toolsList = connection.client.listTools()

                        toolsList?.tools?.forEach { tool ->
                            allTools.add(
                                MCPToolInfo(
                                    name = tool.name,
                                    description = "${tool.description} (от $serverUrl)",
                                    inputSchema = tool.inputSchema?.let { convertSchemaToMap(it) },
                                    serverUrl = serverUrl
                                )
                            )
                        }
                    } catch (e: Exception) {
                        logger.error("Failed to list tools from $serverUrl: ${e.message}", e)
                    }
                }

                logger.info("Successfully retrieved ${allTools.size} tools from ${mcpClients.size} MCP server(s)")
                MCPToolsResponse(
                    tools = allTools,
                    totalCount = allTools.size,
                    serverUrl = null, // Multiple servers
                    connected = true
                )
            } catch (e: Exception) {
                logger.error("Failed to list tools: ${e.message}", e)
                MCPToolsResponse(
                    tools = emptyList(),
                    totalCount = 0,
                    serverUrl = null,
                    connected = true
                )
            }
        }
    }

    /**
     * Вызов инструмента MCP
     * Автоматически определяет на каком сервере находится инструмент
     */
    suspend fun callTool(toolName: String, arguments: Map<String, String>): MCPCallToolResponse {
        return connectionMutex.withLock {
            if (mcpClients.isEmpty()) {
                logger.warn("Attempted to call tool without active connections")
                return MCPCallToolResponse(
                    result = "",
                    toolName = toolName,
                    success = false,
                    error = "Not connected to any MCP server"
                )
            }

            try {
                logger.info("Calling MCP tool: $toolName with arguments: $arguments")

                // Ищем сервер с этим инструментом
                for ((serverUrl, connection) in mcpClients) {
                    try {
                        val toolsList = connection.client.listTools()
                        val hasTool = toolsList?.tools?.any { it.name == toolName } ?: false

                        if (hasTool) {
                            logger.info("Found tool $toolName on server: $serverUrl")
                            val result = connection.client.callTool(
                                name = toolName,
                                arguments = arguments
                            )

                            // Извлекаем текстовое содержимое из результата
                            val resultText = result?.content
                                ?.filterIsInstance<io.modelcontextprotocol.kotlin.sdk.TextContent>()
                                ?.joinToString("\n") { it.text ?: "" }
                                ?: ""

                            logger.info("Successfully called tool: $toolName on $serverUrl")
                            return MCPCallToolResponse(
                                result = resultText,
                                toolName = toolName,
                                success = true
                            )
                        }
                    } catch (e: Exception) {
                        logger.warn("Error checking tools on $serverUrl: ${e.message}")
                        continue
                    }
                }

                // Инструмент не найден ни на одном сервере
                logger.warn("Tool $toolName not found on any connected server")
                MCPCallToolResponse(
                    result = "",
                    toolName = toolName,
                    success = false,
                    error = "Tool $toolName not found on any connected MCP server"
                )
            } catch (e: Exception) {
                logger.error("Failed to call tool $toolName: ${e.message}", e)
                MCPCallToolResponse(
                    result = "",
                    toolName = toolName,
                    success = false,
                    error = e.message ?: "Unknown error"
                )
            }
        }
    }

    /**
     * Получение статуса подключения
     */
    fun getConnectionStatus(): MCPConnectionStatus {
        return MCPConnectionStatus(
            connected = mcpClients.isNotEmpty(),
            serverUrl = if (mcpClients.size == 1) mcpClients.keys.first() else "${mcpClients.size} servers"
        )
    }

    /**
     * Получение списка всех подключенных серверов
     */
    suspend fun getConnectedServers(): MCPServersListResponse {
        return connectionMutex.withLock {
            val servers = mcpClients.map { (serverUrl, connection) ->
                try {
                    val toolsList = connection.client.listTools()
                    MCPServerInfo(
                        serverUrl = serverUrl,
                        connected = true,
                        toolsCount = toolsList?.tools?.size ?: 0
                    )
                } catch (e: Exception) {
                    MCPServerInfo(
                        serverUrl = serverUrl,
                        connected = false,
                        toolsCount = 0
                    )
                }
            }

            MCPServersListResponse(
                servers = servers,
                totalCount = servers.size
            )
        }
    }

    /**
     * Отключение от конкретного MCP сервера
     */
    suspend fun disconnect(serverUrl: String) {
        connectionMutex.withLock {
            val connection = mcpClients.remove(serverUrl)
            if (connection != null) {
                try {
                    connection.httpClient.close()
                    logger.info("Disconnected from MCP server: $serverUrl")
                } catch (e: Exception) {
                    logger.error("Error closing connection to $serverUrl: ${e.message}", e)
                }
            } else {
                logger.warn("Attempted to disconnect from non-existing server: $serverUrl")
            }
        }
    }

    /**
     * Отключение от всех MCP серверов
     */
    suspend fun disconnectAll() {
        connectionMutex.withLock {
            mcpClients.forEach { (serverUrl, connection) ->
                try {
                    connection.httpClient.close()
                    logger.info("Disconnected from MCP server: $serverUrl")
                } catch (e: Exception) {
                    logger.error("Error closing connection to $serverUrl: ${e.message}", e)
                }
            }
            mcpClients.clear()
            logger.info("Disconnected from all MCP servers")
        }
    }

    /**
     * Вспомогательная функция для конвертации схемы MCP в JsonObject для Claude API
     */
    private fun convertSchemaToMap(schema: Any): JsonObject {
        return when (schema) {
            is Tool.Input -> {
                // Извлекаем properties и required из Tool.Input
                buildJsonObject {
                    put("type", "object")
                    put("properties", schema.properties)
                    schema.required?.let {
                        put("required", JsonArray(it.map { JsonPrimitive(it) }))
                    }
                }
            }
            is JsonObject -> schema
            is Map<*, *> -> {
                // Пытаемся конвертировать Map в JsonObject
                buildJsonObject {
                    schema.forEach { (key, value) ->
                        if (key is String) {
                            when (value) {
                                is String -> put(key, value)
                                is Number -> put(key, value)
                                is Boolean -> put(key, value)
                                else -> put(key, value.toString())
                            }
                        }
                    }
                }
            }
            else -> {
                // Fallback для неизвестных типов
                logger.warn("Unknown schema type: ${schema::class.simpleName}, using toString()")
                buildJsonObject {
                    put("raw", schema.toString())
                }
            }
        }
    }
}
