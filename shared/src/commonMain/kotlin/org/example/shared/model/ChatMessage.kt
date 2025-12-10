package org.example.shared.model

import kotlinx.serialization.Serializable

@Serializable
data class ChatMessage(
    val id: String,
    val role: MessageRole,
    val content: String,
    val timestamp: Long,
    val toolCall: ToolCall? = null,
    val toolResult: ToolResult? = null,
    val tokenUsage: TokenUsage? = null
)
