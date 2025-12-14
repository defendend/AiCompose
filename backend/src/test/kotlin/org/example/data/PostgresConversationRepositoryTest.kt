package org.example.data

import org.example.data.schema.ConversationMetadata
import org.example.data.schema.Conversations
import org.example.data.schema.Messages
import org.example.model.LLMMessage
import org.example.model.FunctionCall
import org.example.model.LLMToolCall
import org.example.shared.model.CollectionMode
import org.example.shared.model.CollectionSettings
import org.example.shared.model.CompressionSettings
import org.example.shared.model.ResponseFormat
import org.jetbrains.exposed.sql.Database
import org.jetbrains.exposed.sql.SchemaUtils
import org.jetbrains.exposed.sql.transactions.transaction
import java.util.UUID
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Тесты для PostgresConversationRepository с использованием H2 in-memory database.
 * H2 используется вместо PostgreSQL для быстрого запуска тестов без внешних зависимостей.
 */
class PostgresConversationRepositoryTest {

    private lateinit var database: Database
    private lateinit var repo: PostgresConversationRepository

    @BeforeTest
    fun setup() {
        // Используем H2 в режиме совместимости с PostgreSQL
        database = Database.connect(
            url = "jdbc:h2:mem:test_${System.nanoTime()};DB_CLOSE_DELAY=-1;MODE=PostgreSQL",
            driver = "org.h2.Driver"
        )

        // Создаём таблицы
        transaction(database) {
            SchemaUtils.create(Conversations, Messages, ConversationMetadata)
        }

        repo = PostgresConversationRepository(database)
    }

    @AfterTest
    fun teardown() {
        transaction(database) {
            SchemaUtils.drop(Messages, ConversationMetadata, Conversations)
        }
    }

    // === Тесты истории диалогов ===

    @Test
    fun `getHistory returns empty list for new conversation`() {
        val convId = UUID.randomUUID().toString()

        val history = repo.getHistory(convId)

        assertTrue(history.isEmpty())
    }

    @Test
    fun `hasConversation returns false for new conversation`() {
        val convId = UUID.randomUUID().toString()

        assertFalse(repo.hasConversation(convId))
    }

    @Test
    fun `initConversation creates new conversation with system message`() {
        val convId = UUID.randomUUID().toString()
        val systemMsg = LLMMessage(role = "system", content = "System prompt")

        repo.initConversation(convId, systemMsg)

        assertTrue(repo.hasConversation(convId))
        val history = repo.getHistory(convId)
        assertEquals(1, history.size)
        assertEquals("system", history[0].role)
        assertEquals("System prompt", history[0].content)
    }

    @Test
    fun `initConversation does not overwrite existing conversation`() {
        val convId = UUID.randomUUID().toString()
        val systemMsg1 = LLMMessage(role = "system", content = "First prompt")
        val systemMsg2 = LLMMessage(role = "system", content = "Second prompt")

        repo.initConversation(convId, systemMsg1)
        repo.initConversation(convId, systemMsg2)

        val history = repo.getHistory(convId)
        assertEquals(1, history.size)
        assertEquals("First prompt", history[0].content)
    }

    @Test
    fun `addMessage adds message to existing conversation`() {
        val convId = UUID.randomUUID().toString()
        repo.initConversation(convId, LLMMessage(role = "system", content = "System"))

        repo.addMessage(convId, LLMMessage(role = "user", content = "Hello"))

        val history = repo.getHistory(convId)
        assertEquals(2, history.size)
        assertEquals("user", history[1].role)
        assertEquals("Hello", history[1].content)
    }

    @Test
    fun `addMessages adds multiple messages at once`() {
        val convId = UUID.randomUUID().toString()
        repo.initConversation(convId, LLMMessage(role = "system", content = "System"))

        repo.addMessages(
            convId,
            listOf(
                LLMMessage(role = "user", content = "Hello"),
                LLMMessage(role = "assistant", content = "Hi!")
            )
        )

        val history = repo.getHistory(convId)
        assertEquals(3, history.size)
        assertEquals("user", history[1].role)
        assertEquals("assistant", history[2].role)
    }

