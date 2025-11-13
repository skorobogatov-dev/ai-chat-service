package dev.skorobogatov.services

import dev.skorobogatov.models.ClaudeApiResponse
import dev.skorobogatov.models.ClaudeContent
import dev.skorobogatov.models.ClaudeMessage
import dev.skorobogatov.models.ClaudeUsage
import io.ktor.client.*
import io.ktor.client.engine.mock.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.client.request.*
import io.ktor.http.*
import io.ktor.http.content.*
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
        val mockResponse = ClaudeApiResponse(
            id = "msg_123",
            type = "message",
            role = "assistant",
            content = listOf(ClaudeContent(type = "text", text = "Hello from Claude!")),
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
        val result = claudeService.sendMessage(listOf(ClaudeMessage(role = "user", content = "Hello")))

        // Then
        assertEquals("Hello from Claude!", result.response)
        assertEquals("claude-sonnet-4-20250514", result.model)
        assertEquals(10, result.inputTokens)
        assertEquals(20, result.outputTokens)
        assertEquals(30, result.totalTokens)
        assertTrue(result.responseTimeMs >= 0)

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
            claudeService.sendMessage(listOf(ClaudeMessage(role = "user", content = "Hello")))
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
            claudeService.sendMessage(listOf(ClaudeMessage(role = "user", content = "Hello")))
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
            claudeService.sendMessage(listOf(ClaudeMessage(role = "user", content = "Hello")))
        }

        assertTrue(exception.message?.contains("Failed to get response from AI") == true)

        httpClient.close()
    }

    @Test
    fun `test message formatting in request`() = runBlocking {
        // Given
        var capturedRequestBody: String? = null

        val mockResponse = ClaudeApiResponse(
            id = "msg_123",
            type = "message",
            role = "assistant",
            content = listOf(ClaudeContent(type = "text", text = "Response")),
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
        claudeService.sendMessage(listOf(ClaudeMessage(role = "user", content = "Test message")))

        // Then
        assertNotNull(capturedRequestBody)

        httpClient.close()
    }

    @Test
    fun `test using custom model in request`() = runBlocking {
        // Given
        var capturedModel: String? = null

        val mockResponse = ClaudeApiResponse(
            id = "msg_123",
            type = "message",
            role = "assistant",
            content = listOf(ClaudeContent(type = "text", text = "Response from Haiku")),
            model = "claude-3-haiku-20240307",
            stop_reason = "end_turn",
            usage = ClaudeUsage(input_tokens = 10, output_tokens = 20)
        )

        val mockEngine = MockEngine { request ->
            // Capture the request body to verify the model
            val bodyContent = (request.body as OutgoingContent.ByteArrayContent)
                .bytes().toString(Charsets.UTF_8)

            // Parse JSON to extract model
            if (bodyContent.contains("claude-3-haiku-20240307")) {
                capturedModel = "claude-3-haiku-20240307"
            }

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
            model = "claude-sonnet-4-20250514", // Default model
            maxTokens = 1024
        )

        // When - using custom model in request
        val result = claudeService.sendMessage(
            messages = listOf(ClaudeMessage(role = "user", content = "Hello")),
            requestModel = "claude-3-haiku-20240307" // Override with Haiku
        )

        // Then
        assertEquals("Response from Haiku", result.response)
        assertEquals("claude-3-haiku-20240307", result.model)
        assertEquals(10, result.inputTokens)
        assertEquals(20, result.outputTokens)
        assertEquals("claude-3-haiku-20240307", capturedModel)

        httpClient.close()
    }

    @Test
    fun `test using default model when no custom model specified`() = runBlocking {
        // Given
        var capturedModel: String? = null

        val mockResponse = ClaudeApiResponse(
            id = "msg_123",
            type = "message",
            role = "assistant",
            content = listOf(ClaudeContent(type = "text", text = "Response from default Sonnet")),
            model = "claude-sonnet-4-20250514",
            stop_reason = "end_turn",
            usage = ClaudeUsage(input_tokens = 10, output_tokens = 20)
        )

        val mockEngine = MockEngine { request ->
            val bodyContent = (request.body as OutgoingContent.ByteArrayContent)
                .bytes().toString(Charsets.UTF_8)

            if (bodyContent.contains("claude-sonnet-4-20250514")) {
                capturedModel = "claude-sonnet-4-20250514"
            }

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
            model = "claude-sonnet-4-20250514", // Default model
            maxTokens = 1024
        )

        // When - not specifying custom model
        val result = claudeService.sendMessage(listOf(ClaudeMessage(role = "user", content = "Hello")))

        // Then - should use default model
        assertEquals("Response from default Sonnet", result.response)
        assertEquals("claude-sonnet-4-20250514", result.model)
        assertEquals(10, result.inputTokens)
        assertEquals(20, result.outputTokens)
        assertEquals("claude-sonnet-4-20250514", capturedModel)

        httpClient.close()
    }

    @Test
    fun `test switching between different models`() = runBlocking {
        // Given
        val capturedModels = mutableListOf<String>()

        val mockEngine = MockEngine { request ->
            val bodyContent = (request.body as OutgoingContent.ByteArrayContent)
                .bytes().toString(Charsets.UTF_8)

            // Extract model from request
            when {
                bodyContent.contains("claude-sonnet-4-20250514") -> {
                    capturedModels.add("claude-sonnet-4-20250514")
                    val response = ClaudeApiResponse(
                        id = "msg_sonnet",
                        type = "message",
                        role = "assistant",
                        content = listOf(ClaudeContent(type = "text", text = "Response from Sonnet")),
                        model = "claude-sonnet-4-20250514",
                        stop_reason = "end_turn",
                        usage = ClaudeUsage(input_tokens = 10, output_tokens = 20)
                    )
                    respond(
                        content = ByteReadChannel(json.encodeToString(response)),
                        status = HttpStatusCode.OK,
                        headers = headersOf(HttpHeaders.ContentType, "application/json")
                    )
                }
                bodyContent.contains("claude-3-haiku-20240307") -> {
                    capturedModels.add("claude-3-haiku-20240307")
                    val response = ClaudeApiResponse(
                        id = "msg_haiku",
                        type = "message",
                        role = "assistant",
                        content = listOf(ClaudeContent(type = "text", text = "Response from Haiku")),
                        model = "claude-3-haiku-20240307",
                        stop_reason = "end_turn",
                        usage = ClaudeUsage(input_tokens = 10, output_tokens = 20)
                    )
                    respond(
                        content = ByteReadChannel(json.encodeToString(response)),
                        status = HttpStatusCode.OK,
                        headers = headersOf(HttpHeaders.ContentType, "application/json")
                    )
                }
                else -> error("Unexpected model in request")
            }
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

        // When - calling with different models
        val sonnetResult = claudeService.sendMessage(
            listOf(ClaudeMessage(role = "user", content = "Complex task")),
            requestModel = "claude-sonnet-4-20250514"
        )
        val haikuResult = claudeService.sendMessage(
            listOf(ClaudeMessage(role = "user", content = "Simple task")),
            requestModel = "claude-3-haiku-20240307"
        )

        // Then
        assertEquals("Response from Sonnet", sonnetResult.response)
        assertEquals("claude-sonnet-4-20250514", sonnetResult.model)
        assertEquals(10, sonnetResult.inputTokens)
        assertEquals(20, sonnetResult.outputTokens)

        assertEquals("Response from Haiku", haikuResult.response)
        assertEquals("claude-3-haiku-20240307", haikuResult.model)
        assertEquals(10, haikuResult.inputTokens)
        assertEquals(20, haikuResult.outputTokens)

        assertEquals(listOf("claude-sonnet-4-20250514", "claude-3-haiku-20240307"), capturedModels)

        httpClient.close()
    }
}
