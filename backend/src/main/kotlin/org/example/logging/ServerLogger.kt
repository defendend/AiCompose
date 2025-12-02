package org.example.logging

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import org.example.model.*
import org.slf4j.LoggerFactory

object ServerLogger {
    private val logger = LoggerFactory.getLogger(ServerLogger::class.java)

    private val _logs = MutableStateFlow<List<ServerLogEntry>>(emptyList())
    val logs: StateFlow<List<ServerLogEntry>> = _logs.asStateFlow()

    private const val MAX_LOGS = 500

    fun logRequest(
        method: String,
        path: String,
        body: String?,
        conversationId: String? = null
    ): String {
        val entry = ServerLogEntry(
            level = LogLevel.INFO,
            category = LogCategory.REQUEST,
            message = "$method $path",
            details = LogDetails(
                method = method,
                path = path,
                requestBody = body,
                conversationId = conversationId
            )
        )
        addLog(entry)
        logger.info("[REQUEST] $method $path - body: ${body?.take(200)}")
        return entry.id
    }

    fun logResponse(
        requestId: String,
        statusCode: Int,
        body: String?,
        durationMs: Long
    ) {
        val entry = ServerLogEntry(
            level = if (statusCode >= 400) LogLevel.ERROR else LogLevel.INFO,
            category = LogCategory.RESPONSE,
            message = "Response $statusCode (${durationMs}ms)",
            details = LogDetails(
                statusCode = statusCode,
                durationMs = durationMs,
                responseBody = body?.take(2000)
            )
        )
        addLog(entry)
        logger.info("[RESPONSE] Status: $statusCode, Duration: ${durationMs}ms")
    }

    fun logLLMRequest(
        model: String,
        messagesCount: Int,
        toolsCount: Int,
        conversationId: String?
    ) {
        val entry = ServerLogEntry(
            level = LogLevel.DEBUG,
            category = LogCategory.LLM_REQUEST,
            message = "LLM Request: $messagesCount messages, $toolsCount tools",
            details = LogDetails(
                model = model,
                conversationId = conversationId
            )
        )
        addLog(entry)
        logger.debug("[LLM_REQUEST] Model: $model, Messages: $messagesCount, Tools: $toolsCount")
    }

    fun logLLMResponse(
        model: String,
        hasToolCalls: Boolean,
        content: String?,
        durationMs: Long,
        conversationId: String?
    ) {
        val entry = ServerLogEntry(
            level = LogLevel.DEBUG,
            category = LogCategory.LLM_RESPONSE,
            message = if (hasToolCalls) "LLM Response with tool calls (${durationMs}ms)" else "LLM Response (${durationMs}ms)",
            details = LogDetails(
                model = model,
                durationMs = durationMs,
                responseBody = content?.take(500),
                conversationId = conversationId
            )
        )
        addLog(entry)
        logger.debug("[LLM_RESPONSE] HasToolCalls: $hasToolCalls, Duration: ${durationMs}ms")
    }

    fun logToolCall(
        toolName: String,
        arguments: String,
        conversationId: String?
    ) {
        val entry = ServerLogEntry(
            level = LogLevel.INFO,
            category = LogCategory.TOOL_CALL,
            message = "Tool call: $toolName",
            details = LogDetails(
                toolName = toolName,
                toolArguments = arguments,
                conversationId = conversationId
            )
        )
        addLog(entry)
        logger.info("[TOOL_CALL] $toolName($arguments)")
    }

    fun logToolResult(
        toolName: String,
        result: String,
        durationMs: Long,
        conversationId: String?
    ) {
        val entry = ServerLogEntry(
            level = LogLevel.INFO,
            category = LogCategory.TOOL_RESULT,
            message = "Tool result: $toolName (${durationMs}ms)",
            details = LogDetails(
                toolName = toolName,
                toolResult = result.take(500),
                durationMs = durationMs,
                conversationId = conversationId
            )
        )
        addLog(entry)
        logger.info("[TOOL_RESULT] $toolName -> $result (${durationMs}ms)")
    }

    fun logError(
        message: String,
        error: Throwable?,
        category: LogCategory = LogCategory.SYSTEM
    ) {
        val entry = ServerLogEntry(
            level = LogLevel.ERROR,
            category = category,
            message = message,
            details = LogDetails(
                error = error?.let { "${it.javaClass.simpleName}: ${it.message}" }
            )
        )
        addLog(entry)
        logger.error("[ERROR] $message", error)
    }

    fun logSystem(message: String, level: LogLevel = LogLevel.INFO) {
        val entry = ServerLogEntry(
            level = level,
            category = LogCategory.SYSTEM,
            message = message
        )
        addLog(entry)
        when (level) {
            LogLevel.DEBUG -> logger.debug("[SYSTEM] $message")
            LogLevel.INFO -> logger.info("[SYSTEM] $message")
            LogLevel.WARNING -> logger.warn("[SYSTEM] $message")
            LogLevel.ERROR -> logger.error("[SYSTEM] $message")
        }
    }

    private fun addLog(entry: ServerLogEntry) {
        _logs.update { currentLogs ->
            val newLogs = currentLogs + entry
            if (newLogs.size > MAX_LOGS) {
                newLogs.drop(newLogs.size - MAX_LOGS)
            } else {
                newLogs
            }
        }
    }

    fun getLogs(
        limit: Int = 100,
        offset: Int = 0,
        level: LogLevel? = null,
        category: LogCategory? = null
    ): LogsResponse {
        val filtered = _logs.value
            .let { logs -> level?.let { l -> logs.filter { it.level == l } } ?: logs }
            .let { logs -> category?.let { c -> logs.filter { it.category == c } } ?: logs }

        return LogsResponse(
            logs = filtered.drop(offset).take(limit),
            totalCount = filtered.size
        )
    }

    fun clear() {
        _logs.value = emptyList()
        logger.info("[SYSTEM] Logs cleared")
    }
}
