package dev.skorobogatov.models

import kotlinx.serialization.Serializable

/**
 * Информация о чанке из RAG системы
 */
@Serializable
data class RAGChunkReference(
    val fileName: String,  // Имя файла-источника
    val chunkId: Int,  // Номер чанка в документе
    val text: String,  // Текст чанка (цитата)
    val similarity: Double,  // Релевантность (0.0 - 1.0)
    val wordCount: Int,  // Количество слов в чанке
    val startWord: Int,  // Начальная позиция в документе
    val endWord: Int,  // Конечная позиция в документе
    val estimatedTokens: Int  // Примерное количество токенов
)

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
    val ragChunks: List<RAGChunkReference> = emptyList(),  // Детальная информация о найденных чанках с цитатами
    val rerankingUsed: Boolean = false,  // Был ли использован reranking
    val rerankingTimeMs: Long = 0,  // Время reranking в миллисекундах
    val settings: SessionSettings? = null  // Текущие настройки сессии
)
