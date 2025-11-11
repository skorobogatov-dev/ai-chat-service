package dev.skorobogatov.models

import kotlinx.serialization.Serializable

@Serializable
data class ChatRequest(
    val message: String,
    val systemPrompt: String? = null,
    val model: String? = null  // Если не указана, используется модель из конфигурации
)
