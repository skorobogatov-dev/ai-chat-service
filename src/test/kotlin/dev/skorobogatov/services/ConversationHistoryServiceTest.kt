package dev.skorobogatov.services

import dev.skorobogatov.models.MessageType
import kotlin.test.*

class ConversationHistoryServiceTest {

    @Test
    fun `test creating new session`() {
        // Given
        val service = ConversationHistoryService(compressionThreshold = 3)

        // When
        val session = service.getOrCreateSession(null)

        // Then
        assertNotNull(session)
        assertNotNull(session.sessionId)
        assertEquals(0, session.messages.size)
    }

    @Test
    fun `test retrieving existing session`() {
        // Given
        val service = ConversationHistoryService(compressionThreshold = 3)
        val session1 = service.getOrCreateSession(null)
        val sessionId = session1.sessionId

        // When
        val session2 = service.getOrCreateSession(sessionId)

        // Then
        assertEquals(sessionId, session2.sessionId)
        assertSame(session1, session2)
    }

    @Test
    fun `test adding messages to session`() {
        // Given
        val service = ConversationHistoryService(compressionThreshold = 3)
        val session = service.getOrCreateSession(null)

        // When
        service.addUserMessage(session.sessionId, "Hello")
        service.addAssistantMessage(session.sessionId, "Hi there!")

        // Then
        val updatedSession = service.getSession(session.sessionId)
        assertNotNull(updatedSession)
        assertEquals(2, updatedSession.messages.size)
        assertEquals(MessageType.USER, updatedSession.messages[0].type)
        assertEquals("Hello", updatedSession.messages[0].content)
        assertEquals(MessageType.ASSISTANT, updatedSession.messages[1].type)
        assertEquals("Hi there!", updatedSession.messages[1].content)
    }

    @Test
    fun `test counting message pairs`() {
        // Given
        val service = ConversationHistoryService(compressionThreshold = 3)
        val session = service.getOrCreateSession(null)

        // When
        service.addUserMessage(session.sessionId, "Message 1")
        service.addAssistantMessage(session.sessionId, "Response 1")
        service.addUserMessage(session.sessionId, "Message 2")
        service.addAssistantMessage(session.sessionId, "Response 2")

        // Then
        val updatedSession = service.getSession(session.sessionId)
        assertNotNull(updatedSession)
        assertEquals(2, updatedSession.getMessagePairsCount())
    }

    @Test
    fun `test should compress history when threshold reached`() {
        // Given
        val service = ConversationHistoryService(compressionThreshold = 3)
        val session = service.getOrCreateSession(null)

        // When - add 3 pairs of messages
        repeat(3) { i ->
            service.addUserMessage(session.sessionId, "Message $i")
            service.addAssistantMessage(session.sessionId, "Response $i")
        }

        // Then
        assertTrue(service.shouldCompressHistory(session.sessionId))
    }

    @Test
    fun `test should not compress history when threshold not reached`() {
        // Given
        val service = ConversationHistoryService(compressionThreshold = 3)
        val session = service.getOrCreateSession(null)

        // When - add only 2 pairs of messages
        repeat(2) { i ->
            service.addUserMessage(session.sessionId, "Message $i")
            service.addAssistantMessage(session.sessionId, "Response $i")
        }

        // Then
        assertFalse(service.shouldCompressHistory(session.sessionId))
    }

    @Test
    fun `test getting messages for compression`() {
        // Given
        val service = ConversationHistoryService(compressionThreshold = 3)
        val session = service.getOrCreateSession(null)

        // Add 4 pairs of messages
        repeat(4) { i ->
            service.addUserMessage(session.sessionId, "Message $i")
            service.addAssistantMessage(session.sessionId, "Response $i")
        }

        // When
        val messagesToCompress = service.getMessagesForCompression(session.sessionId, 3)

        // Then
        assertEquals(6, messagesToCompress.size) // 3 pairs = 6 messages
        assertEquals(MessageType.USER, messagesToCompress[0].type)
        assertEquals("Message 0", messagesToCompress[0].content)
        assertEquals(MessageType.ASSISTANT, messagesToCompress[1].type)
        assertEquals("Response 0", messagesToCompress[1].content)
    }

