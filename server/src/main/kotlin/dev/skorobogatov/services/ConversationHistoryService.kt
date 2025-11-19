package dev.skorobogatov.services

import dev.skorobogatov.models.ConversationHistory
import dev.skorobogatov.models.HistoryMessage
import dev.skorobogatov.models.MessageType
import org.slf4j.LoggerFactory
import java.util.concurrent.ConcurrentHashMap

/**
 * Сервис для управления историями диалогов
 * Использует in-memory хранилище (ConcurrentHashMap для thread-safety)
 * с автоматическим сохранением в файлы
 */
class ConversationHistoryService(
    private val compressionThreshold: Int = 3,  // Сжимать каждые N пар сообщений
    private val fileStorageService: FileStorageService? = null  // Сервис для сохранения в файлы
) {
    private val logger = LoggerFactory.getLogger(ConversationHistoryService::class.java)
    private val sessions = ConcurrentHashMap<String, ConversationHistory>()

    init {
        // Загрузить существующие диалоги при инициализации
        if (fileStorageService != null) {
            val loadedSessions = fileStorageService.loadAllConversations()
            sessions.putAll(loadedSessions)
            logger.info("Loaded ${loadedSessions.size} conversations from storage")
        }
    }

    /**
     * Сохранить сессию в файл (если fileStorageService доступен)
     */
    private fun saveSession(session: ConversationHistory) {
        fileStorageService?.saveConversation(session)
    }

    /**
     * Получить или создать историю диалога
     */
    fun getOrCreateSession(sessionId: String?): ConversationHistory {
        return if (sessionId != null && sessions.containsKey(sessionId)) {
            logger.debug("Retrieved existing session: $sessionId")
            sessions[sessionId]!!.also {
                it.lastAccessedAt = System.currentTimeMillis()
                saveSession(it)
            }
        } else {
            val newSession = ConversationHistory()
            sessions[newSession.sessionId] = newSession
            logger.debug("Created new session: ${newSession.sessionId}")
            saveSession(newSession)
            newSession
        }
    }

    /**
     * Установить название диалога
     */
    fun setConversationTitle(sessionId: String, title: String) {
        val session = sessions[sessionId] ?: throw IllegalArgumentException("Session not found: $sessionId")
        session.title = title
        logger.debug("Set title for session $sessionId: $title")
        saveSession(session)
    }

    /**
     * Добавить сообщение пользователя
     */
    fun addUserMessage(sessionId: String, message: String, fromScheduledTask: Boolean = false) {
        val session = sessions[sessionId] ?: throw IllegalArgumentException("Session not found: $sessionId")
        session.addMessage(MessageType.USER, message, fromScheduledTask)
        logger.debug("Added user message to session $sessionId")
        saveSession(session)
    }

    /**
     * Добавить ответ ассистента
     */
    fun addAssistantMessage(sessionId: String, message: String, fromScheduledTask: Boolean = false) {
        val session = sessions[sessionId] ?: throw IllegalArgumentException("Session not found: $sessionId")
        session.addMessage(MessageType.ASSISTANT, message, fromScheduledTask)
        logger.debug("Added assistant message to session $sessionId (fromScheduledTask=$fromScheduledTask)")
        saveSession(session)
    }

    /**
     * Проверить, нужно ли сжать историю
     */
    fun shouldCompressHistory(sessionId: String): Boolean {
        val session = sessions[sessionId] ?: return false
        val pairsCount = session.getMessagePairsCount()
        val shouldCompress = pairsCount >= compressionThreshold

        if (shouldCompress) {
            logger.debug("Session $sessionId has $pairsCount pairs, compression needed (threshold: $compressionThreshold)")
        }

        return shouldCompress
    }

    /**
     * Получить первые N пар сообщений для сжатия
     */
    fun getMessagesForCompression(sessionId: String, pairsToCompress: Int = 3): List<HistoryMessage> {
        val session = sessions[sessionId] ?: throw IllegalArgumentException("Session not found: $sessionId")
        val messages = mutableListOf<HistoryMessage>()
        var pairsFound = 0
        var i = 0

        while (i < session.messages.size && pairsFound < pairsToCompress) {
            val msg = session.messages[i]

            // Проверяем, является ли это началом пары USER-ASSISTANT
            if (msg.type == MessageType.USER &&
                i + 1 < session.messages.size &&
                session.messages[i + 1].type == MessageType.ASSISTANT) {
                // Добавляем обе части пары
                messages.add(msg)
                messages.add(session.messages[i + 1])
                pairsFound++
                i += 2 // Пропускаем следующее сообщение, так как мы его уже добавили
            } else {
                // Если это не пара, добавляем только текущее сообщение
                messages.add(msg)
                i++
            }
        }

        logger.debug("Retrieved ${messages.size} messages ($pairsFound pairs) for compression from session $sessionId")
        return messages
    }

    /**
     * Сжать историю - заменить первые N пар сообщений на summary
     */
    fun compressHistory(sessionId: String, summary: String, messagesToRemove: Int) {
        val session = sessions[sessionId] ?: throw IllegalArgumentException("Session not found: $sessionId")

        // Удаляем первые messagesToRemove сообщений
        repeat(messagesToRemove) {
            if (session.messages.isNotEmpty()) {
                session.messages.removeAt(0)
            }
        }

        // Добавляем summary в начало
        session.messages.add(0, HistoryMessage(MessageType.SUMMARY, summary))

        logger.info("Compressed history for session $sessionId: removed $messagesToRemove messages, added summary")
        saveSession(session)
    }

    /**
     * Получить сессию
     */
    fun getSession(sessionId: String): ConversationHistory? {
        return sessions[sessionId]
    }

    /**
     * Удалить старые сессии (можно вызывать периодически для очистки памяти)
     */
    fun cleanupOldSessions(maxAgeMs: Long = 24 * 60 * 60 * 1000) { // 24 часа по умолчанию
        val now = System.currentTimeMillis()
        val toRemove = sessions.filter { (_, session) ->
            now - session.lastAccessedAt > maxAgeMs
        }.keys

        toRemove.forEach { sessionId ->
            sessions.remove(sessionId)
            logger.debug("Removed old session: $sessionId")
        }

        if (toRemove.isNotEmpty()) {
            logger.info("Cleaned up ${toRemove.size} old sessions")
        }
    }

    /**
     * Получить статистику
     */
    fun getStats(): Map<String, Any> {
        return mapOf(
            "totalSessions" to sessions.size,
            "compressionThreshold" to compressionThreshold
        )
    }

    /**
     * Пометить все сообщения как прочитанные
     */
    fun markAllAsRead(sessionId: String) {
        val session = sessions[sessionId] ?: throw IllegalArgumentException("Session not found: $sessionId")
        session.lastReadMessageIndex = session.messages.size - 1
        logger.debug("Marked all messages as read for session $sessionId")
        saveSession(session)
    }

    /**
     * Удалить диалог
     */
    fun deleteConversation(sessionId: String): Boolean {
        val removed = sessions.remove(sessionId) != null
        if (removed) {
            fileStorageService?.deleteConversation(sessionId)
            logger.info("Deleted conversation: $sessionId")
        } else {
            logger.warn("Attempted to delete non-existent conversation: $sessionId")
        }
        return removed
    }

    /**
     * Получить список всех диалогов
     */
    fun getAllConversations(): List<ConversationHistory> {
        return sessions.values
            .sortedByDescending { it.lastAccessedAt }
            .toList()
    }
}
