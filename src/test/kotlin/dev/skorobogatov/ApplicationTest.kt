package dev.skorobogatov

import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.server.testing.*
import kotlin.test.*

/**
 * Integration tests for the application.
 * These tests verify basic application functionality without mocking ClaudeService.
 * More detailed API endpoint tests are in ChatRoutesTest with mocked service.
 */
class ApplicationTest {

    @Test
    fun `test health endpoint returns OK`() = testApplication {
        val response = client.get("/api/health")

        assertEquals(HttpStatusCode.OK, response.status)
        assertTrue(response.bodyAsText().contains("UP"))
    }

    @Test
    fun `test root returns content`() = testApplication {
        // testApplication follows redirects automatically
        val response = client.get("/")

        assertEquals(HttpStatusCode.OK, response.status)
        val body = response.bodyAsText()
        assertTrue(body.contains("Многоэкспертная консультация"))
    }

    @Test
    fun `test static CSS is accessible`() = testApplication {
        val response = client.get("/static/css/styles.css")

        assertEquals(HttpStatusCode.OK, response.status)
        val body = response.bodyAsText()
        assertTrue(body.contains("body"))
    }

    @Test
    fun `test static JS is accessible`() = testApplication {
        val response = client.get("/static/js/app.js")

        assertEquals(HttpStatusCode.OK, response.status)
        val body = response.bodyAsText()
        assertTrue(body.contains("ChatApp"))
    }
}
