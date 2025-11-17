package dev.skorobogatov.plugins

import dev.skorobogatov.routes.chatRoutes
import dev.skorobogatov.routes.mcpRoutes
import dev.skorobogatov.services.ClaudeService
import dev.skorobogatov.services.ConversationHistoryService
import dev.skorobogatov.services.MCPService
import io.ktor.server.application.*
import io.ktor.server.response.*
import io.ktor.server.routing.*

fun Application.configureRouting(
    claudeService: ClaudeService,
    historyService: ConversationHistoryService,
    mcpService: MCPService
) {
    routing {
        chatRoutes(claudeService, historyService)
        mcpRoutes(mcpService)
    }
}
