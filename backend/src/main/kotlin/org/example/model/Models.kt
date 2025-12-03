package org.example.model

import kotlinx.serialization.Serializable
import java.util.UUID

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
    val responseFormat: ResponseFormat = ResponseFormat.PLAIN
)

@Serializable
data class ChatResponse(
    val message: ChatMessage,
    val conversationId: String
)

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

// Модели для LLM API
@Serializable
data class LLMRequest(
    val model: String,
    val messages: List<LLMMessage>,
    val tools: List<Tool>? = null,
    val max_tokens: Int = 4096
)

@Serializable
data class LLMMessage(
    val role: String,
    val content: String? = null,
    val tool_calls: List<LLMToolCall>? = null,
    val tool_call_id: String? = null
)

@Serializable
data class LLMToolCall(
    val id: String,
    val type: String? = null,
    val function: FunctionCall
)

@Serializable
data class FunctionCall(
    val name: String,
    val arguments: String
)

@Serializable
data class Tool(
    val type: String,
    val function: FunctionDefinition
)

@Serializable
data class FunctionDefinition(
    val name: String,
    val description: String,
    val parameters: FunctionParameters
)

@Serializable
data class FunctionParameters(
    val type: String,
    val properties: Map<String, PropertyDefinition>,
    val required: List<String> = emptyList()
)

@Serializable
data class PropertyDefinition(
    val type: String,
    val description: String
)

@Serializable
data class LLMResponse(
    val id: String? = null,
    val choices: List<Choice>
)

@Serializable
data class Choice(
    val message: LLMMessage,
    val finish_reason: String? = null
)