    @Test
    fun `updateSystemPrompt updates first system message`() {
        val convId = UUID.randomUUID().toString()
        repo.initConversation(convId, LLMMessage(role = "system", content = "Old prompt"))
        repo.addMessage(convId, LLMMessage(role = "user", content = "Hello"))

        repo.updateSystemPrompt(convId, "New prompt")

        val history = repo.getHistory(convId)
        assertEquals("New prompt", history[0].content)
        assertEquals(2, history.size)
    }

    // === Тесты форматов ===

    @Test
    fun `getFormat returns null for new conversation`() {
        val convId = UUID.randomUUID().toString()

        assertNull(repo.getFormat(convId))
    }

    @Test
    fun `setFormat and getFormat work correctly`() {
        val convId = UUID.randomUUID().toString()
        repo.initConversation(convId, LLMMessage(role = "system", content = "System"))

        repo.setFormat(convId, ResponseFormat.MARKDOWN)

        assertEquals(ResponseFormat.MARKDOWN, repo.getFormat(convId))
    }

    // === Тесты настроек сбора ===

    @Test
    fun `getCollectionSettings returns null for new conversation`() {
        val convId = UUID.randomUUID().toString()

        assertNull(repo.getCollectionSettings(convId))
    }

    @Test
    fun `setCollectionSettings and getCollectionSettings work correctly`() {
        val convId = UUID.randomUUID().toString()
        repo.initConversation(convId, LLMMessage(role = "system", content = "System"))
        val settings = CollectionSettings(
            mode = CollectionMode.TECHNICAL_SPEC,
            enabled = true,
            resultTitle = "ТЗ"
        )

        repo.setCollectionSettings(convId, settings)

        val retrieved = repo.getCollectionSettings(convId)
        assertNotNull(retrieved)
        assertEquals(CollectionMode.TECHNICAL_SPEC, retrieved.mode)
        assertTrue(retrieved.enabled)
        assertEquals("ТЗ", retrieved.resultTitle)
    }

    // === Тесты настроек сжатия ===

    @Test
    fun `getCompressionSettings returns null for new conversation`() {
        val convId = UUID.randomUUID().toString()

        assertNull(repo.getCompressionSettings(convId))
    }

    @Test
    fun `setCompressionSettings and getCompressionSettings work correctly`() {
        val convId = UUID.randomUUID().toString()
        repo.initConversation(convId, LLMMessage(role = "system", content = "System"))
        val settings = CompressionSettings(
            enabled = true,
            messageThreshold = 10,
            keepRecentMessages = 4
        )

        repo.setCompressionSettings(convId, settings)

        val retrieved = repo.getCompressionSettings(convId)
        assertNotNull(retrieved)
        assertTrue(retrieved.enabled)
        assertEquals(10, retrieved.messageThreshold)
        assertEquals(4, retrieved.keepRecentMessages)
    }

    // === Тесты replaceHistory ===

    @Test
    fun `replaceHistory replaces all messages`() {
        val convId = UUID.randomUUID().toString()
        repo.initConversation(convId, LLMMessage(role = "system", content = "Old system"))
        repo.addMessage(convId, LLMMessage(role = "user", content = "Old message"))

        repo.replaceHistory(
            convId, listOf(
                LLMMessage(role = "system", content = "New system"),
                LLMMessage(role = "assistant", content = "Summary of conversation")
            )
        )

        val history = repo.getHistory(convId)
        assertEquals(2, history.size)
        assertEquals("New system", history[0].content)
        assertEquals("Summary of conversation", history[1].content)
    }

    @Test
    fun `getMessageCount returns correct count`() {
        val convId = UUID.randomUUID().toString()
        repo.initConversation(convId, LLMMessage(role = "system", content = "System"))
        repo.addMessages(
            convId, listOf(
                LLMMessage(role = "user", content = "Hello"),
                LLMMessage(role = "assistant", content = "Hi!")
            )
        )

        assertEquals(3, repo.getMessageCount(convId))
    }

    // === Тесты tool_calls ===

