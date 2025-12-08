package org.example.agent

import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import kotlinx.coroutines.test.runTest
import org.example.data.ConversationRepository
import org.example.data.LLMClient
import org.example.model.Choice
import org.example.model.FunctionCall
import org.example.model.LLMMessage
import org.example.model.LLMResponse
import org.example.model.LLMToolCall
import org.example.model.Tool
import org.example.shared.model.CollectionMode
import org.example.shared.model.CollectionSettings
import org.example.shared.model.MessageRole
import org.example.shared.model.ResponseFormat
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

class AgentTest {

    private fun createMockLLMClient(): LLMClient = mockk {
        every { close() } returns Unit
    }

    private fun createMockRepository(): ConversationRepository = mockk(relaxed = true) {
        every { hasConversation(any()) } returns false
        every { getFormat(any()) } returns null
        every { getCollectionSettings(any()) } returns null
        every { getHistory(any()) } returns emptyList()
    }

    private fun createAgent(
        llmClient: LLMClient = createMockLLMClient(),
        repository: ConversationRepository = createMockRepository(),
        promptBuilder: PromptBuilder = PromptBuilder(),
        toolExecutor: ToolExecutor = ToolExecutor()
    ): Agent = Agent(llmClient, repository, promptBuilder, toolExecutor)

    // === Тесты базового потока ===

    @Test
    fun `chat returns response with message content`() = runTest {
        val llmClient = createMockLLMClient()
        val repository = createMockRepository()
        every { repository.getHistory(any()) } returns listOf(
            LLMMessage(role = "system", content = "System"),
            LLMMessage(role = "user", content = "Hello")
        )

        coEvery { llmClient.chat(any(), any(), any(), any()) } returns LLMResponse(
            id = "resp_1",
            choices = listOf(
                Choice(
                    message = LLMMessage(role = "assistant", content = "Hi there!"),
                    finish_reason = "stop"
                )
            )
        )

        val agent = createAgent(llmClient, repository)

        val response = agent.chat("Hello", "conv-1")

        assertEquals("Hi there!", response.message.content)
        assertEquals(MessageRole.ASSISTANT, response.message.role)
        assertEquals("conv-1", response.conversationId)
    }

    @Test
    fun `chat initializes new conversation`() = runTest {
        val llmClient = createMockLLMClient()
        val repository = createMockRepository()
        every { repository.hasConversation("new-conv") } returns false
        every { repository.getHistory(any()) } returns listOf(
            LLMMessage(role = "system", content = "System"),
            LLMMessage(role = "user", content = "Hello")
        )

        coEvery { llmClient.chat(any(), any(), any(), any()) } returns LLMResponse(
            choices = listOf(Choice(message = LLMMessage(role = "assistant", content = "Hi")))
        )

        val agent = createAgent(llmClient, repository)

        agent.chat("Hello", "new-conv")

        verify { repository.initConversation("new-conv", any()) }
    }

    @Test
    fun `chat updates system prompt when format changes`() = runTest {
        val llmClient = createMockLLMClient()
        val repository = createMockRepository()
        every { repository.hasConversation("conv-1") } returns true
        every { repository.getFormat("conv-1") } returns ResponseFormat.PLAIN
        every { repository.getHistory(any()) } returns listOf(
            LLMMessage(role = "system", content = "System"),
            LLMMessage(role = "user", content = "Hello")
        )

        coEvery { llmClient.chat(any(), any(), any(), any()) } returns LLMResponse(
            choices = listOf(Choice(message = LLMMessage(role = "assistant", content = "Hi")))
        )

        val agent = createAgent(llmClient, repository)

        agent.chat("Hello", "conv-1", ResponseFormat.MARKDOWN)

        verify { repository.updateSystemPrompt("conv-1", any()) }
    }

