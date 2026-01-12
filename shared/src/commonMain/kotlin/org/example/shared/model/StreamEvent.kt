package org.example.shared.model

import kotlinx.serialization.Serializable

/**
 * События для Server-Sent Events (SSE) стриминга.
 */

/**
 * Тип SSE события.
 */
enum class StreamEventType {
    /** Начало стриминга */
    START,
    /** Чанк текста */
    CONTENT,
    /** Вызов инструмента (tool call) */
    TOOL_CALL,
    /** Результат выполнения инструмента */
    TOOL_RESULT,
    /** Выполняется обработка (heartbeat для поддержания соединения) */
    PROCESSING,
    /** Завершение стриминга */
    DONE,
    /** Ошибка */
    ERROR
}

/**
 * SSE событие для клиента.
 */
@Serializable
data class StreamEvent(
    val type: StreamEventType,
    val content: String? = null,
    val toolCall: ToolCall? = null,
    val toolResult: String? = null,
    val conversationId: String? = null,
    val messageId: String? = null,
    val error: String? = null
)

/**
 * Запрос на streaming чат.
 */
@Serializable
data class ChatStreamRequest(
    val message: String,
    val conversationId: String? = null,
    val responseFormat: ResponseFormat = ResponseFormat.PLAIN,
    val collectionSettings: CollectionSettings? = null,
    val temperature: Float? = null
)

/**
 * Ответ health check.
 */
@Serializable
data class HealthCheckResponse(
    val status: String,
    val services: Map<String, ServiceHealth> = emptyMap(),
    val timestamp: Long = 0
)

/**
 * Состояние отдельного сервиса.
 */
@Serializable
data class ServiceHealth(
    val status: String,
    val message: String? = null,
    val latencyMs: Long? = null
)
