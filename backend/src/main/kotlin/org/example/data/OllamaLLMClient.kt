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
import kotlinx.coroutines.flow.channelFlow
import kotlinx.serialization.SerializationException
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.example.logging.ServerLogger
import org.example.model.*

/**
 * Клиент для локального Ollama API.
 * Используется для работы с локальной LLM на VPS.
 */
class OllamaLLMClient(
    private val baseUrl: String = "http://localhost:11434",
    private val defaultModel: String = "qwen2.5:0.5b"
) : LLMClient {

    companion object {
        private val jsonPretty = Json {
            prettyPrint = true
            isLenient = true
            ignoreUnknownKeys = true
            encodeDefaults = true  // Важно! Включает stream=false в JSON
        }

        private val jsonParser = Json {
            ignoreUnknownKeys = true
            isLenient = true
            encodeDefaults = true
        }
    }

    private val httpClient = HttpClient(CIO) {
        install(ContentNegotiation) {
            json(jsonParser)
        }
        install(HttpTimeout) {
            requestTimeoutMillis = 300000  // 5 минут для локальной модели
            connectTimeoutMillis = 10000
            socketTimeoutMillis = 300000
        }
    }

    override suspend fun chat(
        messages: List<LLMMessage>,
        tools: List<Tool>,
        temperature: Float?,
        conversationId: String
    ): LLMResponse {
        val messagesPreview = messages.joinToString("\n") { msg ->
            val content = msg.content?.take(200) ?: (msg.tool_calls?.firstOrNull()?.let { "tool_call: ${it.function.name}" } ?: "")
            "[${msg.role}] $content"
        }

        ServerLogger.logLLMRequest(defaultModel, messages.size, tools.size, conversationId, "$messagesPreview [OLLAMA]")

        // Конвертируем сообщения в формат Ollama
        val ollamaMessages = messages.map { msg ->
            OllamaChatMessage(
                role = msg.role,
                content = msg.content ?: ""
            )
        }

        val request = OllamaChatRequest(
            model = defaultModel,
            messages = ollamaMessages,
            stream = false,
            options = if (temperature != null) OllamaOptions(temperature = temperature) else null
        )

        val requestJson = jsonPretty.encodeToString(request)
        ServerLogger.logLLMRawRequest(requestJson, conversationId)

        val startTime = System.currentTimeMillis()

        try {
            val response = httpClient.post("$baseUrl/api/chat") {
                contentType(ContentType.Application.Json)
                // Важно: сериализуем вручную чтобы гарантировать stream=false в JSON
                setBody(jsonParser.encodeToString(OllamaChatRequest.serializer(), request))
            }

            val duration = System.currentTimeMillis() - startTime
            val rawResponseBody = response.body<String>()

            if (!response.status.isSuccess()) {
                ServerLogger.logError(
                    "Ollama API error: ${response.status} - $rawResponseBody",
                    null,
                    LogCategory.LLM_RESPONSE
                )
                throw LLMApiException("Ошибка Ollama API: ${response.status}", response.status.value)
            }

            ServerLogger.logLLMRawResponse(rawResponseBody, duration, conversationId)

            val ollamaResponse: OllamaChatResponse = try {
                jsonParser.decodeFromString(rawResponseBody)
            } catch (e: SerializationException) {
                ServerLogger.logError("Failed to parse Ollama response: ${e.message}", e, LogCategory.LLM_RESPONSE)
                throw LLMParseException("Невалидный формат ответа Ollama: ${e.message}", e)
            }

            // Конвертируем ответ в формат LLMResponse
            val llmResponse = LLMResponse(
                id = "ollama-${System.currentTimeMillis()}",
                choices = listOf(
                    Choice(
                        message = LLMMessage(
                            role = ollamaResponse.message.role,
                            content = ollamaResponse.message.content
                        ),
                        finish_reason = if (ollamaResponse.done) "stop" else null
                    )
                ),
                usage = Usage(
                    prompt_tokens = ollamaResponse.prompt_eval_count ?: 0,
                    completion_tokens = ollamaResponse.eval_count ?: 0,
                    total_tokens = (ollamaResponse.prompt_eval_count ?: 0) + (ollamaResponse.eval_count ?: 0)
                )
            )

            ServerLogger.logLLMResponse(defaultModel, false, ollamaResponse.message.content?.take(500), duration, conversationId)

            llmResponse.usage?.let { usage ->
                ServerLogger.logTokenUsage(
                    promptTokens = usage.prompt_tokens,
                    completionTokens = usage.completion_tokens,
                    totalTokens = usage.total_tokens,
                    model = defaultModel,
                    conversationId = conversationId,
                    durationMs = duration
                )
            }

            return llmResponse

        } catch (e: HttpRequestTimeoutException) {
            val duration = System.currentTimeMillis() - startTime
            ServerLogger.logError("Ollama API timeout after ${duration}ms: ${e.message}", e, LogCategory.LLM_REQUEST)
            throw LLMTimeoutException("Таймаут запроса к Ollama API (${duration}ms)", e)
        } catch (e: LLMException) {
            throw e
        } catch (e: Exception) {
            val duration = System.currentTimeMillis() - startTime
            ServerLogger.logError("Unexpected Ollama client error after ${duration}ms: ${e.message}", e, LogCategory.LLM_REQUEST)
            throw LLMClientException("Неожиданная ошибка при вызове Ollama: ${e.message}", e)
        }
    }

    override fun chatStream(
        messages: List<LLMMessage>,
        tools: List<Tool>,
        temperature: Float?,
        conversationId: String
    ): Flow<LLMStreamChunk> = channelFlow {
        val messagesPreview = messages.joinToString("\n") { msg ->
            val content = msg.content?.take(200) ?: ""
            "[${msg.role}] $content"
        }

        ServerLogger.logLLMRequest(defaultModel, messages.size, tools.size, conversationId, "$messagesPreview [OLLAMA STREAMING]")

        val ollamaMessages = messages.map { msg ->
            OllamaChatMessage(
                role = msg.role,
                content = msg.content ?: ""
            )
        }

        val request = OllamaChatRequest(
            model = defaultModel,
            messages = ollamaMessages,
            stream = true,
            options = if (temperature != null) OllamaOptions(temperature = temperature) else null
        )

        val requestJson = jsonPretty.encodeToString(request)
        ServerLogger.logLLMRawRequest(requestJson, conversationId)

        val startTime = System.currentTimeMillis()

        try {
            httpClient.preparePost("$baseUrl/api/chat") {
                contentType(ContentType.Application.Json)
                setBody(jsonParser.encodeToString(OllamaChatRequest.serializer(), request))
            }.execute { response ->
                if (!response.status.isSuccess()) {
                    val errorBody = response.bodyAsText()
                    ServerLogger.logError(
                        "Ollama API error: ${response.status} - $errorBody",
                        null,
                        LogCategory.LLM_RESPONSE
                    )
                    throw LLMApiException("Ошибка Ollama API: ${response.status}", response.status.value)
                }

                val channel: ByteReadChannel = response.bodyAsChannel()
                val buffer = StringBuilder()

                while (!channel.isClosedForRead) {
                    val line = channel.readUTF8Line() ?: break

                    if (line.isNotEmpty()) {
                        try {
                            val ollamaChunk = jsonParser.decodeFromString<OllamaStreamChunk>(line)

                            // Конвертируем в формат LLMStreamChunk
                            val chunk = LLMStreamChunk(
                                id = "ollama-${System.currentTimeMillis()}",
                                model = defaultModel,
                                choices = listOf(
                                    StreamChoice(
                                        index = 0,
                                        delta = DeltaMessage(
                                            role = if (buffer.isEmpty()) "assistant" else null,
                                            content = ollamaChunk.message?.content
                                        ),
                                        finish_reason = if (ollamaChunk.done) "stop" else null
                                    )
                                )
                            )

                            send(chunk)

                            ollamaChunk.message?.content?.let {
                                buffer.append(it)
                            }

                            if (ollamaChunk.done) {
                                break
                            }
                        } catch (e: SerializationException) {
                            ServerLogger.logError("Failed to parse Ollama stream chunk: $line", e, LogCategory.LLM_RESPONSE)
                        }
                    }
                }

                val duration = System.currentTimeMillis() - startTime
                ServerLogger.logLLMResponse(defaultModel, false, buffer.toString().take(500), duration, conversationId)
            }
        } catch (e: HttpRequestTimeoutException) {
            val duration = System.currentTimeMillis() - startTime
            ServerLogger.logError("Ollama API stream timeout after ${duration}ms: ${e.message}", e, LogCategory.LLM_REQUEST)
            throw LLMTimeoutException("Таймаут streaming запроса к Ollama API (${duration}ms)", e)
        } catch (e: LLMException) {
            throw e
        } catch (e: Exception) {
            val duration = System.currentTimeMillis() - startTime
            ServerLogger.logError("Unexpected Ollama stream error after ${duration}ms: ${e.message}", e, LogCategory.LLM_REQUEST)
            throw LLMClientException("Неожиданная ошибка при Ollama streaming: ${e.message}", e)
        }
    }

    override suspend fun healthCheck(): Boolean {
        return try {
            val response = httpClient.get("$baseUrl/api/tags") {
                timeout {
                    requestTimeoutMillis = 5000
                }
            }
            response.status.isSuccess()
        } catch (e: Exception) {
            ServerLogger.logError("Ollama health check failed: ${e.message}", e, LogCategory.SYSTEM)
            false
        }
    }

    /**
     * Получить список доступных моделей.
     */
    suspend fun listModels(): List<String> {
        return try {
            val response = httpClient.get("$baseUrl/api/tags")
            if (response.status.isSuccess()) {
                val body = response.body<String>()
                val tagsResponse = jsonParser.decodeFromString<OllamaTagsResponse>(body)
                tagsResponse.models.map { it.name }
            } else {
                emptyList()
            }
        } catch (e: Exception) {
            ServerLogger.logError("Failed to list Ollama models: ${e.message}", e, LogCategory.SYSTEM)
            emptyList()
        }
    }

    override fun close() {
        httpClient.close()
    }
}

// Модели для Ollama API

@Serializable
data class OllamaChatRequest(
    val model: String,
    val messages: List<OllamaChatMessage>,
    val stream: Boolean = false,
    val options: OllamaOptions? = null
)

@Serializable
data class OllamaChatMessage(
    val role: String,
    val content: String
)

@Serializable
data class OllamaOptions(
    val temperature: Float? = null,
    val num_predict: Int? = null
)

@Serializable
data class OllamaChatResponse(
    val model: String,
    val created_at: String? = null,
    val message: OllamaChatMessage,
    val done: Boolean,
    val total_duration: Long? = null,
    val load_duration: Long? = null,
    val prompt_eval_count: Int? = null,
    val prompt_eval_duration: Long? = null,
    val eval_count: Int? = null,
    val eval_duration: Long? = null
)

@Serializable
data class OllamaStreamChunk(
    val model: String? = null,
    val created_at: String? = null,
    val message: OllamaChatMessage? = null,
    val done: Boolean = false
)

@Serializable
data class OllamaTagsResponse(
    val models: List<OllamaModelInfo> = emptyList()
)

@Serializable
data class OllamaModelInfo(
    val name: String,
    val size: Long = 0,
    val digest: String = ""
)
