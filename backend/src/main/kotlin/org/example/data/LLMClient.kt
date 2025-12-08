package org.example.data

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
import kotlinx.serialization.SerializationException
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.example.logging.ServerLogger
import org.example.model.LLMMessage
import org.example.model.LLMRequest
import org.example.model.LLMResponse
import org.example.model.LLMStreamChunk
import org.example.model.LLMStreamRequest
import org.example.model.LogCategory
import org.example.model.Tool

/**
 * Клиент для взаимодействия с LLM API.
 * Интерфейс позволяет легко заменить DeepSeek на OpenAI/Claude/etc.
 */
interface LLMClient {
    suspend fun chat(
        messages: List<LLMMessage>,
        tools: List<Tool>,
        temperature: Float?,
        conversationId: String
    ): LLMResponse

    /**
     * Streaming версия chat - возвращает Flow чанков.
     */
    fun chatStream(
        messages: List<LLMMessage>,
        tools: List<Tool>,
        temperature: Float?,
        conversationId: String
    ): Flow<LLMStreamChunk>

    /**
     * Проверка доступности API (health check).
     */
    suspend fun healthCheck(): Boolean

    fun close()
}

/**
 * Исключения LLM клиента
 */
sealed class LLMException(message: String, cause: Throwable? = null) : Exception(message, cause)
class LLMApiException(message: String, val statusCode: Int) : LLMException(message)
class LLMTimeoutException(message: String, cause: Throwable?) : LLMException(message, cause)
class LLMParseException(message: String, cause: Throwable?) : LLMException(message, cause)
class LLMClientException(message: String, cause: Throwable?) : LLMException(message, cause)

/**
 * Реализация клиента для DeepSeek API.
 */
