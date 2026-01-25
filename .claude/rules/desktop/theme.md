---
name: theme-rules
description: Правила темы и стилей для desktop AiCompose — Material3, цвета, типографика
---

# Theme Rules

Правила темы и стилей в desktop приложении.

## Material3 тема

### AppTheme

```kotlin
// ui/theme/AppTheme.kt
@Composable
fun AppTheme(
    darkTheme: Boolean = true,  // По умолчанию тёмная тема
    content: @Composable () -> Unit
) {
    val colorScheme = if (darkTheme) darkColorScheme() else lightColorScheme()

    MaterialTheme(
        colorScheme = colorScheme,
        typography = AppTypography,
        content = content
    )
}
```

### Использование темы

```kotlin
fun main() = application {
    Window(
        onCloseRequest = ::exitApplication,
        title = "AiCompose"
    ) {
        AppTheme {
            App()
        }
    }
}
```

## Цветовая схема

### Тёмная тема (по умолчанию)

```kotlin
private val DarkColorScheme = darkColorScheme(
    primary = Color(0xFF90CAF9),           // Голубой
    onPrimary = Color(0xFF003258),
    primaryContainer = Color(0xFF004880),
    onPrimaryContainer = Color(0xFFD1E4FF),

    secondary = Color(0xFFBBC7DB),          // Серо-голубой
    onSecondary = Color(0xFF253140),
    secondaryContainer = Color(0xFF3B4858),

    tertiary = Color(0xFFD6BEE4),           // Фиолетовый
    onTertiary = Color(0xFF3B2948),

    background = Color(0xFF1A1C1E),         // Тёмный фон
    onBackground = Color(0xFFE3E2E6),

    surface = Color(0xFF1A1C1E),
    onSurface = Color(0xFFE3E2E6),

    surfaceVariant = Color(0xFF43474E),     // Для меню
    onSurfaceVariant = Color(0xFFC3C6CF),

    error = Color(0xFFFFB4AB),
    onError = Color(0xFF690005)
)
```

### Цвета по назначению

| Элемент | Цвет | Использование |
|---------|------|---------------|
| `primary` | Голубой | Акценты, кнопки действий |
| `secondary` | Серо-голубой | Второстепенные элементы |
| `tertiary` | Фиолетовый | Специальные элементы (pipeline) |
| `surface` | Тёмно-серый | Фон карточек, панелей |
| `surfaceVariant` | Серый | Боковое меню |
| `error` | Красный | Ошибки, предупреждения |

## Типографика

```kotlin
val AppTypography = Typography(
    // Заголовки
    headlineLarge = TextStyle(
        fontWeight = FontWeight.Bold,
        fontSize = 32.sp,
        lineHeight = 40.sp
    ),
    headlineMedium = TextStyle(
        fontWeight = FontWeight.Bold,
        fontSize = 24.sp,
        lineHeight = 32.sp
    ),

    // Тело текста
    bodyLarge = TextStyle(
        fontWeight = FontWeight.Normal,
        fontSize = 16.sp,
        lineHeight = 24.sp
    ),
    bodyMedium = TextStyle(
        fontWeight = FontWeight.Normal,
        fontSize = 14.sp,
        lineHeight = 20.sp
    ),

    // Подписи
    labelMedium = TextStyle(
        fontWeight = FontWeight.Medium,
        fontSize = 12.sp,
        lineHeight = 16.sp
    )
)
```

## Компоненты UI

### Карточки

```kotlin
@Composable
fun MessageCard(message: ChatMessage) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(8.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (message.role == MessageRole.USER) {
                MaterialTheme.colorScheme.primaryContainer
            } else {
                MaterialTheme.colorScheme.surfaceVariant
            }
        ),
        shape = RoundedCornerShape(12.dp)
    ) {
        Text(
            text = message.content,
            modifier = Modifier.padding(16.dp),
            color = MaterialTheme.colorScheme.onSurface
        )
    }
}
```

### Кнопки

```kotlin
// Основная кнопка
Button(
    onClick = { },
    colors = ButtonDefaults.buttonColors(
        containerColor = MaterialTheme.colorScheme.primary
    )
) {
    Text("Отправить")
}

// Второстепенная кнопка
OutlinedButton(onClick = { }) {
    Text("Отмена")
}

// Текстовая кнопка
TextButton(onClick = { }) {
    Text("Подробнее")
}
```

### Чипы (FilterChip)

```kotlin
@Composable
fun ModeSelector(
    selectedMode: CollectionMode,
    onModeSelected: (CollectionMode) -> Unit
) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        CollectionMode.values().forEach { mode ->
            FilterChip(
                selected = mode == selectedMode,
                onClick = { onModeSelected(mode) },
                label = { Text(mode.displayName) },
                colors = FilterChipDefaults.filterChipColors(
                    selectedContainerColor = MaterialTheme.colorScheme.primaryContainer
                )
            )
        }
    }
}
```

## Цвета по категориям

### Инструменты Pipeline

```kotlin
fun getToolColor(toolName: String): Color {
    return when (toolName) {
        "pipeline_search_docs" -> MaterialTheme.colorScheme.primary
        "pipeline_summarize" -> MaterialTheme.colorScheme.secondary
        "pipeline_save_to_file" -> MaterialTheme.colorScheme.tertiary
        else -> MaterialTheme.colorScheme.surfaceVariant
    }
}

fun getToolIcon(toolName: String): String {
    return when (toolName) {
        "pipeline_search_docs" -> "🔍"
        "pipeline_summarize" -> "📝"
        "pipeline_save_to_file" -> "💾"
        else -> "🔧"
    }
}
```

### Категории логов

```kotlin
fun getLogCategoryColor(category: String): Color {
    return when (category) {
        "REQUEST" -> Color(0xFF4CAF50)       // Зелёный
        "RESPONSE" -> Color(0xFF2196F3)      // Синий
        "LLM_REQUEST" -> Color(0xFFFF9800)   // Оранжевый
        "LLM_RESPONSE" -> Color(0xFFE91E63)  // Розовый
        "TOOL_CALL" -> Color(0xFF9C27B0)     // Фиолетовый
        "TOOL_RESULT" -> Color(0xFF00BCD4)   // Бирюзовый
        "ERROR" -> Color(0xFFF44336)         // Красный
        else -> Color.Gray
    }
}
```

## Отступы и размеры

### Стандартные отступы

```kotlin
object Spacing {
    val xs = 4.dp
    val sm = 8.dp
    val md = 16.dp
    val lg = 24.dp
    val xl = 32.dp
}

// Использование
Modifier.padding(Spacing.md)
```

### Размеры элементов

```kotlin
object Sizes {
    val iconSmall = 16.dp
    val iconMedium = 24.dp
    val iconLarge = 32.dp

    val avatarSmall = 32.dp
    val avatarMedium = 48.dp

    val menuWidth = 200.dp
    val maxContentWidth = 800.dp
}
```

## Анимации

### Переходы

```kotlin
@Composable
fun AnimatedContent(visible: Boolean, content: @Composable () -> Unit) {
    AnimatedVisibility(
        visible = visible,
        enter = fadeIn() + expandVertically(),
        exit = fadeOut() + shrinkVertically()
    ) {
        content()
    }
}
```

### Индикатор загрузки

```kotlin
@Composable
fun LoadingIndicator() {
    CircularProgressIndicator(
        modifier = Modifier.size(24.dp),
        color = MaterialTheme.colorScheme.primary,
        strokeWidth = 2.dp
    )
}
```

---

## Связанные документы

- Compose правила — см. rules/desktop/compose.md
- Навигация — см. rules/desktop/navigation.md
