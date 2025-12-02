package org.example.model

import kotlinx.serialization.Serializable

@Serializable
data class ServerLogEntry(
    val id: String,
    val timestamp: String,
    val level: ServerLogLevel,
    val category: ServerLogCategory,
    val message: String,
    val details: ServerLogDetails? = null
)

@Serializable
enum class ServerLogLevel {
    DEBUG, INFO, WARNING, ERROR
}

@Serializable
enum class ServerLogCategory {
    REQUEST,
    RESPONSE,
    LLM_REQUEST,
    LLM_RESPONSE,
    TOOL_CALL,
    TOOL_RESULT,
    SYSTEM
}

@Serializable
data class ServerLogDetails(
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
    val tokensUsed: Int? = null
)

@Serializable
data class ServerLogsResponse(
    val logs: List<ServerLogEntry>,
    val totalCount: Int
)
