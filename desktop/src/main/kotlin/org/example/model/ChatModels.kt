package org.example.model

import kotlinx.serialization.Serializable
import java.util.UUID

@Serializable
data class ChatMessage(
    val id: String = UUID.randomUUID().toString(),
    val role: MessageRole,
    val content: String,
    val timestamp: Long = System.currentTimeMillis(),
    val toolCall: ToolCall? = null,
    val toolResult: ToolResult? = null
)

@Serializable
enum class MessageRole {
    USER,
    ASSISTANT,
    SYSTEM,
    TOOL
}

@Serializable
data class ToolCall(
    val id: String,
    val name: String,
    val arguments: String
)

@Serializable
data class ToolResult(
    val toolCallId: String,
    val result: String
)

@Serializable
enum class ResponseFormat {
    PLAIN,      // Простой текст
    JSON,       // Структурированный JSON
    MARKDOWN    // Markdown форматирование
}

@Serializable
data class ChatRequest(
    val message: String,
    val conversationId: String? = null,
    val responseFormat: ResponseFormat = ResponseFormat.PLAIN,
    val collectionSettings: CollectionSettings? = null
)

@Serializable
data class ChatResponse(
    val message: ChatMessage,
    val conversationId: String
)

/**
 * Модель для парсинга структурированного JSON ответа от агента
 */
@Serializable
data class StructuredResponse(
    val topic: String = "",
    val period: String = "",
    val summary: String = "",
    val main_content: String = "",
    val interesting_facts: List<String> = emptyList(),
    val related_topics: List<String> = emptyList(),
    val quote: String = ""
)
