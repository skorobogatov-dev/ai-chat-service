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

    @Test
    fun `test chat endpoint with temperature below 0 returns bad request`() = testApplication {
        val response = client.post("/api/chat") {
            contentType(ContentType.Application.Json)
            setBody("""{"message": "Hello", "temperature": -0.1}""")
        }

        assertEquals(HttpStatusCode.BadRequest, response.status)
        assertTrue(response.bodyAsText().contains("Temperature must be between 0.0 and 1.0"))
    }

    @Test
    fun `test chat endpoint with temperature above 1 returns bad request`() = testApplication {
        val response = client.post("/api/chat") {
            contentType(ContentType.Application.Json)
            setBody("""{"message": "Hello", "temperature": 1.1}""")
        }

        assertEquals(HttpStatusCode.BadRequest, response.status)
        assertTrue(response.bodyAsText().contains("Temperature must be between 0.0 and 1.0"))
    }

    @Test
    fun `test chat endpoint with valid temperature at boundary values`() = testApplication {
        val responseMin = client.post("/api/chat") {
            contentType(ContentType.Application.Json)
            setBody("""{"message": "Hello", "temperature": 0.0}""")
        }
        // Should not return BadRequest for validation
        assertTrue(responseMin.status != HttpStatusCode.BadRequest || !responseMin.bodyAsText().contains("Temperature must be between"))

        val responseMax = client.post("/api/chat") {
            contentType(ContentType.Application.Json)
            setBody("""{"message": "Hello", "temperature": 1.0}""")
        }
        // Should not return BadRequest for validation
        assertTrue(responseMax.status != HttpStatusCode.BadRequest || !responseMax.bodyAsText().contains("Temperature must be between"))
    }
}
