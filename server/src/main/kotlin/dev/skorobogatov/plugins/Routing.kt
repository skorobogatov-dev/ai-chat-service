package dev.skorobogatov.plugins

import dev.skorobogatov.routes.chatRoutes
import dev.skorobogatov.routes.embeddingRoutes
import dev.skorobogatov.routes.mcpRoutes
import dev.skorobogatov.routes.supportRoutes
import dev.skorobogatov.routes.taskRoutes
import dev.skorobogatov.routes.taskManagementRoutes
import dev.skorobogatov.services.ClaudeService
import dev.skorobogatov.services.ConversationHistoryService
import dev.skorobogatov.services.MCPService
import dev.skorobogatov.services.OllamaService
import dev.skorobogatov.services.SchedulerService
import io.ktor.server.application.*
import io.ktor.server.response.*
import io.ktor.server.routing.*

fun Application.configureRouting(
    claudeService: ClaudeService,
    historyService: ConversationHistoryService,
    mcpService: MCPService,
    schedulerService: SchedulerService,
    ollamaService: OllamaService,
    chunkerService: dev.skorobogatov.services.TextChunkerService,
    vectorStoreService: dev.skorobogatov.services.VectorStoreService,
    commandHandler: dev.skorobogatov.services.CommandHandler,
    supportSystemPrompt: String
) {
    routing {
        chatRoutes(claudeService, historyService, mcpService, vectorStoreService, ollamaService, commandHandler)
        mcpRoutes(mcpService)
        taskRoutes(schedulerService)
        taskManagementRoutes(mcpService)
        embeddingRoutes(ollamaService, chunkerService, vectorStoreService)
        supportRoutes(claudeService, historyService, mcpService, vectorStoreService, ollamaService, supportSystemPrompt)
    }
}
