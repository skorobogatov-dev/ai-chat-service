package dev.skorobogatov.services

import dev.skorobogatov.models.*
import io.modelcontextprotocol.kotlin.sdk.client.Client
import io.modelcontextprotocol.kotlin.sdk.client.WebSocketClientTransport
import io.modelcontextprotocol.kotlin.sdk.Implementation
import io.ktor.client.*
import io.ktor.client.engine.cio.*
import io.ktor.client.plugins.websocket.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.slf4j.LoggerFactory

/**
 * Сервис для работы с Model Context Protocol (MCP)
 * Управляет подключением к MCP серверу и взаимодействием с инструментами
 */
class MCPService {
    private val logger = LoggerFactory.getLogger(MCPService::class.java)
    private var mcpClient: Client? = null
    private var currentServerUrl: String? = null
    private val connectionMutex = Mutex()

    /**
     * Подключение к MCP серверу
     */
    suspend fun connect(serverUrl: String, transportType: String = "websocket"): MCPConnectionStatus {
        return connectionMutex.withLock {
            try {
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
                mcpClient = client
                currentServerUrl = serverUrl

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
     * Получение списка доступных инструментов
     */
    suspend fun listTools(): MCPToolsResponse {
        return connectionMutex.withLock {
            val client = mcpClient

            if (client == null) {
                logger.warn("Attempted to list tools without active connection")
                return MCPToolsResponse(
                    tools = emptyList(),
                    totalCount = 0,
                    serverUrl = currentServerUrl,
                    connected = false
                )
            }

            try {
                logger.info("Fetching tools list from MCP server")
                val toolsList = client.listTools()

                if (toolsList == null) {
                    logger.warn("listTools() returned null")
                    return MCPToolsResponse(
                        tools = emptyList(),
                        totalCount = 0,
                        serverUrl = currentServerUrl,
                        connected = true
                    )
                }

                val mcpTools = toolsList.tools.map { tool ->
                    MCPToolInfo(
                        name = tool.name,
                        description = tool.description,
                        inputSchema = tool.inputSchema?.let { convertSchemaToMap(it) }
                    )
                }

                logger.info("Successfully retrieved ${mcpTools.size} tools from MCP server")
                MCPToolsResponse(
                    tools = mcpTools,
                    totalCount = mcpTools.size,
                    serverUrl = currentServerUrl,
                    connected = true
                )
            } catch (e: Exception) {
                logger.error("Failed to list tools: ${e.message}", e)
                MCPToolsResponse(
                    tools = emptyList(),
                    totalCount = 0,
                    serverUrl = currentServerUrl,
                    connected = true
                )
            }
        }
    }

    /**
     * Вызов инструмента MCP
     */
    suspend fun callTool(toolName: String, arguments: Map<String, String>): MCPCallToolResponse {
        return connectionMutex.withLock {
            val client = mcpClient

            if (client == null) {
                logger.warn("Attempted to call tool without active connection")
                return MCPCallToolResponse(
                    result = "",
                    toolName = toolName,
                    success = false,
                    error = "Not connected to MCP server"
                )
            }

            try {
                logger.info("Calling MCP tool: $toolName with arguments: $arguments")
                val result = client.callTool(
                    name = toolName,
                    arguments = arguments
                )

                // Извлекаем текстовое содержимое из результата
                val resultText = result?.content
                    ?.filterIsInstance<io.modelcontextprotocol.kotlin.sdk.TextContent>()
                    ?.joinToString("\n") { it.text ?: "" }
                    ?: ""

                logger.info("Successfully called tool: $toolName")
                MCPCallToolResponse(
                    result = resultText,
                    toolName = toolName,
                    success = true
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
            connected = mcpClient != null,
            serverUrl = currentServerUrl
        )
    }

    /**
     * Отключение от MCP сервера
     */
    suspend fun disconnect() {
        connectionMutex.withLock {
            mcpClient = null
            currentServerUrl = null
            logger.info("Disconnected from MCP server")
        }
    }

    /**
     * Вспомогательная функция для конвертации схемы в Map
     */
    private fun convertSchemaToMap(schema: Any): Map<String, String> {
        // Простая конвертация схемы в Map для сериализации
        // В будущем можно расширить для более сложных структур
        return when (schema) {
            is Map<*, *> -> schema.filterKeys { it is String }.mapKeys { it.key as String }.mapValues { (it.value ?: "").toString() }
            else -> mapOf("raw" to schema.toString())
        }
    }
}
