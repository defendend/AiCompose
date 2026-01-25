---
name: mcp-integration
description: Model Context Protocol интеграция — Weather, Reminder, Tracker серверы и McpToolsAdapter
---

# MCP (Model Context Protocol) интеграция

## О MCP

- Стандартный протокол для взаимодействия LLM с внешними системами
- Используется Kotlin MCP SDK (`io.modelcontextprotocol:kotlin-sdk:0.8.1`)
- Транспорт: stdio (подключение к Python процессам через stdin/stdout)

## Архитектура

```
MCP Server (Python)
    ↓ stdio
WeatherMcpClient.kt
    ↓
McpToolsAdapter.kt
    ↓
ToolRegistry
    ↓
Agent
```

---

## Weather MCP Server (Open-Meteo)

**Установка на сервере:**
```bash
pip install mcp_weather_server
```

**Реализация:**
- `WeatherMcpClient.kt` — Kotlin клиент, запускает Python процесс MCP сервера
- Подключение через `StdioClientTransport` (stdin/stdout)
- Автоматический запуск при старте backend (`Application.kt:configureMcpTools()`)
- 3 инструмента: текущая погода, детали, качество воздуха

**Использование:**
```kotlin
val client = WeatherMcpClient()
client.connect()
val weather = client.getCurrentWeather("Moscow")  // параметр: city
```

**Важно:** MCP сервер ожидает параметр `city` (не `location`)!

**Инструменты:**
| Инструмент | Описание |
|------------|----------|
| `weather_get_current` | Текущая погода в любом городе мира |
| `weather_get_details` | Детальные метеорологические данные (JSON) |
| `weather_get_air_quality` | Качество воздуха в городе |

---

## Reminder System (Планировщик напоминаний)

**О системе:**
- Агент работает 24/7, выдавая сводку о задачах
- Хранение в JSON файле (`reminders.json`)
- Фоновый планировщик проверяет каждые **15 секунд**
- Автоматические уведомления о просроченных задачах
- **In-app уведомления** в Desktop приложении через Snackbar

**Компоненты:**
- `ReminderModels.kt` — модели данных (Reminder, ReminderStatus)
- `ReminderRepository.kt` — JSON репозиторий с методами CRUD
- `ReminderScheduler.kt` — корутина, проверяющая overdue каждые 15 секунд
- `McpToolsAdapter.kt` — регистрация 5 reminder инструментов
- `GET /api/reminders/notifications` — API endpoint для получения уведомлений

**Модель данных:**
```kotlin
@Serializable
data class Reminder(
    val id: String = UUID.randomUUID().toString(),
    val title: String,
    val description: String? = null,
    @Serializable(with = InstantSerializer::class)
    val reminderTime: Instant,
    val status: ReminderStatus = ReminderStatus.PENDING,
    val notified: Boolean = false,
    val createdAt: Instant = Instant.now(),
    val updatedAt: Instant = Instant.now()
)

enum class ReminderStatus { PENDING, COMPLETED, CANCELLED }
```

**Инструменты:**
| Инструмент | Параметры | Описание |
|------------|-----------|----------|
| `reminder_add` | title, description?, reminder_time | Создать напоминание |
| `reminder_list` | filter (all\|pending\|completed) | Список напоминаний |
| `reminder_complete` | reminder_id | Пометить выполненным |
| `reminder_delete` | reminder_id | Удалить |
| `reminder_get_summary` | — | Сводка |

**Планировщик:**
```kotlin
class ReminderScheduler(checkIntervalSeconds: Long = 15) {
    fun start()  // Запуск фоновой проверки каждые 15 секунд
    fun stop()   // Остановка
    private suspend fun checkAndNotify()  // Поиск overdue, логирование
}
```

**Логирование уведомлений:**
- При обнаружении overdue создаётся сводка с эмодзи (📊, ⚠️, 📋, ⏰, 💬)
- Логируется на уровне WARNING для видимости
- Помечается как `notified = true` чтобы не повторяться

**Автозапуск:**
```kotlin
// Application.kt
fun Application.startReminderScheduler() {
    val scheduler = getKoin().get<ReminderScheduler>()
    scheduler.start()
}
```

**Desktop уведомления (In-App Snackbar):**
- Polling каждые 30 секунд к `GET /api/reminders/notifications`
- `ChatViewModel.currentNotification` — StateFlow для текущего уведомления
- Snackbar внизу экрана с автоскрытием через 5 секунд
- Кнопка закрытия (X) для ручного dismiss

---

## McpToolsAdapter

Адаптер для оборачивания MCP инструментов в `AgentTool` интерфейс.

**Регистрация инструментов:**
```kotlin
// Application.kt
val mcpTools = mcpToolsAdapter.getTools()
mcpTools.forEach { tool ->
    ToolRegistry.register(tool)
}
```

---

## UI для MCP серверов

**Desktop приложение:**
- Меню → "MCP Серверы" (🔌)
- `McpServersScreen.kt` — экран со списком серверов
- `McpViewModel.kt` — управление серверами и инструментами
- Показывает: статус подключения, список инструментов, параметры

**Отображаемые серверы:**
1. Demo Server (5 тестовых инструментов: echo, add, multiply, get_time, reverse)
2. Яндекс.Трекер Server (3 инструмента для работы с задачами)
3. Weather MCP Server (3 погодных инструмента)
4. Reminder MCP Server (5 инструментов планировщика)

---

## Связанные документы

- Инструменты агента — см. docs/tools.md
- API endpoints — см. docs/api.md
