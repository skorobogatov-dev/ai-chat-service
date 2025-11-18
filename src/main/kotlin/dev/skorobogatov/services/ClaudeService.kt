package dev.skorobogatov.services

import dev.skorobogatov.models.*
import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
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
     * Очищает ответ от markdown code fences (```json, ```, и т.д.)
     */
    private fun cleanMarkdownCodeFences(text: String): String {
        var cleaned = text.trim()

        // Убираем ```json ... ``` или ```text ... ``` или просто ``` ... ```
        val codeBlockRegex = Regex("^```(?:json|text)?\\s*\\n?([\\s\\S]*?)\\n?```$", RegexOption.MULTILINE)
        val match = codeBlockRegex.find(cleaned)
        if (match != null) {
            cleaned = match.groupValues[1].trim()
        }

        return cleaned
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

        // Конвертируем старые сообщения в новый формат
        val requestMessages = messages.map { msg ->
            ClaudeMessageRequest(
                role = msg.role,
                content = listOf(ClaudeContentRequest(type = "text", text = msg.content))
            )
        }

        val request = ClaudeApiRequest(
            model = effectiveModel,
            max_tokens = maxTokens,
            messages = requestMessages,
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

                    // Очистить ответ от markdown code fences
                    val cleanedAnswer = cleanMarkdownCodeFences(extractedAnswer)

                    val usage = apiResponse.usage ?: ClaudeUsage(input_tokens = 0, output_tokens = 0)
                    val totalTokens = usage.input_tokens + usage.output_tokens

                    logger.debug("Received response from Claude API")
                    logger.info("Usage: input=${usage.input_tokens}, output=${usage.output_tokens}, total=$totalTokens tokens, time=${responseTime}ms")

                    ChatResponse(
                        response = cleanedAnswer,
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
     * Отправить сообщение с поддержкой MCP tools
     * Обрабатывает tool use loop автоматически
     */
    suspend fun sendMessageWithTools(
        messages: List<ClaudeMessage>,
        tools: List<ClaudeTool>,
        systemPrompt: String? = null,
        requestModel: String? = null,
        onToolCall: suspend (toolName: String, input: JsonObject) -> String
    ): ChatResponse {
        val startTime = System.currentTimeMillis()
        logger.debug("Sending ${messages.size} messages with ${tools.size} tools to Claude API")

        val effectiveModel = requestModel ?: model
        val effectiveSystemPrompt = systemPrompt ?: defaultSystemPrompt

        // Конвертируем старые сообщения в новый формат
        val requestMessages = messages.map { msg ->
            ClaudeMessageRequest(
                role = msg.role,
                content = listOf(ClaudeContentRequest(type = "text", text = msg.content))
            )
        }.toMutableList()

        var totalInputTokens = 0
        var totalOutputTokens = 0
        var finalResponse: String? = null
        var iterations = 0
        val maxIterations = 10 // Защита от бесконечных циклов

        while (iterations < maxIterations) {
            iterations++
            logger.debug("Tool use iteration $iterations")

            val request = ClaudeApiRequest(
                model = effectiveModel,
                max_tokens = maxTokens,
                messages = requestMessages,
                system = effectiveSystemPrompt,
                tools = tools
            )

            val response: HttpResponse = try {
                httpClient.post(apiUrl) {
                    header("x-api-key", apiKey)
                    header("anthropic-version", "2023-06-01")
                    contentType(ContentType.Application.Json)
                    setBody(request)
                }
            } catch (e: Exception) {
                logger.error("Error calling Claude API", e)
                throw Exception("Failed to get response from AI: ${e.message}", e)
            }

            when (response.status) {
                HttpStatusCode.OK -> {
                    val apiResponse: ClaudeApiResponse = response.body()

                    val usage = apiResponse.usage ?: ClaudeUsage(input_tokens = 0, output_tokens = 0)
                    totalInputTokens += usage.input_tokens
                    totalOutputTokens += usage.output_tokens

                    logger.debug("Received response with stop_reason: ${apiResponse.stop_reason}")

                    // Проверяем, нужно ли вызывать инструменты
                    if (apiResponse.stop_reason == "tool_use") {
                        // Добавляем ответ ассистента в историю (конвертируем Response в Request)
                        val assistantContent = apiResponse.content.map { responseContent ->
                            ClaudeContentRequest(
                                type = responseContent.type,
                                text = responseContent.text,
                                id = responseContent.id,
                                name = responseContent.name,
                                input = responseContent.input
                            )
                        }
                        requestMessages.add(ClaudeMessageRequest(
                            role = "assistant",
                            content = assistantContent
                        ))

                        // Обрабатываем все tool_use блоки
                        val toolResults = mutableListOf<ClaudeContentRequest>()

                        for (content in apiResponse.content) {
                            if (content.type == "tool_use") {
                                val toolName = content.name ?: continue
                                val toolInput = content.input ?: JsonObject(emptyMap())
                                val toolUseId = content.id ?: continue

                                logger.info("Calling MCP tool: $toolName with input: $toolInput")

                                try {
                                    val toolResult = onToolCall(toolName, toolInput)
                                    logger.debug("Tool $toolName returned: ${toolResult.take(100)}...")

                                    toolResults.add(ClaudeContentRequest(
                                        type = "tool_result",
                                        tool_use_id = toolUseId,
                                        content = toolResult
                                    ))
                                } catch (e: Exception) {
                                    logger.error("Error calling tool $toolName: ${e.message}", e)
                                    toolResults.add(ClaudeContentRequest(
                                        type = "tool_result",
                                        tool_use_id = toolUseId,
                                        content = "Error: ${e.message}",
                                    ))
                                }
                            }
                        }

                        // Добавляем результаты инструментов как новое сообщение пользователя
                        if (toolResults.isNotEmpty()) {
                            requestMessages.add(ClaudeMessageRequest(
                                role = "user",
                                content = toolResults
                            ))
                        }

                        // Продолжаем цикл для следующего запроса
                        continue
                    } else {
                        // Получили финальный ответ
                        val messageText = apiResponse.content.firstOrNull { it.type == "text" }?.text
                            ?: throw Exception("No text content in Claude response")

                        // Попытка парсить JSON ответ и извлечь поле answer
                        finalResponse = try {
                            val jsonResponse = json.decodeFromString<ClaudeJsonResponse>(messageText)
                            logger.debug("Successfully parsed JSON response, extracting answer field")
                            jsonResponse.answer
                        } catch (e: Exception) {
                            logger.debug("Failed to parse JSON response, using full text: ${e.message}")
                            messageText
                        }

                        break
                    }
                }
                else -> {
                    val errorBody = response.bodyAsText()
                    logger.error("Claude API error: ${response.status} - $errorBody")
                    throw Exception("Claude API error: ${response.status} - $errorBody")
                }
            }
        }

        if (iterations >= maxIterations) {
            throw Exception("Tool use loop exceeded maximum iterations ($maxIterations)")
        }

        val endTime = System.currentTimeMillis()
        val responseTime = endTime - startTime
        val totalTokens = totalInputTokens + totalOutputTokens

        logger.info("Usage: input=$totalInputTokens, output=$totalOutputTokens, total=$totalTokens tokens, time=${responseTime}ms, iterations=$iterations")

        // Очистить финальный ответ от markdown code fences
        val cleanedFinalResponse = cleanMarkdownCodeFences(finalResponse ?: "")

        return ChatResponse(
            response = cleanedFinalResponse,
            model = effectiveModel,
            inputTokens = totalInputTokens,
            outputTokens = totalOutputTokens,
            totalTokens = totalTokens,
            responseTimeMs = responseTime
        )
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
            messages = listOf(ClaudeMessageRequest(
                role = "user",
                content = listOf(ClaudeContentRequest(type = "text", text = summaryPrompt))
            )),
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

    /**
     * Создать название диалога на основе первого сообщения пользователя
     */
    suspend fun generateConversationTitle(firstMessage: String): String {
        logger.debug("Generating conversation title for first message")

        val titlePrompt = """
            На основе следующего сообщения пользователя создай краткое название для диалога (максимум 5-7 слов).
            Название должно отражать суть вопроса или темы.
            Верни ТОЛЬКО название, без кавычек и дополнительных пояснений.

            Сообщение пользователя:
            $firstMessage
        """.trimIndent()

        val titleRequest = ClaudeApiRequest(
            model = model,
            max_tokens = 50, // Короткое название
            messages = listOf(ClaudeMessageRequest(
                role = "user",
                content = listOf(ClaudeContentRequest(type = "text", text = titlePrompt))
            )),
            system = "Ты помощник, который создает краткие названия для диалогов. Отвечай только названием, без дополнительного текста."
        )

        return try {
            val response: HttpResponse = httpClient.post(apiUrl) {
                header("x-api-key", apiKey)
                header("anthropic-version", "2023-06-01")
                contentType(ContentType.Application.Json)
                setBody(titleRequest)
            }

            when (response.status) {
                HttpStatusCode.OK -> {
                    val apiResponse: ClaudeApiResponse = response.body()
                    val title = apiResponse.content.firstOrNull()?.text
                        ?.trim()
                        ?.removeSurrounding("\"")
                        ?: "Новый диалог"

                    logger.debug("Successfully generated title: $title")
                    title
                }
                else -> {
                    logger.warn("Failed to generate title: ${response.status}, using default")
                    "Новый диалог"
                }
            }
        } catch (e: Exception) {
            logger.warn("Error generating title, using default: ${e.message}")
            "Новый диалог"
        }
    }
}
