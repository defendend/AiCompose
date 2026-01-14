package org.example.ui

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import org.example.logging.AppLogger
import org.example.network.ChatApiClient

/**
 * Сообщение в чате поддержки.
 */
data class SupportMessage(
    val id: String,
    val content: String,
    val isUser: Boolean,
    val ticketId: String? = null,
    val durationMs: Long? = null
)

/**
 * ViewModel для экрана поддержки.
 */
class SupportViewModel(
    private val apiClient: ChatApiClient
) {
    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    private val json = Json { ignoreUnknownKeys = true }

    private val _messages = MutableStateFlow<List<SupportMessage>>(emptyList())
    val messages: StateFlow<List<SupportMessage>> = _messages.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val _currentTicketId = MutableStateFlow<String?>(null)
    val currentTicketId: StateFlow<String?> = _currentTicketId.asStateFlow()

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()

    init {
        // Приветственное сообщение
        _messages.value = listOf(
            SupportMessage(
                id = "welcome",
                content = """Добро пожаловать в службу поддержки AiCompose! 👋

Я могу помочь вам с:
• Просмотром и поиском тикетов
• Ответами на вопросы (FAQ)
• Информацией о пользователях
• Созданием новых тикетов

**Примеры вопросов:**
- "Покажи все открытые тикеты"
- "Расскажи о тикете TKT-001"
- "Как сбросить пароль?"
- "Покажи статистику поддержки"

Вы также можете указать контекст тикета для более точного ответа.""",
                isUser = false
            )
        )
    }

    /**
     * Установить текущий тикет для контекста.
     */
    fun setTicketContext(ticketId: String?) {
        _currentTicketId.value = ticketId
        if (ticketId != null) {
            addSystemMessage("📋 Контекст установлен: тикет $ticketId")
        } else {
            addSystemMessage("📋 Контекст тикета очищен")
        }
    }

    /**
     * Отправить вопрос в поддержку.
     */
    fun sendQuestion(question: String) {
        if (question.isBlank()) return

        scope.launch {
            // Добавляем сообщение пользователя
            val userMessage = SupportMessage(
                id = "user-${System.currentTimeMillis()}",
                content = question,
                isUser = true
            )
            _messages.value = _messages.value + userMessage

            _isLoading.value = true
            _error.value = null

            try {
                val response = apiClient.sendSupportQuestion(
                    question = question,
                    ticketId = _currentTicketId.value
                )

                val assistantMessage = SupportMessage(
                    id = "assistant-${System.currentTimeMillis()}",
                    content = response.answer,
                    isUser = false,
                    ticketId = response.ticketId,
                    durationMs = response.durationMs
                )
                _messages.value = _messages.value + assistantMessage

                AppLogger.info("SupportViewModel", "Ответ получен за ${response.durationMs}ms")

            } catch (e: Exception) {
                AppLogger.error("SupportViewModel", "Ошибка: ${e.message}")
                _error.value = e.message

                val errorMessage = SupportMessage(
                    id = "error-${System.currentTimeMillis()}",
                    content = "❌ Ошибка: ${e.message}",
                    isUser = false
                )
                _messages.value = _messages.value + errorMessage
            } finally {
                _isLoading.value = false
            }
        }
    }

    private fun addSystemMessage(content: String) {
        val message = SupportMessage(
            id = "system-${System.currentTimeMillis()}",
            content = content,
            isUser = false
        )
        _messages.value = _messages.value + message
    }

    /**
     * Очистить историю чата.
     */
    fun clearHistory() {
        _messages.value = listOf(
            SupportMessage(
                id = "welcome-new",
                content = "История очищена. Чем могу помочь?",
                isUser = false
            )
        )
        _currentTicketId.value = null
    }

    fun dismissError() {
        _error.value = null
    }
}

/**
 * Ответ от API поддержки.
 */
@Serializable
data class SupportApiResponse(
    val answer: String,
    val ticketId: String? = null,
    val durationMs: Long
)
