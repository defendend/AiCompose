# День 21: Автоматизация ревью кода

## Цель
Создать пайплайн автоматического ревью PR:
1. GitHub Action триггерится на новый PR
2. Ассистент получает diff и файлы через GitHub API
3. Использует RAG для контекста (документация + код)
4. Выдаёт структурированное ревью с замечаниями

---

## Архитектура

```
┌─────────────────────────────────────────────────────────────────┐
│                    GitHub Pull Request                          │
│                         (opened/updated)                         │
└────────────────────────────┬────────────────────────────────────┘
                             │
                             ▼
┌─────────────────────────────────────────────────────────────────┐
│                    GitHub Action                                 │
│              .github/workflows/code-review.yml                   │
│                                                                  │
│   1. Получает PR number, repo, branch                           │
│   2. Вызывает POST /api/review с данными PR                     │
└────────────────────────────┬────────────────────────────────────┘
                             │
                             ▼
┌─────────────────────────────────────────────────────────────────┐
│                    Backend API                                   │
│                  POST /api/review                                │
│                                                                  │
│   Параметры:                                                     │
│   - owner: string (владелец репо)                               │
│   - repo: string (название репо)                                │
│   - pr_number: int (номер PR)                                   │
│   - github_token: string (токен для API)                        │
└────────────────────────────┬────────────────────────────────────┘
                             │
                             ▼
┌─────────────────────────────────────────────────────────────────┐
│                    Code Review Agent                             │
│                                                                  │
│   1. github_get_pr_info     → метаданные PR                     │
│   2. github_get_pr_diff     → diff изменений                    │
│   3. github_get_pr_files    → список файлов                     │
│   4. docs_search / rag      → контекст из документации          │
│   5. code_search            → связанный код                     │
│   6. LLM анализ             → формирование ревью                │
│   7. github_post_review     → публикация ревью                  │
└─────────────────────────────────────────────────────────────────┘
```

---

## Файлы для создания

### 1. GitHub Tools
```
backend/src/main/kotlin/org/example/tools/devassistant/github/
├── GithubToolBase.kt         # Базовый класс (HTTP client, auth)
├── GithubPrInfoTool.kt       # github_get_pr_info
├── GithubPrDiffTool.kt       # github_get_pr_diff
├── GithubPrFilesTool.kt      # github_get_pr_files
└── GithubPostReviewTool.kt   # github_post_review
```

### 2. API Endpoint
```
backend/src/main/kotlin/org/example/api/
└── Routes.kt                  # Добавить POST /api/review
```

### 3. GitHub Action
```
.github/workflows/
└── code-review.yml            # Триггер на PR
```

---

## Инструменты (4 штуки)

| Инструмент | Описание | Параметры |
|------------|----------|-----------|
| `github_get_pr_info` | Получить метаданные PR | `owner`, `repo`, `pr_number`, `token` |
| `github_get_pr_diff` | Получить diff PR | `owner`, `repo`, `pr_number`, `token` |
| `github_get_pr_files` | Список изменённых файлов | `owner`, `repo`, `pr_number`, `token` |
| `github_post_review` | Опубликовать ревью | `owner`, `repo`, `pr_number`, `token`, `body`, `event` |

---

## API Endpoint

### POST /api/review
```json
Request:
{
  "owner": "anthropics",
  "repo": "claude-code",
  "pr_number": 123,
  "github_token": "ghp_xxx"
}

Response:
{
  "status": "completed",
  "review_id": 12345,
  "summary": "Найдено 3 замечания...",
  "comments": [
    {
      "path": "src/main.kt",
      "line": 42,
      "body": "Потенциальная проблема с null safety..."
    }
  ]
}
```

---

## План реализации

### Этап 1: GitHub Tools (базовые)
1. Создать `GithubToolBase.kt` — HTTP клиент с auth
2. Реализовать `github_get_pr_info` — метаданные PR
3. Реализовать `github_get_pr_diff` — diff изменений
4. Реализовать `github_get_pr_files` — список файлов

### Этап 2: Публикация ревью
1. Реализовать `github_post_review` — создание review
2. Добавить поддержку inline comments

### Этап 3: API Endpoint
1. Добавить `POST /api/review` в Routes.kt
2. Интегрировать с Agent для анализа

### Этап 4: GitHub Action
1. Создать `.github/workflows/code-review.yml`
2. Настроить триггер на PR events
3. Передать GITHUB_TOKEN и вызвать API

### Этап 5: Интеграция с RAG
1. Использовать `docs_search` для контекста
2. Использовать `code_search` для связанного кода

---

## GitHub API

### Get PR Info
```bash
GET /repos/{owner}/{repo}/pulls/{pull_number}
Authorization: Bearer {token}
```

### Get PR Diff
```bash
GET /repos/{owner}/{repo}/pulls/{pull_number}
Accept: application/vnd.github.v3.diff
Authorization: Bearer {token}
```

### Get PR Files
```bash
GET /repos/{owner}/{repo}/pulls/{pull_number}/files
Authorization: Bearer {token}
```

### Create Review
```bash
POST /repos/{owner}/{repo}/pulls/{pull_number}/reviews
Authorization: Bearer {token}
Content-Type: application/json

{
  "body": "Общий комментарий к PR",
  "event": "COMMENT",  // или "APPROVE" / "REQUEST_CHANGES"
  "comments": [
    {
      "path": "file.kt",
      "position": 10,
      "body": "Замечание к строке"
    }
  ]
}
```

---

## Системный промпт для Code Review Agent

```
Ты — опытный код-ревьюер. Анализируй изменения в PR и давай конструктивные замечания.

Фокусируйся на:
1. Архитектура и паттерны
2. Потенциальные баги
3. Безопасность
4. Производительность
5. Читаемость кода
6. Соответствие стилю проекта

Формат ответа:
- Краткое резюме изменений
- Список замечаний с указанием файла и строки
- Рекомендации по улучшению
- Итоговая оценка (Approve / Request Changes / Comment)
```

---

## Верификация

1. **Локальный тест:**
   ```bash
   curl -X POST http://localhost:8080/api/review \
     -H "Content-Type: application/json" \
     -d '{
       "owner": "defendend",
       "repo": "horobot",
       "pr_number": 1,
       "github_token": "ghp_xxx"
     }'
   ```

2. **GitHub Action тест:**
   - Создать тестовый PR
   - Проверить что Action запустился
   - Проверить что ревью опубликовано

---

## Переменные окружения

| Переменная | Описание |
|------------|----------|
| `GITHUB_TOKEN` | Токен для GitHub API (в secrets) |
| `REVIEW_API_URL` | URL бэкенда (http://89.169.190.22/api/review) |
