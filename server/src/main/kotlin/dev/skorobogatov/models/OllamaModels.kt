package dev.skorobogatov.models

import kotlinx.serialization.Serializable

// =====================================================
// OLLAMA CHAT API MODELS
// =====================================================

/**
 * Запрос к Ollama /api/chat endpoint
 */
@Serializable
data class OllamaChatRequest(
    val model: String,
    val messages: List<OllamaChatMessage>,
    val stream: Boolean = false,
    val options: OllamaChatOptions? = null
)

/**
 * Сообщение для Ollama Chat API
 */
@Serializable
data class OllamaChatMessage(
    val role: String,  // "system", "user", "assistant"
    val content: String
)

/**
 * Опции генерации для Ollama
 * https://github.com/ollama/ollama/blob/main/docs/modelfile.md#valid-parameters-and-values
 */
@Serializable
data class OllamaChatOptions(
    val temperature: Double? = null,      // Креативность (0.0-2.0, default: 0.8)
    val num_predict: Int? = null,         // Max tokens в ответе (default: 128, -1 = infinite)
    val num_ctx: Int? = null,             // Размер контекстного окна (default: 2048)
    val top_p: Double? = null,            // Nucleus sampling (0.0-1.0, default: 0.9)
    val top_k: Int? = null,               // Top-K sampling (default: 40)
    val repeat_penalty: Double? = null,   // Штраф за повторения (default: 1.1)
    val seed: Int? = null,                // Seed для воспроизводимости
    val stop: List<String>? = null        // Stop sequences
)

/**
 * Ответ от Ollama /api/chat endpoint (non-streaming)
 */
@Serializable
data class OllamaChatResponse(
    val model: String,
    val message: OllamaChatMessage,
    val done: Boolean,
    val total_duration: Long? = null,
    val load_duration: Long? = null,
    val prompt_eval_count: Int? = null,  // input tokens
    val prompt_eval_duration: Long? = null,
    val eval_count: Int? = null,         // output tokens
    val eval_duration: Long? = null
)

/**
 * Информация о модели Ollama
 */
@Serializable
data class OllamaModelInfo(
    val name: String,
    val modified_at: String? = null,
    val size: Long? = null,
    val digest: String? = null
)

/**
 * Ответ от Ollama /api/tags endpoint (список моделей)
 */
@Serializable
data class OllamaModelsResponse(
    val models: List<OllamaModelInfo>
)

// =====================================================
// OLLAMA EMBEDDINGS API MODELS
// =====================================================

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
