package org.example.agent

import org.example.logging.ServerLogger
import org.example.model.LLMMessage
import org.example.model.LLMToolCall
import org.example.tools.ToolRegistry

/**
 * Исполнитель инструментов агента.
 * Отвечает за выполнение tool calls и формирование результатов.
 */
class ToolExecutor {

    /**
     * Выполняет список tool calls и возвращает сообщения с результатами.
     */
    suspend fun executeToolCalls(
        toolCalls: List<LLMToolCall>,
        conversationId: String
    ): List<LLMMessage> {
        return toolCalls.map { toolCall ->
            executeToolCall(toolCall, conversationId)
        }
    }

    /**
     * Выполняет один tool call и возвращает сообщение с результатом.
     */
    suspend fun executeToolCall(
        toolCall: LLMToolCall,
        conversationId: String
    ): LLMMessage {
        val toolName = toolCall.function.name
        val toolArgs = toolCall.function.arguments

        ServerLogger.logToolCall(toolName, toolArgs, conversationId)

        val startTime = System.currentTimeMillis()
        val toolResult = ToolRegistry.executeTool(toolName, toolArgs)
        val duration = System.currentTimeMillis() - startTime

        ServerLogger.logToolResult(toolName, toolResult, duration, conversationId)

        return LLMMessage(
            role = "tool",
            content = toolResult,
            tool_call_id = toolCall.id
        )
    }

    /**
     * Фиксит tool calls, убеждаясь что type заполнен (DeepSeek может возвращать null).
     */
    fun fixToolCalls(toolCalls: List<LLMToolCall>): List<LLMToolCall> {
        return toolCalls.map { tc ->
            LLMToolCall(
                id = tc.id,
                type = tc.type ?: "function",
                function = tc.function
            )
        }
    }
}
