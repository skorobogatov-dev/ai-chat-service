package dev.skorobogatov.services

import org.slf4j.LoggerFactory

/**
 * Сервис для разбиения текста на чанки с перекрытием
 */
class TextChunkerService(
    private val defaultChunkSize: Int = 750,
    private val defaultOverlap: Int = 75
) {
    private val logger = LoggerFactory.getLogger(TextChunkerService::class.java)

    data class TextChunk(
        val chunkId: Int,
        val text: String,
        val startWord: Int,
        val endWord: Int,
        val estimatedTokens: Int,
        val wordCount: Int
    )

    /**
     * Примерная оценка количества токенов в тексте
     * Для русского языка примерно 1 токен = 0.75 слова
     */
    fun estimateTokens(text: String): Int {
        val words = text.split(Regex("\\s+")).filter { it.isNotBlank() }
        return (words.size / 0.75).toInt()
    }

    /**
     * Проверяет, нужно ли разбивать текст на чанки
     */
    fun needsChunking(text: String, maxTokens: Int = 1000): Boolean {
        val estimatedTokens = estimateTokens(text)
        return estimatedTokens > maxTokens
    }

    /**
     * Разбивает текст на чанки с перекрытием
     *
     * @param text Исходный текст
     * @param chunkSize Размер чанка в токенах (по умолчанию 750)
     * @param overlap Размер перекрытия в токенах (по умолчанию 75)
     * @return Список чанков
     */
    fun chunkText(
        text: String,
        chunkSize: Int = defaultChunkSize,
        overlap: Int = defaultOverlap
    ): List<TextChunk> {
        val words = text.split(Regex("\\s+")).filter { it.isNotBlank() }

        if (words.isEmpty()) {
            logger.warn("Empty text provided for chunking")
            return emptyList()
        }

        val wordsPerChunk = (chunkSize * 0.75).toInt()
        val overlapWords = (overlap * 0.75).toInt()

        // Если текст меньше одного чанка, возвращаем его целиком
        if (words.size <= wordsPerChunk) {
            logger.debug("Text is small enough for single chunk: ${words.size} words")
            return listOf(
                TextChunk(
                    chunkId = 0,
                    text = words.joinToString(" "),
                    startWord = 0,
                    endWord = words.size,
                    estimatedTokens = estimateTokens(text),
                    wordCount = words.size
                )
            )
        }

        val chunks = mutableListOf<TextChunk>()
        var position = 0
        var chunkId = 0

        while (position < words.size) {
            val endPosition = minOf(position + wordsPerChunk, words.size)
            val chunkWords = words.subList(position, endPosition)
            val chunkText = chunkWords.joinToString(" ")
            val estimatedTokens = estimateTokens(chunkText)

            chunks.add(
                TextChunk(
                    chunkId = chunkId,
                    text = chunkText,
                    startWord = position,
                    endWord = endPosition,
                    estimatedTokens = estimatedTokens,
                    wordCount = chunkWords.size
                )
            )

            logger.debug("Created chunk $chunkId: $estimatedTokens tokens, ${chunkWords.size} words")

            chunkId++
            position = endPosition - overlapWords

            if (endPosition >= words.size) {
                break
            }
        }

        logger.info("Split text into ${chunks.size} chunks (total ${words.size} words, ~${estimateTokens(text)} tokens)")
        return chunks
    }
}
