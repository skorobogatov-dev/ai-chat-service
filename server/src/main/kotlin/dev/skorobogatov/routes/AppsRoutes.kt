package dev.skorobogatov.routes

import dev.skorobogatov.services.AppsService
import dev.skorobogatov.services.AppInfo
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import kotlinx.serialization.Serializable
import org.slf4j.LoggerFactory

@Serializable
data class AppsListResponse(
    val apps: List<AppInfo>,
    val totalCount: Int
)

@Serializable
data class AppFilesResponse(
    val name: String,
    val files: List<dev.skorobogatov.services.AppFile>
)

@Serializable
data class DeleteAppResponse(
    val deleted: Boolean,
    val name: String
)

fun Route.appsRoutes(appsService: AppsService) {
    val logger = LoggerFactory.getLogger("AppsRoutes")

    route("/api/apps") {
        // Получить список всех приложений
        get {
            try {
                val apps = appsService.listApps()
                call.respond(
                    HttpStatusCode.OK,
                    AppsListResponse(
                        apps = apps,
                        totalCount = apps.size
                    )
                )
            } catch (e: Exception) {
                logger.error("Error listing apps", e)
                call.respond(
                    HttpStatusCode.InternalServerError,
                    mapOf("error" to (e.message ?: "Unknown error"))
                )
            }
        }

        // Получить информацию о конкретном приложении
        get("/{name}") {
            try {
                val name = call.parameters["name"] ?: run {
                    call.respond(HttpStatusCode.BadRequest, mapOf("error" to "App name is required"))
                    return@get
                }

                val appInfo = appsService.getAppInfo(name)
                if (appInfo == null) {
                    call.respond(HttpStatusCode.NotFound, mapOf("error" to "App not found"))
                    return@get
                }

                call.respond(HttpStatusCode.OK, appInfo)
            } catch (e: Exception) {
                logger.error("Error getting app info", e)
                call.respond(
                    HttpStatusCode.InternalServerError,
                    mapOf("error" to (e.message ?: "Unknown error"))
                )
            }
        }

        // Получить файлы приложения
        get("/{name}/files") {
            try {
                val name = call.parameters["name"] ?: run {
                    call.respond(HttpStatusCode.BadRequest, mapOf("error" to "App name is required"))
                    return@get
                }

                if (!appsService.appExists(name)) {
                    call.respond(HttpStatusCode.NotFound, mapOf("error" to "App not found"))
                    return@get
                }

                val files = appsService.getAppFiles(name)
                call.respond(
                    HttpStatusCode.OK,
                    AppFilesResponse(
                        name = name,
                        files = files
                    )
                )
            } catch (e: Exception) {
                logger.error("Error getting app files", e)
                call.respond(
                    HttpStatusCode.InternalServerError,
                    mapOf("error" to (e.message ?: "Unknown error"))
                )
            }
        }

        // Удалить приложение
        delete("/{name}") {
            try {
                val name = call.parameters["name"] ?: run {
                    call.respond(HttpStatusCode.BadRequest, mapOf("error" to "App name is required"))
                    return@delete
                }

                if (!appsService.appExists(name)) {
                    call.respond(HttpStatusCode.NotFound, mapOf("error" to "App not found"))
                    return@delete
                }

                val deleted = appsService.deleteApp(name)
                if (deleted) {
                    call.respond(
                        HttpStatusCode.OK,
                        DeleteAppResponse(deleted = true, name = name)
                    )
                    logger.info("Deleted app: $name")
                } else {
                    call.respond(
                        HttpStatusCode.InternalServerError,
                        mapOf("error" to "Failed to delete app")
                    )
                }
            } catch (e: Exception) {
                logger.error("Error deleting app", e)
                call.respond(
                    HttpStatusCode.InternalServerError,
                    mapOf("error" to (e.message ?: "Unknown error"))
                )
            }
        }
    }
}
