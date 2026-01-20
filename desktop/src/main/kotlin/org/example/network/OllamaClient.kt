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
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import org.example.logging.AppLogger

/**
 * Клиент для локальной LLM через Ollama API.
 * Используется как fallback когда нет сети.
 */
class OllamaClient(
    private val baseUrl: String = "http://localhost:11434"
) {
    private val jsonParser = Json {
        prettyPrint = true
        isLenient = true
        ignoreUnknownKeys = true
    }

    private val client = HttpClient(CIO) {
        engine {
            requestTimeout = 0
        }
        install(ContentNegotiation) {
            json(jsonParser)
        }
        install(HttpTimeout) {
            requestTimeoutMillis = 120000  // 2 минуты для генерации
            connectTimeoutMillis = 5000
            socketTimeoutMillis = 120000
        }
    }

    /**
     * Проверить доступность Ollama.
     */
    suspend fun isAvailable(): Boolean {
        return try {
            val response = client.get("$baseUrl/api/tags")
            response.status.isSuccess()
        } catch (e: Exception) {
            AppLogger.warning("OllamaClient", "Ollama недоступен: ${e.message}")
            false
        }
    }

    /**
     * Получить список доступных моделей.
     */
    suspend fun listModels(): List<OllamaModel> {
        return try {
            val response = client.get("$baseUrl/api/tags")
            val tagsResponse = response.body<OllamaTagsResponse>()
            tagsResponse.models
        } catch (e: Exception) {
            AppLogger.error("OllamaClient", "Ошибка получения списка моделей: ${e.message}")
            emptyList()
        }
    }

    /**
     * Отправить сообщение и получить ответ (non-streaming).
     */
    suspend fun chat(
        model: String = "qwen2.5:0.5b",
        messages: List<OllamaMessage>,
        systemPrompt: String? = null
    ): Result<OllamaChatResponse> {
        return try {
            AppLogger.info("OllamaClient", "Отправка запроса к локальной LLM ($model)")

            val allMessages = buildList {
                if (systemPrompt != null) {
                    add(OllamaMessage(role = "system", content = systemPrompt))
                }
                addAll(messages)
            }

            val response = client.post("$baseUrl/api/chat") {
                contentType(ContentType.Application.Json)
                setBody(OllamaChatRequest(
                    model = model,
                    messages = allMessages,
                    stream = false
                ))
            }

            val chatResponse = response.body<OllamaChatResponse>()
            AppLogger.info("OllamaClient", "Получен ответ от локальной LLM: ${chatResponse.message.content.take(100)}...")

            Result.success(chatResponse)
        } catch (e: Exception) {
            AppLogger.error("OllamaClient", "Ошибка при запросе к Ollama: ${e.message}")
            Result.failure(e)
        }
    }

    /**
     * Отправить одно сообщение (удобный метод).
     */
    suspend fun chat(
        model: String = "qwen2.5:0.5b",
        userMessage: String,
        systemPrompt: String? = null,
        history: List<OllamaMessage> = emptyList()
    ): Result<String> {
        val messages = history + OllamaMessage(role = "user", content = userMessage)
        return chat(model, messages, systemPrompt).map { it.message.content }
    }

    /**
     * Streaming версия чата.
     */
    fun chatStream(
        model: String = "qwen2.5:0.5b",
        messages: List<OllamaMessage>,
        systemPrompt: String? = null
    ): Flow<String> = flow {
        AppLogger.info("OllamaClient", "Начало streaming запроса к локальной LLM ($model)")

        val allMessages = buildList {
            if (systemPrompt != null) {
                add(OllamaMessage(role = "system", content = systemPrompt))
            }
            addAll(messages)
        }

        try {
            client.preparePost("$baseUrl/api/chat") {
                contentType(ContentType.Application.Json)
                setBody(OllamaChatRequest(
                    model = model,
                    messages = allMessages,
                    stream = true
                ))
            }.execute { response ->
                val channel: ByteReadChannel = response.bodyAsChannel()

                while (!channel.isClosedForRead) {
                    val line = channel.readUTF8Line() ?: break

                    if (line.isNotBlank()) {
                        try {
                            val chunk = jsonParser.decodeFromString<OllamaStreamChunk>(line)
                            if (chunk.message.content.isNotEmpty()) {
                                emit(chunk.message.content)
                            }
                        } catch (e: Exception) {
                            // Пропускаем некорректные строки
                        }
                    }
                }
            }

            AppLogger.info("OllamaClient", "Streaming завершён")
        } catch (e: Exception) {
            AppLogger.error("OllamaClient", "Ошибка streaming: ${e.message}")
            throw e
        }
    }

    fun close() {
        client.close()
    }
}

// === Data classes для Ollama API ===

@Serializable
data class OllamaTagsResponse(
    val models: List<OllamaModel> = emptyList()
)

@Serializable
data class OllamaModel(
    val name: String,
    val size: Long = 0,
    val digest: String = "",
    val modifiedAt: String = ""
)

@Serializable
data class OllamaChatRequest(
    val model: String,
    val messages: List<OllamaMessage>,
    val stream: Boolean = false
)

@Serializable
data class OllamaMessage(
    val role: String,
    val content: String
)

@Serializable
data class OllamaChatResponse(
    val model: String = "",
    val message: OllamaMessage,
    val done: Boolean = true
)

@Serializable
data class OllamaStreamChunk(
    val model: String = "",
    val message: OllamaMessage,
    val done: Boolean = false
)
