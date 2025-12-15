package dev.skorobogatov.models

import kotlinx.serialization.Serializable

/**
 * Модель профиля пользователя для персонализации ответов AI
 */
@Serializable
data class UserProfile(
    // Базовая информация
    val name: String? = null,
    val role: String? = null,
    val timezone: String? = null,
    val preferredLanguage: String? = "ru",
    val location: String? = null,
    val birthDate: String? = null,

    // Предпочтения в общении
    val communicationStyle: CommunicationStyle? = null,

    // Рабочие привычки и контекст
    val workContext: WorkContext? = null,

    // Технический стек
    val technicalStack: TechnicalStack? = null,

    // Личные интересы и увлечения
    val personalInterests: PersonalInterests? = null,

    // Дополнительная информация
    val additionalInfo: String? = null
)

/**
 * Стиль общения
 */
@Serializable
data class CommunicationStyle(
    val responseLength: ResponseLength = ResponseLength.BALANCED,
    val formality: Formality = Formality.FRIENDLY,
    val technicalLevel: TechnicalLevel = TechnicalLevel.ADVANCED,
    val useEmojis: Boolean = false,
    val preferredTone: String? = null // например: "мотивирующий", "прямолинейный", "поддерживающий"
)

/**
 * Рабочий контекст пользователя
 */
@Serializable
data class WorkContext(
    val currentProjects: List<String> = emptyList(),
    val goals: List<String> = emptyList(),
    val challenges: List<String> = emptyList(),
    val workingHours: String? = null,
    val notes: String? = null
)

/**
 * Технический стек пользователя
 */
@Serializable
data class TechnicalStack(
    val programmingLanguages: List<String> = emptyList(),
    val frameworks: List<String> = emptyList(),
    val tools: List<String> = emptyList(),
    val preferredCodingStyle: String? = null,
    val operatingSystem: String? = null,
    val ide: String? = null
)

/**
 * Личные интересы и увлечения
 */
@Serializable
data class PersonalInterests(
    // Хобби и увлечения
    val hobbies: List<String> = emptyList(),

    // Культурные предпочтения
    val favoriteBooks: List<String> = emptyList(),
    val favoriteMovies: List<String> = emptyList(),
    val favoriteTvShows: List<String> = emptyList(),
    val musicGenres: List<String> = emptyList(),
    val favoriteArtists: List<String> = emptyList(),

    // Спорт и активности
    val sports: List<String> = emptyList(),

    // Образование и саморазвитие
    val learningGoals: List<String> = emptyList(),
    val favoriteTopics: List<String> = emptyList(),

    // Прочее
    val travelDestinations: List<String> = emptyList(),
    val favoriteFood: List<String> = emptyList(),
    val pets: List<String> = emptyList()
)

/**
 * Предпочтительная длина ответов
 */
@Serializable
enum class ResponseLength {
    BRIEF,      // Краткие ответы
    BALANCED,   // Сбалансированные
    DETAILED    // Подробные
}

/**
 * Уровень формальности
 */
@Serializable
enum class Formality {
    CASUAL,     // Неформально
    FRIENDLY,   // Дружелюбно
    PROFESSIONAL // Профессионально
}

/**
 * Технический уровень
 */
@Serializable
enum class TechnicalLevel {
    BEGINNER,   // Начинающий
    INTERMEDIATE, // Средний
    ADVANCED,   // Продвинутый
    EXPERT      // Эксперт
}