class DeepSeekClient(
    private val apiKey: String,
    private val model: String = "deepseek-chat",
    private val baseUrl: String = "https://api.deepseek.com/v1"
) : LLMClient {

    companion object {
        private val jsonPretty = Json {
            prettyPrint = true
            isLenient = true
            ignoreUnknownKeys = true
        }

        private val jsonParser = Json {
            ignoreUnknownKeys = true
        }
    }

    private val httpClient = HttpClient(CIO) {
        install(ContentNegotiation) {
            json(jsonParser)
        }
        install(HttpTimeout) {
            requestTimeoutMillis = 120000
            connectTimeoutMillis = 30000
            socketTimeoutMillis = 120000
        }
    }

    override suspend fun chat(
        messages: List<LLMMessage>,
        tools: List<Tool>,
        temperature: Float?,
        conversationId: String
    ): LLMResponse {
        // Формируем превью сообщений для логов
        val messagesPreview = messages.joinToString("\n") { msg ->
            val content = msg.content?.take(200) ?: (msg.tool_calls?.firstOrNull()?.let { "tool_call: ${it.function.name}" } ?: "")
            "[${msg.role}] $content"
        }

        ServerLogger.logLLMRequest(model, messages.size, tools.size, conversationId, messagesPreview)

        val request = LLMRequest(
            model = model,
            messages = messages,
            tools = if (tools.isNotEmpty()) tools else null,
            temperature = temperature
        )

        // Логируем полный JSON запрос
        val requestJson = jsonPretty.encodeToString(LLMRequest.serializer(), request)
        ServerLogger.logLLMRawRequest(requestJson, conversationId)

        val startTime = System.currentTimeMillis()

        try {
            val response = httpClient.post("$baseUrl/chat/completions") {
                contentType(ContentType.Application.Json)
                header("Authorization", "Bearer $apiKey")
                setBody(request)
            }

            val duration = System.currentTimeMillis() - startTime

            // Получаем сырой ответ как строку
            val rawResponseBody = response.body<String>()

            if (!response.status.isSuccess()) {
                ServerLogger.logError(
                    "LLM API error: ${response.status} - $rawResponseBody",
                    null,
                    LogCategory.LLM_RESPONSE
                )
                throw LLMApiException("Ошибка LLM API: ${response.status}", response.status.value)
            }

            // Логируем полный JSON ответ
            ServerLogger.logLLMRawResponse(rawResponseBody, duration, conversationId)

            // Парсим ответ
            val llmResponse: LLMResponse = try {
                jsonParser.decodeFromString(rawResponseBody)
            } catch (e: SerializationException) {
                ServerLogger.logError("Failed to parse LLM response: ${e.message}", e, LogCategory.LLM_RESPONSE)
                throw LLMParseException("Невалидный формат ответа LLM: ${e.message}", e)
            }

            val hasToolCalls = llmResponse.choices.firstOrNull()?.message?.tool_calls?.isNotEmpty() == true
            val content = llmResponse.choices.firstOrNull()?.message?.content

            ServerLogger.logLLMResponse(model, hasToolCalls, content, duration, conversationId)

            return llmResponse

        } catch (e: HttpRequestTimeoutException) {
            val duration = System.currentTimeMillis() - startTime
            ServerLogger.logError("LLM API timeout after ${duration}ms: ${e.message}", e, LogCategory.LLM_REQUEST)
            throw LLMTimeoutException("Таймаут запроса к LLM API (${duration}ms)", e)

        } catch (e: LLMException) {
            // Пробрасываем наши исключения дальше
            throw e

        } catch (e: Exception) {
            val duration = System.currentTimeMillis() - startTime
            ServerLogger.logError("Unexpected LLM client error after ${duration}ms: ${e.message}", e, LogCategory.LLM_REQUEST)
            throw LLMClientException("Неожиданная ошибка при вызове LLM: ${e.message}", e)
        }
    }

    override fun chatStream(
        messages: List<LLMMessage>,
        tools: List<Tool>,
        temperature: Float?,
        conversationId: String
    ): Flow<LLMStreamChunk> = flow {
        val messagesPreview = messages.joinToString("\n") { msg ->
            val content = msg.content?.take(200) ?: (msg.tool_calls?.firstOrNull()?.let { "tool_call: ${it.function.name}" } ?: "")
            "[${msg.role}] $content"
        }

        ServerLogger.logLLMRequest(model, messages.size, tools.size, conversationId, "$messagesPreview [STREAMING]")

        val request = LLMStreamRequest(
            model = model,
            messages = messages,
            tools = if (tools.isNotEmpty()) tools else null,
            temperature = temperature,
            stream = true
        )

        val requestJson = jsonPretty.encodeToString(LLMStreamRequest.serializer(), request)
        ServerLogger.logLLMRawRequest(requestJson, conversationId)

        val startTime = System.currentTimeMillis()

        try {
            httpClient.preparePost("$baseUrl/chat/completions") {
                contentType(ContentType.Application.Json)
                header("Authorization", "Bearer $apiKey")
                setBody(request)
            }.execute { response ->
                if (!response.status.isSuccess()) {
                    val errorBody = response.bodyAsText()
                    ServerLogger.logError(
                        "LLM API error: ${response.status} - $errorBody",
                        null,
                        LogCategory.LLM_RESPONSE
                    )
                    throw LLMApiException("Ошибка LLM API: ${response.status}", response.status.value)
                }

                val channel: ByteReadChannel = response.bodyAsChannel()
                val buffer = StringBuilder()

                while (!channel.isClosedForRead) {
                    val line = channel.readUTF8Line() ?: break

                    if (line.startsWith("data: ")) {
                        val data = line.removePrefix("data: ").trim()

                        if (data == "[DONE]") {
                            val duration = System.currentTimeMillis() - startTime
                            ServerLogger.log(
                                level = org.example.model.LogLevel.DEBUG,
                                message = "Stream completed in ${duration}ms",
                                category = LogCategory.LLM_RESPONSE,
                                conversationId = conversationId
                            )
                            break
                        }

                        if (data.isNotEmpty()) {
                            try {
                                val chunk = jsonParser.decodeFromString<LLMStreamChunk>(data)
                                emit(chunk)

                                // Собираем контент для логирования
                                chunk.choices.firstOrNull()?.delta?.content?.let {
                                    buffer.append(it)
                                }
                            } catch (e: SerializationException) {
                                ServerLogger.logError("Failed to parse stream chunk: $data", e, LogCategory.LLM_RESPONSE)
                            }
                        }
                    }
                }

                val duration = System.currentTimeMillis() - startTime
                ServerLogger.logLLMResponse(model, false, buffer.toString().take(500), duration, conversationId)
            }
        } catch (e: HttpRequestTimeoutException) {
            val duration = System.currentTimeMillis() - startTime
            ServerLogger.logError("LLM API stream timeout after ${duration}ms: ${e.message}", e, LogCategory.LLM_REQUEST)
            throw LLMTimeoutException("Таймаут streaming запроса к LLM API (${duration}ms)", e)
        } catch (e: LLMException) {
            throw e
        } catch (e: Exception) {
            val duration = System.currentTimeMillis() - startTime
            ServerLogger.logError("Unexpected LLM stream error after ${duration}ms: ${e.message}", e, LogCategory.LLM_REQUEST)
            throw LLMClientException("Неожиданная ошибка при streaming: ${e.message}", e)
        }
    }

    override suspend fun healthCheck(): Boolean {
        return try {
            // Простой запрос к API для проверки доступности
            val response = httpClient.post("$baseUrl/chat/completions") {
                contentType(ContentType.Application.Json)
                header("Authorization", "Bearer $apiKey")
                setBody(LLMRequest(
                    model = model,
                    messages = listOf(LLMMessage(role = "user", content = "ping")),
                    max_tokens = 1
                ))
                timeout {
                    requestTimeoutMillis = 10000
                }
            }
            response.status.isSuccess()
        } catch (e: Exception) {
            ServerLogger.logError("LLM health check failed: ${e.message}", e, LogCategory.SYSTEM)
            false
        }
    }

    override fun close() {
        httpClient.close()
    }
}
