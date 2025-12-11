package dev.skorobogatov.models

import kotlinx.serialization.Serializable

/**
 * Параметры генерации для настройки поведения модели
 */
@Serializable
data class GenerationOptions(
    val temperature: Double? = null,      // Креативность (0.0-2.0, default: 0.7)
    val topP: Double? = null,             // Nucleus sampling (0.0-1.0, default: 0.9)
    val topK: Int? = null,                // Top-K sampling (default: 40)
    val repeatPenalty: Double? = null,    // Штраф за повторения (default: 1.1)
    val maxTokens: Int? = null,           // Максимум токенов в ответе
    val numCtx: Int? = null               // Размер контекстного окна (default: 4096)
)

@Serializable
data class ChatRequest(
    val message: String,
    val sessionId: String? = null,  // ID сессии для продолжения диалога (если null - новая сессия)
    val systemPrompt: String? = null,  // Пользовательский системный промпт (переопределяет preset)
    val provider: String? = "ollama",  // "ollama" (default) или "claude" - какой LLM провайдер использовать
    val model: String? = null,  // Если не указана, используется модель из конфигурации (для ollama: qwen2.5:0.5b, llama3.2, mistral, etc.)
    val useRAG: Boolean = false,  // Использовать ли RAG для обогащения контекста
    val ragTopK: Int = 3,  // Количество похожих чанков для RAG
    val ragMinSimilarity: Double = 0.5,  // Минимальное значение сходства (0.0 - 1.0)
    val useReranking: Boolean = false,  // Использовать ли reranking для улучшения качества RAG (работает только с useRAG=true, только для Claude)
    val preset: String? = null,  // Пресет настроек: "standard", "coding" (определяет системный промпт и параметры)
    val codingMode: Boolean = false,  // Deprecated: используйте preset="coding". Оставлено для совместимости
    val options: GenerationOptions? = null  // Пользовательские параметры генерации (переопределяют preset)
)
