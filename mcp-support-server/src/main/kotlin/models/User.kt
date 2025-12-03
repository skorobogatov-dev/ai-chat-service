package dev.skorobogatov.mcp.support.models

import kotlinx.serialization.Serializable

@Serializable
data class User(
    val userId: String,
    val name: String,
    val email: String,
    val role: String,  // basic, premium, enterprise
    val registeredAt: String
)
