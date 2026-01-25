---
name: rag-system
description: RAG (Retrieval-Augmented Generation) система — индексация, поиск, реранкинг, CLI чат-бот
---

# RAG (Retrieval-Augmented Generation) система

## Обзор

RAG система позволяет агенту искать релевантную информацию в документах и использовать её для ответов на вопросы.

## Компоненты

| Файл | Описание |
|------|----------|
| `DocumentChunker.kt` | Разбивка документов на чанки (500 символов, 50 overlap) |
| `SimpleEmbeddings.kt` | TF-IDF векторизация |
| `DocumentIndex.kt` | Индекс документов (save/load JSON, поддержка minRelevance) |
| `RerankerService.kt` | Фильтрация и реранкинг результатов |
| `RagQueryService.kt` | Сервис RAG-запросов |
| `RagChatBot.kt` | CLI чат-бот с RAG-памятью |

## Инструменты

| Инструмент | Описание |
|------------|----------|
| `rag_index_documents` | Индексация документов для векторного поиска |
| `rag_search` | Поиск релевантных фрагментов в индексе |
| `rag_index_info` | Информация об индексе |
| `ask_with_rag` | Запрос к LLM с контекстом из документов |
| `compare_rag_answers` | Сравнение ответов с RAG и без |
| `compare_rag_with_reranking` | Трёхстороннее сравнение |

---

## Pipeline RAG-запроса

1. Вопрос пользователя
2. Поиск релевантных чанков в индексе (TF-IDF + cosine similarity)
3. Объединение чанков с вопросом в контекст
4. Запрос к LLM с обогащённым контекстом

**RagQueryService методы:**
- `queryWithRag()` — запрос С поиском по документам
- `queryWithoutRag()` — запрос БЕЗ дополнительного контекста
- `compareAnswers()` — сравнение обоих режимов
- `compareWithReranking()` — трёхстороннее сравнение

**Статистика в ответе:**
- Найдено фрагментов
- Источники (список файлов)
- Средняя релевантность (cosine similarity)
- Время ответа (ms)
- Токены (вход → выход)

---

## Реранкинг и фильтрация

**RerankerService методы:**
- `filterByRelevance()` — фильтрация по порогу (0.0-1.0)
- `rerank()` — реранкинг результатов
- `processResults()` — комбинированная обработка

**Константы порогов:**
| Константа | Значение | Описание |
|-----------|----------|----------|
| STRICT | 0.5 | Только максимально релевантные |
| MODERATE | 0.3 | Хорошая релевантность |
| RELAXED | 0.1 | Допускает слабо релевантные |
| NONE | 0.0 | Все результаты topK |

**Рекомендации:**
| Сценарий | Порог |
|----------|-------|
| Критическая точность | 0.5 (STRICT) |
| Баланс качество/покрытие | 0.3 (MODERATE) |
| Максимальное покрытие | 0.1 (RELAXED) |
| Без фильтрации | 0.0 (NONE) |

---

## Примеры использования

**Задать вопрос с RAG:**
```bash
curl -X POST http://localhost:8080/api/tools/execute \
  -H "Content-Type: application/json" \
  -d '{
    "tool": "ask_with_rag",
    "arguments": "{\"question\": \"What is RAG?\", \"top_k\": 3}"
  }'
```

**С фильтром релевантности:**
```bash
curl -X POST http://localhost:8080/api/tools/execute \
  -H "Content-Type: application/json" \
  -d '{
    "tool": "ask_with_rag",
    "arguments": "{\"question\": \"What is RAG?\", \"top_k\": 3, \"min_relevance\": 0.3}"
  }'
```

**Трёхстороннее сравнение:**
```bash
curl -X POST http://localhost:8080/api/tools/execute \
  -H "Content-Type: application/json" \
  -d '{
    "tool": "compare_rag_with_reranking",
    "arguments": "{\"question\": \"How does Docker work?\", \"top_k\": 3, \"min_relevance\": 0.3}"
  }'
```

---

## RAG Chat Bot (CLI)

Интерактивный CLI чат-бот с RAG-памятью и выводом источников.

**Возможности:**
- Хранит историю диалога (до 20 сообщений)
- При каждом вопросе ищет контекст в базе документов
- Возвращает ответ с указанием источников
- Визуализация релевантности (progress bar)

**Команды в чате:**
| Команда | Описание |
|---------|----------|
| `/index <путь>` | Индексировать документы |
| `/history` | Показать историю диалога |
| `/clear` | Очистить историю |
| `/status` | Показать статус |
| `/help` | Справка |
| `/exit` | Выход |

**Запуск:**
```bash
DEEPSEEK_API_KEY=xxx ./scripts/rag_chat_demo.sh ./docs

# Или через Gradle
DEEPSEEK_API_KEY=xxx ./gradlew :backend:runRagChat --args="./docs" --console=plain
```

**Формат ответа:**
```kotlin
data class ChatResponse(
    val answer: String,           // Ответ AI
    val sources: List<SourceInfo>, // Список источников
    val usedRag: Boolean,         // Был ли использован RAG
    val historySize: Int,         // Размер истории
    val durationMs: Long          // Время ответа
)

data class SourceInfo(
    val file: String,      // Файл-источник
    val relevance: Float,  // Релевантность (0.0-1.0)
    val snippet: String    // Превью контента
)
```

---

## Важно

- RagQueryService использует `globalIndex` из RagTools
- Индекс должен быть проиндексирован через `rag_index_documents` перед использованием
- При загрузке индекса из файла SimpleEmbeddings НЕ восстанавливает vocabulary/IDF — нужна переиндексация после перезапуска

---

## Связанные документы

- Инструменты агента — см. docs/tools.md
- API endpoints — см. docs/api.md
- Документация RAG — см. docs/RAG_DEMO.md (корень проекта)
