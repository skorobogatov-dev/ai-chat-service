package dev.skorobogatov.services

import dev.skorobogatov.models.TranscriptionResult
import org.slf4j.LoggerFactory
import org.vosk.Model
import org.vosk.Recognizer
import java.io.File
import java.io.FileInputStream
import javax.sound.sampled.AudioInputStream
import javax.sound.sampled.AudioSystem

/**
 * Сервис для распознавания речи через Vosk
 */
class SpeechRecognitionService(
    private val modelPath: String
) {
    private val logger = LoggerFactory.getLogger(SpeechRecognitionService::class.java)
    private var model: Model? = null
    private val sampleRate = 16000.0f  // Vosk требует 16kHz

    /**
     * Инициализация Vosk модели
     */
    fun initialize() {
        try {
            logger.info("Initializing Vosk model from: $modelPath")
            val modelDir = File(modelPath)

            if (!modelDir.exists()) {
                throw IllegalStateException("Vosk model directory not found: $modelPath")
            }

            model = Model(modelPath)
            logger.info("Vosk model initialized successfully")
        } catch (e: Exception) {
            logger.error("Failed to initialize Vosk model: ${e.message}", e)
            throw e
        }
    }

    /**
     * Проверка, инициализирован ли сервис
     */
    fun isInitialized(): Boolean = model != null

    /**
     * Распознавание речи из WAV файла
     *
     * @param audioFile WAV файл с аудио (должен быть 16kHz, mono, 16-bit PCM)
     * @return Результат распознавания
     */
    fun transcribe(audioFile: File): TranscriptionResult {
        if (!isInitialized()) {
            throw IllegalStateException("Speech recognition service is not initialized. Call initialize() first.")
        }

        val startTime = System.currentTimeMillis()

        try {
            logger.info("Transcribing audio file: ${audioFile.name}")

            // Проверка формата файла
            if (!audioFile.name.endsWith(".wav", ignoreCase = true)) {
                throw IllegalArgumentException("Only WAV files are supported. File: ${audioFile.name}")
            }

            // Открыть аудио файл
            val audioInputStream = AudioSystem.getAudioInputStream(audioFile)
            val format = audioInputStream.format

            logger.debug("Audio format: sampleRate=${format.sampleRate}, channels=${format.channels}, sampleSize=${format.sampleSizeInBits}")

            // Проверить формат (Vosk требует 16kHz, mono)
            if (format.sampleRate !in 15000f..17000f) {
                logger.warn("Audio sample rate is ${format.sampleRate}Hz, Vosk expects 16000Hz. Quality may be reduced.")
            }

            if (format.channels != 1) {
                logger.warn("Audio has ${format.channels} channels, Vosk expects mono (1 channel). Quality may be reduced.")
            }

            // Создать recognizer для этого файла
            val recognizer = Recognizer(model, format.sampleRate)
            recognizer.setMaxAlternatives(0)  // Не нужны альтернативы
            recognizer.setWords(false)        // Не нужна разбивка на слова

            // Читать аудио и распознавать
            val buffer = ByteArray(4096)
            var bytesRead: Int
            var totalBytes = 0

            while (audioInputStream.read(buffer).also { bytesRead = it } >= 0) {
                totalBytes += bytesRead
                recognizer.acceptWaveForm(buffer, bytesRead)
            }

            // Получить финальный результат
            val resultJson = recognizer.finalResult
            logger.debug("Vosk result JSON: $resultJson")

            // Парсить результат (простой парсинг JSON вручную, чтобы не добавлять зависимость)
            val text = extractTextFromVoskJson(resultJson)

            val duration = System.currentTimeMillis() - startTime

            logger.info("Transcription completed in ${duration}ms. Text length: ${text.length} chars")

            audioInputStream.close()

            return TranscriptionResult(
                text = text,
                confidence = 1.0,  // Vosk не предоставляет confidence в простом режиме
                language = "ru",
                duration = duration
            )

        } catch (e: Exception) {
            logger.error("Transcription failed: ${e.message}", e)
            throw RuntimeException("Speech recognition failed: ${e.message}", e)
        }
    }

    /**
     * Извлечь текст из JSON ответа Vosk
     * Формат: {"text":"распознанный текст"}
     */
    private fun extractTextFromVoskJson(json: String): String {
        // Простой парсинг JSON для извлечения поля "text"
        val textPattern = """"text"\s*:\s*"([^"]*)"""".toRegex()
        val match = textPattern.find(json)

        return match?.groupValues?.get(1)?.trim() ?: ""
    }

    /**
     * Закрыть модель и освободить ресурсы
     */
    fun close() {
        model?.close()
        model = null
        logger.info("Speech recognition service closed")
    }
}
