package dev.skorobogatov.plugins

import dev.skorobogatov.routes.chatRoutes
import dev.skorobogatov.services.ClaudeService
import io.ktor.server.application.*
import io.ktor.server.response.*
import io.ktor.server.routing.*

fun Application.configureRouting(claudeService: ClaudeService) {
    routing {
        get("/") {
            call.respondText("AI Chat Service is running. API available at /api/chat")
        }

        chatRoutes(claudeService)
    }
}
