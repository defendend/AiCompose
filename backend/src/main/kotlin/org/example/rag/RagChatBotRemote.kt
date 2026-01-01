package org.example.rag

import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.engine.cio.*
import io.ktor.client.plugins.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.client.request.*
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.*
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import org.slf4j.LoggerFactory

/**
 * RAG чат-бот, работающий через удалённый сервер.
 * Не требует локального API ключа — использует ключ на сервере.
 *
 * Возможности:
 * - Хранит историю диалога локально
 * - RAG-поиск через серверный индекс
 * - Вывод источников
 */
class RagChatBotRemote(
    private val serverUrl: String = "http://89.169.190.22",
    private val topK: Int = 3,
    private val minRelevance: Float? = 0.1f
) {
    private val logger = LoggerFactory.getLogger(RagChatBotRemote::class.java)
    private val history = mutableListOf<ChatMessage>()
    private var conversationId: String? = null

    private val httpClient = HttpClient(CIO) {
        install(ContentNegotiation) {
            json(Json { ignoreUnknownKeys = true })
        }
        install(HttpTimeout) {
            requestTimeoutMillis = 120000
            connectTimeoutMillis = 30000
        }
    }

    @Serializable
    data class ChatMessage(
        val role: String,
        val content: String,
        val timestamp: Long = System.currentTimeMillis()
    )

    @Serializable
    data class ChatResponse(
        val answer: String,
        val sources: List<SourceInfo>,
        val usedRag: Boolean,
        val historySize: Int,
        val durationMs: Long
    )

    @Serializable
    data class SourceInfo(
        val file: String,
        val relevance: Float,
        val snippet: String
    )

    // API модели
    @Serializable
    data class ApiChatRequest(
        val message: String,
        val conversationId: String? = null
    )

    @Serializable
    data class ApiChatResponse(
        val message: ApiMessage,
        val conversationId: String
    )

    @Serializable
    data class ApiMessage(
        val id: String,
        val role: String,
        val content: String
    )

    @Serializable
    data class ToolExecuteRequest(
        val tool: String,
        val arguments: String
    )

    @Serializable
    data class ToolExecuteResponse(
        val success: Boolean,
        val result: String? = null,
        val error: String? = null
    )

    /**
     * Отправить сообщение и получить ответ с источниками.
     */
    suspend fun chat(userMessage: String): ChatResponse {
        logger.info("Новое сообщение: ${userMessage.take(50)}...")
        val startTime = System.currentTimeMillis()

        // 1. Добавляем в локальную историю
        history.add(ChatMessage(role = "user", content = userMessage))

        // 2. Ищем релевантные документы через RAG на сервере
        val searchResults = searchRag(userMessage)

        // 3. Формируем обогащённый запрос
        val enrichedMessage = if (searchResults.isNotEmpty()) {
            buildString {
                appendLine("=== КОНТЕКСТ ИЗ ДОКУМЕНТОВ ===")
                searchResults.forEachIndexed { idx, result ->
                    appendLine("[Источник ${idx + 1}] ${result.file} (релевантность: ${String.format("%.0f%%", result.relevance * 100)})")
                    appendLine(result.snippet)
                    appendLine()
                }
                appendLine("=== КОНЕЦ КОНТЕКСТА ===")
                appendLine()
                appendLine("Вопрос: $userMessage")
                appendLine()
                appendLine("Ответь используя информацию из контекста. Укажи источники.")
            }
        } else {
            userMessage
        }

        // 4. Отправляем на сервер
        val response = try {
            httpClient.post("$serverUrl/api/chat") {
                contentType(ContentType.Application.Json)
                setBody(ApiChatRequest(
                    message = enrichedMessage,
                    conversationId = conversationId
                ))
            }
        } catch (e: Exception) {
            logger.error("Ошибка запроса к серверу", e)
            throw e
        }

        val apiResponse: ApiChatResponse = response.body()
        conversationId = apiResponse.conversationId

        val answer = apiResponse.message.content
        val duration = System.currentTimeMillis() - startTime

        // 5. Добавляем ответ в историю
        history.add(ChatMessage(role = "assistant", content = answer))

        logger.info("Ответ получен за ${duration}ms, источников: ${searchResults.size}")

        return ChatResponse(
            answer = answer,
            sources = searchResults,
            usedRag = searchResults.isNotEmpty(),
            historySize = history.size,
            durationMs = duration
        )
    }

    /**
     * Поиск через RAG на сервере
     */
    private suspend fun searchRag(query: String): List<SourceInfo> {
        return try {
            val args = buildString {
                append("{\"query\":\"${query.replace("\"", "\\\"")}\",\"top_k\":$topK")
                if (minRelevance != null) {
                    append(",\"min_relevance\":$minRelevance")
                }
                append("}")
            }

            val response = httpClient.post("$serverUrl/api/tools/execute") {
                contentType(ContentType.Application.Json)
                setBody(ToolExecuteRequest(
                    tool = "rag_search",
                    arguments = args
                ))
            }

            val toolResponse: ToolExecuteResponse = response.body()

            if (toolResponse.success && toolResponse.result != null) {
                parseSearchResults(toolResponse.result)
            } else {
                emptyList()
            }
        } catch (e: Exception) {
            logger.warn("RAG поиск недоступен: ${e.message}")
            emptyList()
        }
    }

    /**
     * Парсинг результатов поиска из текстового вывода
     */
    private fun parseSearchResults(result: String): List<SourceInfo> {
        val sources = mutableListOf<SourceInfo>()
        val lines = result.lines()

        var currentSource: String? = null
        var currentRelevance: Float? = null
        val contentBuilder = StringBuilder()

        for (line in lines) {
            when {
                line.startsWith("Источник:") -> {
                    // Сохраняем предыдущий результат
                    if (currentSource != null && currentRelevance != null) {
                        sources.add(SourceInfo(
                            file = currentSource,
                            relevance = currentRelevance,
                            snippet = contentBuilder.toString().trim().take(150)
                        ))
                        contentBuilder.clear()
                    }
                    currentSource = line.removePrefix("Источник:").trim()
                }
                line.contains("релевантность:") -> {
                    val match = Regex("""(\d+\.?\d*)""").find(line.substringAfter("релевантность:"))
                    currentRelevance = match?.value?.toFloatOrNull() ?: 0f
                }
                line.startsWith("---") -> {
                    // Разделитель
                }
                currentSource != null -> {
                    contentBuilder.appendLine(line)
                }
            }
        }

        // Последний результат
        if (currentSource != null && currentRelevance != null) {
            sources.add(SourceInfo(
                file = currentSource,
                relevance = currentRelevance,
                snippet = contentBuilder.toString().trim().take(150)
            ))
        }

        return sources
    }

    /**
     * Индексация документов на сервере
     */
    suspend fun indexDocuments(path: String): String {
        return try {
            val response = httpClient.post("$serverUrl/api/tools/execute") {
                contentType(ContentType.Application.Json)
                setBody(ToolExecuteRequest(
                    tool = "rag_index_documents",
                    arguments = "{\"path\":\"$path\"}"
                ))
            }

            val toolResponse: ToolExecuteResponse = response.body()
            toolResponse.result ?: toolResponse.error ?: "Неизвестная ошибка"
        } catch (e: Exception) {
            "Ошибка: ${e.message}"
        }
    }

    /**
     * Информация об индексе
     */
    suspend fun getIndexInfo(): String {
        return try {
            val response = httpClient.post("$serverUrl/api/tools/execute") {
                contentType(ContentType.Application.Json)
                setBody(ToolExecuteRequest(
                    tool = "rag_index_info",
                    arguments = "{}"
                ))
            }

            val toolResponse: ToolExecuteResponse = response.body()
            toolResponse.result ?: toolResponse.error ?: "Неизвестная ошибка"
        } catch (e: Exception) {
            "Ошибка: ${e.message}"
        }
    }

    fun clearHistory() {
        history.clear()
        conversationId = null
        logger.info("История очищена")
    }

    fun getHistory(): List<ChatMessage> = history.toList()

    fun historySize(): Int = history.size

    fun close() {
        httpClient.close()
    }
}
