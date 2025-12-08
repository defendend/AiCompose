package org.example.data

import org.example.model.LLMMessage
import org.example.shared.model.CollectionMode
import org.example.shared.model.CollectionSettings
import org.example.shared.model.ResponseFormat
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ConversationRepositoryTest {
    private fun createRepository(): InMemoryConversationRepository = InMemoryConversationRepository()

    // === Тесты истории диалогов ===

    @Test
    fun `getHistory returns empty list for new conversation`() {
        val repo = createRepository()

        val history = repo.getHistory("new-conversation")

        assertTrue(history.isEmpty())
    }

    @Test
    fun `hasConversation returns false for new conversation`() {
        val repo = createRepository()

        assertFalse(repo.hasConversation("new-conversation"))
    }

    @Test
    fun `initConversation creates new conversation with system message`() {
        val repo = createRepository()
        val systemMsg = LLMMessage(role = "system", content = "System prompt")

        repo.initConversation("conv-1", systemMsg)

        assertTrue(repo.hasConversation("conv-1"))
        val history = repo.getHistory("conv-1")
        assertEquals(1, history.size)
        assertEquals("system", history[0].role)
        assertEquals("System prompt", history[0].content)
    }

    @Test
    fun `initConversation does not overwrite existing conversation`() {
        val repo = createRepository()
        val systemMsg1 = LLMMessage(role = "system", content = "First prompt")
        val systemMsg2 = LLMMessage(role = "system", content = "Second prompt")

        repo.initConversation("conv-1", systemMsg1)
        repo.initConversation("conv-1", systemMsg2)

        val history = repo.getHistory("conv-1")
        assertEquals(1, history.size)
        assertEquals("First prompt", history[0].content)
    }

    @Test
    fun `addMessage adds message to existing conversation`() {
        val repo = createRepository()
        repo.initConversation("conv-1", LLMMessage(role = "system", content = "System"))

        repo.addMessage("conv-1", LLMMessage(role = "user", content = "Hello"))

        val history = repo.getHistory("conv-1")
        assertEquals(2, history.size)
        assertEquals("user", history[1].role)
        assertEquals("Hello", history[1].content)
    }

    @Test
    fun `addMessage creates conversation if not exists`() {
        val repo = createRepository()

        repo.addMessage("conv-1", LLMMessage(role = "user", content = "Hello"))

        val history = repo.getHistory("conv-1")
        assertEquals(1, history.size)
    }

    @Test
    fun `addMessages adds multiple messages at once`() {
        val repo = createRepository()
        repo.initConversation("conv-1", LLMMessage(role = "system", content = "System"))

        repo.addMessages(
            "conv-1",
            listOf(
                LLMMessage(role = "user", content = "Hello"),
                LLMMessage(role = "assistant", content = "Hi!")
            )
        )

        val history = repo.getHistory("conv-1")
        assertEquals(3, history.size)
        assertEquals("user", history[1].role)
        assertEquals("assistant", history[2].role)
    }

    @Test
    fun `updateSystemPrompt updates first system message`() {
        val repo = createRepository()
        repo.initConversation("conv-1", LLMMessage(role = "system", content = "Old prompt"))
        repo.addMessage("conv-1", LLMMessage(role = "user", content = "Hello"))

        repo.updateSystemPrompt("conv-1", "New prompt")

        val history = repo.getHistory("conv-1")
        assertEquals("New prompt", history[0].content)
        assertEquals(2, history.size)
    }

    @Test
    fun `updateSystemPrompt does nothing for non-existent conversation`() {
        val repo = createRepository()

        repo.updateSystemPrompt("non-existent", "New prompt")

        assertFalse(repo.hasConversation("non-existent"))
    }

    @Test
    fun `updateSystemPrompt does nothing if first message is not system`() {
        val repo = createRepository()
        repo.addMessage("conv-1", LLMMessage(role = "user", content = "Hello"))

        repo.updateSystemPrompt("conv-1", "New prompt")

        val history = repo.getHistory("conv-1")
        assertEquals("Hello", history[0].content)
    }

    @Test
    fun `getHistory returns copy of list not original`() {
        val repo = createRepository()
        repo.initConversation("conv-1", LLMMessage(role = "system", content = "System"))

        val history1 = repo.getHistory("conv-1")
        repo.addMessage("conv-1", LLMMessage(role = "user", content = "Hello"))
        val history2 = repo.getHistory("conv-1")

        assertEquals(1, history1.size)
        assertEquals(2, history2.size)
    }

    // === Тесты форматов ===

    @Test
    fun `getFormat returns null for new conversation`() {
        val repo = createRepository()

        assertNull(repo.getFormat("conv-1"))
    }

    @Test
    fun `setFormat and getFormat work correctly`() {
        val repo = createRepository()

        repo.setFormat("conv-1", ResponseFormat.MARKDOWN)

        assertEquals(ResponseFormat.MARKDOWN, repo.getFormat("conv-1"))
    }

    @Test
    fun `setFormat overwrites previous format`() {
        val repo = createRepository()
        repo.setFormat("conv-1", ResponseFormat.PLAIN)

        repo.setFormat("conv-1", ResponseFormat.JSON)

        assertEquals(ResponseFormat.JSON, repo.getFormat("conv-1"))
    }

    // === Тесты настроек сбора ===

    @Test
    fun `getCollectionSettings returns null for new conversation`() {
        val repo = createRepository()

        assertNull(repo.getCollectionSettings("conv-1"))
    }

    @Test
    fun `setCollectionSettings and getCollectionSettings work correctly`() {
        val repo = createRepository()
        val settings = CollectionSettings(
            mode = CollectionMode.TECHNICAL_SPEC,
            enabled = true,
            resultTitle = "ТЗ"
        )

        repo.setCollectionSettings("conv-1", settings)

        val retrieved = repo.getCollectionSettings("conv-1")
        assertEquals(CollectionMode.TECHNICAL_SPEC, retrieved?.mode)
        assertTrue(retrieved?.enabled == true)
        assertEquals("ТЗ", retrieved?.resultTitle)
    }

    // === Тесты изоляции диалогов ===

    @Test
    fun `different conversations are isolated`() {
        val repo = createRepository()
        repo.initConversation("conv-1", LLMMessage(role = "system", content = "System 1"))
        repo.initConversation("conv-2", LLMMessage(role = "system", content = "System 2"))
        repo.addMessage("conv-1", LLMMessage(role = "user", content = "Hello 1"))
        repo.setFormat("conv-1", ResponseFormat.JSON)
        repo.setFormat("conv-2", ResponseFormat.MARKDOWN)

        assertEquals(2, repo.getHistory("conv-1").size)
        assertEquals(1, repo.getHistory("conv-2").size)
        assertEquals(ResponseFormat.JSON, repo.getFormat("conv-1"))
        assertEquals(ResponseFormat.MARKDOWN, repo.getFormat("conv-2"))
    }

    // === Тесты tool_call_id ===

    @Test
    fun `message with tool_call_id is stored correctly`() {
        val repo = createRepository()

        repo.addMessage(
            "conv-1",
            LLMMessage(
                role = "tool",
                content = "Result",
                tool_call_id = "call_123"
            )
        )

        val history = repo.getHistory("conv-1")
        assertEquals("call_123", history[0].tool_call_id)
    }
}
