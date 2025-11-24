package dev.skorobogatov.services

import dev.skorobogatov.models.EmbeddingResponse
import dev.skorobogatov.models.BatchEmbeddingResponse
import dev.skorobogatov.models.OllamaEmbeddingRequest
import dev.skorobogatov.models.OllamaEmbeddingResponse
import dev.skorobogatov.models.OllamaStatus
import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import org.slf4j.LoggerFactory

/**
 * Сервис для работы с локальной Ollama для векторизации текста
 */
class OllamaService(
    private val httpClient: HttpClient,
    private val baseUrl: String,
    private val model: String
) {
    private val logger = LoggerFactory.getLogger(OllamaService::class.java)
    private val embeddingsUrl = "$baseUrl/api/embeddings"

    /**
     * Получить embedding для одного текста
     */
    suspend fun getEmbedding(text: String): EmbeddingResponse {
        val startTime = System.currentTimeMillis()
        logger.debug("Getting embedding for text: ${text.take(50)}...")

        return try {
            val request = OllamaEmbeddingRequest(
                model = model,
                prompt = text
            )

            val response: HttpResponse = httpClient.post(embeddingsUrl) {
                contentType(ContentType.Application.Json)
                setBody(request)
            }

            val endTime = System.currentTimeMillis()
            val processingTime = endTime - startTime

            when (response.status) {
                HttpStatusCode.OK -> {
                    val ollamaResponse: OllamaEmbeddingResponse = response.body()
                    val embedding = ollamaResponse.embedding

                    logger.debug("Successfully received embedding (dimension: ${embedding.size}, time: ${processingTime}ms)")

                    EmbeddingResponse(
                        embedding = embedding,
                        model = model,
                        dimension = embedding.size,
                        processingTimeMs = processingTime
                    )
                }
                else -> {
                    val errorBody = response.bodyAsText()
                    logger.error("Ollama API error: ${response.status} - $errorBody")
                    throw Exception("Ollama API error: ${response.status} - $errorBody")
                }
            }
        } catch (e: Exception) {
            logger.error("Error calling Ollama API", e)
            throw Exception("Failed to get embedding from Ollama: ${e.message}", e)
        }
    }

    /**
     * Получить embeddings для нескольких текстов
     */
    suspend fun getBatchEmbeddings(texts: List<String>): BatchEmbeddingResponse {
        val startTime = System.currentTimeMillis()
        logger.debug("Getting embeddings for ${texts.size} texts")

        val embeddings = mutableListOf<List<Double>>()
        var dimension = 0

        try {
            for ((index, text) in texts.withIndex()) {
                logger.debug("Processing text ${index + 1}/${texts.size}")

                val request = OllamaEmbeddingRequest(
                    model = model,
                    prompt = text
                )

                val response: HttpResponse = httpClient.post(embeddingsUrl) {
                    contentType(ContentType.Application.Json)
                    setBody(request)
                }

                when (response.status) {
                    HttpStatusCode.OK -> {
                        val ollamaResponse: OllamaEmbeddingResponse = response.body()
                        embeddings.add(ollamaResponse.embedding)
                        if (dimension == 0) {
                            dimension = ollamaResponse.embedding.size
                        }
                    }
                    else -> {
                        val errorBody = response.bodyAsText()
                        logger.error("Ollama API error for text ${index + 1}: ${response.status} - $errorBody")
                        throw Exception("Ollama API error: ${response.status} - $errorBody")
                    }
                }
            }

            val endTime = System.currentTimeMillis()
            val processingTime = endTime - startTime

            logger.debug("Successfully received ${embeddings.size} embeddings (dimension: $dimension, total time: ${processingTime}ms)")

            return BatchEmbeddingResponse(
                embeddings = embeddings,
                model = model,
                dimension = dimension,
                count = embeddings.size,
                processingTimeMs = processingTime
            )
        } catch (e: Exception) {
            logger.error("Error calling Ollama API for batch embeddings", e)
            throw Exception("Failed to get batch embeddings from Ollama: ${e.message}", e)
        }
    }

    /**
     * Проверить доступность Ollama сервера
     */
    suspend fun checkStatus(): OllamaStatus {
        logger.debug("Checking Ollama server status at $baseUrl")

        return try {
            // Пробуем получить embedding для тестового текста
            val testRequest = OllamaEmbeddingRequest(
                model = model,
                prompt = "test"
            )

            val response: HttpResponse = httpClient.post(embeddingsUrl) {
                contentType(ContentType.Application.Json)
                setBody(testRequest)
            }

            when (response.status) {
                HttpStatusCode.OK -> {
                    logger.debug("Ollama server is available")
                    OllamaStatus(
                        available = true,
                        url = baseUrl,
                        model = model
                    )
                }
                else -> {
                    val errorBody = response.bodyAsText()
                    logger.warn("Ollama server returned error: ${response.status} - $errorBody")
                    OllamaStatus(
                        available = false,
                        url = baseUrl,
                        model = model,
                        error = "Server returned ${response.status}: $errorBody"
                    )
                }
            }
        } catch (e: Exception) {
            logger.error("Failed to connect to Ollama server", e)
            OllamaStatus(
                available = false,
                url = baseUrl,
                model = model,
                error = "Connection failed: ${e.message}"
            )
        }
    }
}
