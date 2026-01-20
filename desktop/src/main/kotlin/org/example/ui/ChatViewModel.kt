package org.example.ui

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.example.logging.AppLogger
import org.example.network.ChatApiClient
import org.example.network.OllamaClient
import org.example.network.OllamaMessage
import org.example.network.OllamaModel
import org.example.shared.model.ChatMessage
import org.example.shared.model.CollectionSettings
import org.example.shared.model.CompressionSettings
import org.example.shared.model.ConversationDetailResponse
import org.example.shared.model.MessageRole
import org.example.shared.model.ResponseFormat
import org.example.shared.model.StreamEventType
import java.util.UUID

class ChatViewModel(
    private val apiClient: ChatApiClient = ChatApiClient(),
    private val ollamaClient: OllamaClient = OllamaClient()
) {
    private val scope = CoroutineScope(Dispatchers.IO)

    // Offline mode state
    private val _isOfflineMode = MutableStateFlow(false)
    val isOfflineMode: StateFlow<Boolean> = _isOfflineMode.asStateFlow()

    private val _ollamaAvailable = MutableStateFlow(false)
    val ollamaAvailable: StateFlow<Boolean> = _ollamaAvailable.asStateFlow()

    private val _currentOllamaModel = MutableStateFlow("qwen2.5:0.5b")
    val currentOllamaModel: StateFlow<String> = _currentOllamaModel.asStateFlow()

    // Список доступных моделей Ollama
    private val _availableOllamaModels = MutableStateFlow<List<OllamaModel>>(emptyList())
    val availableOllamaModels: StateFlow<List<OllamaModel>> = _availableOllamaModels.asStateFlow()

    // Время последнего ответа (мс)
    private val _lastResponseTime = MutableStateFlow<Long?>(null)
    val lastResponseTime: StateFlow<Long?> = _lastResponseTime.asStateFlow()

    // Скорость генерации (токенов/сек) — приблизительно по символам
    private val _generationSpeed = MutableStateFlow<Float?>(null)
    val generationSpeed: StateFlow<Float?> = _generationSpeed.asStateFlow()

    // История для Ollama (локальная)
    private val ollamaHistory = mutableListOf<OllamaMessage>()

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

    private val _conversationId = MutableStateFlow<String?>(null)
    val conversationId: StateFlow<String?> = _conversationId.asStateFlow()

    private val _currentNotification = MutableStateFlow<String?>(null)
    val currentNotification: StateFlow<String?> = _currentNotification.asStateFlow()

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
        AppLogger.info("ChatViewModel", "sendMessage called, useStreaming=${_useStreaming.value}, offlineMode=${_isOfflineMode.value}")

        // Если включён offline режим — используем Ollama
        if (_isOfflineMode.value) {
            sendMessageOllama(text)
            return
        }

        if (_useStreaming.value) {
            sendMessageStreaming(text)
        } else {
            sendMessageClassic(text)
        }
    }

    /**
     * Отправка сообщения через локальную Ollama LLM с поддержкой streaming.
     */
    private fun sendMessageOllama(text: String) {
        if (text.isBlank()) return

        val userMessage = ChatMessage(
            id = UUID.randomUUID().toString(),
            role = MessageRole.USER,
            content = text,
            timestamp = System.currentTimeMillis()
        )

        _messages.value = _messages.value + userMessage
        ollamaHistory.add(OllamaMessage(role = "user", content = text))

        _isLoading.value = true
        _isStreaming.value = true
        _streamingContent.value = ""
        _error.value = null
        _lastResponseTime.value = null
        _generationSpeed.value = null

        scope.launch {
            val startTime = System.currentTimeMillis()
            AppLogger.info("ChatViewModel", "🔌 Отправка в локальную LLM (${_currentOllamaModel.value}): $text")

            val systemPrompt = if (_collectionSettings.value.customSystemPrompt.isNotBlank()) {
                _collectionSettings.value.customSystemPrompt
            } else {
                "Ты — дружелюбный AI-ассистент. Отвечай кратко и по делу на русском языке."
            }

            val allMessages = buildList {
                add(OllamaMessage(role = "system", content = systemPrompt))
                addAll(ollamaHistory)
            }

            val contentBuilder = StringBuilder()

            try {
                ollamaClient.chatStream(
                    model = _currentOllamaModel.value,
                    messages = allMessages.dropLast(1) + OllamaMessage(role = "user", content = text),
                    systemPrompt = null // уже в messages
                ).flowOn(Dispatchers.IO)
                    .catch { e ->
                        AppLogger.error("ChatViewModel", "❌ Ошибка streaming Ollama: ${e.message}")
                        withContext(Dispatchers.Main) {
                            _error.value = "Ошибка локальной LLM: ${e.message}"
                            _isStreaming.value = false
                            _isLoading.value = false
                        }
                    }
                    .collect { chunk ->
                        contentBuilder.append(chunk)
                        withContext(Dispatchers.Main) {
                            _streamingContent.value = "[Offline] ${contentBuilder}"
                        }
                    }

                val endTime = System.currentTimeMillis()
                val responseTime = endTime - startTime
                val responseText = contentBuilder.toString()

                // Примерная скорость (символов в секунду / 4 ≈ токенов в секунду для русского)
                val charsPerSecond = if (responseTime > 0) {
                    (responseText.length.toFloat() / responseTime * 1000)
                } else 0f
                val tokensPerSecond = charsPerSecond / 2 // ~2 символа на токен для русского

                ollamaHistory.add(OllamaMessage(role = "assistant", content = responseText))

                val assistantMessage = ChatMessage(
                    id = UUID.randomUUID().toString(),
                    role = MessageRole.ASSISTANT,
                    content = "[Offline] $responseText",
                    timestamp = System.currentTimeMillis()
                )

                withContext(Dispatchers.Main) {
                    _messages.value = _messages.value + assistantMessage
                    _streamingContent.value = ""
                    _lastResponseTime.value = responseTime
                    _generationSpeed.value = tokensPerSecond
                    _isStreaming.value = false
                    _isLoading.value = false
                }

                AppLogger.info(
                    "ChatViewModel",
                    "✅ Ответ от локальной LLM: ${responseText.take(100)}... " +
                            "(${responseTime}ms, ~${String.format("%.1f", tokensPerSecond)} tok/s)"
                )
            } catch (e: Exception) {
                AppLogger.error("ChatViewModel", "❌ Исключение Ollama: ${e.message}")
                withContext(Dispatchers.Main) {
                    _error.value = "Ошибка локальной LLM: ${e.message}"
                    _isStreaming.value = false
                    _isLoading.value = false
                }
            }
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
                    conversationId = _conversationId.value,
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
                                _conversationId.value = event.conversationId
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

                            StreamEventType.PROCESSING -> {
                                // Heartbeat для поддержания соединения
                                event.content?.let { content ->
                                    AppLogger.info("ChatViewModel", "Обработка: $content")
                                }
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

                // Пробуем автоматически переключиться на offline режим
                val isNetworkError = e.message?.contains("connect", ignoreCase = true) == true ||
                        e.message?.contains("timeout", ignoreCase = true) == true ||
                        e.message?.contains("network", ignoreCase = true) == true ||
                        e.message?.contains("socket", ignoreCase = true) == true

                if (isNetworkError && tryFallbackToOffline()) {
                    // Повторяем запрос через Ollama
                    withContext(Dispatchers.Main) {
                        _isLoading.value = false
                        _isStreaming.value = false
                        _error.value = "⚡ Переключено в офлайн режим"
                    }
                    // Удаляем последнее сообщение пользователя (оно уже добавлено)
                    // и отправляем заново через Ollama
                    sendMessageOllama(text)
                    return@launch
                }

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
                conversationId = _conversationId.value,
                responseFormat = _responseFormat.value,
                collectionSettings = if (shouldSendSettings) currentSettings else null,
                temperature = _temperature.value,
                compressionSettings = _compressionSettings.value
            )
                .onSuccess { response ->
                    _conversationId.value = response.conversationId
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
        _conversationId.value = null
        AppLogger.info("ChatViewModel", "Чат очищен")
    }

    /**
     * Загрузить диалог с сервера по ID.
     */
    fun loadConversation(id: String) {
        scope.launch {
            _isLoading.value = true
            _error.value = null

            apiClient.getConversation(id)
                .onSuccess { response ->
                    _conversationId.value = id
                    // Фильтруем системные сообщения — не показываем их пользователю
                    _messages.value = response.messages.filter { it.role != MessageRole.SYSTEM }
                    AppLogger.info("ChatViewModel", "Загружен диалог: $id, ${_messages.value.size} сообщений")
                }
                .onFailure { e ->
                    AppLogger.error("ChatViewModel", "Ошибка загрузки диалога: ${e.message}")
                    _error.value = e.message ?: "Ошибка загрузки диалога"
                }

            _isLoading.value = false
        }
    }

    /**
     * Переключиться на другой диалог.
     */
    fun switchConversation(id: String?) {
        if (id == null) {
            clearChat()
        } else {
            loadConversation(id)
        }
    }

    /**
     * Начать новый диалог (очищает текущий и сбрасывает conversationId).
     */
    fun startNewConversation() {
        _messages.value = emptyList()
        _conversationId.value = null
        _streamingContent.value = ""
        _error.value = null
        ollamaHistory.clear()
        AppLogger.info("ChatViewModel", "Начат новый диалог")
    }

    // ========== Offline Mode (Ollama) ==========

    /**
     * Проверить доступность Ollama и загрузить список моделей.
     */
    fun checkOllamaAvailability() {
        scope.launch {
            val available = ollamaClient.isAvailable()
            withContext(Dispatchers.Main) {
                _ollamaAvailable.value = available
            }
            if (available) {
                AppLogger.info("ChatViewModel", "🟢 Ollama доступен")
                // Получаем список моделей
                val models = ollamaClient.listModels()
                withContext(Dispatchers.Main) {
                    _availableOllamaModels.value = models
                    // Если текущая модель не в списке — выбираем первую доступную
                    if (models.isNotEmpty() && models.none { it.name == _currentOllamaModel.value }) {
                        _currentOllamaModel.value = models.first().name
                    }
                }
                if (models.isNotEmpty()) {
                    AppLogger.info("ChatViewModel", "📦 Доступные модели: ${models.map { it.name }}")
                }
            } else {
                AppLogger.warning("ChatViewModel", "🔴 Ollama недоступен")
                withContext(Dispatchers.Main) {
                    _availableOllamaModels.value = emptyList()
                }
            }
        }
    }

    /**
     * Обновить список моделей Ollama.
     */
    fun refreshOllamaModels() {
        scope.launch {
            if (_ollamaAvailable.value) {
                val models = ollamaClient.listModels()
                withContext(Dispatchers.Main) {
                    _availableOllamaModels.value = models
                }
                AppLogger.info("ChatViewModel", "🔄 Обновлён список моделей: ${models.map { it.name }}")
            }
        }
    }

    /**
     * Включить/выключить offline режим.
     */
    fun setOfflineMode(enabled: Boolean) {
        if (enabled && !_ollamaAvailable.value) {
            AppLogger.warning("ChatViewModel", "Нельзя включить offline режим — Ollama недоступен")
            _error.value = "Ollama недоступен. Запустите: brew services start ollama"
            return
        }
        _isOfflineMode.value = enabled
        if (enabled) {
            ollamaHistory.clear()
            AppLogger.info("ChatViewModel", "🔌 Включён OFFLINE режим (локальная LLM: ${_currentOllamaModel.value})")
        } else {
            AppLogger.info("ChatViewModel", "🌐 Включён ONLINE режим (сервер)")
        }
    }

    /**
     * Установить модель Ollama.
     */
    fun setOllamaModel(model: String) {
        _currentOllamaModel.value = model
        AppLogger.info("ChatViewModel", "Ollama модель: $model")
    }

    /**
     * Попробовать автоматически переключиться в offline режим при ошибке сети.
     */
    private suspend fun tryFallbackToOffline(): Boolean {
        if (_ollamaAvailable.value || ollamaClient.isAvailable()) {
            withContext(Dispatchers.Main) {
                _ollamaAvailable.value = true
                _isOfflineMode.value = true
            }
            AppLogger.info("ChatViewModel", "⚡ Автоматический переход в offline режим")
            return true
        }
        return false
    }

    // ========== Notification Polling ==========

    private var notificationPollingJob: Job? = null
    private val shownNotificationIds = mutableSetOf<String>()

    /**
     * Запустить polling уведомлений о напоминаниях.
     * Проверяет каждые 30 секунд.
     */
    fun startNotificationPolling() {
        if (notificationPollingJob?.isActive == true) {
            AppLogger.info("ChatViewModel", "Polling уведомлений уже запущен")
            return
        }

        notificationPollingJob = scope.launch {
            AppLogger.info("ChatViewModel", "🔔 Запущен polling уведомлений (каждые 30 сек)")

            while (true) {
                try {
                    val result = apiClient.getReminderNotifications(limit = 10)
                    result.onSuccess { response ->
                        val newNotifications = response.notifications.filter {
                            !shownNotificationIds.contains(it.id)
                        }

                        if (newNotifications.isNotEmpty()) {
                            AppLogger.info("ChatViewModel", "📬 Получено ${newNotifications.size} новых уведомлений")

                            newNotifications.forEach { notification ->
                                // Показываем in-app уведомление
                                withContext(Dispatchers.Main) {
                                    _currentNotification.value = "⏰ ${notification.title}"
                                }
                                shownNotificationIds.add(notification.id)

                                // Автоматически скрываем через 5 секунд
                                delay(5000)
                                withContext(Dispatchers.Main) {
                                    _currentNotification.value = null
                                }
                            }
                        }
                    }
                    result.onFailure { error ->
                        AppLogger.error("ChatViewModel", "Ошибка polling уведомлений: ${error.message}")
                    }
                } catch (e: Exception) {
                    AppLogger.error("ChatViewModel", "Ошибка в polling loop: ${e.message}")
                }

                delay(30_000) // 30 секунд
            }
        }
    }

    /**
     * Остановить polling уведомлений.
     */
    fun stopNotificationPolling() {
        notificationPollingJob?.cancel()
        notificationPollingJob = null
        AppLogger.info("ChatViewModel", "🔕 Polling уведомлений остановлен")
    }

    /**
     * Скрыть текущее уведомление.
     */
    fun dismissNotification() {
        _currentNotification.value = null
    }
}
