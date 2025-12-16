package dev.skorobogatov.routes

import dev.skorobogatov.models.ClaudeMessage
import dev.skorobogatov.models.VoiceResponse
import dev.skorobogatov.services.*
import io.ktor.http.*
import io.ktor.http.content.*
import io.ktor.server.application.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import org.slf4j.LoggerFactory
import java.io.File

private val logger = LoggerFactory.getLogger("VoiceRoutes")

fun Route.voiceRoutes(
    speechRecognitionService: SpeechRecognitionService,
    audioConverterService: AudioConverterService,
    claudeService: ClaudeService,
    ollamaChatService: OllamaChatService,
    historyService: ConversationHistoryService
) {
    route("/api/voice") {
        /**
         * POST /api/voice - Голосовой запрос
         *
         * Принимает аудио файл (WebM/WAV), распознает речь, отправляет в LLM
         *
         * Form data:
         * - audio: файл (WebM/WAV)
         * - sessionId: ID сессии (опционально)
         * - provider: claude/ollama (по умолчанию: ollama)
         * - model: модель LLM (опционально)
         */
        post {
            val totalStartTime = System.currentTimeMillis()
            var audioFile: File? = null
            var convertedFile: File? = null

            try {
                // Проверить, инициализирован ли сервис распознавания
                if (!speechRecognitionService.isInitialized()) {
                    logger.error("Speech recognition service is not initialized")
                    call.respond(
                        HttpStatusCode.ServiceUnavailable,
                        mapOf("error" to "Speech recognition service is not available. Vosk model not loaded.")
                    )
                    return@post
                }

                // Парсинг multipart/form-data
                val multipart = call.receiveMultipart()

                var sessionId: String? = null
                var provider = "ollama"
                var model: String? = null

                multipart.forEachPart { part ->
                    when (part) {
                        is PartData.FileItem -> {
                            // Сохранить аудио файл во временную директорию
                            val fileName = part.originalFileName ?: "audio_${System.currentTimeMillis()}"
                            val extension = fileName.substringAfterLast(".", "webm")
                            audioFile = File.createTempFile("voice_", ".$extension")

                            logger.info("Receiving audio file: $fileName (${audioFile!!.name})")

                            part.streamProvider().use { input ->
                                audioFile!!.outputStream().buffered().use { output ->
                                    input.copyTo(output)
                                    output.flush()
                                }
                            }

                            logger.info("Audio file saved: ${audioFile!!.absolutePath} (${audioFile!!.length()} bytes)")
                        }
                        is PartData.FormItem -> {
                            when (part.name) {
                                "sessionId" -> sessionId = part.value
                                "provider" -> provider = part.value
                                "model" -> model = part.value
                            }
                        }
                        else -> {}
                    }
                    part.dispose()
                }

                // Проверка наличия аудио файла
                if (audioFile == null || !audioFile!!.exists()) {
                    logger.error("No audio file received")
                    call.respond(
                        HttpStatusCode.BadRequest,
                        mapOf("error" to "No audio file provided")
                    )
                    return@post
                }

                // Проверка размера файла
                if (audioFile!!.length() == 0L) {
                    logger.error("Received empty audio file")
                    call.respond(
                        HttpStatusCode.BadRequest,
                        mapOf("error" to "Received empty audio file")
                    )
                    return@post
                }

                // Проверка минимального размера (меньше 1KB подозрительно)
                if (audioFile!!.length() < 1024) {
                    logger.warn("Received very small audio file: ${audioFile!!.length()} bytes")
                    call.respond(
                        HttpStatusCode.BadRequest,
                        mapOf("error" to "Audio recording is too short. Please hold the button longer and speak clearly.")
                    )
                    return@post
                }

                // --- ЭТАП 1: Конвертация аудио (если нужно) ---
                var fileToTranscribe = audioFile!!

                if (audioConverterService.needsConversion(audioFile!!)) {
                    logger.info("Audio needs conversion: ${audioFile!!.extension} -> WAV")

                    convertedFile = audioConverterService.getOutputFile(audioFile!!)
                    val conversionSuccess = audioConverterService.convertToWav(audioFile!!, convertedFile!!)

                    if (!conversionSuccess || !convertedFile!!.exists()) {
                        logger.error("Audio conversion failed")
                        call.respond(
                            HttpStatusCode.InternalServerError,
                            mapOf("error" to "Audio conversion failed. Make sure FFmpeg is installed.")
                        )
                        return@post
                    }

                    fileToTranscribe = convertedFile!!
                    logger.info("Audio converted successfully: ${convertedFile!!.name} (${convertedFile!!.length()} bytes)")
                }

                // --- ЭТАП 2: Распознавание речи (STT) ---
                val transcriptionStartTime = System.currentTimeMillis()
                logger.info("Starting speech recognition...")

                val transcriptionResult = speechRecognitionService.transcribe(fileToTranscribe)
                val transcriptionTime = System.currentTimeMillis() - transcriptionStartTime

                logger.info("Speech recognition completed in ${transcriptionTime}ms: '${transcriptionResult.text}'")

                // Проверка результата
                if (transcriptionResult.text.isBlank()) {
                    logger.warn("Speech recognition returned empty text")
                    call.respond(
                        HttpStatusCode.OK,
                        mapOf(
                            "error" to "Could not recognize speech. Please speak clearly and try again.",
                            "transcribedText" to "",
                            "transcriptionTimeMs" to transcriptionTime
                        )
                    )
                    return@post
                }

                // --- ЭТАП 3: Отправка в LLM ---
                val llmStartTime = System.currentTimeMillis()
                logger.info("Sending transcribed text to LLM (provider: $provider, model: ${model ?: "default"})")

                // Получить или создать сессию
                val session = historyService.getOrCreateSession(sessionId)
                val isNewSession = sessionId == null

                // Добавить распознанный текст как сообщение пользователя
                historyService.addUserMessage(session.sessionId, transcriptionResult.text)

                // Получить все сообщения для отправки в API
                val allMessages = session.toClaudeMessages()

                // Определить, какой провайдер использовать
                val useOllama = provider.lowercase() == "ollama"
                val apiResponse = if (useOllama) {
                    logger.debug("Using Ollama for LLM response")
                    ollamaChatService.sendMessage(
                        messages = allMessages,
                        systemPrompt = null,
                        requestModel = model
                    )
                } else {
                    logger.debug("Using Claude for LLM response")
                    claudeService.sendMessage(allMessages, null, model)
                }

                val llmTime = System.currentTimeMillis() - llmStartTime
                logger.info("LLM response received in ${llmTime}ms")

                // Добавить ответ ассистента в историю
                historyService.addAssistantMessage(session.sessionId, apiResponse.response)

                // Сгенерировать название для нового диалога
                if (isNewSession && session.title == null) {
                    try {
                        val title = if (useOllama) {
                            ollamaChatService.generateConversationTitle(transcriptionResult.text)
                        } else {
                            claudeService.generateConversationTitle(transcriptionResult.text)
                        }
                        historyService.setConversationTitle(session.sessionId, title)
                        logger.debug("Generated title for new conversation: $title")
                    } catch (e: Exception) {
                        logger.warn("Failed to generate title: ${e.message}")
                    }
                }

                // --- ЭТАП 4: Формирование ответа ---
                val totalTime = System.currentTimeMillis() - totalStartTime

                val voiceResponse = VoiceResponse(
                    transcribedText = transcriptionResult.text,
                    response = apiResponse.response,
                    sessionId = session.sessionId,
                    provider = provider,
                    model = apiResponse.model,
                    transcriptionTimeMs = transcriptionTime,
                    llmResponseTimeMs = llmTime,
                    totalTimeMs = totalTime,
                    inputTokens = apiResponse.inputTokens,
                    outputTokens = apiResponse.outputTokens,
                    totalTokens = apiResponse.totalTokens,
                    historyCompressed = apiResponse.historyCompressed,
                    ragUsed = apiResponse.ragUsed,
                    ragChunksFound = apiResponse.ragChunksFound
                )

                logger.info("Voice request completed successfully in ${totalTime}ms")
                call.respond(HttpStatusCode.OK, voiceResponse)

            } catch (e: Exception) {
                logger.error("Voice request failed: ${e.message}", e)
                call.respond(
                    HttpStatusCode.InternalServerError,
                    mapOf("error" to (e.message ?: "Unknown error occurred"))
                )
            } finally {
                // Очистка временных файлов
                try {
                    audioFile?.delete()
                    convertedFile?.delete()
                } catch (e: Exception) {
                    logger.warn("Failed to cleanup temporary files: ${e.message}")
                }
            }
        }

        /**
         * GET /api/voice/status - Статус сервиса распознавания речи
         */
        get("/status") {
            val status = mapOf(
                "initialized" to speechRecognitionService.isInitialized(),
                "ffmpegAvailable" to audioConverterService.isFFmpegAvailable()
            )
            call.respond(HttpStatusCode.OK, status)
        }
    }
}
