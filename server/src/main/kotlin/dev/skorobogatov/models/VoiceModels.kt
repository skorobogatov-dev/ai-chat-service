package dev.skorobogatov.models

import kotlinx.serialization.Serializable

/**
 * Ответ на голосовой запрос
 */
@Serializable
data class VoiceResponse(
    val transcribedText: String,          // Распознанный текст из аудио
    val response: String,                 // Ответ LLM на распознанный текст
    val sessionId: String,                // ID сессии диалога
    val provider: String,                 // Провайдер LLM (claude/ollama)
    val model: String,                    // Модель LLM
    val transcriptionTimeMs: Long,        // Время распознавания речи (STT)
    val llmResponseTimeMs: Long,          // Время генерации ответа LLM
    val totalTimeMs: Long,                // Общее время обработки
    val inputTokens: Int,                 // Токены на входе LLM
    val outputTokens: Int,                // Токены на выходе LLM
    val totalTokens: Int,                 // Всего токенов
    val historyCompressed: Boolean = false, // Была ли сжата история в этом запросе
    val ragUsed: Boolean = false,         // Был ли использован RAG
    val ragChunksFound: Int = 0           // Количество найденных чанков для RAG
)

/**
 * Результат транскрибации аудио
 */
data class TranscriptionResult(
    val text: String,                     // Распознанный текст
    val confidence: Double = 1.0,         // Уверенность распознавания (0.0 - 1.0)
    val language: String = "ru",          // Язык распознавания
    val duration: Long                    // Длительность аудио в миллисекундах
)
