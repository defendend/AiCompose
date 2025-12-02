package org.example.network

import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.engine.cio.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.client.request.*
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.*
import kotlinx.serialization.json.Json
import org.example.logging.AppLogger
import org.example.model.ChatRequest
import org.example.model.ChatResponse

class ChatApiClient(
    private val baseUrl: String = "http://89.169.190.22:8080"
) {
    private val client = HttpClient(CIO) {
        install(ContentNegotiation) {
            json(Json {
                prettyPrint = true
                isLenient = true
                ignoreUnknownKeys = true
            })
        }
    }

    suspend fun sendMessage(message: String, conversationId: String? = null): Result<ChatResponse> {
        return try {
            AppLogger.info("ChatApiClient", "Отправка запроса: $message")

            val response = client.post("$baseUrl/api/chat") {
                contentType(ContentType.Application.Json)
                setBody(ChatRequest(message = message, conversationId = conversationId))
            }

            val chatResponse = response.body<ChatResponse>()
            AppLogger.info("ChatApiClient", "Получен ответ: ${chatResponse.message.content}")

            Result.success(chatResponse)
        } catch (e: Exception) {
            AppLogger.error("ChatApiClient", "Ошибка при отправке запроса: ${e.message}")
            Result.failure(e)
        }
    }

    fun close() {
        client.close()
    }
}
