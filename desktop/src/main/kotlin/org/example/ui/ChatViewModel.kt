package org.example.ui

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import org.example.logging.AppLogger
import org.example.model.ChatMessage
import org.example.model.MessageRole
import org.example.model.ResponseFormat
import org.example.network.ChatApiClient

class ChatViewModel(
    private val apiClient: ChatApiClient = ChatApiClient()
) {
    private val scope = CoroutineScope(Dispatchers.Default)

    private val _messages = MutableStateFlow<List<ChatMessage>>(emptyList())
    val messages: StateFlow<List<ChatMessage>> = _messages.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()

    private val _responseFormat = MutableStateFlow(ResponseFormat.PLAIN)
    val responseFormat: StateFlow<ResponseFormat> = _responseFormat.asStateFlow()

    private var conversationId: String? = null

    fun setResponseFormat(format: ResponseFormat) {
        _responseFormat.value = format
        AppLogger.info("ChatViewModel", "Формат ответа изменён на: $format")
    }

    fun sendMessage(text: String) {
        if (text.isBlank()) return

        val userMessage = ChatMessage(
            role = MessageRole.USER,
            content = text
        )

        _messages.value = _messages.value + userMessage
        _isLoading.value = true
        _error.value = null

        scope.launch {
            AppLogger.info("ChatViewModel", "Отправка сообщения пользователя: $text (формат: ${_responseFormat.value})")

            apiClient.sendMessage(text, conversationId, _responseFormat.value)
                .onSuccess { response ->
                    conversationId = response.conversationId
                    _messages.value = _messages.value + response.message

                    if (response.message.toolCall != null) {
                        AppLogger.info(
                            "ChatViewModel",
                            "Агент вызвал инструмент: ${response.message.toolCall.name}"
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
