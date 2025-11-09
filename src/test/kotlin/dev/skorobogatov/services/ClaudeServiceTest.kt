package dev.skorobogatov.services

import dev.skorobogatov.models.ClaudeApiResponse
import dev.skorobogatov.models.ClaudeContent
import dev.skorobogatov.models.ClaudeUsage
import io.ktor.client.*
import io.ktor.client.engine.mock.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.*
import io.ktor.utils.io.*
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlin.test.*

class ClaudeServiceTest {

    private val json = Json {
        prettyPrint = true
        isLenient = true
        ignoreUnknownKeys = true
    }

    @Test
    fun `test successful API response`() = runBlocking {
        // Given
        val expertResponseJson = """
        {
          "question": "Hello",
          "experts": [
            {
              "expertRole": "Business Manager",
              "expertName": "Алекс Петров",
              "response": "Business perspective response"
            },
            {
              "expertRole": "Software Developer",
              "expertName": "Дмитрий Козлов",
              "response": "Technical perspective response"
            }
          ]
        }
        """.trimIndent()

        val mockResponse = ClaudeApiResponse(
            id = "msg_123",
            type = "message",
            role = "assistant",
            content = listOf(ClaudeContent(type = "text", text = expertResponseJson)),
            model = "claude-sonnet-4-20250514",
            stop_reason = "end_turn",
            usage = ClaudeUsage(input_tokens = 10, output_tokens = 20)
        )

        val mockEngine = MockEngine { request ->
            // Verify request headers
            assertEquals("test-api-key", request.headers["x-api-key"])
            assertEquals("2023-06-01", request.headers["anthropic-version"])

            respond(
                content = ByteReadChannel(json.encodeToString(mockResponse)),
                status = HttpStatusCode.OK,
                headers = headersOf(HttpHeaders.ContentType, "application/json")
            )
        }

        val httpClient = HttpClient(mockEngine) {
            install(ContentNegotiation) {
                json(json)
            }
        }

        val claudeService = ClaudeService(
            httpClient = httpClient,
            apiKey = "test-api-key",
            apiUrl = "https://api.anthropic.com/v1/messages",
            model = "claude-sonnet-4-20250514",
            maxTokens = 1024
        )

        // When
        val result = claudeService.sendMessage("Hello")

        // Then
        assertEquals("Hello", result.question)
        assertEquals(2, result.experts.size)
        assertEquals("Business Manager", result.experts[0].expertRole)
        assertEquals("Алекс Петров", result.experts[0].expertName)
        assertEquals("claude-sonnet-4-20250514", result.model)

        httpClient.close()
    }

    @Test
    fun `test API error response`() = runBlocking {
        // Given
        val mockEngine = MockEngine { request ->
            respond(
                content = ByteReadChannel("""{"error": {"type": "invalid_request_error", "message": "Invalid API key"}}"""),
                status = HttpStatusCode.Unauthorized,
                headers = headersOf(HttpHeaders.ContentType, "application/json")
            )
        }

        val httpClient = HttpClient(mockEngine) {
            install(ContentNegotiation) {
                json(json)
            }
        }

        val claudeService = ClaudeService(
            httpClient = httpClient,
            apiKey = "invalid-key",
            apiUrl = "https://api.anthropic.com/v1/messages",
            model = "claude-sonnet-4-20250514",
            maxTokens = 1024
        )

        // When & Then
        val exception = assertFails {
            claudeService.sendMessage("Hello")
        }

        assertTrue(exception.message?.contains("Failed to get response from AI") == true)

        httpClient.close()
    }

    @Test
    fun `test empty content in API response throws exception`() = runBlocking {
        // Given
        val mockResponse = ClaudeApiResponse(
            id = "msg_123",
            type = "message",
            role = "assistant",
            content = emptyList(), // Empty content
            model = "claude-sonnet-4-20250514",
            stop_reason = "end_turn",
            usage = ClaudeUsage(input_tokens = 10, output_tokens = 0)
        )

        val mockEngine = MockEngine { request ->
            respond(
                content = ByteReadChannel(json.encodeToString(mockResponse)),
                status = HttpStatusCode.OK,
                headers = headersOf(HttpHeaders.ContentType, "application/json")
            )
        }

        val httpClient = HttpClient(mockEngine) {
            install(ContentNegotiation) {
                json(json)
            }
        }

        val claudeService = ClaudeService(
            httpClient = httpClient,
            apiKey = "test-api-key",
            apiUrl = "https://api.anthropic.com/v1/messages",
            model = "claude-sonnet-4-20250514",
            maxTokens = 1024
        )

        // When & Then
        val exception = assertFails {
            claudeService.sendMessage("Hello")
        }

        assertTrue(exception.message?.contains("No content in Claude response") == true)

        httpClient.close()
    }

    @Test
    fun `test rate limit error`() = runBlocking {
        // Given
        val mockEngine = MockEngine { request ->
            respond(
                content = ByteReadChannel("""{"error": {"type": "rate_limit_error", "message": "Rate limit exceeded"}}"""),
                status = HttpStatusCode.TooManyRequests,
                headers = headersOf(HttpHeaders.ContentType, "application/json")
            )
        }

        val httpClient = HttpClient(mockEngine) {
            install(ContentNegotiation) {
                json(json)
            }
        }

        val claudeService = ClaudeService(
            httpClient = httpClient,
            apiKey = "test-api-key",
            apiUrl = "https://api.anthropic.com/v1/messages",
            model = "claude-sonnet-4-20250514",
            maxTokens = 1024
        )

        // When & Then
        val exception = assertFails {
            claudeService.sendMessage("Hello")
        }

        assertTrue(exception.message?.contains("Failed to get response from AI") == true)

        httpClient.close()
    }

    @Test
    fun `test message formatting in request`() = runBlocking {
        // Given
        var capturedRequestBody: String? = null

        val expertResponseJson = """
        {
          "question": "Test message",
          "experts": [
            {
              "expertRole": "Test Expert",
              "expertName": "Test Name",
              "response": "Test response"
            }
          ]
        }
        """.trimIndent()

        val mockResponse = ClaudeApiResponse(
            id = "msg_123",
            type = "message",
            role = "assistant",
            content = listOf(ClaudeContent(type = "text", text = expertResponseJson)),
            model = "claude-sonnet-4-20250514",
            stop_reason = "end_turn",
            usage = ClaudeUsage(input_tokens = 10, output_tokens = 20)
        )

        val mockEngine = MockEngine { request ->
            capturedRequestBody = request.body.toString()

            respond(
                content = ByteReadChannel(json.encodeToString(mockResponse)),
                status = HttpStatusCode.OK,
                headers = headersOf(HttpHeaders.ContentType, "application/json")
            )
        }

        val httpClient = HttpClient(mockEngine) {
            install(ContentNegotiation) {
                json(json)
            }
        }

        val claudeService = ClaudeService(
            httpClient = httpClient,
            apiKey = "test-api-key",
            apiUrl = "https://api.anthropic.com/v1/messages",
            model = "claude-sonnet-4-20250514",
            maxTokens = 1024
        )

        // When
        claudeService.sendMessage("Test message")

        // Then
        assertNotNull(capturedRequestBody)

        httpClient.close()
    }
}
