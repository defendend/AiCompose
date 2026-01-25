---
name: koin-di-rules
description: Правила Koin DI для backend AiCompose — модули, singleOf vs single
---

# Koin DI Rules

Правила Dependency Injection (Koin 4.0.0) для backend.

## Основные правила

### singleOf vs single

**Важно:** `singleOf()` пытается автоматически инжектировать ВСЕ параметры конструктора, даже если у них есть дефолтные значения.

```kotlin
// ❌ Не работает — Koin ищет String для baseUrl
singleOf(::ChatApiClient)

// ✅ Работает — явно вызываем конструктор с дефолтами
single { ChatApiClient() }
```

### Когда использовать что

| Функция | Когда использовать |
|---------|-------------------|
| `single { }` | Классы с дефолтными параметрами |
| `singleOf(::Class)` | Классы БЕЗ дефолтных параметров |
| `factory { }` | Stateless объекты, создаваемые каждый раз |

## Структура модуля

```kotlin
val appModule = module {
    // Repositories
    single<ConversationRepository> {
        when (RepositoryConfig.storageType) {
            "redis" -> RedisConversationRepository(...)
            "postgres" -> PostgresConversationRepository(...)
            else -> InMemoryConversationRepository()
        }
    }

    // Services
    single { LLMClient() }
    single { PromptBuilder() }
    single { ToolExecutor(get()) }

    // Agent
    single {
        Agent(
            llmClient = get(),
            repository = get(),
            promptBuilder = get(),
            toolExecutor = get()
        )
    }
}
```

## Инициализация

```kotlin
fun Application.configureDI() {
    install(Koin) {
        modules(appModule)
    }
}
```

## Получение зависимостей

### В Ktor routes

```kotlin
fun Route.chatRoutes() {
    val agent by inject<Agent>()

    post("/chat") {
        val response = agent.chat(request)
        call.respond(response)
    }
}
```

### В классах

```kotlin
class MyService : KoinComponent {
    private val agent: Agent by inject()
}
```

## Тестирование

```kotlin
class AppModuleTest : KoinTest {
    @Test
    fun `should create all dependencies`() {
        startKoin {
            modules(appModule)
        }

        val agent = get<Agent>()
        assertThat(agent).isNotNull()

        stopKoin()
    }
}
```

## Конфигурация хранилища

```kotlin
object RepositoryConfig {
    val storageType: String = System.getenv("STORAGE_TYPE") ?: "memory"
    val redisUrl: String = System.getenv("REDIS_URL") ?: "redis://localhost:6379"
    val dbUrl: String? = System.getenv("DB_URL")
}
```

---

## Связанные документы

- Архитектура — см. rules/architecture.md
- Деплой — см. docs/deployment.md