    @Test
    fun `chat adds user message to repository`() = runTest {
        val llmClient = createMockLLMClient()
        val repository = createMockRepository()
        val capturedMessages = mutableListOf<LLMMessage>()

        every { repository.addMessage(any(), capture(capturedMessages)) } returns Unit
        every { repository.getHistory(any()) } returns listOf(
            LLMMessage(role = "system", content = "System"),
            LLMMessage(role = "user", content = "Hello")
        )

        coEvery { llmClient.chat(any(), any(), any(), any()) } returns LLMResponse(
            choices = listOf(Choice(message = LLMMessage(role = "assistant", content = "Hi")))
        )

        val agent = createAgent(llmClient, repository)

        agent.chat("Hello world", "conv-1")

        // Первое сообщение — это user message
        assert(capturedMessages.isNotEmpty())
        val userMessage = capturedMessages.first { it.role == "user" }
        assertEquals("Hello world", userMessage.content)
    }

    @Test
    fun `chat adds assistant response to repository`() = runTest {
        val llmClient = createMockLLMClient()
        val repository = createMockRepository()
        val capturedMessages = mutableListOf<LLMMessage>()

        every { repository.addMessage(any(), capture(capturedMessages)) } returns Unit
        every { repository.getHistory(any()) } returns listOf(
            LLMMessage(role = "system", content = "System"),
            LLMMessage(role = "user", content = "Hello")
        )

        coEvery { llmClient.chat(any(), any(), any(), any()) } returns LLMResponse(
            choices = listOf(Choice(message = LLMMessage(role = "assistant", content = "Hi!")))
        )

        val agent = createAgent(llmClient, repository)

        agent.chat("Hello", "conv-1")

        assertEquals(2, capturedMessages.size) // user + assistant
        assertEquals("assistant", capturedMessages[1].role)
        assertEquals("Hi!", capturedMessages[1].content)
    }

    // === Тесты tool calling ===

    @Test
    fun `chat handles tool call and returns final response`() = runTest {
        val llmClient = createMockLLMClient()
        val repository = createMockRepository()
        var callCount = 0

        every { repository.getHistory(any()) } returns listOf(
            LLMMessage(role = "system", content = "System"),
            LLMMessage(role = "user", content = "What happened in 1945?")
        )

        coEvery { llmClient.chat(any(), any<List<Tool>>(), any(), any()) } answers {
            callCount++
            if (callCount == 1) {
                // Первый вызов - возвращаем tool_call
                LLMResponse(
                    choices = listOf(
                        Choice(
                            message = LLMMessage(
                                role = "assistant",
                                content = null,
                                tool_calls = listOf(
                                    LLMToolCall(
                                        id = "call_1",
                                        type = "function",
                                        function = FunctionCall(
                                            name = "get_historical_events",
                                            arguments = """{"year": 1945}"""
                                        )
                                    )
                                )
                            )
                        )
                    )
                )
            } else {
                // Второй вызов - возвращаем финальный ответ
                LLMResponse(
                    choices = listOf(
                        Choice(
                            message = LLMMessage(
                                role = "assistant",
                                content = "В 1945 году закончилась Вторая мировая война."
                            )
                        )
                    )
                )
            }
        }

        val agent = createAgent(llmClient, repository)

        val response = agent.chat("What happened in 1945?", "conv-1")

        assertEquals("В 1945 году закончилась Вторая мировая война.", response.message.content)
        assertNotNull(response.message.toolCall)
        assertEquals("get_historical_events", response.message.toolCall?.name)
        coVerify(exactly = 2) { llmClient.chat(any(), any(), any(), any()) }
    }

