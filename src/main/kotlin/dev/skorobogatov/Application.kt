package dev.skorobogatov

import dev.skorobogatov.plugins.*
import dev.skorobogatov.services.ClaudeService
import io.ktor.client.*
import io.ktor.client.engine.cio.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.client.plugins.logging.*
import io.ktor.serialization.kotlinx.json.*
import io.ktor.server.application.*
import io.ktor.server.netty.*
import kotlinx.serialization.json.Json

fun main(args: Array<String>) {
    EngineMain.main(args)
}

fun Application.module() {
    // Чтение конфигурации
    val apiKey = environment.config.property("claude.apiKey").getString()
    val apiUrl = environment.config.property("claude.apiUrl").getString()
    val model = environment.config.property("claude.model").getString()
    val maxTokens = environment.config.property("claude.maxTokens").getString().toInt()

    // Системный промпт с дефолтным значением
    val defaultSystemPrompt = """
        You are a multi-expert consultation system. When a user asks a question, you must provide responses from multiple experts, each with their unique perspective and expertise.

        CRITICAL: Respond with PURE JSON ONLY - NO markdown formatting, NO code blocks, NO backticks (```), NO extra text.

        You must respond with valid JSON in this exact format:
        {
          "question": "user's question",
          "experts": [
            {
              "expertRole": "Business Manager",
              "expertName": "Алекс Петров",
              "response": "detailed response from business perspective"
            },
            {
              "expertRole": "Business Analyst",
              "expertName": "Мария Иванова",
              "response": "detailed response from analytical perspective"
            },
            {
              "expertRole": "Software Developer",
              "expertName": "Дмитрий Козлов",
              "response": "detailed response from technical development perspective"
            },
            {
              "expertRole": "Диванный эксперт",
              "expertName": "Виктор Сидоров",
              "response": "detailed response from armchair expert perspective"
            }
          ]
        }

        Rules:
        - Return ONLY the JSON object starting with { and ending with }
        - NO markdown code blocks (```json or ```)
        - NO explanatory text before or after the JSON
        - question: repeat the user's question exactly
        - experts: array of 4 expert responses
        - Each expert must provide a detailed response from their unique perspective
        - expertRole: the expert's role (Business Manager, Business Analyst, Software Developer, Диванный эксперт)
        - expertName: a Russian name for the expert
        - response: comprehensive answer from that expert's perspective (minimum 3-4 sentences)
        - Business Manager focuses on: ROI, business strategy, market opportunities, stakeholder management
        - Business Analyst focuses on: requirements, user stories, process optimization, data analysis
        - Software Developer focuses on: technical architecture, coding practices, frameworks, implementation details
        - Диванный эксперт focuses on: subjective opinions, user experience criticism, skeptical questions, potential problems from layman's perspective, common sense concerns
        - All responses must be in Russian
    """.trimIndent()
    val systemPrompt = environment.config.propertyOrNull("claude.systemPrompt")?.getString() ?: defaultSystemPrompt

    // Создание HTTP клиента для запросов к Anthropic API
    val httpClient = HttpClient(CIO) {
        install(ContentNegotiation) {
            json(Json {
                prettyPrint = true
                isLenient = true
                ignoreUnknownKeys = true
            })
        }
        install(Logging) {
            logger = Logger.DEFAULT
            level = LogLevel.INFO
        }
        engine {
            requestTimeout = 120_000 // 120 секунд для сложных запросов с множественными экспертами
        }
    }

    // Создание сервиса для работы с Claude
    val claudeService = ClaudeService(
        httpClient = httpClient,
        apiKey = apiKey,
        apiUrl = apiUrl,
        model = model,
        maxTokens = maxTokens,
        defaultSystemPrompt = systemPrompt
    )

    // Конфигурация плагинов
    configureSerialization()
    configureHTTP()
    configureStatusPages()
    configureStaticContent()
    configureRouting(claudeService)

    // Логирование при старте
    environment.monitor.subscribe(ApplicationStarted) {
        environment.log.info("Application started successfully")
        environment.log.info("Server running on: http://0.0.0.0:${environment.config.property("ktor.deployment.port").getString()}")
        environment.log.info("Using Claude model: $model")
    }

    // Закрытие HTTP клиента при остановке приложения
    environment.monitor.subscribe(ApplicationStopped) {
        httpClient.close()
        environment.log.info("Application stopped")
    }
}
