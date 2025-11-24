package dev.skorobogatov.routes

import dev.skorobogatov.models.*
import dev.skorobogatov.services.OllamaService
import dev.skorobogatov.services.TextChunkerService
import io.ktor.http.*
import io.ktor.http.content.*
import io.ktor.server.application.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.slf4j.LoggerFactory
import java.io.File
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

private val logger = LoggerFactory.getLogger("EmbeddingRoutes")

fun Route.embeddingRoutes(ollamaService: OllamaService, chunkerService: TextChunkerService) {
    route("/api/embeddings") {

        /**
         * Получить embedding для одного текста
         * POST /api/embeddings
         * Body: {"text": "текст для векторизации"}
         */
        post {
            try {
                val request = call.receive<EmbeddingRequest>()

                if (request.text.isBlank()) {
                    call.respond(HttpStatusCode.BadRequest, mapOf("error" to "Text cannot be empty"))
                    return@post
                }

                logger.info("Getting embedding for text: ${request.text.take(50)}...")
                val response = ollamaService.getEmbedding(request.text)

                call.respond(HttpStatusCode.OK, response)
            } catch (e: Exception) {
                logger.error("Error getting embedding", e)
                call.respond(
                    HttpStatusCode.InternalServerError,
                    mapOf("error" to (e.message ?: "Unknown error occurred"))
                )
            }
        }

        /**
         * Получить embeddings для нескольких текстов
         * POST /api/embeddings/batch
         * Body: {"texts": ["текст 1", "текст 2", "текст 3"]}
         */
        post("/batch") {
            try {
                val request = call.receive<BatchEmbeddingRequest>()

                if (request.texts.isEmpty()) {
                    call.respond(HttpStatusCode.BadRequest, mapOf("error" to "Texts list cannot be empty"))
                    return@post
                }

                if (request.texts.any { it.isBlank() }) {
                    call.respond(HttpStatusCode.BadRequest, mapOf("error" to "All texts must be non-empty"))
                    return@post
                }

                logger.info("Getting batch embeddings for ${request.texts.size} texts")
                val response = ollamaService.getBatchEmbeddings(request.texts)

                call.respond(HttpStatusCode.OK, response)
            } catch (e: Exception) {
                logger.error("Error getting batch embeddings", e)
                call.respond(
                    HttpStatusCode.InternalServerError,
                    mapOf("error" to (e.message ?: "Unknown error occurred"))
                )
            }
        }

        /**
         * Проверить статус подключения к Ollama
         * GET /api/embeddings/status
         */
        get("/status") {
            try {
                logger.info("Checking Ollama server status")
                val status = ollamaService.checkStatus()

                if (status.available) {
                    call.respond(HttpStatusCode.OK, status)
                } else {
                    call.respond(HttpStatusCode.ServiceUnavailable, status)
                }
            } catch (e: Exception) {
                logger.error("Error checking Ollama status", e)
                call.respond(
                    HttpStatusCode.InternalServerError,
                    mapOf("error" to (e.message ?: "Unknown error occurred"))
                )
            }
        }

        /**
         * Векторизация текста с автоматическим чанкованием
         * POST /api/embeddings/vectorize
         * Body: {"text": "большой текст...", "chunkSize": 750, "overlap": 75, "saveToFile": true}
         */
        post("/vectorize") {
            try {
                val request = call.receive<VectorizeTextRequest>()

                if (request.text.isBlank()) {
                    call.respond(HttpStatusCode.BadRequest, mapOf("error" to "Text cannot be empty"))
                    return@post
                }

                logger.info("Vectorizing text with automatic chunking: ${request.text.length} characters")

                val startTime = System.currentTimeMillis()

                // Разбиваем текст на чанки
                val chunks = chunkerService.chunkText(request.text, request.chunkSize, request.overlap)
                logger.info("Text split into ${chunks.size} chunks")

                // Векторизуем каждый чанк
                val vectorizedChunks = mutableListOf<VectorizedChunkInfo>()

                for (chunk in chunks) {
                    val chunkStartTime = System.currentTimeMillis()
                    val embeddingResponse = ollamaService.getEmbedding(chunk.text)

                    vectorizedChunks.add(
                        VectorizedChunkInfo(
                            chunkId = chunk.chunkId,
                            text = chunk.text,
                            startWord = chunk.startWord,
                            endWord = chunk.endWord,
                            estimatedTokens = chunk.estimatedTokens,
                            wordCount = chunk.wordCount,
                            embedding = embeddingResponse.embedding,
                            dimension = embeddingResponse.dimension,
                            processingTimeMs = System.currentTimeMillis() - chunkStartTime
                        )
                    )

                    logger.info("Vectorized chunk ${chunk.chunkId + 1}/${chunks.size}")
                }

                val totalProcessingTime = System.currentTimeMillis() - startTime

                // Создаем метаданные
                val metadata = VectorizationMetadata(
                    timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")),
                    totalChunks = vectorizedChunks.size,
                    chunkSizeTokens = request.chunkSize,
                    overlapTokens = request.overlap,
                    totalTextTokens = chunks.sumOf { it.estimatedTokens },
                    model = vectorizedChunks.firstOrNull()?.let { "nomic-embed-text" } ?: "unknown",
                    embeddingDimension = vectorizedChunks.firstOrNull()?.dimension ?: 0,
                    totalProcessingTimeMs = totalProcessingTime,
                    savedToFile = null
                )

                // Сохраняем в файл если требуется
                var savedFilePath: String? = null
                if (request.saveToFile) {
                    val outputDir = File("embeddings_output")
                    outputDir.mkdirs()

                    val fileName = request.outputFileName ?: "embeddings_${System.currentTimeMillis()}.json"
                    val outputFile = File(outputDir, fileName)

                    val result = VectorizeTextResponse(
                        metadata = metadata.copy(savedToFile = outputFile.absolutePath),
                        chunks = vectorizedChunks
                    )

                    val json = Json { prettyPrint = true }
                    outputFile.writeText(json.encodeToString(result))
                    savedFilePath = outputFile.absolutePath

                    logger.info("Saved vectorization result to: ${outputFile.absolutePath}")
                }

                val response = VectorizeTextResponse(
                    metadata = if (savedFilePath != null) metadata.copy(savedToFile = savedFilePath) else metadata,
                    chunks = vectorizedChunks
                )

                call.respond(HttpStatusCode.OK, response)

            } catch (e: Exception) {
                logger.error("Error vectorizing text", e)
                call.respond(
                    HttpStatusCode.InternalServerError,
                    mapOf("error" to (e.message ?: "Unknown error occurred"))
                )
            }
        }

        /**
         * Векторизация текста из загруженного файла
         * POST /api/embeddings/vectorize-file
         * Form data: file (text file), chunkSize (optional), overlap (optional), saveToFile (optional)
         */
        post("/vectorize-file") {
            try {
                val multipart = call.receiveMultipart()
                var fileText: String? = null
                var chunkSize = 750
                var overlap = 75
                var saveToFile = true
                var originalFileName: String? = null

                multipart.forEachPart { part ->
                    when (part) {
                        is PartData.FileItem -> {
                            originalFileName = part.originalFileName
                            fileText = part.streamProvider().bufferedReader().use { it.readText() }
                            logger.info("Received file: $originalFileName, size: ${fileText?.length ?: 0} characters")
                        }
                        is PartData.FormItem -> {
                            when (part.name) {
                                "chunkSize" -> chunkSize = part.value.toIntOrNull() ?: 750
                                "overlap" -> overlap = part.value.toIntOrNull() ?: 75
                                "saveToFile" -> saveToFile = part.value.toBooleanStrictOrNull() ?: true
                            }
                        }
                        else -> {}
                    }
                    part.dispose()
                }

                if (fileText.isNullOrBlank()) {
                    call.respond(HttpStatusCode.BadRequest, mapOf("error" to "No file or empty file provided"))
                    return@post
                }

                logger.info("Vectorizing file: $originalFileName with automatic chunking")

                val startTime = System.currentTimeMillis()

                // Разбиваем текст на чанки
                val chunks = chunkerService.chunkText(fileText!!, chunkSize, overlap)
                logger.info("File text split into ${chunks.size} chunks")

                // Векторизуем каждый чанк
                val vectorizedChunks = mutableListOf<VectorizedChunkInfo>()

                for (chunk in chunks) {
                    val chunkStartTime = System.currentTimeMillis()
                    val embeddingResponse = ollamaService.getEmbedding(chunk.text)

                    vectorizedChunks.add(
                        VectorizedChunkInfo(
                            chunkId = chunk.chunkId,
                            text = chunk.text,
                            startWord = chunk.startWord,
                            endWord = chunk.endWord,
                            estimatedTokens = chunk.estimatedTokens,
                            wordCount = chunk.wordCount,
                            embedding = embeddingResponse.embedding,
                            dimension = embeddingResponse.dimension,
                            processingTimeMs = System.currentTimeMillis() - chunkStartTime
                        )
                    )

                    logger.info("Vectorized chunk ${chunk.chunkId + 1}/${chunks.size}")
                }

                val totalProcessingTime = System.currentTimeMillis() - startTime

                // Создаем метаданные
                val metadata = VectorizationMetadata(
                    timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")),
                    totalChunks = vectorizedChunks.size,
                    chunkSizeTokens = chunkSize,
                    overlapTokens = overlap,
                    totalTextTokens = chunks.sumOf { it.estimatedTokens },
                    model = "nomic-embed-text",
                    embeddingDimension = vectorizedChunks.firstOrNull()?.dimension ?: 0,
                    totalProcessingTimeMs = totalProcessingTime,
                    savedToFile = null
                )

                // Сохраняем в файл если требуется
                var savedFilePath: String? = null
                if (saveToFile) {
                    val outputDir = File("embeddings_output")
                    outputDir.mkdirs()

                    val baseFileName = originalFileName?.substringBeforeLast(".") ?: "file"
                    val fileName = "${baseFileName}_embeddings_${System.currentTimeMillis()}.json"
                    val outputFile = File(outputDir, fileName)

                    val result = VectorizeTextResponse(
                        metadata = metadata.copy(savedToFile = outputFile.absolutePath),
                        chunks = vectorizedChunks
                    )

                    val json = Json { prettyPrint = true }
                    outputFile.writeText(json.encodeToString(result))
                    savedFilePath = outputFile.absolutePath

                    logger.info("Saved vectorization result to: ${outputFile.absolutePath}")
                }

                val response = VectorizeTextResponse(
                    metadata = if (savedFilePath != null) metadata.copy(savedToFile = savedFilePath) else metadata,
                    chunks = vectorizedChunks
                )

                call.respond(HttpStatusCode.OK, response)

            } catch (e: Exception) {
                logger.error("Error vectorizing file", e)
                call.respond(
                    HttpStatusCode.InternalServerError,
                    mapOf("error" to (e.message ?: "Unknown error occurred"))
                )
            }
        }
    }
}
