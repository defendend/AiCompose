---
name: compose-rules
description: Правила Compose Multiplatform для desktop AiCompose — компоненты, состояние, UI паттерны
---

# Compose Rules

Правила для Compose Multiplatform в desktop приложении.

## Структура компонентов

### Именование

```kotlin
// Экраны — суффикс Screen
@Composable
fun ChatScreen(viewModel: ChatViewModel) { }

// Переиспользуемые компоненты — описательное имя
@Composable
fun MessageBubble(message: ChatMessage) { }

// Превью — суффикс Preview
@Preview
@Composable
fun MessageBubblePreview() { }
```

### Организация файлов

```
ui/
├── components/
│   ├── ChatScreen.kt          # Основной экран чата
│   ├── SettingsScreen.kt      # Экран настроек
│   ├── McpServersScreen.kt    # Экран MCP серверов
│   ├── SupportScreen.kt       # Экран поддержки
│   └── ServerLogWindow.kt     # Окно логов
├── theme/
│   └── AppTheme.kt            # Тема приложения
├── ChatViewModel.kt           # ViewModel чата
├── McpViewModel.kt            # ViewModel MCP
└── SupportViewModel.kt        # ViewModel поддержки
```

## State Management

### State в Composable

```kotlin
// ✅ Хорошо — remember для локального состояния
@Composable
fun MessageInput() {
    var text by remember { mutableStateOf("") }

    TextField(
        value = text,
        onValueChange = { text = it }
    )
}

// ❌ Плохо — состояние теряется при рекомпозиции
@Composable
fun MessageInput() {
    var text = ""  // Сбрасывается!
}
```

### Подъём состояния (State Hoisting)

```kotlin
// ✅ Stateless компонент — состояние снаружи
@Composable
fun MessageInput(
    text: String,
    onTextChange: (String) -> Unit,
    onSend: () -> Unit
) {
    Row {
        TextField(value = text, onValueChange = onTextChange)
        Button(onClick = onSend) { Text("Отправить") }
    }
}

// Использование
@Composable
fun ChatScreen(viewModel: ChatViewModel) {
    val inputText by viewModel.inputText.collectAsState()

    MessageInput(
        text = inputText,
        onTextChange = { viewModel.updateInput(it) },
        onSend = { viewModel.sendMessage() }
    )
}
```

## Сбор состояния из ViewModel

```kotlin
@Composable
fun ChatScreen(viewModel: ChatViewModel) {
    // ✅ collectAsState для StateFlow
    val messages by viewModel.messages.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()

    // ✅ collectAsState с initial для nullable
    val notification by viewModel.currentNotification.collectAsState(initial = null)
}
```

## Модификаторы

### Порядок модификаторов

```kotlin
// ✅ Правильный порядок
Box(
    modifier = Modifier
        .fillMaxWidth()           // 1. Размер
        .padding(16.dp)           // 2. Внешние отступы
        .background(Color.Gray)   // 3. Фон
        .padding(8.dp)            // 4. Внутренние отступы
        .clickable { }            // 5. Интерактивность
)
```

### Передача модификатора

```kotlin
// ✅ Всегда принимать modifier как параметр
@Composable
fun MyComponent(
    modifier: Modifier = Modifier,  // Default Modifier
    content: String
) {
    Text(
        text = content,
        modifier = modifier  // Применяем переданный modifier
    )
}
```

## Списки

### LazyColumn для длинных списков

```kotlin
@Composable
fun MessageList(messages: List<ChatMessage>) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        reverseLayout = true,  // Новые сообщения внизу
        state = rememberLazyListState()
    ) {
        items(
            items = messages,
            key = { it.id }  // Стабильный ключ!
        ) { message ->
            MessageBubble(message = message)
        }
    }
}
```

### Автоскролл к новым сообщениям

```kotlin
@Composable
fun MessageList(messages: List<ChatMessage>) {
    val listState = rememberLazyListState()

    // Автоскролл при новом сообщении
    LaunchedEffect(messages.size) {
        if (messages.isNotEmpty()) {
            listState.animateScrollToItem(0)
        }
    }

    LazyColumn(state = listState, reverseLayout = true) {
        items(messages, key = { it.id }) { MessageBubble(it) }
    }
}
```

## Side Effects

### LaunchedEffect

```kotlin
// ✅ Для одноразовых действий при входе в композицию
@Composable
fun ChatScreen(viewModel: ChatViewModel) {
    LaunchedEffect(Unit) {
        viewModel.startNotificationPolling()
    }
}

// ✅ С ключом — перезапуск при изменении
LaunchedEffect(conversationId) {
    viewModel.loadConversation(conversationId)
}
```

### DisposableEffect

```kotlin
// ✅ Для cleanup при выходе из композиции
@Composable
fun NotificationListener(viewModel: ChatViewModel) {
    DisposableEffect(Unit) {
        viewModel.startPolling()

        onDispose {
            viewModel.stopPolling()
        }
    }
}
```

## Диалоги и Popup

```kotlin
@Composable
fun ConfirmDialog(
    show: Boolean,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    if (show) {
        AlertDialog(
            onDismissRequest = onDismiss,
            title = { Text("Подтверждение") },
            text = { Text("Вы уверены?") },
            confirmButton = {
                TextButton(onClick = onConfirm) {
                    Text("Да")
                }
            },
            dismissButton = {
                TextButton(onClick = onDismiss) {
                    Text("Отмена")
                }
            }
        )
    }
}
```

## Snackbar для уведомлений

```kotlin
@Composable
fun ChatScreenWithNotifications(viewModel: ChatViewModel) {
    val notification by viewModel.currentNotification.collectAsState()

    Box(modifier = Modifier.fillMaxSize()) {
        // Основной контент
        ChatContent(viewModel)

        // Snackbar внизу
        notification?.let { text ->
            Surface(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(16.dp),
                color = MaterialTheme.colorScheme.inverseSurface,
                shape = RoundedCornerShape(8.dp)
            ) {
                Row(
                    modifier = Modifier.padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(text, color = MaterialTheme.colorScheme.inverseOnSurface)
                    Spacer(Modifier.width(8.dp))
                    IconButton(onClick = { viewModel.dismissNotification() }) {
                        Icon(Icons.Default.Close, "Закрыть")
                    }
                }
            }
        }
    }
}
```

## Производительность

### Стабильные классы

```kotlin
// ✅ Используйте @Stable или @Immutable для data class
@Immutable
data class ChatMessage(
    val id: String,
    val content: String,
    val role: MessageRole
)
```

### Избегайте лямбд в параметрах

```kotlin
// ❌ Новая лямбда при каждой рекомпозиции
items(messages) { message ->
    MessageBubble(
        message = message,
        onClick = { viewModel.selectMessage(message.id) }  // Новая лямбда!
    )
}

// ✅ remember для стабильной лямбды
items(messages) { message ->
    val onClick = remember(message.id) {
        { viewModel.selectMessage(message.id) }
    }
    MessageBubble(message = message, onClick = onClick)
}
```

---

## Связанные документы

- ViewModel правила — см. rules/desktop/viewmodel.md
- Навигация — см. rules/desktop/navigation.md
- Тема — см. rules/desktop/theme.md