    @Test
    fun `test compressing history`() {
        // Given
        val service = ConversationHistoryService(compressionThreshold = 3)
        val session = service.getOrCreateSession(null)

        // Add 4 pairs of messages
        repeat(4) { i ->
            service.addUserMessage(session.sessionId, "Message $i")
            service.addAssistantMessage(session.sessionId, "Response $i")
        }

        val initialSize = service.getSession(session.sessionId)!!.messages.size
        assertEquals(8, initialSize)

        // When - compress first 3 pairs (6 messages)
        val summary = "Summary of first 3 conversations"
        service.compressHistory(session.sessionId, summary, 6)

        // Then
        val updatedSession = service.getSession(session.sessionId)
        assertNotNull(updatedSession)

        // Should have: 1 summary + 2 remaining messages = 3 messages
        assertEquals(3, updatedSession.messages.size)

        // First message should be the summary
        assertEquals(MessageType.SUMMARY, updatedSession.messages[0].type)
        assertEquals(summary, updatedSession.messages[0].content)

        // Next messages should be from the 4th pair
        assertEquals(MessageType.USER, updatedSession.messages[1].type)
        assertEquals("Message 3", updatedSession.messages[1].content)
        assertEquals(MessageType.ASSISTANT, updatedSession.messages[2].type)
        assertEquals("Response 3", updatedSession.messages[2].content)
    }

    @Test
    fun `test converting to Claude messages`() {
        // Given
        val service = ConversationHistoryService(compressionThreshold = 3)
        val session = service.getOrCreateSession(null)

        service.addUserMessage(session.sessionId, "Hello")
        service.addAssistantMessage(session.sessionId, "Hi")

        // When
        val updatedSession = service.getSession(session.sessionId)!!
        val claudeMessages = updatedSession.toClaudeMessages()

        // Then
        assertEquals(2, claudeMessages.size)
        assertEquals("user", claudeMessages[0].role)
        assertEquals("Hello", claudeMessages[0].content)
        assertEquals("assistant", claudeMessages[1].role)
        assertEquals("Hi", claudeMessages[1].content)
    }

    @Test
    fun `test cleanup old sessions`() {
        // Given
        val service = ConversationHistoryService(compressionThreshold = 3)

        // Create a session
        val session = service.getOrCreateSession(null)
        val sessionId = session.sessionId

        // Wait a bit to make session "old"
        Thread.sleep(10)

        // When - cleanup sessions older than 5ms
        service.cleanupOldSessions(maxAgeMs = 5)

        // Then - session should be removed
        assertNull(service.getSession(sessionId))
    }

    @Test
    fun `test get stats`() {
        // Given
        val service = ConversationHistoryService(compressionThreshold = 5)

        // Create 3 sessions
        service.getOrCreateSession(null)
        service.getOrCreateSession(null)
        service.getOrCreateSession(null)

        // When
        val stats = service.getStats()

        // Then
        assertEquals(3, stats["totalSessions"])
        assertEquals(5, stats["compressionThreshold"])
    }

    @Test
    fun `test full compression workflow`() {
        // Given
        val service = ConversationHistoryService(compressionThreshold = 2)
        val session = service.getOrCreateSession(null)

        // When - add exactly 2 pairs to trigger compression
        service.addUserMessage(session.sessionId, "Question 1")
        service.addAssistantMessage(session.sessionId, "Answer 1")
        service.addUserMessage(session.sessionId, "Question 2")
        service.addAssistantMessage(session.sessionId, "Answer 2")

        // Check that compression is needed
        assertTrue(service.shouldCompressHistory(session.sessionId))

        // Get messages to compress
        val messagesToCompress = service.getMessagesForCompression(session.sessionId, 2)
        assertEquals(4, messagesToCompress.size)

        // Compress history
        service.compressHistory(session.sessionId, "Summary of Q1-Q2", messagesToCompress.size)

        // Add new message
        service.addUserMessage(session.sessionId, "Question 3")

        // Then
        val updatedSession = service.getSession(session.sessionId)!!
        assertEquals(2, updatedSession.messages.size) // summary + question 3
        assertEquals(MessageType.SUMMARY, updatedSession.messages[0].type)
        assertEquals(MessageType.USER, updatedSession.messages[1].type)
        assertEquals("Question 3", updatedSession.messages[1].content)
    }
}
