package dev.skorobogatov.routes

import dev.skorobogatov.models.*
import dev.skorobogatov.services.SchedulerService
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import org.slf4j.LoggerFactory

fun Route.taskRoutes(schedulerService: SchedulerService) {
    val logger = LoggerFactory.getLogger("TaskRoutes")

    route("/api/tasks") {
        // Получить все задачи
        get {
            try {
                val tasks = schedulerService.getAllTasks()
                val response = TaskListResponse(
                    tasks = tasks,
                    totalCount = tasks.size
                )
                call.respond(HttpStatusCode.OK, response)
            } catch (e: Exception) {
                logger.error("Error getting tasks", e)
                call.respond(
                    HttpStatusCode.InternalServerError,
                    mapOf("error" to (e.message ?: "Unknown error"))
                )
            }
        }

        // Создать новую задачу
        post {
            try {
                val request = call.receive<CreateTaskRequest>()

                // Валидация
                if (request.name.isBlank()) {
                    call.respond(HttpStatusCode.BadRequest, mapOf("error" to "Task name is required"))
                    return@post
                }
                if (request.question.isBlank()) {
                    call.respond(HttpStatusCode.BadRequest, mapOf("error" to "Question is required"))
                    return@post
                }

                val task = schedulerService.createTask(request)
                call.respond(HttpStatusCode.Created, task)
                logger.info("Created task: ${task.id}")
            } catch (e: IllegalArgumentException) {
                call.respond(HttpStatusCode.BadRequest, mapOf("error" to (e.message ?: "Invalid request")))
            } catch (e: Exception) {
                logger.error("Error creating task", e)
                call.respond(
                    HttpStatusCode.InternalServerError,
                    mapOf("error" to (e.message ?: "Unknown error"))
                )
            }
        }

        // Получить конкретную задачу
        get("/{id}") {
            try {
                val id = call.parameters["id"] ?: run {
                    call.respond(HttpStatusCode.BadRequest, mapOf("error" to "Task ID is required"))
                    return@get
                }

                val task = schedulerService.getTask(id)
                if (task == null) {
                    call.respond(HttpStatusCode.NotFound, mapOf("error" to "Task not found"))
                    return@get
                }

                call.respond(HttpStatusCode.OK, task)
            } catch (e: Exception) {
                logger.error("Error getting task", e)
                call.respond(
                    HttpStatusCode.InternalServerError,
                    mapOf("error" to (e.message ?: "Unknown error"))
                )
            }
        }

        // Обновить задачу
        put("/{id}") {
            try {
                val id = call.parameters["id"] ?: run {
                    call.respond(HttpStatusCode.BadRequest, mapOf("error" to "Task ID is required"))
                    return@put
                }

                val request = call.receive<UpdateTaskRequest>()
                val updatedTask = schedulerService.updateTask(id, request)

                if (updatedTask == null) {
                    call.respond(HttpStatusCode.NotFound, mapOf("error" to "Task not found"))
                    return@put
                }

                call.respond(HttpStatusCode.OK, updatedTask)
                logger.info("Updated task: $id")
            } catch (e: Exception) {
                logger.error("Error updating task", e)
                call.respond(
                    HttpStatusCode.InternalServerError,
                    mapOf("error" to (e.message ?: "Unknown error"))
                )
            }
        }

        // Удалить задачу
        delete("/{id}") {
            try {
                val id = call.parameters["id"] ?: run {
                    call.respond(HttpStatusCode.BadRequest, mapOf("error" to "Task ID is required"))
                    return@delete
                }

                val deleted = schedulerService.deleteTask(id)
                if (!deleted) {
                    call.respond(HttpStatusCode.NotFound, mapOf("error" to "Task not found"))
                    return@delete
                }

                call.respond(HttpStatusCode.OK, mapOf("message" to "Task deleted successfully"))
                logger.info("Deleted task: $id")
            } catch (e: Exception) {
                logger.error("Error deleting task", e)
                call.respond(
                    HttpStatusCode.InternalServerError,
                    mapOf("error" to (e.message ?: "Unknown error"))
                )
            }
        }

        // Получить выполнения задачи
        get("/{id}/executions") {
            try {
                val id = call.parameters["id"] ?: run {
                    call.respond(HttpStatusCode.BadRequest, mapOf("error" to "Task ID is required"))
                    return@get
                }

                val limit = call.request.queryParameters["limit"]?.toIntOrNull() ?: 100

                val executions = schedulerService.getExecutions(id, limit)
                val response = TaskExecutionListResponse(
                    executions = executions,
                    totalCount = executions.size,
                    taskId = id
                )

                call.respond(HttpStatusCode.OK, response)
            } catch (e: Exception) {
                logger.error("Error getting task executions", e)
                call.respond(
                    HttpStatusCode.InternalServerError,
                    mapOf("error" to (e.message ?: "Unknown error"))
                )
            }
        }
    }
}
