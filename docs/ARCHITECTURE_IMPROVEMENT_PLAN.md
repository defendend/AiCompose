# План улучшения архитектуры AiCompose

## Текущее состояние

### Достоинства
- Чёткое разделение на desktop/backend/shared модули
- MVVM паттерн на клиенте (ChatViewModel + StateFlow)
- Слоистая архитектура на сервере (api → agent → tools)
- Kotlinx Serialization для DTO
- **Shared модуль с общими моделями** ✅

### Проблемы
1. ~~**Дублирование моделей** — ChatMessage, ChatRequest, ResponseFormat дублируются~~ ✅ РЕШЕНО
2. **God Object** — Agent.kt (500+ строк) делает слишком много
3. **In-memory state** — conversations теряются при перезапуске
4. **Нет DI** — зависимости создаются вручную
5. **Нет UseCase слоя** — бизнес-логика размазана

---

## ✅ Фаза 1: Shared модуль (ВЫПОЛНЕНО)

### Что сделано
- Создан модуль `shared` с Kotlin Multiplatform
- Вынесены общие модели в `org.example.shared.model`:
  - `ChatMessage`, `ChatRequest`, `ChatResponse`
  - `MessageRole`, `ResponseFormat`
  - `ToolCall`, `ToolResult`
  - `CollectionMode`, `CollectionSettings`
- Обновлены импорты в desktop и backend
- В desktop оставлены UI-специфичные модели:
  - `StructuredResponse` — для парсинга JSON ответов
  - `CollectionModeTemplate`, `CollectionModeTemplates` — для UI
- В backend оставлены LLM-специфичные модели:
  - `LLMRequest`, `LLMMessage`, `LLMResponse`
  - `Tool`, `FunctionDefinition`, `FunctionParameters`

### Структура shared модуля
```
shared/
├── build.gradle.kts
└── src/commonMain/kotlin/org/example/shared/model/
    ├── ChatMessage.kt
    ├── ChatRequest.kt
    ├── ChatResponse.kt
    ├── CollectionMode.kt
    ├── CollectionSettings.kt
    ├── MessageRole.kt
    ├── ResponseFormat.kt
    ├── ToolCall.kt
    └── ToolResult.kt
```

### Результат
- ✅ Единый источник правды для API контракта
- ✅ Изменения в одном месте
- ✅ Type-safe API между клиентом и сервером
- ✅ Сборка проходит успешно

---

## ✅ Фаза 2: Декомпозиция Agent.kt (ВЫПОЛНЕНО)

### Цель
Разбить Agent.kt на отдельные классы с единой ответственностью.

### Было: Agent.kt (530 строк)
```
Agent.kt (530 строк)
├── HTTP client setup
├── Conversation storage (in-memory)
├── System prompt building (5 методов)
├── Tool calling loop
└── LLM API calls
```

### Стало: Декомпозированная структура

```
backend/src/main/kotlin/org/example/
├── agent/
│   ├── Agent.kt                 # Оркестратор (~150 строк) ✅
│   ├── PromptBuilder.kt         # Построение system prompt (~200 строк) ✅
│   └── ToolExecutor.kt          # Выполнение инструментов (~50 строк) ✅
├── data/
│   ├── ConversationRepository.kt  # Хранение истории (~65 строк) ✅
│   └── LLMClient.kt               # HTTP клиент для DeepSeek (~130 строк) ✅
└── domain/
    └── ChatUseCase.kt           # Бизнес-логика чата (опционально, не реализовано)
```

#### 2.1 ConversationRepository.kt

```kotlin
interface ConversationRepository {
    fun getHistory(conversationId: String): List<LLMMessage>
    fun addMessage(conversationId: String, message: LLMMessage)
    fun updateSystemPrompt(conversationId: String, prompt: String)
    fun getFormat(conversationId: String): ResponseFormat?
    fun setFormat(conversationId: String, format: ResponseFormat)
    fun getCollectionSettings(conversationId: String): CollectionSettings?
    fun setCollectionSettings(conversationId: String, settings: CollectionSettings)
}

class InMemoryConversationRepository : ConversationRepository {
    private val conversations = mutableMapOf<String, MutableList<LLMMessage>>()
    private val formats = mutableMapOf<String, ResponseFormat>()
    private val settings = mutableMapOf<String, CollectionSettings>()
    // ... implementation
}

// Будущее: RedisConversationRepository, PostgresConversationRepository
```

