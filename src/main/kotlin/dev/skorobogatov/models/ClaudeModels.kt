package dev.skorobogatov.models

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Перечисление доступных моделей Claude AI, отсортированных от самых слабых/быстрых к самым сильным/медленным
 */
@Serializable
enum class ClaudeModel(val modelId: String, val description: String) {
    @SerialName("claude-3-haiku-20240307")
    CLAUDE_3_HAIKU(
        "claude-3-haiku-20240307",
        "Fastest and most affordable model, ideal for simple tasks"
    ),

    @SerialName("claude-sonnet-4-20250514")
    CLAUDE_SONNET_4(
        "claude-sonnet-4-20250514",
        "Most advanced Claude model with superior capabilities (recommended default)"
    );

    companion object {
        /**
         * Модель по умолчанию для использования
         */
        val DEFAULT = CLAUDE_SONNET_4

        /**
         * Получить модель по её ID
         */
        fun fromModelId(modelId: String): ClaudeModel? {
            return entries.find { it.modelId == modelId }
        }
    }
}
