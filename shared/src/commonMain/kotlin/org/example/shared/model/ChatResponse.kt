package org.example.shared.model

import kotlinx.serialization.Serializable

@Serializable
data class ChatResponse(
    val message: ChatMessage,
    val conversationId: String,
    val tokenUsage: TokenUsage? = null
)