#### 2.2 PromptBuilder.kt

```kotlin
class PromptBuilder {
    fun buildSystemPrompt(
        format: ResponseFormat,
        collectionSettings: CollectionSettings?
    ): String

    private fun getBasePrompt(customPrompt: String?): String
    private fun getFormatInstruction(format: ResponseFormat): String
    private fun getCollectionModeInstruction(settings: CollectionSettings?): String
}
```

#### 2.3 LLMClient.kt

```kotlin
interface LLMClient {
    suspend fun chat(
        messages: List<LLMMessage>,
        tools: List<Tool>,
        temperature: Float?
    ): LLMResponse
}

class DeepSeekClient(
    private val apiKey: String,
    private val model: String = "deepseek-chat",
    private val baseUrl: String = "https://api.deepseek.com/v1"
) : LLMClient {
    // HTTP client setup
    // callLLM implementation
}
```

#### 2.4 ToolExecutor.kt

```kotlin
class ToolExecutor(
    private val toolRegistry: ToolRegistry
) {
    suspend fun executeToolCalls(
        toolCalls: List<LLMToolCall>,
        conversationId: String
    ): List<LLMMessage>
}
```

#### 2.5 Новый Agent.kt (< 100 строк)

```kotlin
class Agent(
    private val llmClient: LLMClient,
    private val conversationRepository: ConversationRepository,
    private val promptBuilder: PromptBuilder,
    private val toolExecutor: ToolExecutor
) {
    suspend fun chat(
        userMessage: String,
        conversationId: String,
        format: ResponseFormat,
        collectionSettings: CollectionSettings?,
        temperature: Float?
    ): ChatResponse {
        // 1. Update settings if changed
        // 2. Add user message
        // 3. Tool calling loop (max 5 iterations)
        // 4. Return response
    }
}
```

### Результат ✅
- Agent.kt: 530 → 150 строк (-72%)
- Каждый класс < 200 строк
- Легко тестировать изолированно (можно мокать LLMClient, ConversationRepository)
- Легко заменить реализации (Redis вместо in-memory, OpenAI вместо DeepSeek)
- Интерфейсы для основных компонентов (LLMClient, ConversationRepository)
- Обратная совместимость через secondary constructor в Agent

---

## ✅ Фаза 3: Dependency Injection (ВЫПОЛНЕНО)

### Цель
Внедрить Koin для управления зависимостями.

### Backend

```kotlin
// backend/build.gradle.kts
dependencies {
    implementation("io.insert-koin:koin-ktor:4.0.0")
    implementation("io.insert-koin:koin-logger-slf4j:4.0.0")
}

// di/AppModule.kt
fun appModule(apiKey: String) = module {
    single<LLMClient> { DeepSeekClient(apiKey) }
    single<ConversationRepository> { InMemoryConversationRepository() }
    singleOf(::PromptBuilder)
    singleOf(::ToolExecutor)
    single { Agent(get(), get(), get(), get()) }
}

// Application.kt
fun Application.configureKoin(apiKey: String) {
    install(Koin) {
        slf4jLogger()
        modules(appModule(apiKey))
    }
}

fun Application.configureRouting() {
    val agent by inject<Agent>()
    routing { chatRoutes(agent) }
}
```

### Desktop

```kotlin
// desktop/build.gradle.kts
dependencies {
    implementation("io.insert-koin:koin-core:4.0.0")
    implementation("io.insert-koin:koin-compose:4.0.0")
}

// di/AppModule.kt
val appModule = module {
    singleOf(::ChatApiClient)
    single { ChatViewModel(get()) }
}

// Main.kt
fun main() = application {
    KoinApplication(application = { modules(appModule) }) {
        App()
    }
}

@Composable
private fun ApplicationScope.App() {
    val chatViewModel: ChatViewModel = koinInject()
    val apiClient: ChatApiClient = koinInject()
    // ...
}
```

### Тестирование DI

