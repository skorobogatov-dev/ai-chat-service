package dev.skorobogatov.models

import kotlinx.serialization.Serializable

@Serializable
data class ChatResponse(
    val response: String,
    val model: String
)
