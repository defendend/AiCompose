---
name: testing-rules
description: Правила написания тестов для проекта AiCompose — naming, structure, mocking
---

# Testing Rules

Правила написания тестов для проекта AiCompose.

## Naming Convention

Формат: `should_expectedBehavior_when_condition`

```kotlin
// ✅ Хорошо
@Test
fun `should return error when message is empty`()

@Test
fun `should save message to repository when chat is called`()

// ❌ Плохо
@Test
fun testEmptyMessage()

@Test
fun test1()
```

## Структура теста (AAA)

```kotlin
@Test
fun `should return greeting when user sends hello`() {
    // Arrange
    val agent = Agent(mockLLMClient, mockRepository)
    val request = ChatRequest(message = "Привет")

    // Act
    val response = agent.chat(request)

    // Assert
    assertThat(response.message.content).contains("Привет")
}
```

## Мокирование (MockK)

```kotlin
// Создание мока
val mockClient = mockk<LLMClient>()

// Настройка поведения
every { mockClient.chat(any()) } returns LLMResponse(content = "Hello")

// Для suspend функций
coEvery { mockClient.chatAsync(any()) } returns LLMResponse(content = "Hello")

// Проверка вызовов
verify { mockClient.chat(any()) }
coVerify { mockClient.chatAsync(any()) }
```

## Тестирование корутин

```kotlin
class AgentTest {
    @Test
    fun `should handle async operations`() = runTest {
        val agent = Agent(mockClient, mockRepo)

        val response = agent.chat(request)

        assertThat(response).isNotNull()
    }
}
```

## Интеграционные тесты (Ktor)

```kotlin
class RoutesTest {
    @Test
    fun `should return 200 for health check`() = testApplication {
        application {
            configureRouting()
        }

        client.get("/api/health").apply {
            assertEquals(HttpStatusCode.OK, status)
        }
    }
}
```

## Тестирование DI (Koin)

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

## Что тестировать

### Обязательно
- Бизнес-логика (Agent, ToolExecutor)
- API endpoints (Routes)
- Репозитории (ConversationRepository)
- Сериализация/десериализация

### Опционально
- UI компоненты (snapshot тесты)
- Интеграции с внешними API (с моками)

### Не тестировать
- Тривиальные геттеры/сеттеры
- Сгенерированный код
- Сторонние библиотеки

## Изоляция тестов

- Каждый тест независим
- Не использовать shared state
- Очищать ресурсы после теста

```kotlin
@BeforeEach
fun setUp() {
    repository = InMemoryConversationRepository()
}

@AfterEach
fun tearDown() {
    repository.clear()
}
```

## Покрытие

- Минимум 80% для бизнес-логики
- Не гнаться за 100% ради цифры
- Фокус на критических путях

---

## Связанные документы

- Документация тестов — см. docs/testing.md
- Правила стиля — см. rules/code-style.md
