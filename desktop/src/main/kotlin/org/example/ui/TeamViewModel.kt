package org.example.ui

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import org.example.logging.AppLogger
import org.example.network.ChatApiClient

/**
 * Сообщение в чате команды.
 */
data class TeamMessage(
    val id: String,
    val content: String,
    val isUser: Boolean,
    val durationMs: Long? = null
)

/**
 * ViewModel для экрана командного ассистента.
 */
class TeamViewModel(
    private val apiClient: ChatApiClient
) {
    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())

    private val _messages = MutableStateFlow<List<TeamMessage>>(emptyList())
    val messages: StateFlow<List<TeamMessage>> = _messages.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()

    init {
        // Приветственное сообщение
        _messages.value = listOf(
            TeamMessage(
                id = "welcome",
                content = """Привет! Я командный ассистент AiCompose 👥

Я помогу вам с управлением задачами и проектом:
• Просмотр и поиск задач
• Создание новых задач
• Обновление статусов
• Информация о команде
• Рекомендации по приоритетам

**Примеры вопросов:**
- "Покажи задачи с приоритетом HIGH"
- "Какие задачи заблокированы?"
- "Создай задачу: Добавить авторизацию"
- "Что делать первым?"
- "Покажи статистику команды"
- "Кто свободен для новых задач?"

Спрашивайте о текущем спринте, прогрессе или любых задачах!""",
                isUser = false
            )
        )
    }

    /**
     * Отправить вопрос командному ассистенту.
     */
    fun sendQuestion(question: String) {
        if (question.isBlank()) return

        scope.launch {
            // Добавляем сообщение пользователя
            val userMessage = TeamMessage(
                id = "user-${System.currentTimeMillis()}",
                content = question,
                isUser = true
            )
            _messages.value = _messages.value + userMessage

            _isLoading.value = true
            _error.value = null

            try {
                val response = apiClient.sendTeamQuestion(question)

                val assistantMessage = TeamMessage(
                    id = "assistant-${System.currentTimeMillis()}",
                    content = response.answer,
                    isUser = false,
                    durationMs = response.durationMs
                )
                _messages.value = _messages.value + assistantMessage

                AppLogger.info("TeamViewModel", "Ответ получен за ${response.durationMs}ms")

            } catch (e: Exception) {
                AppLogger.error("TeamViewModel", "Ошибка: ${e.message}")
                _error.value = e.message

                val errorMessage = TeamMessage(
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

    /**
     * Очистить историю чата.
     */
    fun clearHistory() {
        _messages.value = listOf(
            TeamMessage(
                id = "welcome-new",
                content = "История очищена. Чем могу помочь с задачами?",
                isUser = false
            )
        )
    }

    fun dismissError() {
        _error.value = null
    }
}
