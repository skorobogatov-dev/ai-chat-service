package dev.skorobogatov.models

import kotlinx.serialization.Serializable

@Serializable
data class ChatResponse(
    val response: String,
    val sessionId: String = "",  // ID сессии для продолжения диалога
    val model: String,
    val inputTokens: Int,
    val outputTokens: Int,
    val totalTokens: Int,
    val responseTimeMs: Long,
    val historyCompressed: Boolean = false,  // Была ли сжата история в этом запросе
    val ragUsed: Boolean = false,  // Был ли использован RAG
    val ragChunksFound: Int = 0,  // Количество найденных чанков для RAG
    val ragSources: List<String> = emptyList(),  // Источники для RAG (имена файлов)
    val rerankingUsed: Boolean = false,  // Был ли использован reranking
    val rerankingTimeMs: Long = 0  // Время reranking в миллисекундах
)
