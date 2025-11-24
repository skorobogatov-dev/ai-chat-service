package dev.skorobogatov.models

import kotlinx.serialization.Serializable

/**
 * Запрос для получения embedding от Ollama
 */
@Serializable
data class OllamaEmbeddingRequest(
    val model: String,
    val prompt: String
)

/**
 * Ответ от Ollama с embedding
 */
@Serializable
data class OllamaEmbeddingResponse(
    val embedding: List<Double>
)

/**
 * Публичный API запрос для векторизации текста
 */
@Serializable
data class EmbeddingRequest(
    val text: String
)

/**
 * Публичный API ответ с вектором
 */
@Serializable
data class EmbeddingResponse(
    val embedding: List<Double>,
    val model: String,
    val dimension: Int,
    val processingTimeMs: Long
)

/**
 * Запрос для векторизации нескольких текстов
 */
@Serializable
data class BatchEmbeddingRequest(
    val texts: List<String>
)

/**
 * Ответ с векторами для нескольких текстов
 */
@Serializable
data class BatchEmbeddingResponse(
    val embeddings: List<List<Double>>,
    val model: String,
    val dimension: Int,
    val count: Int,
    val processingTimeMs: Long
)

/**
 * Статус подключения к Ollama
 */
@Serializable
data class OllamaStatus(
    val available: Boolean,
    val url: String,
    val model: String,
    val error: String? = null
)

/**
 * Запрос для векторизации текста с автоматическим чанкованием
 */
@Serializable
data class VectorizeTextRequest(
    val text: String,
    val chunkSize: Int = 750,
    val overlap: Int = 75,
    val saveToFile: Boolean = true,
    val outputFileName: String? = null
)

/**
 * Информация об одном векторизованном чанке
 */
@Serializable
data class VectorizedChunkInfo(
    val chunkId: Int,
    val text: String,
    val startWord: Int,
    val endWord: Int,
    val estimatedTokens: Int,
    val wordCount: Int,
    val embedding: List<Double>,
    val dimension: Int,
    val processingTimeMs: Long
)

/**
 * Метаданные результата векторизации
 */
@Serializable
data class VectorizationMetadata(
    val timestamp: String,
    val totalChunks: Int,
    val chunkSizeTokens: Int,
    val overlapTokens: Int,
    val totalTextTokens: Int,
    val model: String,
    val embeddingDimension: Int,
    val totalProcessingTimeMs: Long,
    val savedToFile: String? = null
)

/**
 * Ответ с результатом векторизации
 */
@Serializable
data class VectorizeTextResponse(
    val metadata: VectorizationMetadata,
    val chunks: List<VectorizedChunkInfo>
)
