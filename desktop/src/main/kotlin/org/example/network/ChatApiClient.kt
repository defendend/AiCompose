package org.example.network

import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.engine.cio.*
import io.ktor.client.plugins.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.*
import io.ktor.utils.io.*
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import org.example.logging.AppLogger
import org.example.model.ServerLogsResponse
import org.example.shared.model.ChatRequest
import org.example.shared.model.ChatResponse
import org.example.shared.model.ChatStreamRequest
import org.example.shared.model.CollectionSettings
import org.example.shared.model.HealthCheckResponse
import org.example.shared.model.ResponseFormat
import org.example.shared.model.StreamEvent

class ChatApiClient(
    private val baseUrl: String = "http://89.169.190.22"
) {
    private val jsonParser = Json {
        prettyPrint = true
        isLenient = true
        ignoreUnknownKeys = true
    }

    private val client = HttpClient(CIO) {
        install(ContentNegotiation) {
            json(jsonParser)
        }
        install(HttpTimeout) {
            requestTimeoutMillis = 180000  // 3 минуты для сложных запросов (группа экспертов)
            connectTimeoutMillis = 10000
            socketTimeoutMillis = 180000
        }
    }

    suspend fun sendMessage(
        text: String,
        conversationId: String? = null,
        responseFormat: ResponseFormat = ResponseFormat.PLAIN,
        collectionSettings: CollectionSettings? = null,
        temperature: Float? = null
    ): Result<ChatResponse> {
        return try {
            AppLogger.info("ChatApiClient", "Отправка запроса: $text (формат: $responseFormat, режим сбора: ${collectionSettings?.mode}, temperature: $temperature)")

            val response = client.post("$baseUrl/api/chat") {
                contentType(ContentType.Application.Json)
                setBody(ChatRequest(
                    message = text,
                    conversationId = conversationId,
                    responseFormat = responseFormat,
                    collectionSettings = collectionSettings,
                    temperature = temperature
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

    /**
     * Streaming версия sendMessage - возвращает Flow событий.
     */
    fun sendMessageStream(
        text: String,
        conversationId: String? = null,
        responseFormat: ResponseFormat = ResponseFormat.PLAIN,
        collectionSettings: CollectionSettings? = null,
        temperature: Float? = null
    ): Flow<StreamEvent> = callbackFlow {
        AppLogger.info("ChatApiClient", "Отправка streaming запроса: $text")

        launch {
            try {
                client.preparePost("$baseUrl/api/chat/stream") {
                    contentType(ContentType.Application.Json)
                    setBody(ChatStreamRequest(
                        message = text,
                        conversationId = conversationId,
                        responseFormat = responseFormat,
                        collectionSettings = collectionSettings,
                        temperature = temperature
                    ))
                }.execute { response ->
                    if (!response.status.isSuccess()) {
                        val errorBody = response.bodyAsText()
                        AppLogger.error("ChatApiClient", "Streaming error: ${response.status} - $errorBody")
                        throw Exception("Ошибка сервера: ${response.status}")
                    }

                    val channel: ByteReadChannel = response.bodyAsChannel()

                    while (!channel.isClosedForRead) {
                        val line = channel.readUTF8Line() ?: break

                        AppLogger.info("ChatApiClient", "SSE line: $line")

                        if (line.startsWith("data: ")) {
                            val data = line.removePrefix("data: ").trim()

                            if (data.isNotEmpty() && data != "[DONE]") {
                                try {
                                    val event = jsonParser.decodeFromString<StreamEvent>(data)
                                    trySend(event)
                                    AppLogger.info("ChatApiClient", "Emitted event: ${event.type}")
                                } catch (e: Exception) {
                                    AppLogger.error("ChatApiClient", "Failed to parse stream event: $data - ${e.message}")
                                }
                            }
                        }
                    }
                }

                AppLogger.info("ChatApiClient", "Streaming завершён")
                close()

            } catch (e: Exception) {
                AppLogger.error("ChatApiClient", "Ошибка streaming: ${e.message}")
                close(e)
            }
        }

        awaitClose {
            AppLogger.info("ChatApiClient", "Flow closed")
        }
    }

    /**
     * Получить расширенный health check.
     */
    suspend fun getDetailedHealth(): Result<HealthCheckResponse> {
        return try {
            val response = client.get("$baseUrl/api/health/detailed")
            Result.success(response.body())
        } catch (e: Exception) {
            AppLogger.error("ChatApiClient", "Ошибка health check: ${e.message}")
            Result.failure(e)
        }
    }

    fun close() {
        client.close()
    }
}
