---
name: navigation-rules
description: Правила навигации для desktop AiCompose — экраны, переходы, боковое меню
---

# Navigation Rules

Правила навигации в desktop приложении.

## Структура экранов

### Enum экранов

```kotlin
// Main.kt
enum class Screen {
    CHAT,           // Основной чат
    SETTINGS,       // Настройки режима сбора
    MCP_SERVERS,    // MCP серверы
    SUPPORT,        // Поддержка
    MODEL_COMPARISON, // Сравнение моделей
    OLLAMA_BENCHMARK, // Бенчмарк Ollama
    SERVER_LOGS     // Серверные логи
}
```

### Состояние навигации

```kotlin
@Composable
fun App() {
    var currentScreen by remember { mutableStateOf(Screen.CHAT) }

    Row {
        // Боковое меню
        NavigationMenu(
            currentScreen = currentScreen,
            onScreenSelected = { currentScreen = it }
        )

        // Контент экрана
        when (currentScreen) {
            Screen.CHAT -> ChatScreen(viewModel)
            Screen.SETTINGS -> SettingsScreen(viewModel, onBack = { currentScreen = Screen.CHAT })
            Screen.MCP_SERVERS -> McpServersScreen(mcpViewModel)
            Screen.SUPPORT -> SupportScreen(supportViewModel)
            // ...
        }
    }
}
```

## Боковое меню

### Компонент NavigationMenu

```kotlin
@Composable
fun NavigationMenu(
    currentScreen: Screen,
    onScreenSelected: (Screen) -> Unit
) {
    Column(
        modifier = Modifier
            .width(200.dp)
            .fillMaxHeight()
            .background(MaterialTheme.colorScheme.surfaceVariant)
    ) {
        NavigationMenuItem(
            icon = Icons.Default.Chat,
            label = "Чат",
            selected = currentScreen == Screen.CHAT,
            onClick = { onScreenSelected(Screen.CHAT) }
        )
        NavigationMenuItem(
            icon = Icons.Default.Settings,
            label = "Настройки",
            selected = currentScreen == Screen.SETTINGS,
            onClick = { onScreenSelected(Screen.SETTINGS) }
        )
        // ...
    }
}
```

### Пункт меню

```kotlin
@Composable
fun NavigationMenuItem(
    icon: ImageVector,
    label: String,
    selected: Boolean,
    onClick: () -> Unit
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        color = if (selected) {
            MaterialTheme.colorScheme.primaryContainer
        } else {
            Color.Transparent
        }
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(icon, contentDescription = null)
            Spacer(Modifier.width(12.dp))
            Text(label)
        }
    }
}
```

## Структура меню приложения

| Иконка | Экран | Описание |
|--------|-------|----------|
| 💬 | CHAT | Основной чат с агентом |
| ⚙️ | SETTINGS | Настройки режима сбора, персонаж, температура |
| 🔌 | MCP_SERVERS | Список MCP серверов и инструментов |
| 🎧 | SUPPORT | Чат поддержки |
| 🔬 | MODEL_COMPARISON | Сравнение HuggingFace моделей |
| 🦙 | OLLAMA_BENCHMARK | Бенчмарк локальных моделей |
| 📋 | SERVER_LOGS | Серверные логи |

## Переходы между экранами

### Навигация назад

```kotlin
@Composable
fun SettingsScreen(
    viewModel: ChatViewModel,
    onBack: () -> Unit
) {
    Column {
        // Кнопка назад в header
        TopAppBar(
            title = { Text("Настройки") },
            navigationIcon = {
                IconButton(onClick = onBack) {
                    Icon(Icons.Default.ArrowBack, "Назад")
                }
            }
        )

        // Контент настроек
        SettingsContent(viewModel)
    }
}
```

### Навигация с параметрами

```kotlin
// Для передачи данных между экранами используйте ViewModel
@Composable
fun App() {
    val viewModel: ChatViewModel = remember { getKoin().get() }
    var currentScreen by remember { mutableStateOf(Screen.CHAT) }

    when (currentScreen) {
        Screen.CHAT -> ChatScreen(
            viewModel = viewModel,
            onOpenSettings = { currentScreen = Screen.SETTINGS }
        )
        Screen.SETTINGS -> SettingsScreen(
            viewModel = viewModel,  // Тот же ViewModel
            onBack = { currentScreen = Screen.CHAT }
        )
    }
}
```

## Индикаторы в меню

### Отображение активного режима

```kotlin
@Composable
fun ChatScreenHeader(
    collectionMode: CollectionMode?,
    onSettingsClick: () -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text("AiCompose")

        Row {
            // Индикатор режима сбора
            collectionMode?.let { mode ->
                if (mode != CollectionMode.NONE) {
                    Chip(
                        label = { Text(mode.displayName) },
                        colors = ChipDefaults.chipColors(
                            containerColor = MaterialTheme.colorScheme.primaryContainer
                        )
                    )
                    Spacer(Modifier.width(8.dp))
                }
            }

            // Кнопка настроек
            IconButton(onClick = onSettingsClick) {
                Icon(Icons.Default.Settings, "Настройки")
            }
        }
    }
}
```

## Диалоги

### Модальные диалоги без навигации

```kotlin
@Composable
fun ChatScreen(viewModel: ChatViewModel) {
    var showContextDialog by remember { mutableStateOf(false) }

    // Основной контент
    Column {
        // ...
        Button(onClick = { showContextDialog = true }) {
            Text("Установить контекст")
        }
    }

    // Диалог поверх контента
    if (showContextDialog) {
        AlertDialog(
            onDismissRequest = { showContextDialog = false },
            title = { Text("Контекст тикета") },
            text = {
                TextField(
                    value = ticketId,
                    onValueChange = { ticketId = it }
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.setTicketContext(ticketId)
                    showContextDialog = false
                }) {
                    Text("Установить")
                }
            }
        )
    }
}
```

## Keyboard Navigation

### Горячие клавиши

```kotlin
@Composable
fun App() {
    var currentScreen by remember { mutableStateOf(Screen.CHAT) }

    // Обработка горячих клавиш
    LaunchedEffect(Unit) {
        // Cmd+1 - Чат
        // Cmd+2 - Настройки
        // Cmd+, - Настройки (macOS стандарт)
    }

    // Контент
}
```

---

## Связанные документы

- Compose правила — см. rules/desktop/compose.md
- ViewModel правила — см. rules/desktop/viewmodel.md
