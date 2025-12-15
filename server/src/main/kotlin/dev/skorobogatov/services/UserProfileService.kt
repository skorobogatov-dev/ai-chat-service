package dev.skorobogatov.services

import dev.skorobogatov.models.*
import kotlinx.serialization.json.Json
import org.slf4j.LoggerFactory
import java.io.File

/**
 * Сервис для работы с профилем пользователя
 * Загружает профиль из JSON файла и генерирует персонализированный системный промпт
 */
class UserProfileService(
    private val profilePath: String = "user_profile.json"
) {
    private val logger = LoggerFactory.getLogger(UserProfileService::class.java)
    private val json = Json {
        prettyPrint = true
        isLenient = true
        ignoreUnknownKeys = true
    }

    private var userProfile: UserProfile? = null

    init {
        loadProfile()
    }

    /**
     * Загрузить профиль пользователя из JSON файла
     */
    fun loadProfile(): UserProfile? {
        return try {
            val file = File(profilePath)
            if (!file.exists()) {
                logger.warn("User profile file not found at: $profilePath. Using default profile.")
                userProfile = null
                return null
            }

            val content = file.readText()
            userProfile = json.decodeFromString<UserProfile>(content)
            logger.info("User profile loaded successfully: ${userProfile?.name ?: "Anonymous"}")
            userProfile
        } catch (e: Exception) {
            logger.error("Failed to load user profile from $profilePath: ${e.message}", e)
            userProfile = null
            null
        }
    }

    /**
     * Получить текущий профиль пользователя
     */
    fun getProfile(): UserProfile? = userProfile

    /**
     * Сформировать персонализированный системный промпт на основе профиля пользователя
     */
    fun generatePersonalizedSystemPrompt(basePrompt: String? = null): String {
        val profile = userProfile ?: return basePrompt ?: ""

        val personalizedContext = buildString {
            appendLine("# ИНФОРМАЦИЯ О ПОЛЬЗОВАТЕЛЕ")
            appendLine()

            // Базовая информация
            profile.name?.let { appendLine("Имя пользователя: $it") }
            profile.role?.let { appendLine("Роль/профессия: $it") }
            profile.location?.let { appendLine("Местоположение: $it") }
            profile.timezone?.let { appendLine("Часовой пояс: $it") }
            appendLine()

            // Стиль общения
            profile.communicationStyle?.let { style ->
                appendLine("## Предпочтения в общении:")
                appendLine("- Длина ответов: ${getResponseLengthDescription(style.responseLength)}")
                appendLine("- Формальность: ${getFormalityDescription(style.formality)}")
                appendLine("- Технический уровень: ${getTechnicalLevelDescription(style.technicalLevel)}")
                if (style.useEmojis) {
                    appendLine("- Можно использовать эмодзи в ответах")
                }
                style.preferredTone?.let { appendLine("- Предпочитаемый тон: $it") }
                appendLine()
            }

            // Рабочий контекст
            profile.workContext?.let { context ->
                if (context.currentProjects.isNotEmpty() || context.goals.isNotEmpty() || context.challenges.isNotEmpty()) {
                    appendLine("## Рабочий контекст:")
                    if (context.currentProjects.isNotEmpty()) {
                        appendLine("Текущие проекты: ${context.currentProjects.joinToString(", ")}")
                    }
                    if (context.goals.isNotEmpty()) {
                        appendLine("Цели: ${context.goals.joinToString(", ")}")
                    }
                    if (context.challenges.isNotEmpty()) {
                        appendLine("Текущие вызовы: ${context.challenges.joinToString(", ")}")
                    }
                    context.workingHours?.let { appendLine("Рабочее время: $it") }
                    context.notes?.let { appendLine("Заметки: $it") }
                    appendLine()
                }
            }

            // Технический стек
            profile.technicalStack?.let { stack ->
                if (stack.programmingLanguages.isNotEmpty() || stack.frameworks.isNotEmpty()) {
                    appendLine("## Технический стек:")
                    if (stack.programmingLanguages.isNotEmpty()) {
                        appendLine("Языки программирования: ${stack.programmingLanguages.joinToString(", ")}")
                    }
                    if (stack.frameworks.isNotEmpty()) {
                        appendLine("Фреймворки: ${stack.frameworks.joinToString(", ")}")
                    }
                    if (stack.tools.isNotEmpty()) {
                        appendLine("Инструменты: ${stack.tools.joinToString(", ")}")
                    }
                    stack.preferredCodingStyle?.let { appendLine("Стиль кодирования: $it") }
                    stack.operatingSystem?.let { appendLine("ОС: $it") }
                    stack.ide?.let { appendLine("IDE: $it") }
                    appendLine()
                }
            }

            // Личные интересы
            profile.personalInterests?.let { interests ->
                val hasInterests = interests.hobbies.isNotEmpty() ||
                        interests.favoriteBooks.isNotEmpty() ||
                        interests.favoriteMovies.isNotEmpty() ||
                        interests.musicGenres.isNotEmpty()

                if (hasInterests) {
                    appendLine("## Личные интересы:")
                    if (interests.hobbies.isNotEmpty()) {
                        appendLine("Хобби: ${interests.hobbies.joinToString(", ")}")
                    }
                    if (interests.favoriteBooks.isNotEmpty()) {
                        appendLine("Любимые книги: ${interests.favoriteBooks.joinToString(", ")}")
                    }
                    if (interests.favoriteMovies.isNotEmpty()) {
                        appendLine("Любимые фильмы: ${interests.favoriteMovies.joinToString(", ")}")
                    }
                    if (interests.favoriteTvShows.isNotEmpty()) {
                        appendLine("Любимые сериалы: ${interests.favoriteTvShows.joinToString(", ")}")
                    }
                    if (interests.musicGenres.isNotEmpty()) {
                        appendLine("Музыкальные жанры: ${interests.musicGenres.joinToString(", ")}")
                    }
                    if (interests.favoriteArtists.isNotEmpty()) {
                        appendLine("Любимые исполнители: ${interests.favoriteArtists.joinToString(", ")}")
                    }
                    if (interests.sports.isNotEmpty()) {
                        appendLine("Спорт: ${interests.sports.joinToString(", ")}")
                    }
                    if (interests.learningGoals.isNotEmpty()) {
                        appendLine("Цели обучения: ${interests.learningGoals.joinToString(", ")}")
                    }
                    if (interests.favoriteTopics.isNotEmpty()) {
                        appendLine("Любимые темы: ${interests.favoriteTopics.joinToString(", ")}")
                    }
                    if (interests.travelDestinations.isNotEmpty()) {
                        appendLine("Места для путешествий: ${interests.travelDestinations.joinToString(", ")}")
                    }
                    if (interests.favoriteFood.isNotEmpty()) {
                        appendLine("Любимая еда: ${interests.favoriteFood.joinToString(", ")}")
                    }
                    if (interests.pets.isNotEmpty()) {
                        appendLine("Питомцы: ${interests.pets.joinToString(", ")}")
                    }
                    appendLine()
                }
            }

            // Дополнительная информация
            profile.additionalInfo?.let {
                appendLine("## Дополнительная информация:")
                appendLine(it)
                appendLine()
            }

            appendLine("---")
            appendLine()

            // Инструкции по использованию профиля
            appendLine("Используй эту информацию, чтобы:")
            profile.name?.let {
                appendLine("- Обращаться к пользователю по имени ($it)")
            }
            appendLine("- Адаптировать стиль и формат ответов под предпочтения")
            appendLine("- Давать релевантные примеры и рекомендации на основе технического стека")
            appendLine("- Учитывать контекст текущих проектов и целей")
            appendLine("- Использовать культурные отсылки к любимым книгам, фильмам, музыке для лучшего взаимопонимания")
            appendLine()
        }

        return if (basePrompt.isNullOrBlank()) {
            personalizedContext
        } else {
            "$personalizedContext\n$basePrompt"
        }
    }

    /**
     * Проверить, загружен ли профиль
     */
    fun hasProfile(): Boolean = userProfile != null

    private fun getResponseLengthDescription(length: ResponseLength): String = when (length) {
        ResponseLength.BRIEF -> "Краткие, по существу"
        ResponseLength.BALANCED -> "Сбалансированные (средняя длина)"
        ResponseLength.DETAILED -> "Подробные, с деталями и примерами"
    }

    private fun getFormalityDescription(formality: Formality): String = when (formality) {
        Formality.CASUAL -> "Неформальный, как с другом"
        Formality.FRIENDLY -> "Дружелюбный, но профессиональный"
        Formality.PROFESSIONAL -> "Строго профессиональный"
    }

    private fun getTechnicalLevelDescription(level: TechnicalLevel): String = when (level) {
        TechnicalLevel.BEGINNER -> "Начинающий (объясняй базовые концепции)"
        TechnicalLevel.INTERMEDIATE -> "Средний (можно использовать технические термины)"
        TechnicalLevel.ADVANCED -> "Продвинутый (глубокие технические детали)"
        TechnicalLevel.EXPERT -> "Эксперт (максимальная техническая глубина)"
    }
}
