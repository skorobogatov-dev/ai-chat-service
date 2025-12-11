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

        // Системный промпт для стандартного режима
        const val STANDARD_SYSTEM_PROMPT = """Ты полезный AI-ассистент. Следуй этим принципам:

1. ЯСНОСТЬ: Отвечай понятно и структурированно. Используй списки и заголовки где уместно.
2. ПОЛНОТА: Давай исчерпывающие ответы, но не перегружай лишними деталями.
3. ЧЕСТНОСТЬ: Если не знаешь ответ - скажи об этом. Не выдумывай факты.
4. ДРУЖЕЛЮБИЕ: Будь вежливым и готовым помочь.
5. ЯЗЫК: Отвечай на языке вопроса пользователя.

Твоя цель - быть максимально полезным собеседником."""

        // Системный промпт для режима программирования
        const val CODING_SYSTEM_PROMPT = """Ты senior software engineer с 15+ годами опыта. Твой подход - всегда технический и практический.

## СТИЛЬ ОТВЕТОВ
- Отвечай как техлид на code review: конкретно, по делу, с примерами кода
- Любой вопрос рассматривай с технической точки зрения
- Называй конкретные технологии, библиотеки, фреймворки, паттерны
- Указывай версии, если это важно для совместимости
- Упоминай trade-offs и альтернативные решения

## СТРУКТУРА ОТВЕТА
1. Краткое решение (1-2 предложения)
2. Код с комментариями
3. Технические детали: сложность O(), память, производительность
4. Потенциальные проблемы и edge cases
5. Альтернативы (если есть лучшие подходы)

## ПРАВИЛА КОДА
- Только рабочий, production-ready код
- Используй современные best practices и идиомы языка
- Добавляй обработку ошибок и валидацию
- Указывай необходимые import/dependencies
- Код-блоки с указанием языка: ```kotlin, ```python, etc.

## ТЕХНИЧЕСКИЙ ФОКУС
- Big O notation для алгоритмов
- Паттерны проектирования где уместно (Singleton, Factory, Observer...)
- Архитектурные принципы (SOLID, DRY, KISS)
- Безопасность (SQL injection, XSS, CSRF...)
- Тестируемость и maintainability

## ЧЕСТНОСТЬ
Если не уверен в точности информации - скажи прямо. Лучше "нужно проверить в документации X" чем галлюцинация.

Язык: объяснения на русском, код и термины на английском."""

        /**
         * Получить системный промпт по названию пресета
         */
        fun getSystemPromptForPreset(preset: String?): String? {
            return when (preset?.lowercase()) {
                "coding" -> CODING_SYSTEM_PROMPT
                "standard" -> STANDARD_SYSTEM_PROMPT
                else -> null
            }
        }

        /**
         * Получить опции генерации по названию пресета
         */
        fun getOptionsForPreset(preset: String?): OllamaChatOptions {
            return when (preset?.lowercase()) {
                "coding" -> CODING_OPTIONS
                else -> DEFAULT_OPTIONS
            }
        }
    }

    /**
     * Отправить сообщение в Ollama и получить ответ
     *
     * @param messages История сообщений
     * @param systemPrompt Пользовательский системный промпт (переопределяет preset)
     * @param requestModel Модель для использования
     * @param options Пользовательские опции генерации (переопределяют preset)
     * @param preset Название пресета: "standard", "coding"
     * @param codingMode Deprecated: используйте preset="coding"
     */
    suspend fun sendMessage(
        messages: List<ClaudeMessage>,
        systemPrompt: String? = null,
        requestModel: String? = null,
        options: OllamaChatOptions? = null,
        preset: String? = null,
        codingMode: Boolean = false
    ): ChatResponse {
        val startTime = System.currentTimeMillis()
        val effectiveModel = requestModel ?: defaultModel

        // Определяем эффективный пресет (codingMode для обратной совместимости)
        val effectivePreset = when {
            preset != null -> preset
            codingMode -> "coding"
            else -> "standard"
        }

        // Определяем системный промпт (приоритет: пользовательский > пресет > дефолтный)
        val effectiveSystemPrompt = when {
            systemPrompt != null -> systemPrompt
            else -> getSystemPromptForPreset(effectivePreset) ?: defaultSystemPrompt
        }

        // Определяем опции генерации (приоритет: пользовательские > пресет)
        val effectiveOptions = (options ?: getOptionsForPreset(effectivePreset)).let { baseOptions ->
            // Применяем maxTokens
            baseOptions.copy(num_predict = baseOptions.num_predict ?: maxTokens)
        }

        logger.debug("Sending ${messages.size} messages to Ollama (model: $effectiveModel, preset: $effectivePreset)")
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
