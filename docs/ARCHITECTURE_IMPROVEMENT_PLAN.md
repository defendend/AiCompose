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

## Фаза 2: Декомпозиция Agent.kt (Приоритет: ВЫСОКИЙ)

### Цель
Разбить Agent.kt на отдельные классы с единой ответственностью.

### Текущая структура Agent.kt
```
Agent.kt (530 строк)
├── HTTP client setup
├── Conversation storage (in-memory)
├── System prompt building (5 методов)
├── Tool calling loop
└── LLM API calls
```

### Новая структура

```
backend/src/.../
├── agent/
│   ├── Agent.kt                 # Оркестратор (< 100 строк)
│   ├── PromptBuilder.kt         # Построение system prompt
│   └── ToolExecutor.kt          # Выполнение инструментов
├── data/
│   ├── ConversationRepository.kt  # Хранение истории
│   └── LLMClient.kt               # HTTP клиент для DeepSeek
└── domain/
    └── ChatUseCase.kt           # Бизнес-логика чата (опционально)
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

### Результат
- Каждый класс < 150 строк
- Легко тестировать изолированно
- Легко заменить реализации (Redis вместо in-memory)

---

## Фаза 3: Dependency Injection (Приоритет: СРЕДНИЙ)

### Цель
Внедрить Koin для управления зависимостями.

### Backend

```kotlin
// backend/build.gradle.kts
dependencies {
    implementation("io.insert-koin:koin-ktor:3.5.3")
}

// di/AppModule.kt
val appModule = module {
    single { DeepSeekClient(getProperty("DEEPSEEK_API_KEY")) } bind LLMClient::class
    single { InMemoryConversationRepository() } bind ConversationRepository::class
    single { PromptBuilder() }
    single { ToolExecutor(get()) }
    single { Agent(get(), get(), get(), get()) }
}

// Application.kt
fun Application.module() {
    install(Koin) {
        modules(appModule)
    }
    configureRouting()
}
```

### Desktop

```kotlin
// desktop/build.gradle.kts
dependencies {
    implementation("io.insert-koin:koin-compose:3.5.3")
}

// di/AppModule.kt
val appModule = module {
    single { ChatApiClient() }
    viewModel { ChatViewModel(get()) }
}

// Main.kt
fun main() = application {
    KoinApplication(application = { modules(appModule) }) {
        Window { App() }
    }
}
```

### Результат
- Централизованное управление зависимостями
- Легко подменять реализации для тестов
- Чистый код без ручного создания объектов

---

## Фаза 4: Персистентное хранение (Приоритет: СРЕДНИЙ)

### Цель
Сохранять историю диалогов между перезапусками сервера.

### Варианты

| Вариант | Плюсы | Минусы |
|---------|-------|--------|
| **SQLite** | Простота, файловое хранение | Не масштабируется |
| **Redis** | Быстро, TTL для сессий | Нужен отдельный сервис |
| **PostgreSQL** | Надёжно, SQL | Overhead для MVP |

### Рекомендация: Redis

```kotlin
class RedisConversationRepository(
    private val redis: RedisClient
) : ConversationRepository {

    override fun getHistory(conversationId: String): List<LLMMessage> {
        val json = redis.get("conv:$conversationId:messages")
        return json?.let { Json.decodeFromString(it) } ?: emptyList()
    }

    override fun addMessage(conversationId: String, message: LLMMessage) {
        val history = getHistory(conversationId).toMutableList()
        history.add(message)
        redis.setex(
            "conv:$conversationId:messages",
            3600 * 24, // TTL 24 часа
            Json.encodeToString(history)
        )
    }
}
```

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

## Фаза 6: Расширяемость инструментов (Приоритет: СРЕДНИЙ)

### Цель
Сделать добавление новых инструментов максимально простым.

### Текущее состояние
- Инструменты реализуют `AgentTool`
- Регистрируются вручную в `ToolRegistry`

### Улучшения

#### 6.1 Аннотации для инструментов

```kotlin
@Target(AnnotationTarget.CLASS)
annotation class Tool(
    val name: String,
    val description: String
)

@Target(AnnotationTarget.VALUE_PARAMETER)
annotation class Param(
    val description: String,
    val required: Boolean = true
)

@Tool(
    name = "get_weather",
    description = "Получить погоду в городе"
)
class WeatherTool : AgentTool {
    override suspend fun execute(
        @Param("Название города") city: String
    ): String {
        // implementation
    }
}
```

#### 6.2 Автоматическая регистрация

```kotlin
object ToolRegistry {
    private val tools = mutableListOf<AgentTool>()

    init {
        // Автоматический поиск через ServiceLoader или Reflections
        ServiceLoader.load(AgentTool::class.java).forEach { register(it) }
    }
}
```

#### 6.3 Plugin система

```kotlin
interface ToolPlugin {
    val tools: List<AgentTool>
    fun initialize()
}

// Загрузка плагинов из JAR файлов
class PluginLoader {
    fun loadPlugins(directory: Path): List<ToolPlugin>
}
```

---

## Фаза 7: Тестирование (Приоритет: ВЫСОКИЙ)

### Структура тестов

```
backend/src/test/kotlin/org/example/
├── agent/
│   ├── AgentTest.kt
│   ├── PromptBuilderTest.kt
│   └── ToolExecutorTest.kt
├── data/
│   ├── ConversationRepositoryTest.kt
│   └── LLMClientTest.kt  # Mock-тесты
├── api/
│   └── RoutesTest.kt     # Integration tests
└── tools/
    └── HistoricalEventsToolTest.kt

desktop/src/test/kotlin/org/example/
├── ui/
│   └── ChatViewModelTest.kt
└── network/
    └── ChatApiClientTest.kt
```

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

| Фаза | Приоритет | Сложность | Влияние | Статус |
|------|-----------|-----------|---------|--------|
| 1. Shared модуль | ВЫСОКИЙ | Средняя | Убирает дублирование | ✅ ВЫПОЛНЕНО |
| 2. Декомпозиция Agent | ВЫСОКИЙ | Высокая | Улучшает поддержку | ⏳ СЛЕДУЮЩИЙ |
| 7. Тестирование | ВЫСОКИЙ | Средняя | Стабильность | |
| 3. Dependency Injection | СРЕДНИЙ | Низкая | Чистый код | |
| 6. Расширяемость tools | СРЕДНИЙ | Средняя | Гибкость | |
| 4. Персистентность | СРЕДНИЙ | Средняя | Production-ready | |
| 5. UseCase слой | НИЗКИЙ | Низкая | Для масштабирования | |

---

## Рекомендуемый порядок

### ✅ Итерация 1 (Фундамент) — ВЫПОЛНЕНО
1. ✅ Создать `shared` модуль
2. ✅ Мигрировать общие модели
3. ✅ Обновить импорты в desktop и backend

### ⏳ Итерация 2 (Рефакторинг backend) — СЛЕДУЮЩАЯ
1. Вынести `PromptBuilder`
2. Вынести `ConversationRepository`
3. Вынести `LLMClient`
4. Вынести `ToolExecutor`
5. Упростить `Agent.kt`

### Итерация 3 (Качество)
1. Добавить unit тесты
2. Внедрить Koin
3. Настроить CI с тестами

### Итерация 4 (Production)
1. Redis для conversations
2. Улучшить error handling
3. Добавить метрики

---

## Claude CLI команды для этого проекта

```bash
# Code review изменений
/review

# Проверка архитектуры
/architecture-check
```
