package dev.skorobogatov.models

import kotlinx.serialization.Serializable

@Serializable
data class ExpertResponse(
    val expertRole: String,
    val expertName: String,
    val response: String
)

@Serializable
data class ChatResponse(
    val question: String,
    val experts: List<ExpertResponse>,
    val model: String = ""
)
