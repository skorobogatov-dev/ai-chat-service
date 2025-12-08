package dev.skorobogatov.routes

import dev.skorobogatov.services.OllamaChatService
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import kotlinx.serialization.Serializable
import org.slf4j.LoggerFactory

private val logger = LoggerFactory.getLogger("OllamaRoutes")

@Serializable
data class OllamaModelsListResponse(
    val models: List<OllamaModelItem>,
    val defaultModel: String,
    val count: Int
)

@Serializable
data class OllamaModelItem(
    val name: String,
    val modifiedAt: String?,
    val sizeBytes: Long?
)

@Serializable
data class OllamaChatStatusResponse(
    val available: Boolean,
    val url: String,
    val defaultModel: String,
    val modelsCount: Int,
    val models: List<String>,
    val error: String? = null
)

fun Route.ollamaRoutes(
    ollamaChatService: OllamaChatService
) {
    route("/api/ollama") {

        /**
         * Получить список доступных моделей Ollama для chat
         * GET /api/ollama/models
         */
        get("/models") {
            try {
                logger.info("Fetching Ollama models list")
                val models = ollamaChatService.listModels()

                val response = OllamaModelsListResponse(
                    models = models.map { model ->
                        OllamaModelItem(
                            name = model.name,
                            modifiedAt = model.modified_at,
                            sizeBytes = model.size
                        )
                    },
                    defaultModel = "llama3.2",
                    count = models.size
                )

                call.respond(HttpStatusCode.OK, response)
            } catch (e: Exception) {
                logger.error("Error fetching Ollama models", e)
                call.respond(
                    HttpStatusCode.InternalServerError,
                    mapOf("error" to (e.message ?: "Unknown error occurred"))
                )
            }
        }

        /**
         * Получить статус Ollama Chat сервиса
         * GET /api/ollama/status
         */
        get("/status") {
            try {
                logger.info("Checking Ollama chat service status")
                val status = ollamaChatService.checkStatus()
                val models = if (status.available) ollamaChatService.listModels() else emptyList()

                val response = OllamaChatStatusResponse(
                    available = status.available,
                    url = status.url,
                    defaultModel = status.model,
                    modelsCount = models.size,
                    models = models.map { it.name },
                    error = status.error
                )

                if (status.available) {
                    call.respond(HttpStatusCode.OK, response)
                } else {
                    call.respond(HttpStatusCode.ServiceUnavailable, response)
                }
            } catch (e: Exception) {
                logger.error("Error checking Ollama status", e)
                call.respond(
                    HttpStatusCode.InternalServerError,
                    mapOf("error" to (e.message ?: "Unknown error occurred"))
                )
            }
        }

        /**
         * Проверить доступность конкретной модели
         * GET /api/ollama/models/{modelName}/check
         */
        get("/models/{modelName}/check") {
            try {
                val modelName = call.parameters["modelName"]
                if (modelName.isNullOrBlank()) {
                    call.respond(HttpStatusCode.BadRequest, mapOf("error" to "Model name is required"))
                    return@get
                }

                logger.info("Checking availability of Ollama model: $modelName")
                val isAvailable = ollamaChatService.isModelAvailable(modelName)

                val response = mapOf(
                    "model" to modelName,
                    "available" to isAvailable
                )

                call.respond(HttpStatusCode.OK, response)
            } catch (e: Exception) {
                logger.error("Error checking model availability", e)
                call.respond(
                    HttpStatusCode.InternalServerError,
                    mapOf("error" to (e.message ?: "Unknown error occurred"))
                )
            }
        }
    }
}
