package org.example.network

import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.engine.cio.*
import io.ktor.client.plugins.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.client.request.*
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.*
import kotlinx.serialization.json.Json
import org.example.logging.AppLogger
import org.example.model.ChatRequest
import org.example.model.ChatResponse
import org.example.model.ResponseFormat
import org.example.model.ServerLogsResponse

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
        install(HttpTimeout) {
            requestTimeoutMillis = 60000
            connectTimeoutMillis = 10000
            socketTimeoutMillis = 60000
        }
    }

    suspend fun sendMessage(
        message: String,
        conversationId: String? = null,
        responseFormat: ResponseFormat = ResponseFormat.PLAIN
    ): Result<ChatResponse> {
        return try {
            AppLogger.info("ChatApiClient", "Отправка запроса: $message (формат: $responseFormat)")

            val response = client.post("$baseUrl/api/chat") {
                contentType(ContentType.Application.Json)
                setBody(ChatRequest(
                    message = message,
                    conversationId = conversationId,
                    responseFormat = responseFormat
                ))
            }

            val chatResponse = response.body<ChatResponse>()
            AppLogger.info("ChatApiClient", "Получен ответ: ${chatResponse.message.content}")

            Result.success(chatResponse)
        } catch (e: Exception) {
            AppLogger.error("ChatApiClient", "Ошибка при отправке запроса: ${e.message}")
            Result.failure(e)
        }
    }

    suspend fun getServerLogs(limit: Int = 100): Result<ServerLogsResponse> {
        return try {
            val response = client.get("$baseUrl/api/logs") {
                parameter("limit", limit)
            }
            Result.success(response.body())
        } catch (e: Exception) {
            AppLogger.error("ChatApiClient", "Ошибка получения логов: ${e.message}")
            Result.failure(e)
        }
    }

    suspend fun clearServerLogs(): Result<Unit> {
        return try {
            client.delete("$baseUrl/api/logs")
            Result.success(Unit)
        } catch (e: Exception) {
            AppLogger.error("ChatApiClient", "Ошибка очистки логов: ${e.message}")
            Result.failure(e)
        }
    }

    fun close() {
        client.close()
    }
}
