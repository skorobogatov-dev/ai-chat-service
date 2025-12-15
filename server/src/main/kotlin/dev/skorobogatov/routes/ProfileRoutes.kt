package dev.skorobogatov.routes

import dev.skorobogatov.services.UserProfileService
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import org.slf4j.LoggerFactory

private val logger = LoggerFactory.getLogger("ProfileRoutes")

/**
 * Роуты для работы с профилем пользователя
 */
fun Route.profileRoutes(userProfileService: UserProfileService) {
    route("/api/profile") {
        // Получить профиль пользователя
        get {
            try {
                val profile = userProfileService.getProfile()
                if (profile != null) {
                    call.respond(HttpStatusCode.OK, profile)
                } else {
                    call.respond(
                        HttpStatusCode.NotFound,
                        mapOf(
                            "error" to "User profile not found",
                            "message" to "Create user_profile.json in the project root to enable personalization"
                        )
                    )
                }
            } catch (e: Exception) {
                logger.error("Error retrieving user profile", e)
                call.respond(
                    HttpStatusCode.InternalServerError,
                    mapOf("error" to (e.message ?: "Unknown error occurred"))
                )
            }
        }

        // Перезагрузить профиль из файла
        post("/reload") {
            try {
                val profile = userProfileService.loadProfile()
                if (profile != null) {
                    logger.info("User profile reloaded successfully: ${profile.name}")
                    call.respond(HttpStatusCode.OK, profile)
                } else {
                    call.respond(
                        HttpStatusCode.NotFound,
                        mapOf("error" to "User profile not found")
                    )
                }
            } catch (e: Exception) {
                logger.error("Error reloading user profile", e)
                call.respond(
                    HttpStatusCode.InternalServerError,
                    mapOf("error" to (e.message ?: "Unknown error occurred"))
                )
            }
        }

        // Получить статус персонализации
        get("/status") {
            try {
                val hasProfile = userProfileService.hasProfile()
                val profile = userProfileService.getProfile()
                val userName = profile?.name ?: "Not set"
                val message = if (hasProfile) {
                    "Personalization is enabled for $userName"
                } else {
                    "Personalization is disabled"
                }

                call.respondText(
                    """{"enabled":$hasProfile,"userName":"$userName","message":"$message"}""",
                    io.ktor.http.ContentType.Application.Json,
                    HttpStatusCode.OK
                )
            } catch (e: Exception) {
                logger.error("Error checking profile status", e)
                call.respond(
                    HttpStatusCode.InternalServerError,
                    mapOf("error" to (e.message ?: "Unknown error occurred"))
                )
            }
        }

        // Получить персонализированный системный промпт (для отладки)
        get("/system-prompt") {
            try {
                val basePrompt = call.request.queryParameters["base"]
                val personalizedPrompt = userProfileService.generatePersonalizedSystemPrompt(basePrompt)
                val hasProfile = userProfileService.hasProfile()

                // Escape JSON string
                val escapedPrompt = personalizedPrompt.replace("\\", "\\\\")
                    .replace("\"", "\\\"")
                    .replace("\n", "\\n")
                    .replace("\r", "\\r")
                    .replace("\t", "\\t")

                call.respondText(
                    """{"systemPrompt":"$escapedPrompt","personalized":$hasProfile}""",
                    io.ktor.http.ContentType.Application.Json,
                    HttpStatusCode.OK
                )
            } catch (e: Exception) {
                logger.error("Error generating personalized system prompt", e)
                call.respond(
                    HttpStatusCode.InternalServerError,
                    mapOf("error" to (e.message ?: "Unknown error occurred"))
                )
            }
        }
    }
}
