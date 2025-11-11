package dev.skorobogatov.routes

import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.server.testing.*
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Integration tests for Chat Routes.
 * Tests with mocked ClaudeService are in ClaudeServiceTest.
 * These tests verify routes work with the real application setup.
 */
class ChatRoutesTest {

    @Test
    fun `test health endpoint returns OK`() = testApplication {
        val response = client.get("/api/health")

        assertEquals(HttpStatusCode.OK, response.status)
        assertTrue(response.bodyAsText().contains("UP"))
    }

    @Test
    fun `test chat endpoint with empty message returns bad request`() = testApplication {
        val response = client.post("/api/chat") {
            contentType(ContentType.Application.Json)
            setBody("""{"message": ""}""")
        }

        assertEquals(HttpStatusCode.BadRequest, response.status)
        assertTrue(response.bodyAsText().contains("Message cannot be empty"))
    }

    @Test
    fun `test chat endpoint with blank message returns bad request`() = testApplication {
        val response = client.post("/api/chat") {
            contentType(ContentType.Application.Json)
            setBody("""{"message": "   "}""")
        }

        assertEquals(HttpStatusCode.BadRequest, response.status)
        assertTrue(response.bodyAsText().contains("Message cannot be empty"))
    }
}
