package dev.skorobogatov.routes

import dev.skorobogatov.models.ChatRequest
import dev.skorobogatov.services.ClaudeService
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import org.slf4j.LoggerFactory

private val logger = LoggerFactory.getLogger("ChatRoutes")

fun Route.chatRoutes(claudeService: ClaudeService) {
    route("/api/chat") {
        post {
            try {
                val request = call.receive<ChatRequest>()

                if (request.message.isBlank()) {
                    call.respond(HttpStatusCode.BadRequest, mapOf("error" to "Message cannot be empty"))
                    return@post
                }

                // Validate temperature range (Claude API accepts 0.0 to 1.0)
                if (request.temperature != null && (request.temperature < 0.0 || request.temperature > 1.0)) {
                    call.respond(HttpStatusCode.BadRequest, mapOf("error" to "Temperature must be between 0.0 and 1.0"))
                    return@post
                }

                logger.info("Received chat request with message length: ${request.message.length}")

                val response = claudeService.sendMessage(request.message, request.systemPrompt, request.temperature)
                call.respond(HttpStatusCode.OK, response)
            } catch (e: Exception) {
                logger.error("Error processing chat request", e)
                call.respond(
                    HttpStatusCode.InternalServerError,
                    mapOf("error" to (e.message ?: "Unknown error occurred"))
                )
            }
        }
    }

    // Health check endpoint
    get("/api/health") {
        call.respond(HttpStatusCode.OK, mapOf("status" to "UP"))
    }
}
