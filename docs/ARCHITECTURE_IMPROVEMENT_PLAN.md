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
| 6. Расширяемость tools  | СРЕДНИЙ   | Средняя   | Гибкость             | ⏳ СЛЕДУЮЩИЙ |
| 4. Персистентность      | СРЕДНИЙ   | Средняя   | Production-ready     |              |
| 5. UseCase слой         | НИЗКИЙ    | Низкая    | Для масштабирования  |              |

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

### ⏳ Итерация 5 (Расширяемость) — СЛЕДУЮЩАЯ
1. Расширяемость tools (аннотации, автоматическая регистрация)

### Итерация 6 (Production)
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
