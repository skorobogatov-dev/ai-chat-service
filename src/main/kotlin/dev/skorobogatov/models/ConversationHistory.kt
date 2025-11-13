package dev.skorobogatov.models

import kotlinx.serialization.Serializable
import java.util.UUID

/**
 * Тип сообщения в истории диалога
 */
enum class MessageType {
    USER,       // Сообщение от пользователя
    ASSISTANT,  // Ответ ассистента
    SUMMARY     // Сжатое summary нескольких сообщений
}

/**
 * Одно сообщение в истории диалога
 */
data class HistoryMessage(
    val type: MessageType,
    val content: String,
    val timestamp: Long = System.currentTimeMillis()
)

/**
 * История одного диалога
 */
data class ConversationHistory(
    val sessionId: String = UUID.randomUUID().toString(),
    val messages: MutableList<HistoryMessage> = mutableListOf(),
    val createdAt: Long = System.currentTimeMillis(),
    var lastAccessedAt: Long = System.currentTimeMillis()
) {
    /**
     * Добавить сообщение в историю
     */
    fun addMessage(type: MessageType, content: String) {
        messages.add(HistoryMessage(type, content))
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
}