```kotlin
// AppModuleTest.kt
class AppModuleTest : KoinTest {
    @BeforeTest
    fun setup() {
        startKoin { modules(appModule("test-api-key")) }
    }

    @Test
    fun `module provides Agent with all dependencies`() {
        val agent: Agent = get()
        assertNotNull(agent)
    }

    @Test
    fun `LLMClient is singleton`() {
        val client1: LLMClient = get()
        val client2: LLMClient = get()
        assertSame(client1, client2)
    }
}
```

### Результат ✅
- Централизованное управление зависимостями
- Легко подменять реализации для тестов
- Koin 4.0.0 с поддержкой Compose
- 8 тестов для DI модуля
- Чистый код без ручного создания объектов

---

## ✅ Фаза 8: CI Pipeline (ВЫПОЛНЕНО)

### Цель
Автоматизировать сборку и тестирование при каждом push/PR.

### CI Workflow (.github/workflows/ci.yml)

Запускается на:
- Push в `main`
- Pull Request в `main`

Шаги:
1. Setup JDK 21
2. Build shared module
3. Build and test backend
4. Compile desktop

```yaml
jobs:
  build-and-test:
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@v4
      - uses: actions/setup-java@v4
        with:
          distribution: 'temurin'
          java-version: '21'
          cache: 'gradle'
      - run: ./gradlew :shared:build :backend:build :desktop:compileKotlin
```

### Deploy Workflow (обновлён)

- Добавлен отдельный job `test` перед `deploy`
- Deploy зависит от успешного прохождения тестов (`needs: test`)
- Триггерится при изменениях в `backend/**` или `shared/**`

### Результат ✅
- Автоматическая проверка при каждом PR
- Тесты запускаются перед деплоем
- Артефакты с результатами тестов
- JUnit report в GitHub Actions

---

## ✅ Фаза 4: Персистентное хранение (ВЫПОЛНЕНО)

### Цель
Сохранять историю диалогов между перезапусками сервера.

### Реализация: Redis (Lettuce)

Добавлен `RedisConversationRepository` с использованием Lettuce (async Redis client).

#### Зависимости (backend/build.gradle.kts)
```kotlin
implementation("io.lettuce:lettuce-core:6.3.2.RELEASE")
implementation("org.jetbrains.kotlinx:kotlinx-coroutines-reactive:1.9.0")
```

#### Структура ключей Redis
- `conv:{id}:messages` — JSON массив сообщений диалога
- `conv:{id}:format` — формат ответа (ResponseFormat)
- `conv:{id}:settings` — настройки сбора данных (CollectionSettings)

#### Конфигурация через env переменные
```bash
REDIS_ENABLED=true        # Включить Redis (по умолчанию: false)
REDIS_URL=redis://host:6379   # URL Redis (по умолчанию: redis://localhost:6379)
REDIS_TTL_HOURS=24        # TTL диалогов в часах (по умолчанию: 24)
```

#### DI конфигурация (AppModule.kt)
```kotlin
data class RepositoryConfig(
    val useRedis: Boolean = false,
    val redisUrl: String = "redis://localhost:6379",
    val redisTtlSeconds: Long = 86400
) {
    companion object {
        fun fromEnv(): RepositoryConfig // Читает из env переменных
    }
}

fun appModule(apiKey: String, repositoryConfig: RepositoryConfig) = module {
    single<ConversationRepository> {
        if (repositoryConfig.useRedis) {
            RedisConversationRepository(redisUrl, ttlSeconds)
        } else {
            InMemoryConversationRepository()
        }
    }
}
```

#### Особенности реализации
- **Lettuce async API** — используется `RedisAsyncCommands` с `await()` через `kotlinx-coroutines-reactive`
- **TTL для диалогов** — автоматическое удаление через SETEX
- **Graceful error handling** — логирование ошибок без падения приложения
- **Thread-safe** — Lettuce connection pool
- **Метод close()** — для корректного завершения соединения

### Результат ✅
- История диалогов сохраняется между перезапусками сервера
- Переключение In-Memory ↔ Redis через env переменные
- TTL для автоматической очистки старых диалогов
- 3 новых теста для RepositoryConfig

---

## Фаза 5: UseCase слой (Приоритет: НИЗКИЙ)

### Цель
Выделить бизнес-логику в отдельный слой для сложных сценариев.

### Когда нужен
- Если появится логика, которая не относится ни к Agent, ни к Repository
- Например: rate limiting, billing, analytics

### Пример

