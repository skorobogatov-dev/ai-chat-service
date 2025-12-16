package dev.skorobogatov.services

import org.slf4j.LoggerFactory
import java.io.File
import java.util.concurrent.TimeUnit

/**
 * Сервис для конвертации аудио файлов
 * Использует FFmpeg для конвертации WebM → WAV
 */
class AudioConverterService {
    private val logger = LoggerFactory.getLogger(AudioConverterService::class.java)

    /**
     * Конвертировать WebM/другой формат в WAV (16kHz, mono, 16-bit PCM)
     *
     * @param inputFile Входной файл (WebM, MP3, OGG и т.д.)
     * @param outputFile Выходной WAV файл
     * @return true если конвертация успешна, false иначе
     */
    fun convertToWav(inputFile: File, outputFile: File): Boolean {
        try {
            logger.info("Converting audio: ${inputFile.name} -> ${outputFile.name}")

            // Получить путь к FFmpeg
            val ffmpegPath = findFFmpegPath()
            if (ffmpegPath == null) {
                logger.error("FFmpeg is not available. Please install FFmpeg to use audio conversion.")
                return false
            }

            // Команда FFmpeg для конвертации в WAV 16kHz mono
            val command = listOf(
                ffmpegPath,
                "-i", inputFile.absolutePath,     // Входной файл
                "-ar", "16000",                   // Sample rate 16kHz (требование Vosk)
                "-ac", "1",                       // Моно (1 канал)
                "-sample_fmt", "s16",             // 16-bit PCM
                "-y",                             // Перезаписать выходной файл без подтверждения
                outputFile.absolutePath           // Выходной файл
            )

            logger.debug("Running FFmpeg command: ${command.joinToString(" ")}")

            // Запустить FFmpeg процесс
            val processBuilder = ProcessBuilder(command)
            processBuilder.redirectErrorStream(true)  // Объединить stdout и stderr

            val process = processBuilder.start()

            // Прочитать вывод (для отладки)
            val output = process.inputStream.bufferedReader().use { it.readText() }

            // Ждать завершения процесса (максимум 30 секунд)
            val exitCode = if (process.waitFor(30, TimeUnit.SECONDS)) {
                process.exitValue()
            } else {
                logger.error("FFmpeg process timed out")
                process.destroy()
                -1
            }

            if (exitCode == 0 && outputFile.exists()) {
                logger.info("Audio conversion successful: ${outputFile.name} (${outputFile.length()} bytes)")
                return true
            } else {
                logger.error("FFmpeg failed with exit code: $exitCode")
                logger.error("FFmpeg output: $output")
                return false
            }

        } catch (e: Exception) {
            logger.error("Audio conversion failed: ${e.message}", e)
            return false
        }
    }

    /**
     * Найти путь к FFmpeg
     */
    private fun findFFmpegPath(): String? {
        // Проверяем стандартные пути
        val possiblePaths = listOf(
            "/opt/homebrew/bin/ffmpeg",  // Homebrew на Apple Silicon
            "/usr/local/bin/ffmpeg",     // Homebrew на Intel Mac
            "/usr/bin/ffmpeg",           // Стандартный путь Linux
            "ffmpeg"                     // PATH
        )

        for (path in possiblePaths) {
            try {
                val process = ProcessBuilder(path, "-version").start()
                val exitCode = process.waitFor(2, TimeUnit.SECONDS)
                if (exitCode && process.exitValue() == 0) {
                    logger.info("Found FFmpeg at: $path")
                    return path
                }
            } catch (e: Exception) {
                // Продолжаем поиск
            }
        }

        logger.error("FFmpeg not found in any standard location")
        return null
    }

    /**
     * Проверить доступность FFmpeg
     */
    fun isFFmpegAvailable(): Boolean {
        return findFFmpegPath() != null
    }

    /**
     * Определить, нужна ли конвертация для файла
     *
     * @param file Аудио файл
     * @return true если файл нужно конвертировать (не WAV)
     */
    fun needsConversion(file: File): Boolean {
        val extension = file.extension.lowercase()
        return extension != "wav"
    }

    /**
     * Получить предполагаемый выходной файл для конвертации
     *
     * @param inputFile Входной файл
     * @return Выходной файл с расширением .wav
     */
    fun getOutputFile(inputFile: File): File {
        val nameWithoutExtension = inputFile.nameWithoutExtension
        val outputDir = inputFile.parentFile ?: File(".")
        return File(outputDir, "${nameWithoutExtension}_converted.wav")
    }
}
