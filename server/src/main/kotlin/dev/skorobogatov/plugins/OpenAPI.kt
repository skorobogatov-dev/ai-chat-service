package dev.skorobogatov.plugins

import io.ktor.server.application.Application
import io.ktor.server.plugins.swagger.swaggerUI
import io.ktor.server.routing.routing

/**
 * Конфигурация OpenAPI и Swagger UI
 */
fun Application.configureOpenAPI() {
    routing {
        // Swagger UI доступен по адресу /swagger
        swaggerUI(path = "swagger", swaggerFile = "openapi/documentation.yaml") {
            version = "4.15.5"
        }

        // OpenAPI спецификация отключена - вызывает ошибки генерации файлов в production
        // Используйте /swagger для просмотра API документации
    }
}
