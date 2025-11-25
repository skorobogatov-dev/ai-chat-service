package dev.skorobogatov.services

import dev.skorobogatov.models.*
import kotlinx.serialization.json.Json
import org.slf4j.LoggerFactory
import java.io.File
import kotlin.math.sqrt

/**
 * Сервис для работы с векторным хранилищем
 * Загружает векторизованные документы из embeddings_output и выполняет поиск похожих чанков
 */
class VectorStoreService(
    private val storageDirectory: String = "embeddings_output"
) {
    private val logger = LoggerFactory.getLogger(VectorStoreService::class.java)
    private val json = Json {
        ignoreUnknownKeys = true
        prettyPrint = true
    }

    // In-memory индекс загруженных документов
    private val documentsIndex = mutableMapOf<String, VectorizeTextResponse>()

    init {
        loadAllDocuments()
    }

    /**
     * Загружает все векторизованные документы из директории
     */
    private fun loadAllDocuments() {
        val dir = File(storageDirectory)
        if (!dir.exists()) {
            logger.warn("Storage directory does not exist: $storageDirectory")
            dir.mkdirs()
            logger.info("Created storage directory: $storageDirectory")
            return
        }

        val jsonFiles = dir.listFiles { file -> file.extension == "json" }
        if (jsonFiles.isNullOrEmpty()) {
            logger.info("No vectorized documents found in $storageDirectory")
            return
        }

        logger.info("Loading ${jsonFiles.size} vectorized documents from $storageDirectory")

        jsonFiles.forEach { file ->
            try {
                val content = file.readText()
                val document = json.decodeFromString<VectorizeTextResponse>(content)
                documentsIndex[file.name] = document
                logger.debug("Loaded document: ${file.name} with ${document.chunks.size} chunks")
            } catch (e: Exception) {
                logger.error("Failed to load document ${file.name}: ${e.message}", e)
            }
        }

        logger.info("Successfully loaded ${documentsIndex.size} documents with ${getTotalChunksCount()} total chunks")
    }

    /**
     * Перезагружает все документы из директории
     */
    fun reloadIndex() {
        logger.info("Reloading vector store index")
        documentsIndex.clear()
        loadAllDocuments()
    }

    /**
     * Возвращает общее количество чанков
     */
    fun getTotalChunksCount(): Int {
        return documentsIndex.values.sumOf { it.chunks.size }
    }

    /**
     * Возвращает количество документов
     */
    fun getDocumentsCount(): Int {
        return documentsIndex.size
    }

    /**
     * Вычисляет косинусное сходство между двумя векторами
     */
    private fun cosineSimilarity(vec1: List<Double>, vec2: List<Double>): Double {
        if (vec1.size != vec2.size) {
            throw IllegalArgumentException("Vectors must have the same dimension")
        }

        var dotProduct = 0.0
        var norm1 = 0.0
        var norm2 = 0.0

        for (i in vec1.indices) {
            dotProduct += vec1[i] * vec2[i]
            norm1 += vec1[i] * vec1[i]
            norm2 += vec2[i] * vec2[i]
        }

        return if (norm1 == 0.0 || norm2 == 0.0) {
            0.0
        } else {
            dotProduct / (sqrt(norm1) * sqrt(norm2))
        }
    }

    /**
     * Ищет наиболее похожие чанки для заданного вектора запроса
     *
     * @param queryEmbedding Вектор запроса
     * @param topK Количество результатов
     * @param minSimilarity Минимальное значение сходства (0.0 - 1.0)
     * @return Список найденных чанков с метриками сходства
     */
    fun searchSimilarChunks(
        queryEmbedding: List<Double>,
        topK: Int = 5,
        minSimilarity: Double = 0.0
    ): List<SearchResultWithMetadata> {
        logger.debug("Searching for top $topK similar chunks (minSimilarity: $minSimilarity)")

        if (documentsIndex.isEmpty()) {
            logger.warn("No documents in index")
            return emptyList()
        }

        val allResults = mutableListOf<SearchResultWithMetadata>()

        // Проходим по всем документам и их чанкам
        documentsIndex.forEach { (fileName, document) ->
            document.chunks.forEach { chunk ->
                try {
                    val similarity = cosineSimilarity(queryEmbedding, chunk.embedding)

                    if (similarity >= minSimilarity) {
                        allResults.add(
                            SearchResultWithMetadata(
                                fileName = fileName,
                                chunkInfo = chunk,
                                similarity = similarity,
                                metadata = document.metadata
                            )
                        )
                    }
                } catch (e: Exception) {
                    logger.error("Error calculating similarity for chunk ${chunk.chunkId} in $fileName: ${e.message}")
                }
            }
        }

        // Сортируем по убыванию сходства и берем topK
        val topResults = allResults
            .sortedByDescending { it.similarity }
            .take(topK)

        logger.info("Found ${topResults.size} similar chunks (total candidates: ${allResults.size})")

        topResults.forEachIndexed { index, result ->
            logger.debug("Result ${index + 1}: ${result.fileName}, chunk ${result.chunkInfo.chunkId}, similarity: ${"%.4f".format(result.similarity)}")
        }

        return topResults
    }

    /**
     * Получает список всех документов
     */
    fun getAllDocuments(): List<DocumentSummary> {
        return documentsIndex.map { (fileName, document) ->
            DocumentSummary(
                fileName = fileName,
                chunksCount = document.chunks.size,
                metadata = document.metadata
            )
        }
    }

    /**
     * Удаляет документ из индекса и файловой системы
     */
    fun deleteDocument(fileName: String): Boolean {
        if (!documentsIndex.containsKey(fileName)) {
            logger.warn("Document not found in index: $fileName")
            return false
        }

        // Удаляем из индекса
        documentsIndex.remove(fileName)

        // Удаляем файл
        val file = File(storageDirectory, fileName)
        if (file.exists()) {
            file.delete()
            logger.info("Deleted document file: $fileName")
        }

        return true
    }
}

/**
 * Результат поиска с метаданными
 */
data class SearchResultWithMetadata(
    val fileName: String,
    val chunkInfo: VectorizedChunkInfo,
    val similarity: Double,
    val metadata: VectorizationMetadata
)

/**
 * Краткая информация о документе
 */
data class DocumentSummary(
    val fileName: String,
    val chunksCount: Int,
    val metadata: VectorizationMetadata
)
