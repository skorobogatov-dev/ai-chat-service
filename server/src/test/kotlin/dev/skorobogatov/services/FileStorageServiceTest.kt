package dev.skorobogatov.services

import dev.skorobogatov.models.ConversationHistory
import dev.skorobogatov.models.MessageType
import java.io.File
import kotlin.test.*

class FileStorageServiceTest {

    private val testDirectory = "test_sessions"
    private lateinit var service: FileStorageService

    @BeforeTest
    fun setup() {
        // Создаем тестовую директорию
        service = FileStorageService(storageDirectory = testDirectory)
    }

    @AfterTest
    fun cleanup() {
        // Удаляем тестовую директорию и все файлы
        val dir = File(testDirectory)
        if (dir.exists()) {
            dir.listFiles()?.forEach { it.delete() }
            dir.delete()
        }
    }

    @Test
    fun `test save and load conversation`() {
        // Given
        val conversation = ConversationHistory()
        conversation.title = "Test Conversation"
        conversation.addMessage(MessageType.USER, "Hello")
        conversation.addMessage(MessageType.ASSISTANT, "Hi there!")

        // When
        service.saveConversation(conversation)
        val loaded = service.loadConversation(conversation.sessionId)

        // Then
        assertNotNull(loaded)
        assertEquals(conversation.sessionId, loaded.sessionId)
        assertEquals("Test Conversation", loaded.title)
        assertEquals(2, loaded.messages.size)
        assertEquals("Hello", loaded.messages[0].content)
        assertEquals("Hi there!", loaded.messages[1].content)
    }

    @Test
    fun `test load non-existent conversation returns null`() {
        // When
        val loaded = service.loadConversation("non-existent-id")

        // Then
        assertNull(loaded)
    }

    @Test
    fun `test load all conversations`() {
        // Given
        val conversation1 = ConversationHistory()
        conversation1.title = "Conversation 1"
        conversation1.addMessage(MessageType.USER, "Message 1")

        val conversation2 = ConversationHistory()
        conversation2.title = "Conversation 2"
        conversation2.addMessage(MessageType.USER, "Message 2")

        // When
        service.saveConversation(conversation1)
        service.saveConversation(conversation2)
        val loaded = service.loadAllConversations()

        // Then
        assertEquals(2, loaded.size)
        assertTrue(loaded.containsKey(conversation1.sessionId))
        assertTrue(loaded.containsKey(conversation2.sessionId))
        assertEquals("Conversation 1", loaded[conversation1.sessionId]?.title)
        assertEquals("Conversation 2", loaded[conversation2.sessionId]?.title)
    }

    @Test
    fun `test delete conversation`() {
        // Given
        val conversation = ConversationHistory()
        service.saveConversation(conversation)

        // Verify it exists
        assertTrue(service.conversationExists(conversation.sessionId))

        // When
        val deleted = service.deleteConversation(conversation.sessionId)

        // Then
        assertTrue(deleted)
        assertFalse(service.conversationExists(conversation.sessionId))
        assertNull(service.loadConversation(conversation.sessionId))
    }

    @Test
    fun `test delete non-existent conversation returns false`() {
        // When
        val deleted = service.deleteConversation("non-existent-id")

        // Then
        assertFalse(deleted)
    }

    @Test
    fun `test get all session IDs`() {
        // Given
        val conversation1 = ConversationHistory()
        val conversation2 = ConversationHistory()

        service.saveConversation(conversation1)
        service.saveConversation(conversation2)

        // When
        val sessionIds = service.getAllSessionIds()

        // Then
        assertEquals(2, sessionIds.size)
        assertTrue(sessionIds.contains(conversation1.sessionId))
        assertTrue(sessionIds.contains(conversation2.sessionId))
    }

    @Test
    fun `test conversation persistence with complex data`() {
        // Given
        val conversation = ConversationHistory()
        conversation.title = "Complex Conversation"
        conversation.addMessage(MessageType.USER, "Question 1")
        conversation.addMessage(MessageType.ASSISTANT, "Answer 1")
        conversation.addMessage(MessageType.SUMMARY, "Summary of conversation")
        conversation.addMessage(MessageType.USER, "Question 2")
        conversation.addMessage(MessageType.ASSISTANT, "Answer 2")

        // When
        service.saveConversation(conversation)
        val loaded = service.loadConversation(conversation.sessionId)

        // Then
        assertNotNull(loaded)
        assertEquals(5, loaded.messages.size)
        assertEquals(MessageType.USER, loaded.messages[0].type)
        assertEquals(MessageType.ASSISTANT, loaded.messages[1].type)
        assertEquals(MessageType.SUMMARY, loaded.messages[2].type)
        assertEquals("Summary of conversation", loaded.messages[2].content)
    }
}
