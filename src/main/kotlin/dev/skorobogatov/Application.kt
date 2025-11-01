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
    }

    // Создание сервиса для работы с Claude
    val claudeService = ClaudeService(
        httpClient = httpClient,
        apiKey = apiKey,
        apiUrl = apiUrl,
        model = model,
        maxTokens = maxTokens
    )

    // Конфигурация плагинов
    configureSerialization()
    configureHTTP()
    configureStatusPages()
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