```kotlin
class SendMessageUseCase(
    private val agent: Agent,
    private val rateLimiter: RateLimiter,
    private val analytics: Analytics
) {
    suspend operator fun invoke(request: ChatRequest): Result<ChatResponse> {
        // 1. Check rate limit
        rateLimiter.checkLimit(request.conversationId)

        // 2. Process message
        val response = agent.chat(...)

        // 3. Track analytics
        analytics.trackMessage(request, response)

        return Result.success(response)
    }
}
```

---

## ✅ Фаза 6: Расширяемость инструментов (ВЫПОЛНЕНО)

### Цель
Сделать добавление новых инструментов максимально простым.

### Реализовано

#### 6.1 Структура пакетов tools

```
backend/src/main/kotlin/org/example/tools/
├── annotations/
│   └── ToolAnnotations.kt      # @Tool, @Param, @Params
├── core/
│   ├── AgentTool.kt            # Интерфейс + AnnotatedAgentTool
│   ├── ToolMetadata.kt         # ToolMetadata, ParamMetadata, ToolMetadataReader
│   └── ToolRegistry.kt         # Реестр с автоинициализацией
├── historical/
│   ├── HistoricalEventsTool.kt
│   ├── HistoricalFigureTool.kt
│   ├── CompareErasTool.kt
│   └── HistoricalQuoteTool.kt
└── Tools.kt                    # Обратная совместимость (deprecated)
```

#### 6.2 Аннотации для инструментов

```kotlin
@Tool(
    name = "get_weather",
    description = "Получить погоду в городе"
)
@Param(
    name = "city",
    description = "Название города",
    type = "string",
    required = true
)
@Param(
    name = "units",
    description = "Единицы измерения",
    type = "string",
    required = false,
    enumValues = ["celsius", "fahrenheit"]
)
object WeatherTool : AnnotatedAgentTool() {
    override suspend fun execute(arguments: String): String {
        val json = Json.parseToJsonElement(arguments).jsonObject
        val city = json["city"]?.jsonPrimitive?.content ?: return "Ошибка: не указан город"
        // implementation
    }
}
```

**Особенности:**
- `@Tool` — определяет имя и описание инструмента
- `@Param` — определяет параметры (поддерживает @Repeatable)
- `AnnotatedAgentTool` — базовый класс, автоматически генерирует `getDefinition()` из аннотаций
- Поддержка enum параметров через `enumValues`

#### 6.3 ToolRegistry

```kotlin
// Автоматическая инициализация при первом обращении
val tools = ToolRegistry.getAllTools()

// Ручная регистрация кастомного инструмента
ToolRegistry.register(MyCustomTool)

// Выполнение инструмента
val result = ToolRegistry.executeTool("my_tool", """{"arg": "value"}""")
```

**Особенности:**
- Thread-safe (ConcurrentHashMap)
- Ленивая инициализация
- Встроенные инструменты регистрируются автоматически
- Graceful error handling при выполнении

#### 6.4 Добавление нового инструмента

1. Создать файл в `tools/` (или новом подпакете)
2. Добавить аннотации `@Tool` и `@Param`
3. Наследовать от `AnnotatedAgentTool`
4. Реализовать `execute(arguments: String)`
5. Зарегистрировать в `ToolRegistry.registerBuiltInTools()` (или через DI)

```kotlin
@Tool(name = "my_tool", description = "Мой инструмент")
@Param(name = "input", description = "Входные данные", type = "string", required = true)
object MyTool : AnnotatedAgentTool() {
    override suspend fun execute(arguments: String): String {
        val json = Json.parseToJsonElement(arguments).jsonObject
        val input = json["input"]?.jsonPrimitive?.content
            ?: return "Ошибка: не указан input"
        return "Результат: $input"
    }
}
```

### Результат ✅
- Декларативное определение инструментов через аннотации
- Автоматическая генерация Tool definition для LLM API
- Модульная структура с разделением по категориям
- 35 новых тестов для системы инструментов
- Обратная совместимость через deprecated aliases

---

## ✅ Фаза 7: Тестирование (ВЫПОЛНЕНО)

### Структура тестов

