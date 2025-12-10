package org.example.model

import kotlinx.serialization.Serializable
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.UUID

@Serializable
data class ServerLogEntry(
    val id: String = UUID.randomUUID().toString(),
    val timestamp: String = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss.SSS")),
    val level: LogLevel,
    val category: LogCategory,
    val message: String,
    val details: LogDetails? = null
)

@Serializable
enum class LogLevel {
    DEBUG, INFO, WARNING, ERROR
}

@Serializable
enum class LogCategory {
    REQUEST,          // Входящий запрос
    RESPONSE,         // Исходящий ответ
    LLM_REQUEST,      // Запрос к DeepSeek (превью)
    LLM_RESPONSE,     // Ответ от DeepSeek (превью)
    LLM_RAW_REQUEST,  // Полный JSON запрос к DeepSeek
    LLM_RAW_RESPONSE, // Полный JSON ответ от DeepSeek
    TOOL_CALL,        // Вызов инструмента
    TOOL_RESULT,      // Результат инструмента
    TOKEN_USAGE,      // Использование токенов
    SYSTEM            // Системные события
}

@Serializable
data class LogDetails(
    val method: String? = null,
    val path: String? = null,
    val statusCode: Int? = null,
    val durationMs: Long? = null,
    val requestBody: String? = null,
    val responseBody: String? = null,
    val toolName: String? = null,
    val toolArguments: String? = null,
    val toolResult: String? = null,
    val error: String? = null,
    val conversationId: String? = null,
    val model: String? = null,
    val tokensUsed: Int? = null,
    // Детальная информация о токенах
    val promptTokens: Int? = null,
    val completionTokens: Int? = null,
    val totalTokens: Int? = null
)

@Serializable
data class LogsResponse(
    val logs: List<ServerLogEntry>,
    val totalCount: Int
)
