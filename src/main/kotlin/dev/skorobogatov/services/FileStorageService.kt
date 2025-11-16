package dev.skorobogatov.services

import dev.skorobogatov.models.ConversationHistory
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.slf4j.LoggerFactory
import java.io.File
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths

/**
 * Сервис для сохранения и загрузки истории диалогов в/из JSON файлов
 */
class FileStorageService(
    private val storageDirectory: String = "chat_sessions"
) {
    private val logger = LoggerFactory.getLogger(FileStorageService::class.java)
    private val json = Json {
        prettyPrint = true
        ignoreUnknownKeys = true
    }

    init {
        // Создать директорию для хранения, если её нет
        val dir = File(storageDirectory)
        if (!dir.exists()) {
            dir.mkdirs()
            logger.info("Created storage directory: $storageDirectory")
        } else {
            logger.info("Using existing storage directory: $storageDirectory")
        }
    }

    /**
     * Сохранить диалог в JSON файл
     */
    fun saveConversation(conversation: ConversationHistory) {
        try {
            val fileName = "${conversation.sessionId}.json"
            val filePath = Paths.get(storageDirectory, fileName)
            val jsonContent = json.encodeToString(conversation)

            Files.writeString(filePath, jsonContent)
            logger.debug("Saved conversation to file: $fileName")
        } catch (e: Exception) {
            logger.error("Error saving conversation ${conversation.sessionId}", e)
            throw e
        }
    }

    /**
     * Загрузить диалог из JSON файла
     */
    fun loadConversation(sessionId: String): ConversationHistory? {
        try {
            val fileName = "$sessionId.json"
            val filePath = Paths.get(storageDirectory, fileName)

            if (!Files.exists(filePath)) {
                logger.debug("Conversation file not found: $fileName")
                return null
            }

            val jsonContent = Files.readString(filePath)
            val conversation = json.decodeFromString<ConversationHistory>(jsonContent)
            logger.debug("Loaded conversation from file: $fileName")
            return conversation
        } catch (e: Exception) {
            logger.error("Error loading conversation $sessionId", e)
            return null
        }
    }

    /**
     * Загрузить все существующие диалоги из директории
     */
    fun loadAllConversations(): Map<String, ConversationHistory> {
        val conversations = mutableMapOf<String, ConversationHistory>()

        try {
            val dir = File(storageDirectory)
            val files = dir.listFiles { file -> file.extension == "json" }

            if (files != null) {
                for (file in files) {
                    try {
                        val jsonContent = file.readText()
                        val conversation = json.decodeFromString<ConversationHistory>(jsonContent)
                        conversations[conversation.sessionId] = conversation
                        logger.debug("Loaded conversation: ${conversation.sessionId} (${conversation.title ?: "No title"})")
                    } catch (e: Exception) {
                        logger.error("Error loading conversation from file: ${file.name}", e)
                    }
                }
                logger.info("Loaded ${conversations.size} conversations from storage")
            }
        } catch (e: Exception) {
            logger.error("Error loading conversations from directory", e)
        }

        return conversations
    }

    /**
     * Удалить диалог
     */
    fun deleteConversation(sessionId: String): Boolean {
        try {
            val fileName = "$sessionId.json"
            val filePath = Paths.get(storageDirectory, fileName)

            if (Files.exists(filePath)) {
                Files.delete(filePath)
                logger.info("Deleted conversation file: $fileName")
                return true
            }

            logger.debug("Conversation file not found for deletion: $fileName")
            return false
        } catch (e: Exception) {
            logger.error("Error deleting conversation $sessionId", e)
            return false
        }
    }

    /**
     * Проверить существование диалога
     */
    fun conversationExists(sessionId: String): Boolean {
        val fileName = "$sessionId.json"
        val filePath = Paths.get(storageDirectory, fileName)
        return Files.exists(filePath)
    }

    /**
     * Получить список всех сохраненных session ID
     */
    fun getAllSessionIds(): List<String> {
        try {
            val dir = File(storageDirectory)
            return dir.listFiles { file -> file.extension == "json" }
                ?.map { it.nameWithoutExtension }
                ?: emptyList()
        } catch (e: Exception) {
            logger.error("Error getting session IDs", e)
            return emptyList()
        }
    }
}
