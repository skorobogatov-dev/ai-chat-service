package dev.skorobogatov.routes

import dev.skorobogatov.models.*
import dev.skorobogatov.services.MCPService
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import kotlinx.serialization.json.*
import kotlinx.serialization.encodeToString
import org.slf4j.LoggerFactory

private val logger = LoggerFactory.getLogger("TaskManagementRoutes")
private val json = Json { ignoreUnknownKeys = true }

/**
 * Convert JsonObject to Map<String, String> for MCP tool calls
 */
private fun JsonObject.toStringMap(): Map<String, String> {
    return this.entries.associate { (key, value) ->
        key to value.toString().removeSurrounding("\"")
    }
}

/**
 * Task Management REST API routes
 * These routes provide direct HTTP access to task management functionality
 * by wrapping MCP Task Server calls
 */
fun Route.taskManagementRoutes(mcpService: MCPService) {
    route("/api/tasks") {

        // GET /api/tasks/search - Search tasks by query (must be before /{taskId})
        get("/search") {
            try {
                val query = call.request.queryParameters["query"] ?: return@get call.respond(
                    HttpStatusCode.BadRequest,
                    mapOf("error" to "Query parameter is required")
                )

                val status = call.request.queryParameters["status"]
                val priority = call.request.queryParameters["priority"]

                val arguments = mutableMapOf<String, String>("query" to query)
                status?.let { arguments["status"] = it }
                priority?.let { arguments["priority"] = it }

                val mcpResponse = mcpService.callTool("search_tasks", arguments)
                val response = json.decodeFromString<ProjectTaskSearchResponse>(mcpResponse.result)

                call.respond(HttpStatusCode.OK, response)
            } catch (e: Exception) {
                logger.error("Error searching tasks: ${e.message}", e)
                call.respond(HttpStatusCode.InternalServerError, mapOf("error" to (e.message ?: "Unknown error")))
            }
        }

        // GET /api/tasks/stats - Get project statistics (must be before /{taskId})
        get("/stats") {
            try {
                val project = call.request.queryParameters["project"]

                val arguments = mutableMapOf<String, String>()
                project?.let { arguments["project"] = it }

                val mcpResponse = mcpService.callTool("get_stats", arguments)
                val stats = json.decodeFromString<ProjectStats>(mcpResponse.result)

                call.respond(HttpStatusCode.OK, stats)
            } catch (e: Exception) {
                logger.error("Error getting stats: ${e.message}", e)
                call.respond(HttpStatusCode.InternalServerError, mapOf("error" to (e.message ?: "Unknown error")))
            }
        }

        // GET /api/tasks/recommendations - Get recommended tasks (must be before /{taskId})
        get("/recommendations") {
            try {
                val assignee = call.request.queryParameters["assignee"]

                val arguments = mutableMapOf<String, String>()
                assignee?.let { arguments["assignee"] = it }

                val mcpResponse = mcpService.callTool("get_recommendations", arguments)
                val response = json.decodeFromString<ProjectTaskRecommendationsResponse>(mcpResponse.result)

                call.respond(HttpStatusCode.OK, response)
            } catch (e: Exception) {
                logger.error("Error getting recommendations: ${e.message}", e)
                call.respond(HttpStatusCode.InternalServerError, mapOf("error" to (e.message ?: "Unknown error")))
            }
        }

        // GET /api/tasks - List tasks with optional filters
        get {
            try {
                val status = call.request.queryParameters["status"]
                val priority = call.request.queryParameters["priority"]
                val assignee = call.request.queryParameters["assignee"]
                val project = call.request.queryParameters["project"]
                val limit = call.request.queryParameters["limit"]

                val arguments = mutableMapOf<String, String>()
                status?.let { arguments["status"] = it }
                priority?.let { arguments["priority"] = it }
                assignee?.let { arguments["assignee"] = it }
                project?.let { arguments["project"] = it }
                limit?.let { arguments["limit"] = it }

                val mcpResponse = mcpService.callTool("list_tasks", arguments)
                val response = json.decodeFromString<ProjectTaskListResponse>(mcpResponse.result)

                call.respond(HttpStatusCode.OK, response)
            } catch (e: Exception) {
                logger.error("Error listing tasks: ${e.message}", e)
                call.respond(HttpStatusCode.InternalServerError, mapOf("error" to (e.message ?: "Unknown error")))
            }
        }

        // POST /api/tasks - Create a new task
        post {
            try {
                val request = call.receive<CreateProjectTaskRequest>()

                val arguments = mutableMapOf<String, String>(
                    "title" to request.title,
                    "description" to request.description,
                    "priority" to request.priority.name
                )

                request.assignee?.let { arguments["assignee"] = it }
                request.dueDate?.let { arguments["dueDate"] = it }
                if (request.tags.isNotEmpty()) {
                    arguments["tags"] = json.encodeToString(request.tags)
                }
                request.project?.let { arguments["project"] = it }
                request.estimatedHours?.let { arguments["estimatedHours"] = it.toString() }

                val mcpResponse = mcpService.callTool("create_task", arguments)
                val task = json.decodeFromString<ProjectTask>(mcpResponse.result)

                call.respond(HttpStatusCode.Created, task)
            } catch (e: Exception) {
                logger.error("Error creating task: ${e.message}", e)
                call.respond(HttpStatusCode.InternalServerError, mapOf("error" to (e.message ?: "Unknown error")))
            }
        }

        // GET /api/tasks/{taskId} - Get a specific task
        get("/{taskId}") {
            try {
                val taskId = call.parameters["taskId"] ?: return@get call.respond(
                    HttpStatusCode.BadRequest,
                    mapOf("error" to "Task ID is required")
                )

                val arguments = mapOf("taskId" to taskId)

                val mcpResponse = mcpService.callTool("get_task", arguments)
                val task = json.decodeFromString<ProjectTask>(mcpResponse.result)

                call.respond(HttpStatusCode.OK, task)
            } catch (e: Exception) {
                logger.error("Error getting task: ${e.message}", e)
                if (e.message?.contains("not found") == true) {
                    call.respond(HttpStatusCode.NotFound, mapOf("error" to e.message!!))
                } else {
                    call.respond(HttpStatusCode.InternalServerError, mapOf("error" to (e.message ?: "Unknown error")))
                }
            }
        }

        // PUT /api/tasks/{taskId} - Update a task
        put("/{taskId}") {
            try {
                val taskId = call.parameters["taskId"] ?: return@put call.respond(
                    HttpStatusCode.BadRequest,
                    mapOf("error" to "Task ID is required")
                )

                val request = call.receive<UpdateProjectTaskRequest>()

                val arguments = mutableMapOf<String, String>("taskId" to taskId)
                request.status?.let { arguments["status"] = it.name }
                request.priority?.let { arguments["priority"] = it.name }
                request.assignee?.let { arguments["assignee"] = it }
                request.dueDate?.let { arguments["dueDate"] = it }
                request.tags?.let { tags -> arguments["tags"] = json.encodeToString(tags) }
                request.actualHours?.let { arguments["actualHours"] = it.toString() }

                val mcpResponse = mcpService.callTool("update_task", arguments)
                val task = json.decodeFromString<ProjectTask>(mcpResponse.result)

                call.respond(HttpStatusCode.OK, task)
            } catch (e: Exception) {
                logger.error("Error updating task: ${e.message}", e)
                if (e.message?.contains("not found") == true) {
                    call.respond(HttpStatusCode.NotFound, mapOf("error" to e.message!!))
                } else {
                    call.respond(HttpStatusCode.InternalServerError, mapOf("error" to (e.message ?: "Unknown error")))
                }
            }
        }
    }
}
