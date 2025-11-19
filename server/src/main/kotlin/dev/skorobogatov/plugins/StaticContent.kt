package dev.skorobogatov.plugins

import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.http.content.*
import io.ktor.server.response.*
import io.ktor.server.routing.*

fun Application.configureStaticContent() {
    routing {
        // Serve static files from resources/static directory
        staticResources("/static", "static")

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
