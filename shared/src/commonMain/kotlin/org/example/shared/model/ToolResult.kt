package org.example.shared.model

import kotlinx.serialization.Serializable

@Serializable
data class ToolResult(
    val toolCallId: String,
    val result: String
)
