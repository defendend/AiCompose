package org.example.ui

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.example.logging.AppLogger
import org.example.network.ChatApiClient
import org.example.shared.model.ChatMessage
import org.example.shared.model.CollectionSettings
import org.example.shared.model.CompressionSettings
import org.example.shared.model.MessageRole
import org.example.shared.model.ResponseFormat
import org.example.shared.model.StreamEventType
import java.util.UUID

class ChatViewModel(
    private val apiClient: ChatApiClient = ChatApiClient()
) {
    private val scope = CoroutineScope(Dispatchers.IO)

    private val _messages = MutableStateFlow<List<ChatMessage>>(emptyList())
    val messages: StateFlow<List<ChatMessage>> = _messages.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val _isStreaming = MutableStateFlow(false)
    val isStreaming: StateFlow<Boolean> = _isStreaming.asStateFlow()

    private val _streamingContent = MutableStateFlow("")
    val streamingContent: StateFlow<String> = _streamingContent.asStateFlow()

    private val _useStreaming = MutableStateFlow(true)  // Streaming включён по умолчанию
    val useStreaming: StateFlow<Boolean> = _useStreaming.asStateFlow()

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()

    private val _responseFormat = MutableStateFlow(ResponseFormat.PLAIN)
    val responseFormat: StateFlow<ResponseFormat> = _responseFormat.asStateFlow()

    private val _collectionSettings = MutableStateFlow(CollectionSettings.DISABLED)
    val collectionSettings: StateFlow<CollectionSettings> = _collectionSettings.asStateFlow()

    private val _temperature = MutableStateFlow<Float?>(null)
    val temperature: StateFlow<Float?> = _temperature.asStateFlow()

    private val _compressionSettings = MutableStateFlow<CompressionSettings?>(null)
    val compressionSettings: StateFlow<CompressionSettings?> = _compressionSettings.asStateFlow()

    private var conversationId: String? = null

    fun setResponseFormat(format: ResponseFormat) {
        _responseFormat.value = format
        AppLogger.info("ChatViewModel", "Формат ответа изменён на: $format")
    }

    fun setCollectionSettings(settings: CollectionSettings) {
        _collectionSettings.value = settings
        val hasCustomPrompt = settings.customSystemPrompt.isNotBlank()
        AppLogger.info("ChatViewModel", "Режим сбора данных: ${settings.mode}, enabled=${settings.enabled}, customSystemPrompt=${if (hasCustomPrompt) "задан (${settings.customSystemPrompt.take(30)}...)" else "пусто"}")
    }

    fun setTemperature(temp: Float?) {
        _temperature.value = temp
        AppLogger.info("ChatViewModel", "Temperature изменён на: ${temp ?: "default"}")
    }

    fun setCompressionSettings(settings: CompressionSettings?) {
        _compressionSettings.value = settings
        AppLogger.info("ChatViewModel", "Compression: ${settings?.enabled ?: "выключено"}, threshold=${settings?.messageThreshold}")
    }

    fun setUseStreaming(enabled: Boolean) {
        _useStreaming.value = enabled
        AppLogger.info("ChatViewModel", "Streaming ${if (enabled) "включён" else "выключен"}")
    }

    fun sendMessage(text: String) {
        AppLogger.info("ChatViewModel", "sendMessage called, useStreaming=${_useStreaming.value}")
        if (_useStreaming.value) {
            sendMessageStreaming(text)
        } else {
            sendMessageClassic(text)
        }
    }

    /**
     * Отправка сообщения с использованием streaming.
     */
    private fun sendMessageStreaming(text: String) {
        if (text.isBlank()) return

        val userMessage = ChatMessage(
            id = UUID.randomUUID().toString(),
            role = MessageRole.USER,
            content = text,
            timestamp = System.currentTimeMillis()
        )

        _messages.value = _messages.value + userMessage
        _isLoading.value = true
        _isStreaming.value = true
        _streamingContent.value = ""
        _error.value = null

        scope.launch {
            val currentSettings = _collectionSettings.value
            AppLogger.info(
                "ChatViewModel",
                "Отправка streaming сообщения: $text (формат: ${_responseFormat.value}, режим сбора: ${currentSettings.mode})"
            )

            val shouldSendSettings = currentSettings.enabled || currentSettings.customSystemPrompt.isNotBlank()

            var messageId: String? = null
            val contentBuilder = StringBuilder()

            try {
                apiClient.sendMessageStream(
                    text = text,
                    conversationId = conversationId,
                    responseFormat = _responseFormat.value,
                    collectionSettings = if (shouldSendSettings) currentSettings else null,
                    temperature = _temperature.value
                ).flowOn(Dispatchers.IO)
                .catch { e ->
                    AppLogger.error("ChatViewModel", "Streaming ошибка: ${e.message}")
                    withContext(Dispatchers.Main) {
                        _error.value = e.message ?: "Ошибка streaming"
                    }
                }.collect { event ->
                    withContext(Dispatchers.Main) {
                        when (event.type) {
                            StreamEventType.START -> {
                                conversationId = event.conversationId
                                messageId = event.messageId
                                AppLogger.info("ChatViewModel", "Streaming начат: ${event.messageId}")
                            }

                            StreamEventType.CONTENT -> {
                                event.content?.let { content ->
                                    contentBuilder.append(content)
                                    _streamingContent.value = contentBuilder.toString()
                                    AppLogger.info("ChatViewModel", "Content update: ${contentBuilder.length} chars")
                                }
                            }

                            StreamEventType.TOOL_CALL -> {
                                event.toolCall?.let { toolCall ->
                                    AppLogger.info("ChatViewModel", "Агент вызывает инструмент: ${toolCall.name}")
                                }
                            }

                            StreamEventType.TOOL_RESULT -> {
                                AppLogger.info("ChatViewModel", "Результат инструмента получен")
                            }

                            StreamEventType.DONE -> {
                                // Добавляем финальное сообщение в список
                                val assistantMessage = ChatMessage(
                                    id = messageId ?: UUID.randomUUID().toString(),
                                    role = MessageRole.ASSISTANT,
                                    content = contentBuilder.toString(),
                                    timestamp = System.currentTimeMillis()
                                )
                                _messages.value = _messages.value + assistantMessage
                                _streamingContent.value = ""
                                AppLogger.info("ChatViewModel", "Streaming завершён, content: ${contentBuilder.length} chars")
                            }

                            StreamEventType.ERROR -> {
                                AppLogger.error("ChatViewModel", "Ошибка от сервера: ${event.error}")
                                _error.value = event.error ?: "Ошибка сервера"
                            }
                        }
                    }
                }
            } catch (e: Exception) {
                AppLogger.error("ChatViewModel", "Исключение при streaming: ${e.message}")
                withContext(Dispatchers.Main) {
                    _error.value = e.message ?: "Неизвестная ошибка"
                }
            }

            _isLoading.value = false
            _isStreaming.value = false
        }
    }

    /**
     * Классическая отправка сообщения (без streaming).
     */
    private fun sendMessageClassic(text: String) {
        if (text.isBlank()) return

        val userMessage = ChatMessage(
            id = UUID.randomUUID().toString(),
            role = MessageRole.USER,
            content = text,
            timestamp = System.currentTimeMillis()
        )

        _messages.value = _messages.value + userMessage
        _isLoading.value = true
        _error.value = null

        scope.launch {
            val currentSettings = _collectionSettings.value
            AppLogger.info(
                "ChatViewModel",
                "Отправка сообщения: $text (формат: ${_responseFormat.value}, режим сбора: ${currentSettings.mode})"
            )

            // Отправляем настройки если включён режим сбора ИЛИ задан кастомный системный промпт
            val shouldSendSettings = currentSettings.enabled || currentSettings.customSystemPrompt.isNotBlank()

            apiClient.sendMessage(
                text = text,
                conversationId = conversationId,
                responseFormat = _responseFormat.value,
                collectionSettings = if (shouldSendSettings) currentSettings else null,
                temperature = _temperature.value,
                compressionSettings = _compressionSettings.value
            )
                .onSuccess { response ->
                    conversationId = response.conversationId
                    _messages.value = _messages.value + response.message

                    response.message.toolCall?.let { toolCall ->
                        AppLogger.info(
                            "ChatViewModel",
                            "Агент вызвал инструмент: ${toolCall.name}"
                        )
                    }

                    response.tokenUsage?.let { usage ->
                        AppLogger.info(
                            "ChatViewModel",
                            "Токены: ${usage.toDetailedString()}"
                        )
                    }

                    response.compressionStats?.let { stats ->
                        AppLogger.info(
                            "ChatViewModel",
                            "Сжатие: сохранено ~${stats.totalTokensSaved} токенов, сжатий: ${stats.totalCompressions}"
                        )
                    }
                }
                .onFailure { e ->
                    AppLogger.error("ChatViewModel", "Ошибка: ${e.message}")
                    _error.value = e.message ?: "Неизвестная ошибка"
                }

            _isLoading.value = false
        }
    }

    fun clearError() {
        _error.value = null
    }

    fun clearChat() {
        _messages.value = emptyList()
        conversationId = null
        AppLogger.info("ChatViewModel", "Чат очищен")
    }
}