    @Test
    fun `message with tool_calls is stored and retrieved correctly`() {
        val convId = UUID.randomUUID().toString()
        val toolCalls = listOf(
            LLMToolCall(
                id = "call_123",
                type = "function",
                function = FunctionCall(
                    name = "get_weather",
                    arguments = """{"city": "Moscow"}"""
                )
            )
        )
        val message = LLMMessage(
            role = "assistant",
            content = null,
            tool_calls = toolCalls
        )

        repo.initConversation(convId, LLMMessage(role = "system", content = "System"))
        repo.addMessage(convId, message)

        val history = repo.getHistory(convId)
        assertEquals(2, history.size)
        val retrieved = history[1]
        assertNotNull(retrieved.tool_calls)
        assertEquals(1, retrieved.tool_calls!!.size)
        assertEquals("call_123", retrieved.tool_calls!![0].id)
        assertEquals("get_weather", retrieved.tool_calls!![0].function.name)
    }

    @Test
    fun `message with tool_call_id is stored correctly`() {
        val convId = UUID.randomUUID().toString()
        repo.initConversation(convId, LLMMessage(role = "system", content = "System"))

        repo.addMessage(
            convId,
            LLMMessage(
                role = "tool",
                content = "Result",
                tool_call_id = "call_123"
            )
        )

        val history = repo.getHistory(convId)
        assertEquals("call_123", history[1].tool_call_id)
    }

    // === Тесты управления чатами ===

    @Test
    fun `createConversation creates new conversation and returns id`() {
        val id = repo.createConversation("Test Chat")

        assertNotNull(id)
        assertTrue(UUID.fromString(id) != null) // Valid UUID
    }

    @Test
    fun `listConversations returns all conversations`() {
        repo.createConversation("Chat 1")
        repo.createConversation("Chat 2")

        val list = repo.listConversations()

        assertEquals(2, list.size)
    }

    @Test
    fun `renameConversation updates title`() {
        val id = repo.createConversation("Old Title")

        repo.renameConversation(id, "New Title")

        val info = repo.getConversationInfo(id)
        assertNotNull(info)
        assertEquals("New Title", info.title)
    }

    @Test
    fun `deleteConversation removes conversation and messages`() {
        val id = repo.createConversation("To Delete")
        repo.initConversation(id, LLMMessage(role = "system", content = "System"))
        repo.addMessage(id, LLMMessage(role = "user", content = "Hello"))

        repo.deleteConversation(id)

        assertFalse(repo.hasConversation(id))
        assertTrue(repo.getHistory(id).isEmpty())
    }

    @Test
    fun `searchMessages finds messages containing query`() {
        val id = repo.createConversation("Test")
        repo.initConversation(id, LLMMessage(role = "system", content = "System prompt"))
        repo.addMessage(id, LLMMessage(role = "user", content = "Tell me about Kotlin"))
        repo.addMessage(id, LLMMessage(role = "assistant", content = "Kotlin is a programming language"))

        val results = repo.searchMessages("Kotlin")

        assertEquals(2, results.size) // Found in user and assistant messages
    }

    // === Тесты изоляции диалогов ===

    @Test
    fun `different conversations are isolated`() {
        val convId1 = UUID.randomUUID().toString()
        val convId2 = UUID.randomUUID().toString()

        repo.initConversation(convId1, LLMMessage(role = "system", content = "System 1"))
        repo.initConversation(convId2, LLMMessage(role = "system", content = "System 2"))
        repo.addMessage(convId1, LLMMessage(role = "user", content = "Hello 1"))
        repo.setFormat(convId1, ResponseFormat.JSON)
        repo.setFormat(convId2, ResponseFormat.MARKDOWN)

        assertEquals(2, repo.getHistory(convId1).size)
        assertEquals(1, repo.getHistory(convId2).size)
        assertEquals(ResponseFormat.JSON, repo.getFormat(convId1))
        assertEquals(ResponseFormat.MARKDOWN, repo.getFormat(convId2))
    }

    // === Тест персистентности ===

    @Test
    fun `data persists in database between repository instances`() {
        val convId = UUID.randomUUID().toString()
        val systemMsg = LLMMessage(role = "system", content = "Persistent system")

        // Первый репозиторий создаёт данные
        repo.initConversation(convId, systemMsg)
        repo.addMessage(convId, LLMMessage(role = "user", content = "Hello"))

        // Создаём новый репозиторий с той же БД
        val repo2 = PostgresConversationRepository(database)

        // Данные должны сохраниться
        assertTrue(repo2.hasConversation(convId))
        val history = repo2.getHistory(convId)
        assertEquals(2, history.size)
        assertEquals("Persistent system", history[0].content)
        assertEquals("Hello", history[1].content)
    }
}
