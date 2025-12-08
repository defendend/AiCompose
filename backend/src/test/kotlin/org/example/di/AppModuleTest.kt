package org.example.di

import org.example.agent.Agent
import org.example.agent.PromptBuilder
import org.example.agent.ToolExecutor
import org.example.data.ConversationRepository
import org.example.data.DeepSeekClient
import org.example.data.InMemoryConversationRepository
import org.example.data.LLMClient
import org.koin.core.context.startKoin
import org.koin.core.context.stopKoin
import org.koin.test.KoinTest
import org.koin.test.get
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertSame

class AppModuleTest : KoinTest {

    @BeforeTest
    fun setup() {
        startKoin {
            modules(appModule("test-api-key"))
        }
    }

    @AfterTest
    fun tearDown() {
        stopKoin()
    }

    @Test
    fun `module provides LLMClient as DeepSeekClient`() {
        val client: LLMClient = get()

        assertNotNull(client)
        assertIs<DeepSeekClient>(client)
    }

    @Test
    fun `module provides ConversationRepository as InMemoryConversationRepository`() {
        val repo: ConversationRepository = get()

        assertNotNull(repo)
        assertIs<InMemoryConversationRepository>(repo)
    }

    @Test
    fun `module provides PromptBuilder`() {
        val builder: PromptBuilder = get()

        assertNotNull(builder)
    }

    @Test
    fun `module provides ToolExecutor`() {
        val executor: ToolExecutor = get()

        assertNotNull(executor)
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

    @Test
    fun `ConversationRepository is singleton`() {
        val repo1: ConversationRepository = get()
        val repo2: ConversationRepository = get()

        assertSame(repo1, repo2)
    }

    @Test
    fun `Agent is singleton`() {
        val agent1: Agent = get()
        val agent2: Agent = get()

        assertSame(agent1, agent2)
    }
}