    @Test
    fun `chat stops after max tool iterations`() = runTest {
        val llmClient = createMockLLMClient()
        val repository = createMockRepository()

        every { repository.getHistory(any()) } returns emptyList()

        // Всегда возвращаем tool_call
        coEvery { llmClient.chat(any(), any<List<Tool>>(), any(), any()) } returns LLMResponse(
            choices = listOf(
                Choice(
                    message = LLMMessage(
                        role = "assistant",
                        content = null,
                        tool_calls = listOf(
                            LLMToolCall(
                                id = "call_1",
                                type = "function",
                                function = FunctionCall(
                                    name = "get_historical_events",
                                    arguments = """{"year": 1945}"""
                                )
                            )
                        )
                    )
                )
            )
        )

        val agent = Agent(llmClient, repository, PromptBuilder(), ToolExecutor(), maxToolIterations = 3)

        agent.chat("Test", "conv-1")

        // 1 начальный вызов + 3 итерации tool_calls = 4 вызова
        coVerify(exactly = 4) { llmClient.chat(any(), any(), any(), any()) }
    }

    // === Тесты настроек ===

    @Test
    fun `chat passes temperature to LLM client`() = runTest {
        val llmClient = createMockLLMClient()
        val repository = createMockRepository()
        var capturedTemperature: Float? = null

        every { repository.getHistory(any()) } returns emptyList()

        coEvery { llmClient.chat(any(), any(), any(), any()) } answers {
            capturedTemperature = thirdArg()
            LLMResponse(
                choices = listOf(Choice(message = LLMMessage(role = "assistant", content = "Hi")))
            )
        }

        val agent = createAgent(llmClient, repository)

        agent.chat("Hello", "conv-1", temperature = 0.7f)

        assertEquals(0.7f, capturedTemperature)
    }

    @Test
    fun `chat saves collection settings to repository`() = runTest {
        val llmClient = createMockLLMClient()
        val repository = createMockRepository()
        val settingsSlot = slot<CollectionSettings>()

        every { repository.setCollectionSettings(any(), capture(settingsSlot)) } returns Unit
        every { repository.getHistory(any()) } returns emptyList()

        coEvery { llmClient.chat(any(), any(), any(), any()) } returns LLMResponse(
            choices = listOf(Choice(message = LLMMessage(role = "assistant", content = "Hi")))
        )

        val agent = createAgent(llmClient, repository)
        val settings = CollectionSettings(
            mode = CollectionMode.SOLVE_STEP_BY_STEP,
            enabled = true
        )

        agent.chat("Hello", "conv-1", collectionSettings = settings)

        assertEquals(CollectionMode.SOLVE_STEP_BY_STEP, settingsSlot.captured.mode)
    }

    @Test
    fun `chat response includes unique message id`() = runTest {
        val llmClient = createMockLLMClient()
        val repository = createMockRepository()

        every { repository.getHistory(any()) } returns emptyList()

        coEvery { llmClient.chat(any(), any(), any(), any()) } returns LLMResponse(
            choices = listOf(Choice(message = LLMMessage(role = "assistant", content = "Hi")))
        )

        val agent = createAgent(llmClient, repository)

        val response1 = agent.chat("Hello", "conv-1")
        val response2 = agent.chat("World", "conv-2")

        assertNotNull(response1.message.id)
        assertNotNull(response2.message.id)
        assert(response1.message.id != response2.message.id)
    }

    @Test
    fun `chat response has no toolCall when LLM returns direct answer`() = runTest {
        val llmClient = createMockLLMClient()
        val repository = createMockRepository()

        every { repository.getHistory(any()) } returns emptyList()

        coEvery { llmClient.chat(any(), any(), any(), any()) } returns LLMResponse(
            choices = listOf(
                Choice(
                    message = LLMMessage(
                        role = "assistant",
                        content = "Just a simple answer"
                    )
                )
            )
        )

        val agent = createAgent(llmClient, repository)

        val response = agent.chat("Hello", "conv-1")

        assertNull(response.message.toolCall)
    }

    // === Тесты закрытия ===

    @Test
    fun `close delegates to LLM client`() {
        val llmClient = createMockLLMClient()
        val agent = createAgent(llmClient)

        agent.close()

        verify { llmClient.close() }
    }
}
