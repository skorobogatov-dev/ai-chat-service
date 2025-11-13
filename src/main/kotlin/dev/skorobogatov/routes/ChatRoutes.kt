package dev.skorobogatov.routes

import dev.skorobogatov.models.ChatRequest
import dev.skorobogatov.models.ClaudeMessage
import dev.skorobogatov.services.ClaudeService
import dev.skorobogatov.services.ConversationHistoryService
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import org.slf4j.LoggerFactory

private val logger = LoggerFactory.getLogger("ChatRoutes")

fun Route.chatRoutes(
    claudeService: ClaudeService,
    historyService: ConversationHistoryService
) {
    route("/api/chat") {
        post {
            try {
                val request = call.receive<ChatRequest>()

                if (request.message.isBlank()) {
                    call.respond(HttpStatusCode.BadRequest, mapOf("error" to "Message cannot be empty"))
                    return@post
                }

                logger.info("Received chat request with message length: ${request.message.length}")
                if (request.model != null) {
                    logger.info("Using custom model: ${request.model}")
                }

                // Получить или создать сессию
                val session = historyService.getOrCreateSession(request.sessionId)
                logger.debug("Using session: ${session.sessionId}")

                // Добавить сообщение пользователя в историю
                historyService.addUserMessage(session.sessionId, request.message)

                // Проверить, нужно ли сжать историю
                var historyCompressed = false
                if (historyService.shouldCompressHistory(session.sessionId)) {
                    logger.info("Compressing history for session ${session.sessionId}")

                    // Получить сообщения для сжатия
                    val messagesToCompress = historyService.getMessagesForCompression(session.sessionId, 3)

                    // Создать список ClaudeMessage для создания summary
                    val claudeMessages = messagesToCompress.map { msg ->
                        val role = when (msg.type) {
                            dev.skorobogatov.models.MessageType.USER -> "user"
                            dev.skorobogatov.models.MessageType.ASSISTANT -> "assistant"
                            dev.skorobogatov.models.MessageType.SUMMARY -> "user"
                        }
                        ClaudeMessage(role = role, content = msg.content)
                    }

                    // Создать summary
                    val summary = claudeService.createSummary(claudeMessages)

                    // Сжать историю
                    historyService.compressHistory(session.sessionId, summary, messagesToCompress.size)
                    historyCompressed = true
                }

                // Получить все сообщения для отправки в Claude API
                val allMessages = session.toClaudeMessages()

                // Отправить запрос в Claude API с историей
                val apiResponse = claudeService.sendMessage(
                    allMessages,
                    request.systemPrompt,
                    request.model
                )

                // Добавить ответ ассистента в историю
                historyService.addAssistantMessage(session.sessionId, apiResponse.response)

                // Создать ответ с sessionId
                val response = apiResponse.copy(
                    sessionId = session.sessionId,
                    historyCompressed = historyCompressed
                )

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

    // Get session history endpoint
    get("/api/chat/history/{sessionId}") {
        try {
            val sessionId = call.parameters["sessionId"]
            if (sessionId.isNullOrBlank()) {
                call.respond(HttpStatusCode.BadRequest, mapOf("error" to "Session ID is required"))
                return@get
            }

            val session = historyService.getSession(sessionId)
            if (session == null) {
                call.respond(HttpStatusCode.NotFound, mapOf("error" to "Session not found"))
                return@get
            }

            // Prepare history data for response
            val historyMessages = session.messages.map { msg ->
                dev.skorobogatov.models.HistoryMessageDto(
                    type = msg.type.name,
                    content = msg.content,
                    timestamp = msg.timestamp
                )
            }

            val response = dev.skorobogatov.models.HistoryResponse(
                sessionId = session.sessionId,
                messages = historyMessages,
                messageCount = session.messages.size,
                pairsCount = session.getMessagePairsCount(),
                createdAt = session.createdAt,
                lastAccessedAt = session.lastAccessedAt
            )

            call.respond(HttpStatusCode.OK, response)
        } catch (e: Exception) {
            logger.error("Error retrieving session history", e)
            call.respond(
                HttpStatusCode.InternalServerError,
                mapOf("error" to (e.message ?: "Unknown error occurred"))
            )
        }
    }

    // Health check endpoint
    get("/api/health") {
        call.respond(HttpStatusCode.OK, mapOf("status" to "UP"))
    }
}
