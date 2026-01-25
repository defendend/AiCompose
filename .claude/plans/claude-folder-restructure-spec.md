# Спецификация: Реструктуризация .claude/ по документации 2026

## Summary

Миграция папки `.claude/` проекта AiCompose в соответствие с документацией Claude Code 2026. Основные изменения:
- Разбиение монолитного CLAUDE.md (~1700 строк) на модульные части
- Создание вложенной структуры rules/ по платформам
- Добавление docs/ с тематической документацией
- Вынесение серверной информации в CLAUDE.local.md
- Создание todos/backlog.md для отслеживания задач

---

## Technical Requirements

### Сохраняется без изменений
- `commands/` — плоская структура (review.md, architecture-check.md, help.md)
- `settings.json` — hooks остаются внутри
- `settings.local.json` — permissions с API ключами остаются (gitignored)
- `skills/ast-index/` — без изменений
- Нет миграции в папки: .mcp.json, agents/, hooks/

### Новые папки и файлы

#### rules/ (вложенная структура)
```
rules/
├── ast-index.md           # Существующий
├── code-style.md          # НОВЫЙ: общий стиль кода Kotlin
├── security.md            # НОВЫЙ: безопасность, API keys, validation
├── testing.md             # НОВЫЙ: правила написания тестов
├── architecture.md        # НОВЫЙ: слоистая архитектура, зависимости
├── git-workflow.md        # НОВЫЙ: коммиты, ветки, PR
├── backend/
│   ├── ktor.md            # НОВЫЙ: Ktor routes, plugins, таймауты
│   ├── di.md              # НОВЫЙ: Koin модули, singleOf vs single
│   ├── serialization.md   # НОВЫЙ: kotlinx.serialization, DeepSeek API
│   └── agent.md           # НОВЫЙ: Agent, ToolExecutor, PromptBuilder
└── desktop/
    ├── compose.md         # НОВЫЙ: Compose UI, state hoisting
    ├── viewmodel.md       # НОВЫЙ: ViewModel patterns, StateFlow
    ├── navigation.md      # НОВЫЙ: Screen navigation
    └── theme.md           # НОВЫЙ: Material3, темы
```

#### docs/ (тематическая документация)
```
docs/
├── api.md                 # НОВЫЙ: API endpoints (/chat, /logs, /health)
├── tools.md               # НОВЫЙ: инструменты агента, @Tool/@Param
├── mcp.md                 # НОВЫЙ: MCP интеграция (Weather, Reminder, Tracker)
├── rag.md                 # НОВЫЙ: RAG система, индексация, поиск
├── testing.md             # НОВЫЙ: покрытие тестами, стратегия
├── deployment.md          # НОВЫЙ: сборка, запуск (без серверных секретов)
├── changelog.md           # НОВЫЙ: история "День N" → хронология
└── structure.md           # НОВЫЙ: структура проекта, модули
```

#### todos/
```
todos/
└── backlog.md             # НОВЫЙ: TODO из CLAUDE.md
```

#### Корневые файлы
- `CLAUDE.md` — переписать: краткий обзор + ссылки на docs/ и rules/
- `CLAUDE.local.md` — НОВЫЙ: сервер (IP, SSH, systemd), gitignored

---

## Architecture Decisions

### 1. Формат commands/ — без изменений
**Решение:** Оставить файлы напрямую в commands/ (не в подпапках)
**Причина:** Оба формата работают, миграция — необязательный косметический шаг

### 2. Hooks в settings.json — без изменений
**Решение:** Не выносить в отдельный hooks/hooks.json
**Причина:** Текущий формат работает, единственный хук (PostToolUse echo)

### 3. API ключи в settings.local.json — без изменений
**Решение:** Оставить прямые ключи (HF_TOKEN, DEEPSEEK_API_KEY) в permissions
**Причина:** Файл в .gitignore, риск минимален

### 4. Вложенная структура rules/
**Решение:** rules/backend/ и rules/desktop/ вместо плоской структуры
**Причина:** Группировка по модулям, лучше масштабируется

### 5. YAML frontmatter для всех файлов
**Решение:** Добавить frontmatter с name и description
**Причина:** Консистентность со SKILL.md, улучшает discoverability

