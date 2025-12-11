package org.example.shared.model

import kotlinx.serialization.Serializable

@Serializable
data class ChatRequest(
    val message: String,
    val conversationId: String? = null,
    val responseFormat: ResponseFormat = ResponseFormat.PLAIN,
    val collectionSettings: CollectionSettings? = null,
    val temperature: Float? = null,
    val compressionSettings: CompressionSettings? = null
)
