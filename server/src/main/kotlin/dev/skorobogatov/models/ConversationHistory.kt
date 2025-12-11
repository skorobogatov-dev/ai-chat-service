package dev.skorobogatov.models

import kotlinx.serialization.Serializable
import java.util.UUID

/**
 * Тип сообщения в истории диалога
 */
@Serializable
enum class MessageType {
    USER,       // Сообщение от пользователя
    ASSISTANT,  // Ответ ассистента
    SUMMARY     // Сжатое summary нескольких сообщений
}

/**
 * Одно сообщение в истории диалога
 */
@Serializable
data class HistoryMessage(
    val type: MessageType,
    val content: String,
    val timestamp: Long = System.currentTimeMillis(),
    val fromScheduledTask: Boolean = false,  // Флаг для сообщений от запланированных задач
    val ragChunks: List<RAGChunkReference>? = null  // RAG чанки для ответов ассистента
)

/**
 * Настройки генерации для сессии
 */
@Serializable
data class SessionSettings(
    val model: String? = null,
    val temperature: Double? = null,
    val topP: Double? = null,
    val topK: Int? = null,
    val repeatPenalty: Double? = null,
    val maxTokens: Int? = null,
    val numCtx: Int? = null,
    val preset: String? = null,  // "standard", "coding", "custom"
    val codingMode: Boolean = false
)

/**
 * История одного диалога
 */
@Serializable
data class ConversationHistory(
    val sessionId: String = UUID.randomUUID().toString(),
    var title: String? = null,  // Название диалога (генерируется автоматически)
    val messages: MutableList<HistoryMessage> = mutableListOf(),
    val createdAt: Long = System.currentTimeMillis(),
    var lastAccessedAt: Long = System.currentTimeMillis(),
    var lastReadMessageIndex: Int = -1,  // Индекс последнего прочитанного сообщения
    var settings: SessionSettings? = null  // Настройки генерации для этой сессии
) {
    /**
     * Добавить сообщение в историю
     */
    fun addMessage(type: MessageType, content: String, fromScheduledTask: Boolean = false, ragChunks: List<RAGChunkReference>? = null) {
        messages.add(HistoryMessage(type, content, fromScheduledTask = fromScheduledTask, ragChunks = ragChunks))
        lastAccessedAt = System.currentTimeMillis()
    }

    /**
     * Получить количество пар "пользователь-ассистент" (не считая summary)
     */
    fun getMessagePairsCount(): Int {
        var count = 0
        for (i in messages.indices) {
            if (messages[i].type == MessageType.USER &&
                i + 1 < messages.size &&
                messages[i + 1].type == MessageType.ASSISTANT) {
                count++
            }
        }
        return count
    }

    /**
     * Получить все сообщения в формате для Claude API
     */
    fun toClaudeMessages(): List<ClaudeMessage> {
        return messages.map { msg ->
            val role = when (msg.type) {
                MessageType.USER -> "user"
                MessageType.ASSISTANT -> "assistant"
                MessageType.SUMMARY -> "user" // Summary отправляем как user message
            }
            ClaudeMessage(role = role, content = msg.content)
        }
    }

    /**
     * Получить количество непрочитанных сообщений от ассистента
     */
    fun getUnreadCount(): Int {
        if (lastReadMessageIndex >= messages.size - 1) return 0

        return messages
            .drop(lastReadMessageIndex + 1)
            .count { it.type == MessageType.ASSISTANT }
    }
}
