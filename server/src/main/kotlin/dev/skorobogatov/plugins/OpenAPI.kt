package dev.skorobogatov.plugins

import io.ktor.server.application.*
import io.ktor.server.plugins.openapi.*
import io.ktor.server.plugins.swagger.*
import io.ktor.server.routing.*

/**
 * Конфигурация OpenAPI и Swagger UI
 */
fun Application.configureOpenAPI() {
    routing {
        // Swagger UI доступен по адресу /swagger
        swaggerUI(path = "swagger", swaggerFile = "openapi/documentation.yaml") {
            version = "4.15.5"
        }

        // OpenAPI спецификация в формате HTML доступна по адресу /openapi
        openAPI(path = "openapi", swaggerFile = "openapi/documentation.yaml")
    }
}
