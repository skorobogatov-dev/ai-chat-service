package dev.skorobogatov.services

import dev.skorobogatov.models.*
import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import org.slf4j.LoggerFactory

class ClaudeService(
    private val httpClient: HttpClient,
    private val apiKey: String,
    private val apiUrl: String,
    private val model: String,
    private val maxTokens: Int,
    private val defaultSystemPrompt: String? = null
) {
    private val logger = LoggerFactory.getLogger(ClaudeService::class.java)

    suspend fun sendMessage(userMessage: String, systemPrompt: String? = null, temperature: Double? = null): ChatResponse {
        logger.debug("Sending message to Claude API: $userMessage")

        // Используем переданный systemPrompt, если есть, иначе дефолтный
        val effectiveSystemPrompt = systemPrompt ?: defaultSystemPrompt

        if (effectiveSystemPrompt != null) {
            logger.debug("Using system prompt: $effectiveSystemPrompt")
        }

        // Логируем temperature для отладки
        if (temperature != null) {
            logger.info("Using temperature: $temperature")
        } else {
            logger.info("Using default temperature (Claude default: 1.0)")
        }

        val request = ClaudeApiRequest(
            model = model,
            max_tokens = maxTokens,
            messages = listOf(
                ClaudeMessage(
                    role = "user",
                    content = userMessage
                )
            ),
            system = effectiveSystemPrompt,
            temperature = temperature
        )

        return try {
            val response: HttpResponse = httpClient.post(apiUrl) {
                header("x-api-key", apiKey)
                header("anthropic-version", "2023-06-01")
                contentType(ContentType.Application.Json)
                setBody(request)
            }

            when (response.status) {
                HttpStatusCode.OK -> {
                    val apiResponse: ClaudeApiResponse = response.body()
                    val messageText = apiResponse.content.firstOrNull()?.text
                        ?: throw Exception("No content in Claude response")

                    logger.debug("Received response from Claude API")
                    logger.debug("Usage: ${apiResponse.usage}")

                    ChatResponse(
                        response = messageText,
                        model = apiResponse.model
                    )
                }
                else -> {
                    val errorBody = response.bodyAsText()
                    logger.error("Claude API error: ${response.status} - $errorBody")
                    throw Exception("Claude API error: ${response.status} - $errorBody")
                }
            }
        } catch (e: Exception) {
            logger.error("Error calling Claude API", e)
            throw Exception("Failed to get response from AI: ${e.message}", e)
        }
    }
}
