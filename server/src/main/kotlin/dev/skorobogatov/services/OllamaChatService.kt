package dev.skorobogatov.services

import dev.skorobogatov.models.*
import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import org.slf4j.LoggerFactory

/**
 * Сервис для работы с локальной Ollama LLM для генерации текста (chat)
 */
class OllamaChatService(
    private val httpClient: HttpClient,
    private val baseUrl: String,
    private val defaultModel: String,
    private val maxTokens: Int = 4096,
    private val defaultSystemPrompt: String? = null
) {
    private val logger = LoggerFactory.getLogger(OllamaChatService::class.java)
    private val chatUrl = "$baseUrl/api/chat"
    private val tagsUrl = "$baseUrl/api/tags"

    // Дефолтные параметры для обычного режима
    companion object {
        // Стандартный режим - сбалансированные настройки
        val DEFAULT_OPTIONS = OllamaChatOptions(
            temperature = 0.7,
            num_ctx = 4096,
            top_p = 0.9,
            top_k = 40,
            repeat_penalty = 1.1
        )

        // Режим программирования - точные, детерминированные ответы
        val CODING_OPTIONS = OllamaChatOptions(
            temperature = 0.1,
            num_ctx = 8192,
            top_p = 0.95,
            top_k = 20,
            repeat_penalty = 1.15
        )

        // Системный промпт для режима программирования
        const val CODING_SYSTEM_PROMPT = """Ты опытный программист-помощник. Следуй этим правилам:

1. ТОЧНОСТЬ: Используй только реальные, существующие библиотеки и функции. Никогда не придумывай API.
2. КОД: Давай рабочий, проверенный код. Если не уверен - скажи об этом.
3. КРАТКОСТЬ: Отвечай по существу, без лишних вступлений.
4. ОШИБКИ: Если видишь ошибку в коде пользователя - укажи на неё.
5. ЯЗЫК: Отвечай на русском, код и термины на английском.

Если не знаешь точного ответа - честно скажи "Я не уверен" вместо галлюцинаций."""
    }

    /**
     * Отправить сообщение в Ollama и получить ответ
     */
    suspend fun sendMessage(
        messages: List<ClaudeMessage>,
        systemPrompt: String? = null,
        requestModel: String? = null,
        options: OllamaChatOptions? = null,
        codingMode: Boolean = false
    ): ChatResponse {
        val startTime = System.currentTimeMillis()
        val effectiveModel = requestModel ?: defaultModel

        // Определяем системный промпт
        val effectiveSystemPrompt = when {
            systemPrompt != null -> systemPrompt
            codingMode -> CODING_SYSTEM_PROMPT
            else -> defaultSystemPrompt
        }

        // Определяем опции генерации
        val effectiveOptions = when {
            options != null -> options
            codingMode -> CODING_OPTIONS
            else -> DEFAULT_OPTIONS
        }.let { baseOptions ->
            // Применяем maxTokens
            baseOptions.copy(num_predict = baseOptions.num_predict ?: maxTokens)
        }

        logger.debug("Sending ${messages.size} messages to Ollama (model: $effectiveModel, codingMode: $codingMode)")
        logger.debug("Options: temp=${effectiveOptions.temperature}, ctx=${effectiveOptions.num_ctx}, top_p=${effectiveOptions.top_p}")

        // Конвертируем ClaudeMessage в OllamaChatMessage
        val ollamaMessages = mutableListOf<OllamaChatMessage>()

        // Добавляем системный промпт как первое сообщение с role="system"
        if (!effectiveSystemPrompt.isNullOrBlank()) {
            ollamaMessages.add(OllamaChatMessage(role = "system", content = effectiveSystemPrompt))
        }

        // Конвертируем остальные сообщения
        messages.forEach { msg ->
            ollamaMessages.add(OllamaChatMessage(
                role = msg.role,
                content = msg.content
            ))
        }

        val request = OllamaChatRequest(
            model = effectiveModel,
            messages = ollamaMessages,
            stream = false,
            options = effectiveOptions
        )

        return try {
            val response: HttpResponse = httpClient.post(chatUrl) {
                contentType(ContentType.Application.Json)
                setBody(request)
            }

            val endTime = System.currentTimeMillis()
            val responseTime = endTime - startTime

            when (response.status) {
                HttpStatusCode.OK -> {
                    // Ollama возвращает ndjson (несколько JSON объектов разделенных \n)
                    // При streaming каждая строка содержит часть контента
                    val responseText = response.bodyAsText()
                    val json = kotlinx.serialization.json.Json { ignoreUnknownKeys = true }

                    // Разбиваем на строки и собираем весь контент
                    val lines = responseText.trim().split("\n").filter { it.isNotBlank() }
                    if (lines.isEmpty()) throw Exception("Empty response from Ollama")

                    // Собираем контент из всех сообщений
                    val contentBuilder = StringBuilder()
                    var lastResponse: OllamaChatResponse? = null

                    for (line in lines) {
                        val parsed = json.decodeFromString<OllamaChatResponse>(line)
                        contentBuilder.append(parsed.message.content)
                        if (parsed.done) {
                            lastResponse = parsed
                        }
                    }

                    val ollamaResponse = lastResponse ?: json.decodeFromString<OllamaChatResponse>(lines.last())
                    val messageText = contentBuilder.toString()

                    val inputTokens = ollamaResponse.prompt_eval_count ?: 0
                    val outputTokens = ollamaResponse.eval_count ?: 0
                    val totalTokens = inputTokens + outputTokens

                    logger.debug("Received response from Ollama")
                    logger.info("Ollama usage: input=$inputTokens, output=$outputTokens, total=$totalTokens tokens, time=${responseTime}ms")

                    ChatResponse(
                        response = messageText,
                        model = ollamaResponse.model,
                        inputTokens = inputTokens,
                        outputTokens = outputTokens,
                        totalTokens = totalTokens,
                        responseTimeMs = responseTime
                    )
                }
                else -> {
                    val errorBody = response.bodyAsText()
                    logger.error("Ollama API error: ${response.status} - $errorBody")
                    throw Exception("Ollama API error: ${response.status} - $errorBody")
                }
            }
        } catch (e: Exception) {
            logger.error("Error calling Ollama API", e)
            throw Exception("Failed to get response from Ollama: ${e.message}", e)
        }
    }

    /**
     * Получить список доступных моделей
     */
    suspend fun listModels(): List<OllamaModelInfo> {
        logger.debug("Fetching list of Ollama models from $tagsUrl")

        return try {
            val response: HttpResponse = httpClient.get(tagsUrl)

            when (response.status) {
                HttpStatusCode.OK -> {
                    val modelsResponse: OllamaModelsResponse = response.body()
                    logger.debug("Found ${modelsResponse.models.size} Ollama models")
                    modelsResponse.models
                }
                else -> {
                    val errorBody = response.bodyAsText()
                    logger.error("Failed to fetch Ollama models: ${response.status} - $errorBody")
                    emptyList()
                }
            }
        } catch (e: Exception) {
            logger.error("Error fetching Ollama models", e)
            emptyList()
        }
    }

    /**
     * Проверить, доступна ли указанная модель
     */
    suspend fun isModelAvailable(model: String): Boolean {
        val models = listModels()
        return models.any { it.name == model || it.name.startsWith("$model:") }
    }

    /**
     * Проверить статус Ollama сервера
     */
    suspend fun checkStatus(): OllamaStatus {
        logger.debug("Checking Ollama server status at $baseUrl")

        return try {
            val models = listModels()
            if (models.isNotEmpty()) {
                logger.debug("Ollama server is available with ${models.size} models")
                OllamaStatus(
                    available = true,
                    url = baseUrl,
                    model = defaultModel
                )
            } else {
                OllamaStatus(
                    available = false,
                    url = baseUrl,
                    model = defaultModel,
                    error = "No models available"
                )
            }
        } catch (e: Exception) {
            logger.error("Failed to connect to Ollama server", e)
            OllamaStatus(
                available = false,
                url = baseUrl,
                model = defaultModel,
                error = "Connection failed: ${e.message}"
            )
        }
    }

    /**
     * Создать summary для сжатия истории диалога (используя локальную модель)
     */
    suspend fun createSummary(messages: List<ClaudeMessage>): String {
        logger.debug("Creating summary for ${messages.size} messages using Ollama")

        val summaryPrompt = """
            Создай краткое резюме следующего диалога на русском языке.
            Сосредоточься на ключевых моментах, заданных вопросах и важной обсуждаемой информации.
            Резюме должно быть кратким, но передавать суть диалога.

            Диалог для резюмирования:
            ${messages.joinToString("\n") { "${it.role}: ${it.content}" }}
        """.trimIndent()

        val summaryMessages = listOf(ClaudeMessage(role = "user", content = summaryPrompt))

        return try {
            val response = sendMessage(
                messages = summaryMessages,
                systemPrompt = "Ты помощник, который создает краткие резюме диалогов на русском языке."
            )
            logger.debug("Successfully created summary (${response.response.length} chars)")
            response.response
        } catch (e: Exception) {
            logger.error("Error creating summary with Ollama", e)
            throw Exception("Failed to create summary: ${e.message}", e)
        }
    }

    /**
     * Создать название диалога на основе первого сообщения
     */
    suspend fun generateConversationTitle(firstMessage: String): String {
        logger.debug("Generating conversation title using Ollama")

        val titlePrompt = """
            На основе следующего сообщения пользователя создай краткое название для диалога (максимум 5-7 слов).
            Название должно отражать суть вопроса или темы.
            Верни ТОЛЬКО название, без кавычек и дополнительных пояснений.

            Сообщение пользователя:
            $firstMessage
        """.trimIndent()

        val titleMessages = listOf(ClaudeMessage(role = "user", content = titlePrompt))

        return try {
            val response = sendMessage(
                messages = titleMessages,
                systemPrompt = "Ты помощник, который создает краткие названия для диалогов. Отвечай только названием, без дополнительного текста."
            )
            val title = response.response.trim().removeSurrounding("\"")
            logger.debug("Successfully generated title: $title")
            title
        } catch (e: Exception) {
            logger.warn("Error generating title with Ollama, using default: ${e.message}")
            "Новый диалог"
        }
    }
}
