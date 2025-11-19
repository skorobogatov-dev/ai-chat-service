package dev.skorobogatov.models

import kotlinx.serialization.Serializable
import java.time.LocalDateTime
import java.util.UUID

/**
 * Модель запланированной задачи
 */
@Serializable
data class ScheduledTask(
    val id: String = UUID.randomUUID().toString(),
    val name: String,
    val description: String = "",
    val question: String,
    val sessionId: String? = null, // Привязка к диалогу (опционально)
    val schedule: TaskSchedule,
    val enabled: Boolean = true,
    @Serializable(with = LocalDateTimeSerializer::class)
    val createdAt: LocalDateTime = LocalDateTime.now(),
    @Serializable(with = LocalDateTimeSerializer::class)
    val updatedAt: LocalDateTime = LocalDateTime.now(),
    @Serializable(with = LocalDateTimeSerializer::class)
    val lastExecutedAt: LocalDateTime? = null,
    @Serializable(with = LocalDateTimeSerializer::class)
    val nextExecutionAt: LocalDateTime? = null
)

/**
 * Расписание выполнения задачи
 */
@Serializable
data class TaskSchedule(
    val type: ScheduleType,
    @Serializable(with = LocalDateTimeSerializer::class)
    val startTime: LocalDateTime? = null, // Для ONCE - когда выполнить
    val hour: Int? = null,               // Для DAILY/WEEKLY - час выполнения (0-23)
    val minute: Int? = null,             // Для DAILY/WEEKLY - минута выполнения (0-59)
    val dayOfWeek: Int? = null           // Для WEEKLY - день недели (1=Monday, 7=Sunday)
)

/**
 * Тип расписания
 */
@Serializable
enum class ScheduleType {
    ONCE,      // Одноразовое выполнение в указанное время
    DAILY,     // Ежедневно в указанное время
    WEEKLY     // Еженедельно в указанный день и время
}

/**
 * Результат выполнения задачи
 */
@Serializable
data class TaskExecution(
    val id: String = UUID.randomUUID().toString(),
    val taskId: String,
    val taskName: String,
    val question: String,
    val response: String,
    val sessionId: String,
    val success: Boolean,
    val errorMessage: String? = null,
    @Serializable(with = LocalDateTimeSerializer::class)
    val executedAt: LocalDateTime = LocalDateTime.now(),
    val executionTimeMs: Long
)

/**
 * Сериализатор для LocalDateTime
 */
object LocalDateTimeSerializer : kotlinx.serialization.KSerializer<LocalDateTime> {
    override val descriptor = kotlinx.serialization.descriptors.PrimitiveSerialDescriptor(
        "LocalDateTime",
        kotlinx.serialization.descriptors.PrimitiveKind.STRING
    )

    override fun serialize(encoder: kotlinx.serialization.encoding.Encoder, value: LocalDateTime) {
        encoder.encodeString(value.toString())
    }

    override fun deserialize(decoder: kotlinx.serialization.encoding.Decoder): LocalDateTime {
        return LocalDateTime.parse(decoder.decodeString())
    }
}
