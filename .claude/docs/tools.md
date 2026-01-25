---
name: agent-tools
description: Инструменты AI агента "Профессор Архивариус" — системные, исторические, погода, трекер, напоминания, pipeline, RAG, support
---

# Инструменты агента

AI агент "Профессор Архивариус" имеет доступ к набору инструментов для выполнения различных задач.

## Системные инструменты

| Инструмент | Описание |
|------------|----------|
| `get_current_time` | Получить текущее время для вычисления относительных дат ("через 2 минуты", "завтра") |

## Исторические инструменты

| Инструмент | Описание |
|------------|----------|
| `get_historical_events` | События по году (1066, 1453, 1492, 1789, 1812, 1861, 1917, 1945, 1961, 1969, 1991) |
| `get_historical_figure` | Биографии (Наполеон, Цезарь, Пётр I, Клеопатра, Александр Македонский, Леонардо да Винчи) |
| `compare_eras` | Сравнение эпох (античность, средневековье, возрождение, просвещение) |
| `get_historical_quote` | Исторические цитаты великих людей |

## Погода (MCP Open-Meteo)

| Инструмент | Описание |
|------------|----------|
| `weather_get_current` | Текущая погода в любом городе мира |
| `weather_get_details` | Детальные метеорологические данные (JSON) |
| `weather_get_air_quality` | Качество воздуха в городе |

## Яндекс.Трекер (MCP)

| Инструмент | Описание |
|------------|----------|
| `yandex_tracker_get_open_issues_count` | Количество открытых задач в очереди |
| `yandex_tracker_search_issues` | Поиск задач по фильтрам |
| `yandex_tracker_get_issue` | Детальная информация о задаче |

## Напоминания (MCP Планировщик)

| Инструмент | Параметры | Описание |
|------------|-----------|----------|
| `reminder_add` | title, description?, reminder_time (ISO-8601) | Создать напоминание |
| `reminder_list` | filter (all\|pending\|completed) | Список напоминаний |
| `reminder_complete` | reminder_id | Пометить как выполненное |
| `reminder_delete` | reminder_id | Удалить напоминание |
| `reminder_get_summary` | — | Сводка: всего, просрочено, на сегодня, ближайшие |

## Pipeline (композиция инструментов)

| Инструмент | Описание |
|------------|----------|
| `pipeline_search_docs` | Поиск документов по ключевым словам (база: kotlin, compose, mcp) |
| `pipeline_summarize` | Суммаризация текста, создание краткой сводки |
| `pipeline_save_to_file` | Сохранение контента в файл (директория `pipeline_results/`) |

**Пример композиции:**
```
Пользователь: "Найди документы про kotlin, суммаризируй и сохрани в файл"
Агент:
  1. Вызывает pipeline_search_docs("kotlin") → получает 3 документа
  2. Вызывает pipeline_summarize(результаты) → создает сводку
  3. Вызывает pipeline_save_to_file(сводка, "kotlin_summary.txt") → сохраняет
```

## RAG инструменты

| Инструмент | Описание |
|------------|----------|
| `rag_index_documents` | Индексация документов для векторного поиска |
| `rag_search` | Поиск релевантных фрагментов в индексе |
| `rag_index_info` | Информация об индексе (количество документов, размер, дата) |
| `ask_with_rag` | Запрос к LLM с автоматическим обогащением контекста из документов |
| `compare_rag_answers` | Сравнение ответов AI с RAG и без RAG |
| `compare_rag_with_reranking` | Трёхстороннее сравнение (БЕЗ RAG / С RAG / С ФИЛЬТРОМ) |

Подробнее о RAG — см. docs/rag.md

## Support инструменты

| Инструмент | Описание |
|------------|----------|
| `support_get_ticket` | Получить информацию о тикете по ID |
| `support_search_tickets` | Поиск тикетов по статусу, пользователю, категории |
| `support_get_user` | Информация о пользователе (план, email, тикеты) |
| `support_create_ticket` | Создать новый тикет |
| `support_update_ticket` | Обновить статус тикета или добавить сообщение |
| `support_get_faq` | Поиск в базе FAQ по категории или ключевым словам |
| `support_get_stats` | Статистика поддержки (открыто, в работе, решено) |

## GitHub инструменты

| Инструмент | Описание |
|------------|----------|
| `github_get_pr_info` | Получить метаданные PR (title, author, ветки, статистика) |
| `github_get_pr_diff` | Получить diff изменений в unified формате |
| `github_get_pr_files` | Получить список изменённых файлов с патчами |
| `github_post_review` | Опубликовать ревью с комментариями к строкам |

---

## Добавление нового инструмента

### Способ 1: С аннотациями (рекомендуется)

```kotlin
@Tool(name = "my_tool", description = "Описание инструмента")
@Param(name = "input", description = "Входные данные", type = "string", required = true)
@Param(name = "count", description = "Количество", type = "integer", required = false)
object MyNewTool : AnnotatedAgentTool() {
    override suspend fun execute(arguments: String): String {
        val json = Json.parseToJsonElement(arguments).jsonObject
        val input = json["input"]?.jsonPrimitive?.content
            ?: return "Ошибка: не указан input"
        val count = json["count"]?.jsonPrimitive?.intOrNull ?: 1
        return "Результат: $input (x$count)"
    }
}

// Зарегистрировать в ToolRegistry.registerBuiltInTools()
```

**Аннотации:**
- `@Tool(name, description)` — метаданные инструмента
- `@Param(name, description, type, required, enumValues)` — параметры
- Типы: `string`, `integer`, `number`, `boolean`, `array`, `object`
- `enumValues` — для параметров с фиксированным набором значений

### Способ 2: Классический (для сложных случаев)

```kotlin
object MyNewTool : AgentTool {
    override val name = "my_tool"
    override val description = "Описание"

    override fun getDefinition() = Tool(
        type = "function",
        function = FunctionDefinition(
            name = name,
            description = description,
            parameters = FunctionParameters(
                type = "object",
                properties = mapOf(
                    "input" to PropertyDefinition("string", "Входные данные")
                ),
                required = listOf("input")
            )
        )
    )

    override suspend fun execute(arguments: String): String {
        // реализация
    }
}
```

---

## Связанные документы

- MCP интеграция — см. docs/mcp.md
- RAG система — см. docs/rag.md
- API endpoints — см. docs/api.md