```
backend/src/test/kotlin/org/example/
├── agent/
│   ├── AgentTest.kt           # 14 тестов — unit тесты с моками ✅
│   ├── PromptBuilderTest.kt   # 19 тестов — форматы, режимы, промпты ✅
│   └── ToolExecutorTest.kt    # 8 тестов — выполнение tool calls ✅
├── data/
│   └── ConversationRepositoryTest.kt  # 16 тестов — история, форматы, настройки ✅
├── api/
│   └── RoutesTest.kt          # 9 тестов — интеграционные тесты API ✅
└── tools/
    └── ToolsTest.kt           # 24 теста — все инструменты + ToolRegistry ✅

desktop/src/test/kotlin/org/example/  # (TODO: будущее)
├── ui/
│   └── ChatViewModelTest.kt
└── network/
    └── ChatApiClientTest.kt
```

### Тестовые зависимости (backend/build.gradle.kts)
```kotlin
testImplementation(kotlin("test"))
testImplementation("io.ktor:ktor-server-test-host:3.0.3")
testImplementation("io.mockk:mockk:1.13.13")
testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.9.0")
testImplementation("io.ktor:ktor-client-mock:3.0.3")
```

### Запуск тестов
```bash
./gradlew :backend:test           # Запуск всех тестов
./gradlew :backend:test --info    # С подробным выводом
```

### Результат
- ✅ **90 тестов**
- ✅ **100% успешных**
- ✅ Время выполнения: ~1.1 секунды

### Пример теста

```kotlin
class PromptBuilderTest {
    private val builder = PromptBuilder()

    @Test
    fun `buildSystemPrompt includes format instruction`() {
        val prompt = builder.buildSystemPrompt(
            format = ResponseFormat.MARKDOWN,
            collectionSettings = null
        )

        assertThat(prompt).contains("Markdown")
        assertThat(prompt).contains("##")
    }

    @Test
    fun `buildSystemPrompt uses custom character`() {
        val settings = CollectionSettings(
            customSystemPrompt = "Ты пират Джек"
        )

        val prompt = builder.buildSystemPrompt(
            format = ResponseFormat.PLAIN,
            collectionSettings = settings
        )

        assertThat(prompt).contains("пират Джек")
        assertThat(prompt).doesNotContain("Архивариус")
    }
}
```

---

## Приоритеты реализации

| Фаза                    | Приоритет | Сложность | Влияние              | Статус       |
|-------------------------|-----------|-----------|----------------------|--------------|
| 1. Shared модуль        | ВЫСОКИЙ   | Средняя   | Убирает дублирование | ✅ ВЫПОЛНЕНО |
| 2. Декомпозиция Agent   | ВЫСОКИЙ   | Высокая   | Улучшает поддержку   | ✅ ВЫПОЛНЕНО |
| 7. Тестирование         | ВЫСОКИЙ   | Средняя   | Стабильность         | ✅ ВЫПОЛНЕНО |
| 3. Dependency Injection | СРЕДНИЙ   | Низкая    | Чистый код           | ✅ ВЫПОЛНЕНО |
| 8. CI Pipeline          | СРЕДНИЙ   | Низкая    | Автоматизация        | ✅ ВЫПОЛНЕНО |
| 6. Расширяемость tools  | СРЕДНИЙ   | Средняя   | Гибкость             | ✅ ВЫПОЛНЕНО |
| 4. Персистентность      | СРЕДНИЙ   | Средняя   | Production-ready     | ✅ ВЫПОЛНЕНО |
| 5. UseCase слой         | НИЗКИЙ    | Низкая    | Для масштабирования  | 💤 Реализовать при необходимости |

---

## Рекомендуемый порядок

### ✅ Итерация 1 (Фундамент) — ВЫПОЛНЕНО
1. ✅ Создать `shared` модуль
2. ✅ Мигрировать общие модели
3. ✅ Обновить импорты в desktop и backend

### ✅ Итерация 2 (Рефакторинг backend) — ВЫПОЛНЕНО
1. ✅ Вынести `PromptBuilder`
2. ✅ Вынести `ConversationRepository`
3. ✅ Вынести `LLMClient`
4. ✅ Вынести `ToolExecutor`
5. ✅ Упростить `Agent.kt` (530 → 150 строк)

