---
name: viewmodel-rules
description: Правила ViewModel для desktop AiCompose — StateFlow, coroutines, состояние UI
---

# ViewModel Rules

Правила для ViewModel в desktop приложении.

## Структура ViewModel

### Базовый шаблон

```kotlin
class ChatViewModel(
    private val apiClient: ChatApiClient  // Инжектируется через Koin
) {
    // === Приватное состояние ===
    private val _messages = MutableStateFlow<List<ChatMessage>>(emptyList())
    private val _isLoading = MutableStateFlow(false)
    private val _error = MutableStateFlow<String?>(null)

    // === Публичное состояние (read-only) ===
    val messages: StateFlow<List<ChatMessage>> = _messages.asStateFlow()
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()
    val error: StateFlow<String?> = _error.asStateFlow()

    // === Scope для корутин ===
    private val viewModelScope = CoroutineScope(Dispatchers.Main + SupervisorJob())

    // === Публичные методы ===
    fun sendMessage(text: String) {
        viewModelScope.launch {
            // реализация
        }
    }

    fun clearError() {
        _error.value = null
    }
}
```

## StateFlow vs MutableStateFlow

### Правила инкапсуляции

```kotlin
// ✅ Правильно — приватный mutable, публичный read-only
private val _state = MutableStateFlow(initialValue)
val state: StateFlow<T> = _state.asStateFlow()

// ❌ Плохо — публичный mutable позволяет изменять извне
val state = MutableStateFlow(initialValue)  // Любой может изменить!
```

### Именование

```kotlin
// Приватное состояние — с подчёркиванием
private val _messages = MutableStateFlow<List<ChatMessage>>(emptyList())
private val _isLoading = MutableStateFlow(false)

// Публичное — без подчёркивания
val messages: StateFlow<List<ChatMessage>>
val isLoading: StateFlow<Boolean>
```

## Корутины в ViewModel

### CoroutineScope

```kotlin
class ChatViewModel {
    // SupervisorJob позволяет дочерним корутинам падать независимо
    private val viewModelScope = CoroutineScope(
        Dispatchers.Main + SupervisorJob()
    )

    // Отмена при уничтожении ViewModel
    fun onCleared() {
        viewModelScope.cancel()
    }
}
```

### Обработка ошибок

```kotlin
fun sendMessage(text: String) {
    viewModelScope.launch {
        _isLoading.value = true
        _error.value = null

        try {
            val response = apiClient.sendMessage(text)
            _messages.update { it + response }
        } catch (e: Exception) {
            _error.value = e.message ?: "Неизвестная ошибка"
        } finally {
            _isLoading.value = false
        }
    }
}
```

### Переключение диспетчеров

```kotlin
fun loadData() {
    viewModelScope.launch {
        _isLoading.value = true

        // I/O операции на IO диспетчере
        val data = withContext(Dispatchers.IO) {
            apiClient.fetchData()
        }

        // Обновление UI на Main
        _data.value = data
        _isLoading.value = false
    }
}
```

## Обновление состояния

### Атомарное обновление списков

```kotlin
// ✅ Правильно — атомарное обновление через update
_messages.update { currentList ->
    currentList + newMessage
}

// ✅ Правильно — замена списка
_messages.value = newMessages

// ❌ Плохо — неатомарно, race condition
val current = _messages.value.toMutableList()
current.add(newMessage)
_messages.value = current
```

### Обновление вложенных структур

```kotlin
// Для сложного состояния используйте copy()
data class UiState(
    val messages: List<ChatMessage> = emptyList(),
    val isLoading: Boolean = false,
    val error: String? = null
)

private val _uiState = MutableStateFlow(UiState())
val uiState: StateFlow<UiState> = _uiState.asStateFlow()

fun setLoading(loading: Boolean) {
    _uiState.update { it.copy(isLoading = loading) }
}
```

## Polling и периодические задачи

### Реализация polling

```kotlin
private var pollingJob: Job? = null

fun startNotificationPolling() {
    pollingJob?.cancel()
    pollingJob = viewModelScope.launch {
        while (isActive) {
            try {
                val notifications = apiClient.fetchNotifications()
                _currentNotification.value = notifications.firstOrNull()
            } catch (e: Exception) {
                // Логируем, но не прерываем polling
            }
            delay(30_000)  // 30 секунд
        }
    }
}

fun stopPolling() {
    pollingJob?.cancel()
    pollingJob = null
}
```

### Автоскрытие уведомлений

```kotlin
fun showNotification(text: String) {
    _currentNotification.value = text

    viewModelScope.launch {
        delay(5_000)  // 5 секунд
        if (_currentNotification.value == text) {
            _currentNotification.value = null
        }
    }
}

fun dismissNotification() {
    _currentNotification.value = null
}
```

## Streaming

### Обработка SSE потока

```kotlin
private val _streamingContent = MutableStateFlow("")
private val _isStreaming = MutableStateFlow(false)

val streamingContent: StateFlow<String> = _streamingContent.asStateFlow()
val isStreaming: StateFlow<Boolean> = _isStreaming.asStateFlow()

fun sendMessageStreaming(text: String) {
    viewModelScope.launch {
        _isStreaming.value = true
        _streamingContent.value = ""

        try {
            apiClient.streamMessage(text).collect { event ->
                when (event.type) {
                    "CONTENT" -> {
                        _streamingContent.update { it + event.content }
                    }
                    "DONE" -> {
                        val finalMessage = ChatMessage(
                            content = _streamingContent.value,
                            role = MessageRole.ASSISTANT
                        )
                        _messages.update { it + finalMessage }
                    }
                }
            }
        } finally {
            _isStreaming.value = false
            _streamingContent.value = ""
        }
    }
}
```

## Dependency Injection

### Koin интеграция

```kotlin
// di/AppModule.kt
val appModule = module {
    single { ChatApiClient() }
    single { ChatViewModel(get()) }
    single { McpViewModel() }
    single { SupportViewModel(get()) }
}

// Использование в Composable
@Composable
fun App() {
    val viewModel: ChatViewModel = remember { getKoin().get() }
    ChatScreen(viewModel)
}
```

## Тестирование

### Мокирование StateFlow

```kotlin
@Test
fun `should update messages on send`() = runTest {
    val mockClient = mockk<ChatApiClient>()
    coEvery { mockClient.sendMessage(any()) } returns ChatMessage(...)

    val viewModel = ChatViewModel(mockClient)
    viewModel.sendMessage("Hello")

    advanceUntilIdle()

    assertThat(viewModel.messages.value).hasSize(1)
}
```

---

## Связанные документы

- Compose правила — см. rules/desktop/compose.md
- Навигация — см. rules/desktop/navigation.md
