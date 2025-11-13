package dev.skorobogatov.services

import dev.skorobogatov.models.*
import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import kotlinx.serialization.json.Json
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
    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
    }

    /**
     * Отправить сообщение с учетом истории диалога
     */
    suspend fun sendMessage(
        messages: List<ClaudeMessage>,
        systemPrompt: String? = null,
        requestModel: String? = null
    ): ChatResponse {
        val startTime = System.currentTimeMillis()
        logger.debug("Sending ${messages.size} messages to Claude API")

        // Используем переданную модель, если есть, иначе дефолтную из конфигурации
        val effectiveModel = requestModel ?: model
        logger.debug("Using model: $effectiveModel")

        // Используем переданный systemPrompt, если есть, иначе дефолтный
        val effectiveSystemPrompt = systemPrompt ?: defaultSystemPrompt

        if (effectiveSystemPrompt != null) {
            logger.debug("Using system prompt: $effectiveSystemPrompt")
        }

        val request = ClaudeApiRequest(
            model = effectiveModel,
            max_tokens = maxTokens,
            messages = messages,
            system = effectiveSystemPrompt
        )

        return try {
            val response: HttpResponse = httpClient.post(apiUrl) {
                header("x-api-key", apiKey)
                header("anthropic-version", "2023-06-01")
                contentType(ContentType.Application.Json)
                setBody(request)
            }

            val endTime = System.currentTimeMillis()
            val responseTime = endTime - startTime

            when (response.status) {
                HttpStatusCode.OK -> {
                    val apiResponse: ClaudeApiResponse = response.body()
                    val messageText = apiResponse.content.firstOrNull()?.text
                        ?: throw Exception("No content in Claude response")

                    // Попытка парсить JSON ответ и извлечь поле answer
                    val extractedAnswer = try {
                        val jsonResponse = json.decodeFromString<ClaudeJsonResponse>(messageText)
                        logger.debug("Successfully parsed JSON response, extracting answer field")
                        jsonResponse.answer
                    } catch (e: Exception) {
                        logger.debug("Failed to parse JSON response, using full text: ${e.message}")
                        messageText
                    }

                    val usage = apiResponse.usage ?: ClaudeUsage(input_tokens = 0, output_tokens = 0)
                    val totalTokens = usage.input_tokens + usage.output_tokens

                    logger.debug("Received response from Claude API")
                    logger.info("Usage: input=${usage.input_tokens}, output=${usage.output_tokens}, total=$totalTokens tokens, time=${responseTime}ms")

                    ChatResponse(
                        response = extractedAnswer,
                        model = apiResponse.model,
                        inputTokens = usage.input_tokens,
                        outputTokens = usage.output_tokens,
                        totalTokens = totalTokens,
                        responseTimeMs = responseTime
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

    /**
     * Создать summary для сжатия истории диалога
     */
    suspend fun createSummary(messages: List<ClaudeMessage>): String {
        logger.debug("Creating summary for ${messages.size} messages")

        val summaryPrompt = """
            Создай краткое резюме следующего диалога на русском языке.
            Сосредоточься на ключевых моментах, заданных вопросах и важной обсуждаемой информации.
            Резюме должно быть кратким, но передавать суть диалога.

            Диалог для резюмирования:
            ${messages.joinToString("\n") { "${it.role}: ${it.content}" }}
        """.trimIndent()

        val summaryRequest = ClaudeApiRequest(
            model = model,
            max_tokens = 500, // Ограничиваем размер summary
            messages = listOf(ClaudeMessage(role = "user", content = summaryPrompt)),
            system = "Ты помощник, который создает краткие резюме диалогов на русском языке."
        )

        return try {
            val response: HttpResponse = httpClient.post(apiUrl) {
                header("x-api-key", apiKey)
                header("anthropic-version", "2023-06-01")
                contentType(ContentType.Application.Json)
                setBody(summaryRequest)
            }

            when (response.status) {
                HttpStatusCode.OK -> {
                    val apiResponse: ClaudeApiResponse = response.body()
                    val summary = apiResponse.content.firstOrNull()?.text
                        ?: throw Exception("No content in summary response")

                    logger.debug("Successfully created summary (${summary.length} chars)")
                    summary
                }
                else -> {
                    val errorBody = response.bodyAsText()
                    logger.error("Failed to create summary: ${response.status} - $errorBody")
                    throw Exception("Failed to create summary: ${response.status}")
                }
            }
        } catch (e: Exception) {
            logger.error("Error creating summary", e)
            throw Exception("Failed to create summary: ${e.message}", e)
        }
    }
}
