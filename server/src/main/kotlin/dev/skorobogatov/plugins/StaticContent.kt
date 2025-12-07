package dev.skorobogatov.plugins

import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.http.content.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import java.io.File

fun Application.configureStaticContent() {
    // Создаем директорию для приложений если её нет
    // Используем абсолютный путь на основе текущей рабочей директории
    val projectRoot = File(System.getProperty("user.dir"))
    val appsDir = File(projectRoot, "src/main/resources/static/apps")
    if (!appsDir.exists()) {
        appsDir.mkdirs()
        log.info("Created apps directory: ${appsDir.absolutePath}")
    }
    log.info("Serving apps from: ${appsDir.absolutePath}")

    routing {
        // Serve static files from resources/static directory
        staticResources("/static", "static")

        // Serve apps from filesystem (created web applications by /dev command)
        staticFiles("/apps", appsDir) {
            default("index.html")
        }

        // Redirect root to web interface
        get("/") {
            call.respondRedirect("/web")
        }

        // Serve web interface
        get("/web") {
            call.respondText(
                this::class.java.classLoader.getResource("static/index.html")!!.readText(),
                ContentType.Text.Html
            )
        }
    }
}
