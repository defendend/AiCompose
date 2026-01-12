package org.example.agent

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.channelFlow
import org.example.data.ConversationRepository
import org.example.data.DeepSeekClient
import org.example.data.InMemoryConversationRepository
import org.example.data.LLMClient
import org.example.model.DeltaToolCall
import org.example.model.LLMMessage
import org.example.model.LLMToolCall
import org.example.model.FunctionCall
import org.example.shared.model.ChatMessage
import org.example.shared.model.ChatResponse
import org.example.shared.model.CollectionSettings
import org.example.shared.model.CompressionSettings
import org.example.shared.model.CompressionStats
import org.example.shared.model.MessageRole
import org.example.shared.model.ResponseFormat
import org.example.shared.model.StreamEvent
import org.example.shared.model.StreamEventType
import org.example.shared.model.TokenUsage
import org.example.shared.model.ToolCall
import org.example.tools.core.ToolRegistry
import java.util.UUID
import org.example.shared.model.CompressionResult as SharedCompressionResult

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
    private val maxToolIterations: Int = MAX_TOOL_ITERATIONS_DEFAULT,
    private var historyCompressor: HistoryCompressor? = null
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
        temperature: Float? = null,
        compressionSettings: CompressionSettings? = null
    ): ChatResponse {
        // Проверяем, изменился ли формат или настройки
        val previousFormat = conversationRepository.getFormat(conversationId)
        val previousSettings = conversationRepository.getCollectionSettings(conversationId)
        val settingsChanged = previousFormat != format || previousSettings != collectionSettings

        // Сохраняем текущие настройки
        conversationRepository.setFormat(conversationId, format)
        collectionSettings?.let { conversationRepository.setCollectionSettings(conversationId, it) }

        // Настраиваем сжатие если указано
        var compressionResult: HistoryCompressor.CompressionResult? = null
        compressionSettings?.let { settings ->
            conversationRepository.setCompressionSettings(conversationId, settings)

            // Инициализируем компрессор если нужно
            if (settings.enabled && historyCompressor == null) {
                historyCompressor = HistoryCompressor(
                    llmClient,
                    HistoryCompressor.CompressionConfig(
                        enabled = settings.enabled,
                        messageThreshold = settings.messageThreshold,
                        keepRecentMessages = settings.keepRecentMessages
                    )
                )
            }
        }

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

        // Проверяем, нужно ли сжатие ПЕРЕД вызовом LLM
        val currentSettings = conversationRepository.getCompressionSettings(conversationId)
        if (currentSettings?.enabled == true) {
            val compressor = historyCompressor ?: HistoryCompressor(
                llmClient,
                HistoryCompressor.CompressionConfig(
                    enabled = true,
                    messageThreshold = currentSettings.messageThreshold,
                    keepRecentMessages = currentSettings.keepRecentMessages
                )
            ).also { historyCompressor = it }

            val history = conversationRepository.getHistory(conversationId)
            if (compressor.needsCompression(history)) {
                val (compressedHistory, result) = compressor.compress(history, conversationId)
                if (result.compressed) {
                    conversationRepository.replaceHistory(conversationId, compressedHistory)
                    compressionResult = result
                }
            }
        }

        // Цикл обработки tool calls
        var firstToolCall: LLMToolCall? = null
        var totalUsage: TokenUsage? = null

        val response = processToolCallLoop(conversationId, temperature,
            onToolCall = { toolCall ->
                if (firstToolCall == null) {
                    firstToolCall = toolCall
                }
            },
            onUsage = { usage ->
                totalUsage = totalUsage?.let { it + usage } ?: usage
            }
        )

        // Добавляем финальное сообщение в историю
        conversationRepository.addMessage(conversationId, response)

        // Собираем статистику сжатия
        val compressionStats = compressionResult?.let { result ->
            val stats = historyCompressor?.getStats(conversationId)
            CompressionStats(
                totalCompressions = stats?.totalCompressions ?: 1,
                totalTokensSaved = stats?.totalTokensSaved ?: result.estimatedTokensSaved,
                currentSummary = result.summary,
                lastCompressionResult = SharedCompressionResult(
                    compressed = result.compressed,
                    originalMessageCount = result.originalMessageCount,
                    compressedMessageCount = result.compressedMessageCount,
                    summary = result.summary,
                    estimatedTokensSaved = result.estimatedTokensSaved
                )
            )
        }

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
                },
                tokenUsage = totalUsage
            ),
            conversationId = conversationId,
            tokenUsage = totalUsage,
            compressionStats = compressionStats
        )
    }

    /**
     * Цикл обработки tool calls с защитой от бесконечного цикла.
     */
    private suspend fun processToolCallLoop(
        conversationId: String,
        temperature: Float?,
        onToolCall: (LLMToolCall) -> Unit,
        onUsage: (TokenUsage) -> Unit
    ): LLMMessage {
        val tools = ToolRegistry.getAllTools()
        var iterations = 0

        // Получаем историю для вызова LLM
        var history = conversationRepository.getHistory(conversationId)
        var currentResponse = llmClient.chat(history, tools, temperature, conversationId)

        // Собираем usage
        currentResponse.usage?.let { usage ->
            onUsage(TokenUsage.fromUsage(usage.prompt_tokens, usage.completion_tokens))
        }

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

            // Собираем usage
            currentResponse.usage?.let { usage ->
                onUsage(TokenUsage.fromUsage(usage.prompt_tokens, usage.completion_tokens))
            }

            currentMessage = currentResponse.choices.firstOrNull()?.message
                ?: throw RuntimeException("Пустой ответ от LLM")
        }

        return currentMessage
    }

    /**
     * Streaming версия chat - возвращает Flow событий.
     */
    fun chatStream(
        userMessage: String,
        conversationId: String,
        format: ResponseFormat = ResponseFormat.PLAIN,
        collectionSettings: CollectionSettings? = null,
        temperature: Float? = null
    ): Flow<StreamEvent> = channelFlow {
        val messageId = UUID.randomUUID().toString()

        // Send start event
        send(StreamEvent(
            type = StreamEventType.START,
            conversationId = conversationId,
            messageId = messageId
        ))

        try {
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

            // Streaming loop с tool calls
            val tools = ToolRegistry.getAllTools()
            var iterations = 0
            var continueLoop = true

            while (continueLoop && iterations < maxToolIterations) {
                val history = conversationRepository.getHistory(conversationId)
                val contentBuffer = StringBuilder()
                val toolCallsBuffer = mutableMapOf<Int, ToolCallBuilder>()
                var finishReason: String? = null

                // Собираем streaming ответ
                llmClient.chatStream(history, tools, temperature, conversationId).collect { chunk ->
                    val choice = chunk.choices.firstOrNull() ?: return@collect

                    // Content delta
                    choice.delta?.content?.let { content ->
                        contentBuffer.append(content)
                        send(StreamEvent(
                            type = StreamEventType.CONTENT,
                            content = content,
                            conversationId = conversationId,
                            messageId = messageId
                        ))
                    }

                    // Tool calls delta
                    choice.delta?.tool_calls?.forEach { deltaToolCall ->
                        val builder = toolCallsBuffer.getOrPut(deltaToolCall.index) { ToolCallBuilder() }
                        builder.update(deltaToolCall)
                    }

                    finishReason = choice.finish_reason
                }

                // Обрабатываем результат
                if (toolCallsBuffer.isNotEmpty()) {
                    val toolCalls = toolCallsBuffer.values.mapNotNull { it.build() }

                    if (toolCalls.isNotEmpty()) {
                        iterations++

                        // Сохраняем ответ ассистента с tool calls
                        val fixedToolCalls = toolExecutor.fixToolCalls(toolCalls)
                        conversationRepository.addMessage(conversationId, LLMMessage(
                            role = "assistant",
                            content = contentBuffer.toString().takeIf { it.isNotEmpty() },
                            tool_calls = fixedToolCalls
                        ))

                        // Выполняем инструменты и send результаты
                        for (toolCall in fixedToolCalls) {
                            send(StreamEvent(
                                type = StreamEventType.TOOL_CALL,
                                toolCall = ToolCall(
                                    id = toolCall.id,
                                    name = toolCall.function.name,
                                    arguments = toolCall.function.arguments
                                ),
                                conversationId = conversationId,
                                messageId = messageId
                            ))

                            // Отправляем PROCESSING перед выполнением инструмента (heartbeat)
                            send(StreamEvent(
                                type = StreamEventType.PROCESSING,
                                content = "Выполняется: ${toolCall.function.name}",
                                conversationId = conversationId,
                                messageId = messageId
                            ))

                            val result = ToolRegistry.executeTool(toolCall.function.name, toolCall.function.arguments)

                            send(StreamEvent(
                                type = StreamEventType.TOOL_RESULT,
                                toolResult = result,
                                conversationId = conversationId,
                                messageId = messageId
                            ))

                            conversationRepository.addMessage(conversationId, LLMMessage(
                                role = "tool",
                                content = result,
                                tool_call_id = toolCall.id
                            ))
                        }

                        // Продолжаем цикл для получения финального ответа
                        continueLoop = true
                    } else {
                        continueLoop = false
                    }
                } else {
                    // Нет tool calls - сохраняем финальный ответ
                    if (contentBuffer.isNotEmpty()) {
                        conversationRepository.addMessage(conversationId, LLMMessage(
                            role = "assistant",
                            content = contentBuffer.toString()
                        ))
                    }
                    continueLoop = false
                }
            }

            // Если достигли лимита итераций, делаем финальный вызов БЕЗ инструментов
            if (iterations >= maxToolIterations) {
                send(StreamEvent(
                    type = StreamEventType.PROCESSING,
                    content = "Формирование итогового ответа...",
                    conversationId = conversationId,
                    messageId = messageId
                ))

                // Добавляем инструкцию для подведения итогов
                conversationRepository.addMessage(conversationId, LLMMessage(
                    role = "user",
                    content = "На основе всей собранной информации выше, дай краткий структурированный ответ на мой вопрос. Не вызывай больше инструменты, просто подведи итог."
                ))

                val history = conversationRepository.getHistory(conversationId)
                val finalContentBuffer = StringBuilder()

                // Вызываем LLM без инструментов для финального ответа
                llmClient.chatStream(history, emptyList(), temperature, conversationId).collect { chunk ->
                    val choice = chunk.choices.firstOrNull() ?: return@collect
                    choice.delta?.content?.let { content ->
                        finalContentBuffer.append(content)
                        send(StreamEvent(
                            type = StreamEventType.CONTENT,
                            content = content,
                            conversationId = conversationId,
                            messageId = messageId
                        ))
                    }
                }

                if (finalContentBuffer.isNotEmpty()) {
                    conversationRepository.addMessage(conversationId, LLMMessage(
                        role = "assistant",
                        content = finalContentBuffer.toString()
                    ))
                }
            }

            // Send done event
            send(StreamEvent(
                type = StreamEventType.DONE,
                conversationId = conversationId,
                messageId = messageId
            ))

        } catch (e: Exception) {
            send(StreamEvent(
                type = StreamEventType.ERROR,
                error = e.message ?: "Неизвестная ошибка",
                conversationId = conversationId,
                messageId = messageId
            ))
        }
    }

    fun close() {
        llmClient.close()
    }
}

/**
 * Вспомогательный класс для сборки tool call из delta-чанков.
 */
private class ToolCallBuilder {
    private var id: String? = null
    private var type: String? = null
    private var functionName: String? = null
    private val functionArguments = StringBuilder()

    fun update(delta: DeltaToolCall) {
        delta.id?.let { id = it }
        delta.type?.let { type = it }
        delta.function?.name?.let { functionName = it }
        delta.function?.arguments?.let { functionArguments.append(it) }
    }

    fun build(): LLMToolCall? {
        val builtId = id ?: return null
        val builtName = functionName ?: return null

        return LLMToolCall(
            id = builtId,
            type = type ?: "function",
            function = FunctionCall(
                name = builtName,
                arguments = functionArguments.toString()
            )
        )
    }
}
