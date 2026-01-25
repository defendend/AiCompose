---
name: deployment-guide
description: Сборка и запуск проекта AiCompose — локально и на сервере
---

# Деплой и запуск

## Локальный запуск

### Desktop
```bash
./gradlew :desktop:run
```

### Backend (нужен DEEPSEEK_API_KEY)
```bash
DEEPSEEK_API_KEY=xxx ./gradlew :backend:run
```

### С HuggingFace токеном
```bash
HF_TOKEN=hf_xxx ./gradlew :desktop:run
```

## Сборка

```bash
./gradlew build                           # Всё
./gradlew :backend:buildFatJar            # JAR для деплоя
./gradlew :desktop:packageDmg             # Mac дистрибутив
```

## GitHub Actions

### CI Workflow (ci.yml)
Запускается при push и PR в `main`. Выполняет:
- Сборку shared модуля
- Сборку и тесты backend
- Компиляцию desktop

### Deploy Workflow (deploy-backend.yml)
Автодеплой срабатывает при пуше в `backend/**`, `shared/**` или в workflow файл.
- Сначала запускает тесты (job: test)
- При успешных тестах выполняет деплой (job: deploy)

**Секреты (настроены в репозитории):**
- `SSH_HOST`
- `SSH_USER`
- `SSH_PRIVATE_KEY`

## Переменные окружения

### Обязательные
| Переменная | Описание |
|------------|----------|
| `DEEPSEEK_API_KEY` | API ключ DeepSeek |

### Опциональные
| Переменная | Описание | Default |
|------------|----------|---------|
| `HF_TOKEN` | HuggingFace токен | — |
| `STORAGE_TYPE` | Тип хранилища (memory/redis/postgres) | memory |
| `REDIS_URL` | URL Redis | redis://localhost:6379 |
| `REDIS_TTL_HOURS` | TTL диалогов | 24 |
| `DB_URL` | JDBC URL PostgreSQL | — |
| `DB_USER` | Пользователь БД | — |
| `DB_PASSWORD` | Пароль БД | — |
| `DB_POOL_SIZE` | Размер пула | 10 |

## Хранилища данных

| Тип | Персистентность | Использование |
|-----|-----------------|---------------|
| In-Memory | ❌ Теряется при перезапуске | Разработка, тестирование |
| Redis | ✅ TTL-based | Кэширование, сессии |
| PostgreSQL | ✅ Постоянное | Продакшн |

### PostgreSQL (рекомендуется для продакшна)

```bash
STORAGE_TYPE=postgres
DB_URL=jdbc:postgresql://localhost:5432/aicompose
DB_USER=postgres
DB_PASSWORD=secret
DB_POOL_SIZE=10
```

### Redis

```bash
STORAGE_TYPE=redis
REDIS_URL=redis://host:6379
REDIS_TTL_HOURS=24
```

## Таймауты

| Компонент | Таймаут | Описание |
|-----------|---------|----------|
| Desktop клиент | 180 сек | Для сложных режимов типа "Группа экспертов" |
| Backend к DeepSeek | 120 сек | HTTP запросы к LLM |

## Известные проблемы

- DeepSeek API может обрывать соединение при длинных промптах (ошибка "Chunked stream has ended unexpectedly")
- При большом системном промпте (режимы сбора) запрос может упасть по таймауту

---

## Серверная информация

Серверные секреты (IP, SSH, пути) находятся в `CLAUDE.local.md` (gitignored).

---

## Связанные документы

- API endpoints — см. docs/api.md
- Тестирование — см. docs/testing.md
