package dev.skorobogatov.routes

import dev.skorobogatov.models.AnalyticsQueryRequest
import dev.skorobogatov.services.AnalyticsService
import io.ktor.http.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import kotlinx.serialization.Serializable

@Serializable
data class SearchRequest(
    val query: String,
    val topK: Int = 10,
    val minSimilarity: Double = 0.3
)

fun Route.analyticsRoutes(analyticsService: AnalyticsService) {
    route("/api/analytics") {
        /**
         * POST /api/analytics/query
         * Аналитический запрос с использованием RAG + LLM
         */
        post("/query") {
            try {
                val request = call.receive<AnalyticsQueryRequest>()
                val response = analyticsService.analyzeQuery(request)
                call.respond(response)
            } catch (e: Exception) {
                call.respond(
                    HttpStatusCode.InternalServerError,
                    mapOf("error" to (e.message ?: "Unknown error"))
                )
            }
        }

        /**
         * POST /api/analytics/search
         * Поиск тикетов по семантическому сходству (без LLM анализа)
         */
        post("/search") {
            try {
                val request = call.receive<SearchRequest>()
                val results = analyticsService.searchTickets(
                    query = request.query,
                    topK = request.topK,
                    minSimilarity = request.minSimilarity
                )
                call.respond(mapOf(
                    "query" to request.query,
                    "results" to results,
                    "count" to results.size
                ))
            } catch (e: Exception) {
                call.respond(
                    HttpStatusCode.InternalServerError,
                    mapOf("error" to (e.message ?: "Unknown error"))
                )
            }
        }

        /**
         * GET /api/analytics/stats
         * Статистика по всем тикетам
         */
        get("/stats") {
            try {
                val stats = analyticsService.getStats()
                call.respond(stats)
            } catch (e: Exception) {
                call.respond(
                    HttpStatusCode.InternalServerError,
                    mapOf("error" to (e.message ?: "Unknown error"))
                )
            }
        }

        /**
         * GET /api/analytics/status
         * Статус аналитической системы
         */
        get("/status") {
            try {
                val status = analyticsService.getStatus()
                call.respond(status)
            } catch (e: Exception) {
                call.respond(
                    HttpStatusCode.InternalServerError,
                    mapOf("error" to (e.message ?: "Unknown error"))
                )
            }
        }
    }
}
