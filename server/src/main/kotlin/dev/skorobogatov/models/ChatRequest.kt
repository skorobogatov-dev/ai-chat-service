package dev.skorobogatov.models

import kotlinx.serialization.Serializable

@Serializable
data class ChatRequest(
    val message: String,
    val sessionId: String? = null,  // ID сессии для продолжения диалога (если null - новая сессия)
    val systemPrompt: String? = null,
    val provider: String? = "ollama",  // "ollama" (default) или "claude" - какой LLM провайдер использовать
    val model: String? = null,  // Если не указана, используется модель из конфигурации (для ollama: qwen2.5:0.5b, llama3.2, mistral, etc.)
    val useRAG: Boolean = false,  // Использовать ли RAG для обогащения контекста
    val ragTopK: Int = 3,  // Количество похожих чанков для RAG
    val ragMinSimilarity: Double = 0.5,  // Минимальное значение сходства (0.0 - 1.0)
    val useReranking: Boolean = false  // Использовать ли reranking для улучшения качества RAG (работает только с useRAG=true, только для Claude)
)