### 6. Ссылающиеся docs/
**Решение:** Файлы docs/*.md ссылаются друг на друга
**Причина:** Claude загружает файлы по релевантности, избегаем дублирования

---

## Interface Design

### CLAUDE.md (новая структура ~200-300 строк)
```markdown
# AiCompose

## Язык общения
- Общаемся на русском языке

## О проекте
[2-3 абзаца: Desktop + Backend, AI агент, DeepSeek]

## Структура проекта
[Краткая схема модулей: desktop/, backend/, shared/]

## Ключевые фичи
- RAG система — см. docs/rag.md
- MCP интеграция — см. docs/mcp.md
- Инструменты агента — см. docs/tools.md

## Технологии
[Таблица: Kotlin, Compose, Ktor, Koin, etc.]

## Запуск
[./gradlew :desktop:run, ./gradlew :backend:run]

## Соглашения
- Kotlin style guide — см. rules/code-style.md
- Архитектура — см. rules/architecture.md

## Документация
- API — docs/api.md
- Тестирование — docs/testing.md
- Деплой — docs/deployment.md
```

### CLAUDE.local.md (gitignored)
```markdown
# Локальная конфигурация AiCompose

## Сервер
- **IP**: 89.169.190.22
- **SSH**: `ssh -l defendend 89.169.190.22`
- **API**: http://89.169.190.22/api/
- **Сервис**: aicompose.service
- **Директория**: /opt/aicompose/
- **Env файл**: /opt/aicompose/.env

## Управление сервисом
```bash
sudo systemctl status aicompose
sudo systemctl restart aicompose
```

## Ручной деплой
[команды scp, ssh]
```

### Формат frontmatter для docs/ и rules/
```yaml
---
name: api-endpoints
description: Описание всех API endpoints проекта AiCompose
---
```

---

## Edge Cases & Error Handling

### Файлы с дублирующимся контентом
- При извлечении в docs/api.md удалить секцию из CLAUDE.md
- Оставить только ссылку: "см. docs/api.md"

### Большие секции (RAG, MCP)
- Извлечь полностью в отдельные файлы
- В CLAUDE.md — краткое описание (2-3 предложения) + ссылка

### История "День N"
- Собрать все "День N" секции в docs/changelog.md
- Формат: хронологический список с датами

### TODO список
- Перенести полностью в todos/backlog.md
- Сохранить чекбоксы [x] и [ ]

---

## Testing Strategy

### Валидация после миграции
1. Проверить что Claude Code корректно загружает CLAUDE.md
2. Проверить доступность rules/*.md в контексте
3. Проверить что docs/*.md подгружаются по запросу
4. Убедиться что settings.json hooks работают
5. Проверить что CLAUDE.local.md в .gitignore

### Ручное тестирование
- `/review` — должен работать без изменений
- `/architecture-check` — должен работать без изменений
- `/help` — должен работать без изменений

---

## Implementation Plan (TODO)

### Фаза 1: Создание структуры
- [ ] Создать папку docs/
- [ ] Создать папку rules/backend/
- [ ] Создать папку rules/desktop/
- [ ] Создать папку todos/

### Фаза 2: Извлечение docs/
- [ ] Создать docs/api.md (API endpoints)
- [ ] Создать docs/tools.md (инструменты агента)
- [ ] Создать docs/mcp.md (MCP интеграция)
- [ ] Создать docs/rag.md (RAG система)
- [ ] Создать docs/testing.md (покрытие тестами)
- [ ] Создать docs/deployment.md (сборка, запуск)
- [ ] Создать docs/changelog.md (история "День N")
- [ ] Создать docs/structure.md (структура проекта)

### Фаза 3: Создание rules/
- [ ] Создать rules/code-style.md
- [ ] Создать rules/security.md
- [ ] Создать rules/testing.md
- [ ] Создать rules/architecture.md
- [ ] Создать rules/git-workflow.md
- [ ] Создать rules/backend/ktor.md
- [ ] Создать rules/backend/di.md
- [ ] Создать rules/backend/serialization.md
- [ ] Создать rules/backend/agent.md
- [ ] Создать rules/desktop/compose.md
- [ ] Создать rules/desktop/viewmodel.md
- [ ] Создать rules/desktop/navigation.md
- [ ] Создать rules/desktop/theme.md

### Фаза 4: Миграция
- [ ] Создать CLAUDE.local.md (сервер)
- [ ] Создать todos/backlog.md (TODO)
- [ ] Переписать CLAUDE.md (краткий обзор + ссылки)

### Фаза 5: Финализация
- [ ] Добавить CLAUDE.local.md в .gitignore (если не добавлен)
- [ ] Проверить все ссылки между файлами
- [ ] Удалить дублирующийся контент из CLAUDE.md
- [ ] Валидация: запустить Claude Code и проверить работу

---

## Риски и Mitigation

| Риск | Вероятность | Mitigation |
|------|-------------|------------|
| Claude не найдёт нужный docs/*.md | Низкая | Правильные description в frontmatter |
| Битые ссылки между файлами | Средняя | Проверка grep'ом после миграции |
| Потеря контента при разбиении | Низкая | Git diff для проверки |
| settings.local.json с секретами в git | Низкая | Проверить .gitignore |

---

## Дата создания
2026-01-24

## Автор
spec-interview agent
