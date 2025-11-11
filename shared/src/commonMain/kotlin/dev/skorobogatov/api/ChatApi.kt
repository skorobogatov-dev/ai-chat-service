package dev.skorobogatov.api

import dev.skorobogatov.models.ChatRequest
import dev.skorobogatov.models.ChatResponse
import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.client.request.*
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.*
import kotlinx.serialization.json.Json

class ChatApi(private val baseUrl: String = "http://localhost:8080") {
    
    private val client = HttpClient {
        install(ContentNegotiation) {
            json(Json {
                prettyPrint = true
                isLenient = true
                ignoreUnknownKeys = true
            })
        }
    }
    
    suspend fun sendMessage(message: String, systemPrompt: String? = null): ChatResponse {
        return client.post("$baseUrl/api/chat") {
            contentType(ContentType.Application.Json)
            setBody(ChatRequest(message, systemPrompt))
        }.body()
    }
    
    fun close() {
        client.close()
    }
}
