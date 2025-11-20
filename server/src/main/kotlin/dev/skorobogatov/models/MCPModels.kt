package dev.skorobogatov.models

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject

/**
 * Запрос для подключения к MCP серверу
 */
@Serializable
data class MCPConnectionRequest(
    val serverUrl: String,
    val transportType: String = "websocket" // websocket, stdio, sse
)

/**
 * Информация об одном инструменте MCP
 */
@Serializable
data class MCPToolInfo(
    val name: String,
    val description: String?,
    val inputSchema: JsonObject? = null,
    val serverUrl: String? = null // URL сервера, предоставляющего этот инструмент
)

/**
 * Ответ со списком инструментов MCP
 */
@Serializable
data class MCPToolsResponse(
    val tools: List<MCPToolInfo>,
    val totalCount: Int,
    val serverUrl: String?,
    val connected: Boolean
)

/**
 * Запрос для вызова инструмента MCP
 */
@Serializable
data class MCPCallToolRequest(
    val toolName: String,
    val arguments: Map<String, String> = emptyMap()
)

/**
 * Ответ от вызова инструмента MCP
 */
@Serializable
data class MCPCallToolResponse(
    val result: String,
    val toolName: String,
    val success: Boolean,
    val error: String? = null
)

/**
 * Статус подключения к MCP серверу
 */
@Serializable
data class MCPConnectionStatus(
    val connected: Boolean,
    val serverUrl: String?,
    val error: String? = null
)

/**
 * Информация об одном подключенном MCP сервере
 */
@Serializable
data class MCPServerInfo(
    val serverUrl: String,
    val connected: Boolean,
    val toolsCount: Int = 0
)

/**
 * Список всех подключенных MCP серверов
 */
@Serializable
data class MCPServersListResponse(
    val servers: List<MCPServerInfo>,
    val totalCount: Int
)

/**
 * Запрос для отключения от конкретного MCP сервера
 */
@Serializable
data class MCPDisconnectRequest(
    val serverUrl: String
)