### ✅ Итерация 3 (Тестирование) — ВЫПОЛНЕНО
1. ✅ Добавлены тестовые зависимости (MockK, kotlinx-coroutines-test, ktor-client-mock)
2. ✅ Unit тесты для PromptBuilder (19 тестов)
3. ✅ Unit тесты для ConversationRepository (16 тестов)
4. ✅ Unit тесты для ToolExecutor (8 тестов)
5. ✅ Unit тесты для Agent с моками LLMClient (14 тестов)
6. ✅ Интеграционные тесты для Routes (9 тестов)
7. ✅ Unit тесты для Tools и ToolRegistry (24 теста)
8. ✅ Исправлена версия JVM toolchain в shared модуле (21)

**Всего: 90 тестов, 100% успешных**

### ✅ Итерация 4 (DI и CI) — ВЫПОЛНЕНО
1. ✅ Внедрить Koin для DI в backend
2. ✅ Внедрить Koin для DI в desktop
3. ✅ Настроить CI workflow (ci.yml)
4. ✅ Добавить тесты в deploy workflow
5. ✅ Тесты для DI модуля (8 тестов)

**Всего: 98 тестов, 100% успешных**

### ✅ Итерация 5 (Расширяемость) — ВЫПОЛНЕНО
1. ✅ Аннотации @Tool и @Param для декларативного определения инструментов
2. ✅ AnnotatedAgentTool базовый класс с автогенерацией definition
3. ✅ ToolMetadataReader для чтения метаданных через рефлексию
4. ✅ Модульная структура tools (core, annotations, historical)
5. ✅ 35 новых тестов для системы инструментов

**Всего: 133 теста, 100% успешных**

### ✅ Итерация 6 (Персистентность) — ВЫПОЛНЕНО
1. ✅ Добавлен Lettuce (async Redis client) для Kotlin/JVM
2. ✅ Реализован `RedisConversationRepository` с TTL и async операциями
3. ✅ Конфигурация через env переменные (REDIS_ENABLED, REDIS_URL, REDIS_TTL_HOURS)
4. ✅ DI переключение между In-Memory и Redis
5. ✅ 3 новых теста для RepositoryConfig

**Всего: 136 тестов, 100% успешных**

### ⏳ Итерация 7 (Production) — СЛЕДУЮЩАЯ
1. Улучшить error handling
2. Добавить метрики
3. Health check для Redis

---

## Итоговая оценка архитектуры (Декабрь 2025)

### Состояние: ✅ Production-Ready

Архитектура проекта достигла зрелого состояния. Все критические улучшения выполнены.

### Достигнутые результаты

| Метрика | Значение |
|---------|----------|
| Тестовое покрытие | 136 тестов, 100% успешных |
| Размер Agent.kt | 530 → 150 строк (-72%) |
| Модульность | 3 модуля (desktop, backend, shared) |
| DI | Koin 4.0.0 |
| CI/CD | GitHub Actions (тесты + автодеплой) |
| Персистентность | Redis (опционально) |

### Архитектурные принципы

- **Single Responsibility** — каждый класс имеет одну ответственность
- **Dependency Inversion** — зависимости через интерфейсы (LLMClient, ConversationRepository)
- **Open/Closed** — расширяемость через аннотации (@Tool, @Param)
- **DRY** — общие модели в shared модуле

### Что НЕ требуется на данном этапе

- **UseCase слой** — текущая архитектура достаточна. Добавить при появлении:
  - Rate limiting
  - Billing/подписки
  - Сложная аналитика
  - Межсервисное взаимодействие

### ✅ Дополнительные улучшения (Декабрь 2025)

Реализованы дополнительные возможности:

| Функция | Описание | Статус |
|---------|----------|--------|
| Streaming (SSE) | POST /api/chat/stream — потоковая передача ответов | ✅ Готово |
| Health Checks | GET /api/health/detailed — проверка LLM и Redis | ✅ Готово |
| Desktop SSE | ChatViewModel с поддержкой streaming | ✅ Готово |

**Новые тесты:** 5 (всего 141 тестов)

### Рекомендации по развитию

При дальнейшем масштабировании рассмотреть:
1. **Метрики** (Micrometer/Prometheus) — для мониторинга
2. **Rate limiting** — при росте нагрузки
3. **Кэширование** — для частых запросов

---

## Claude CLI команды для этого проекта

```bash
# Code review изменений
/review

# Проверка архитектуры
/architecture-check
```
