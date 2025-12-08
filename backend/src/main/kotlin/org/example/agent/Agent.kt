package org.example.agent

import org.example.data.ConversationRepository
import org.example.data.DeepSeekClient
import org.example.data.InMemoryConversationRepository
import org.example.data.LLMClient
import org.example.model.LLMMessage
import org.example.model.LLMToolCall
import org.example.shared.model.ChatMessage
import org.example.shared.model.ChatResponse
import org.example.shared.model.CollectionSettings
import org.example.shared.model.MessageRole
import org.example.shared.model.ResponseFormat
import org.example.shared.model.ToolCall
import org.example.tools.ToolRegistry
import java.util.UUID

/**
 * AI Агент — оркестратор взаимодействия с LLM.
 *
 * Координирует работу:
 * - ConversationRepository — хранение истории диалогов
 * - PromptBuilder — построение системных промптов
 * - LLMClient — вызовы LLM API
 * - ToolExecutor — выполнение инструментов
 */
class Agent(
    private val llmClient: LLMClient,
    private val conversationRepository: ConversationRepository = InMemoryConversationRepository(),
    private val promptBuilder: PromptBuilder = PromptBuilder(),
    private val toolExecutor: ToolExecutor = ToolExecutor(),
    private val maxToolIterations: Int = MAX_TOOL_ITERATIONS_DEFAULT
) {
    /**
     * Удобный конструктор для обратной совместимости.
     */
    constructor(
        apiKey: String,
        model: String = "deepseek-chat",
        baseUrl: String = "https://api.deepseek.com/v1"
    ) : this(
        llmClient = DeepSeekClient(apiKey, model, baseUrl)
    )

    companion object {
        private const val MAX_TOOL_ITERATIONS_DEFAULT = 5
    }

    suspend fun chat(
        userMessage: String,
        conversationId: String,
        format: ResponseFormat = ResponseFormat.PLAIN,
        collectionSettings: CollectionSettings? = null,
        temperature: Float? = null
    ): ChatResponse {
        // Проверяем, изменился ли формат или настройки
        val previousFormat = conversationRepository.getFormat(conversationId)
        val previousSettings = conversationRepository.getCollectionSettings(conversationId)
        val settingsChanged = previousFormat != format || previousSettings != collectionSettings

        // Сохраняем текущие настройки
        conversationRepository.setFormat(conversationId, format)
        collectionSettings?.let { conversationRepository.setCollectionSettings(conversationId, it) }

        // Строим системный промпт
        val systemPrompt = promptBuilder.buildSystemPrompt(format, collectionSettings)

        // Инициализируем или обновляем диалог
        if (!conversationRepository.hasConversation(conversationId)) {
            conversationRepository.initConversation(
                conversationId,
                LLMMessage(role = "system", content = systemPrompt)
            )
        } else if (settingsChanged) {
            conversationRepository.updateSystemPrompt(conversationId, systemPrompt)
        }

        // Добавляем сообщение пользователя
        conversationRepository.addMessage(conversationId, LLMMessage(role = "user", content = userMessage))

        // Цикл обработки tool calls
        var firstToolCall: LLMToolCall? = null
        val response = processToolCallLoop(conversationId, temperature) { toolCall ->
            if (firstToolCall == null) {
                firstToolCall = toolCall
            }
        }

        // Добавляем финальное сообщение в историю
        conversationRepository.addMessage(conversationId, response)

        return ChatResponse(
            message = ChatMessage(
                id = UUID.randomUUID().toString(),
                role = MessageRole.ASSISTANT,
                content = response.content ?: "",
                timestamp = System.currentTimeMillis(),
                toolCall = firstToolCall?.let { tc ->
                    ToolCall(
                        id = tc.id,
                        name = tc.function.name,
                        arguments = tc.function.arguments
                    )
                }
            ),
            conversationId = conversationId
        )
    }

    /**
     * Цикл обработки tool calls с защитой от бесконечного цикла.
     */
    private suspend fun processToolCallLoop(
        conversationId: String,
        temperature: Float?,
        onToolCall: (LLMToolCall) -> Unit
    ): LLMMessage {
        val tools = ToolRegistry.getAllTools()
        var iterations = 0

        // Получаем историю для вызова LLM
        var history = conversationRepository.getHistory(conversationId)
        var currentResponse = llmClient.chat(history, tools, temperature, conversationId)
        var currentMessage = currentResponse.choices.firstOrNull()?.message
            ?: throw RuntimeException("Пустой ответ от LLM")

        while (currentMessage.tool_calls?.isNotEmpty() == true && iterations < maxToolIterations) {
            iterations++

            // Сохраняем первый tool call для UI
            currentMessage.tool_calls?.firstOrNull()?.let { onToolCall(it) }

            // Фиксим и добавляем ответ ассистента с tool_calls
            val fixedToolCalls = toolExecutor.fixToolCalls(currentMessage.tool_calls!!)
            conversationRepository.addMessage(conversationId, currentMessage.copy(tool_calls = fixedToolCalls))

            // Выполняем инструменты
            val toolResults = toolExecutor.executeToolCalls(fixedToolCalls, conversationId)
            conversationRepository.addMessages(conversationId, toolResults)

            // Получаем обновлённую историю и вызываем LLM снова
            history = conversationRepository.getHistory(conversationId)
            currentResponse = llmClient.chat(history, tools, temperature, conversationId)
            currentMessage = currentResponse.choices.firstOrNull()?.message
                ?: throw RuntimeException("Пустой ответ от LLM")
        }

        return currentMessage
    }

    fun close() {
        llmClient.close()
    }
}
