package dev.skorobogatov.plugins

import dev.skorobogatov.routes.chatRoutes
import dev.skorobogatov.services.ClaudeService
import dev.skorobogatov.services.ConversationHistoryService
import io.ktor.server.application.*
import io.ktor.server.response.*
import io.ktor.server.routing.*

fun Application.configureRouting(
    claudeService: ClaudeService,
    historyService: ConversationHistoryService
) {
    routing {
        chatRoutes(claudeService, historyService)
    }
}
