package dev.skorobogatov.services

import dev.skorobogatov.models.*
import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import kotlinx.coroutines.delay
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

    suspend fun sendMessage(
        userMessage: String,
        systemPrompt: String? = null,
        requestModel: String? = null
    ): ChatResponse {
        val startTime = System.currentTimeMillis()
        logger.debug("Sending message to Claude API: $userMessage")

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
            messages = listOf(
                ClaudeMessage(
                    role = "user",
                    content = userMessage
                )
            ),
            system = effectiveSystemPrompt
        )

        // Retry logic с экспоненциальной задержкой для обработки ошибок перегрузки
        val maxRetries = 3
        var lastException: Exception? = null

        try {
            for (attempt in 1..maxRetries) {
                try {
                    val response: HttpResponse = httpClient.post(apiUrl) {
                        header("x-api-key", apiKey)
                        header("anthropic-version", "2023-06-01")
                        contentType(ContentType.Application.Json)
                        setBody(request)
                    }

                    // Если получили 529 (Overloaded) или 503 (Service Unavailable), повторяем попытку
                    if (response.status.value == 529 || response.status.value == 503) {
                        val errorBody = response.bodyAsText()
                        logger.warn("Claude API overloaded (attempt $attempt/$maxRetries): ${response.status} - $errorBody")

                        if (attempt < maxRetries) {
                            val delayMs = (1000L * attempt * attempt) // Экспоненциальная задержка: 1s, 4s, 9s
                            logger.info("Retrying in ${delayMs}ms...")
                            delay(delayMs)
                            continue
                        } else {
                            throw Exception("Claude API overloaded after $maxRetries attempts: ${response.status} - $errorBody")
                        }
                    }

                    // Если статус не OK и не overload, сразу возвращаем ошибку
                    if (response.status != HttpStatusCode.OK) {
                        val errorBody = response.bodyAsText()
                        logger.error("Claude API error: ${response.status} - $errorBody")
                        throw Exception("Claude API error: ${response.status} - $errorBody")
                    }

                    // Успешный ответ
                    val endTime = System.currentTimeMillis()
                    val responseTime = endTime - startTime

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

                    return ChatResponse(
                        response = extractedAnswer,
                        model = apiResponse.model,
                        inputTokens = usage.input_tokens,
                        outputTokens = usage.output_tokens,
                        totalTokens = totalTokens,
                        responseTimeMs = responseTime
                    )
                } catch (e: Exception) {
                    lastException = e
                    if (attempt < maxRetries && (e.message?.contains("529") == true || e.message?.contains("overloaded") == true)) {
                        val delayMs = (1000L * attempt * attempt)
                        logger.info("Retrying after exception in ${delayMs}ms...")
                        delay(delayMs)
                    } else if (attempt >= maxRetries) {
                        break
                    } else {
                        throw e
                    }
                }
            }

            // Если дошли сюда, значит все попытки исчерпаны
            throw lastException ?: Exception("Unknown error during API call")
        } catch (e: Exception) {
            logger.error("Error calling Claude API", e)
            throw Exception("Failed to get response from AI: ${e.message}", e)
        }
    }
}
