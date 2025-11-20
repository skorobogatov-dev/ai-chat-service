package dev.skorobogatov.routes

import dev.skorobogatov.models.MCPConnectionRequest
import dev.skorobogatov.models.MCPCallToolRequest
import dev.skorobogatov.models.MCPDisconnectRequest
import dev.skorobogatov.services.MCPService
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import org.slf4j.LoggerFactory

private val logger = LoggerFactory.getLogger("MCPRoutes")

fun Route.mcpRoutes(mcpService: MCPService) {
    route("/api/mcp") {

        /**
         * Подключение к MCP серверу
         * POST /api/mcp/connect
         * Body: {"serverUrl": "ws://localhost:8080/mcp", "transportType": "websocket"}
         */
        post("/connect") {
            try {
                val request = call.receive<MCPConnectionRequest>()

                if (request.serverUrl.isBlank()) {
                    call.respond(HttpStatusCode.BadRequest, mapOf("error" to "Server URL cannot be empty"))
                    return@post
                }

                logger.info("Connecting to MCP server: ${request.serverUrl}")
                val status = mcpService.connect(request.serverUrl, request.transportType)

                if (status.connected) {
                    call.respond(HttpStatusCode.OK, status)
                } else {
                    call.respond(HttpStatusCode.BadGateway, status)
                }
            } catch (e: Exception) {
                logger.error("Error connecting to MCP server", e)
                call.respond(
                    HttpStatusCode.InternalServerError,
                    mapOf("error" to (e.message ?: "Unknown error occurred"))
                )
            }
        }

        /**
         * Получение списка доступных инструментов
         * GET /api/mcp/tools
         */
        get("/tools") {
            try {
                logger.info("Fetching MCP tools list")
                val response = mcpService.listTools()

                if (response.connected) {
                    call.respond(HttpStatusCode.OK, response)
                } else {
                    call.respond(
                        HttpStatusCode.ServiceUnavailable,
                        mapOf("error" to "Not connected to MCP server")
                    )
                }
            } catch (e: Exception) {
                logger.error("Error fetching MCP tools", e)
                call.respond(
                    HttpStatusCode.InternalServerError,
                    mapOf("error" to (e.message ?: "Unknown error occurred"))
                )
            }
        }

        /**
         * Вызов инструмента MCP
         * POST /api/mcp/tools/call
         * Body: {"toolName": "echo", "arguments": {"text": "Hello"}}
         */
        post("/tools/call") {
            try {
                val request = call.receive<MCPCallToolRequest>()

                if (request.toolName.isBlank()) {
                    call.respond(HttpStatusCode.BadRequest, mapOf("error" to "Tool name cannot be empty"))
                    return@post
                }

                logger.info("Calling MCP tool: ${request.toolName}")
                val response = mcpService.callTool(request.toolName, request.arguments)

                if (response.success) {
                    call.respond(HttpStatusCode.OK, response)
                } else {
                    call.respond(HttpStatusCode.BadRequest, response)
                }
            } catch (e: Exception) {
                logger.error("Error calling MCP tool", e)
                call.respond(
                    HttpStatusCode.InternalServerError,
                    mapOf("error" to (e.message ?: "Unknown error occurred"))
                )
            }
        }

        /**
         * Получение статуса подключения
         * GET /api/mcp/status
         */
        get("/status") {
            try {
                val status = mcpService.getConnectionStatus()
                call.respond(HttpStatusCode.OK, status)
            } catch (e: Exception) {
                logger.error("Error getting MCP status", e)
                call.respond(
                    HttpStatusCode.InternalServerError,
                    mapOf("error" to (e.message ?: "Unknown error occurred"))
                )
            }
        }

        /**
         * Получение списка всех подключенных MCP серверов
         * GET /api/mcp/servers
         */
        get("/servers") {
            try {
                logger.info("Fetching list of connected MCP servers")
                val response = mcpService.getConnectedServers()
                call.respond(HttpStatusCode.OK, response)
            } catch (e: Exception) {
                logger.error("Error fetching MCP servers list", e)
                call.respond(
                    HttpStatusCode.InternalServerError,
                    mapOf("error" to (e.message ?: "Unknown error occurred"))
                )
            }
        }

        /**
         * Отключение от конкретного MCP сервера
         * POST /api/mcp/disconnect
         * Body: {"serverUrl": "ws://localhost:3000/mcp"}
         */
        post("/disconnect") {
            try {
                val request = call.receive<MCPDisconnectRequest>()

                if (request.serverUrl.isBlank()) {
                    call.respond(HttpStatusCode.BadRequest, mapOf("error" to "Server URL cannot be empty"))
                    return@post
                }

                logger.info("Disconnecting from MCP server: ${request.serverUrl}")
                mcpService.disconnect(request.serverUrl)
                call.respond(
                    HttpStatusCode.OK,
                    mapOf("message" to "Disconnected from MCP server: ${request.serverUrl}")
                )
            } catch (e: Exception) {
                logger.error("Error disconnecting from MCP server", e)
                call.respond(
                    HttpStatusCode.InternalServerError,
                    mapOf("error" to (e.message ?: "Unknown error occurred"))
                )
            }
        }

        /**
         * Отключение от всех MCP серверов
         * POST /api/mcp/disconnect-all
         */
        post("/disconnect-all") {
            try {
                logger.info("Disconnecting from all MCP servers")
                mcpService.disconnectAll()
                call.respond(
                    HttpStatusCode.OK,
                    mapOf("message" to "Disconnected from all MCP servers")
                )
            } catch (e: Exception) {
                logger.error("Error disconnecting from all MCP servers", e)
                call.respond(
                    HttpStatusCode.InternalServerError,
                    mapOf("error" to (e.message ?: "Unknown error occurred"))
                )
            }
        }
    }
}
