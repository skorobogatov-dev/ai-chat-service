package dev.skorobogatov.models

import kotlinx.serialization.Serializable

/**
 * Тикет технической поддержки
 */
@Serializable
data class Ticket(
    val id: String,
    val createdAt: String,
    val category: String,
    val priority: String,
    val status: String,
    val title: String,
    val description: String,
    val errorCode: String? = null,
    val resolution: String? = null,
    val resolutionTimeHours: Int? = null,
    val userSegment: String,
    val funnelStage: String,
    val browser: String? = null,
    val platform: String? = null,
    val userId: String,
    val tags: List<String>
)

/**
 * Тикет с embedding для поиска
 */
data class TicketWithEmbedding(
    val ticket: Ticket,
    val embedding: List<Double>,
    val searchableText: String
)

/**
 * Запрос аналитики
 */
@Serializable
data class AnalyticsQueryRequest(
    val query: String,
    val topK: Int = 10,
    val minSimilarity: Double = 0.3
)

/**
 * Результат поиска тикета
 */
@Serializable
data class TicketSearchResult(
    val ticket: Ticket,
    val similarity: Double
)

/**
 * Ответ на аналитический запрос
 */
@Serializable
data class AnalyticsQueryResponse(
    val query: String,
    val answer: String,
    val relevantTickets: List<TicketSearchResult>,
    val totalTicketsAnalyzed: Int,
    val processingTimeMs: Long
)

/**
 * Статус аналитической системы
 */
@Serializable
data class AnalyticsStatus(
    val initialized: Boolean,
    val totalTickets: Int,
    val vectorizedTickets: Int,
    val embeddingDimension: Int,
    val categories: Map<String, Int>,
    val priorities: Map<String, Int>,
    val statuses: Map<String, Int>
)

/**
 * Статистика по тикетам
 */
@Serializable
data class TicketStats(
    val totalTickets: Int,
    val byCategory: Map<String, Int>,
    val byPriority: Map<String, Int>,
    val byStatus: Map<String, Int>,
    val byUserSegment: Map<String, Int>,
    val byFunnelStage: Map<String, Int>,
    val topErrorCodes: List<ErrorCodeCount>,
    val avgResolutionTimeByPriority: Map<String, Double>
)

@Serializable
data class ErrorCodeCount(
    val errorCode: String,
    val count: Int
)
