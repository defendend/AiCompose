package org.example.api

import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.*
import io.ktor.server.application.*
import io.ktor.server.plugins.contentnegotiation.*
import io.ktor.server.routing.*
import io.ktor.server.testing.*
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.serialization.json.Json
import org.example.agent.Agent
import org.example.data.ConversationRepository
import org.example.data.InMemoryConversationRepository
import org.example.data.LLMClient
import org.example.model.LLMMessage
import org.example.model.LLMResponse
import org.example.model.LLMStreamChunk
import org.example.model.Tool
import org.example.shared.model.ChatResponse
import org.example.shared.model.MessageRole
import org.example.shared.model.StreamEvent
import org.example.shared.model.StreamEventType
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals

class RoutesTest {

    private val json = Json {
        prettyPrint = true
        isLenient = true
        ignoreUnknownKeys = true
    }

    private fun createMockAgent(): Agent = mockk {
        every { close() } returns Unit
    }

    private fun createMockLLMClient(healthy: Boolean = true): LLMClient = mockk {
        coEvery { healthCheck() } returns healthy
        every { close() } returns Unit
    }

    private fun Application.configureTestApp(
        agent: Agent,
        llmClient: LLMClient? = null,
        conversationRepository: ConversationRepository? = null
    ) {
        install(ContentNegotiation) { json(json) }
        routing { chatRoutes(agent, llmClient, conversationRepository) }
    }

    // === Тесты /api/health ===

    @Test
    fun `health endpoint returns ok status`() = testApplication {
        application { configureTestApp(createMockAgent()) }

        val response = client.get("/api/health")

        assertEquals(HttpStatusCode.OK, response.status)
        assertContains(response.bodyAsText(), "ok")
    }

    // === Тесты /api/chat ===

    @Test
    fun `chat endpoint returns response with message`() = testApplication {
        val mockAgent = createMockAgent()
        coEvery {
            mockAgent.chat(any(), any(), any(), any(), any())
        } returns ChatResponse(
            message = org.example.shared.model.ChatMessage(
                id = "msg-1",
                role = MessageRole.ASSISTANT,
                content = "Hello from agent!",
                timestamp = System.currentTimeMillis()
            ),
            conversationId = "conv-123"
        )

        application { configureTestApp(mockAgent) }

        val response = client.post("/api/chat") {
            contentType(ContentType.Application.Json)
            setBody("""{"message": "Hello"}""")
        }

        assertEquals(HttpStatusCode.OK, response.status)
        val body = response.bodyAsText()
        assertContains(body, "Hello from agent!")
        assertContains(body, "conv-123")
    }

    @Test
    fun `chat endpoint uses provided conversationId`() = testApplication {
        val mockAgent = createMockAgent()
        coEvery {
            mockAgent.chat("Test", "my-conv-id", any(), any(), any())
        } returns ChatResponse(
            message = org.example.shared.model.ChatMessage(
                id = "msg-1",
                role = MessageRole.ASSISTANT,
                content = "Response",
                timestamp = System.currentTimeMillis()
            ),
            conversationId = "my-conv-id"
        )

        application { configureTestApp(mockAgent) }

        val response = client.post("/api/chat") {
            contentType(ContentType.Application.Json)
            setBody("""{"message": "Test", "conversationId": "my-conv-id"}""")
        }

        assertEquals(HttpStatusCode.OK, response.status)
        assertContains(response.bodyAsText(), "my-conv-id")
    }

    @Test
    fun `chat endpoint passes responseFormat to agent`() = testApplication {
        val mockAgent = createMockAgent()
        coEvery {
            mockAgent.chat(any(), any(), org.example.shared.model.ResponseFormat.MARKDOWN, any(), any())
        } returns ChatResponse(
            message = org.example.shared.model.ChatMessage(
                id = "msg-1",
                role = MessageRole.ASSISTANT,
                content = "# Markdown response",
                timestamp = System.currentTimeMillis()
            ),
            conversationId = "conv-1"
        )

        application { configureTestApp(mockAgent) }

        val response = client.post("/api/chat") {
            contentType(ContentType.Application.Json)
            setBody("""{"message": "Test", "responseFormat": "MARKDOWN"}""")
        }

        assertEquals(HttpStatusCode.OK, response.status)
    }

    @Test
    fun `chat endpoint returns 500 on agent error`() = testApplication {
        val mockAgent = createMockAgent()
        coEvery {
            mockAgent.chat(any(), any(), any(), any(), any())
        } throws RuntimeException("Agent error")

        application { configureTestApp(mockAgent) }

        val response = client.post("/api/chat") {
            contentType(ContentType.Application.Json)
            setBody("""{"message": "Test"}""")
        }

        assertEquals(HttpStatusCode.InternalServerError, response.status)
        assertContains(response.bodyAsText(), "error")
    }

    @Test
    fun `chat endpoint returns 500 on invalid json`() = testApplication {
        val mockAgent = createMockAgent()

        application { configureTestApp(mockAgent) }

        val response = client.post("/api/chat") {
            contentType(ContentType.Application.Json)
            setBody("not a valid json")
        }

        assertEquals(HttpStatusCode.InternalServerError, response.status)
    }

    // === Тесты /api/logs ===

    @Test
    fun `logs endpoint returns logs list`() = testApplication {
        application { configureTestApp(createMockAgent()) }

        val response = client.get("/api/logs")

        assertEquals(HttpStatusCode.OK, response.status)
        assertContains(response.bodyAsText(), "logs")
    }

    @Test
    fun `logs endpoint accepts query parameters`() = testApplication {
        application { configureTestApp(createMockAgent()) }

        val response = client.get("/api/logs?limit=10&offset=0&level=INFO")

        assertEquals(HttpStatusCode.OK, response.status)
    }

    @Test
    fun `delete logs endpoint clears logs`() = testApplication {
        application { configureTestApp(createMockAgent()) }

        val response = client.delete("/api/logs")

        assertEquals(HttpStatusCode.OK, response.status)
        assertContains(response.bodyAsText(), "cleared")
    }

    // === Тесты /api/health/detailed ===

    @Test
    fun `detailed health endpoint returns healthy when all services ok`() = testApplication {
        val mockLLMClient = createMockLLMClient(healthy = true)
        val repository = InMemoryConversationRepository()

        application { configureTestApp(createMockAgent(), mockLLMClient, repository) }

        val response = client.get("/api/health/detailed")

        assertEquals(HttpStatusCode.OK, response.status)
        val body = response.bodyAsText()
        assertContains(body, "healthy")
        assertContains(body, "llm")
        assertContains(body, "storage")
    }

    @Test
    fun `detailed health endpoint returns degraded when LLM unhealthy`() = testApplication {
        val mockLLMClient = createMockLLMClient(healthy = false)
        val repository = InMemoryConversationRepository()

        application { configureTestApp(createMockAgent(), mockLLMClient, repository) }

        val response = client.get("/api/health/detailed")

        assertEquals(HttpStatusCode.ServiceUnavailable, response.status)
        val body = response.bodyAsText()
        assertContains(body, "degraded")
        assertContains(body, "unhealthy")
    }

    @Test
    fun `detailed health endpoint works without llmClient`() = testApplication {
        application { configureTestApp(createMockAgent()) }

        val response = client.get("/api/health/detailed")

        assertEquals(HttpStatusCode.OK, response.status)
        assertContains(response.bodyAsText(), "healthy")
    }

    // === Тесты /api/chat/stream ===

    @Test
    fun `stream endpoint returns SSE content type`() = testApplication {
        val mockAgent = createMockAgent()
        every {
            mockAgent.chatStream(any(), any(), any(), any(), any())
        } returns flowOf(
            StreamEvent(type = StreamEventType.START, conversationId = "conv-1", messageId = "msg-1"),
            StreamEvent(type = StreamEventType.CONTENT, content = "Hello"),
            StreamEvent(type = StreamEventType.DONE, conversationId = "conv-1", messageId = "msg-1")
        )

        application { configureTestApp(mockAgent) }

        val response = client.post("/api/chat/stream") {
            contentType(ContentType.Application.Json)
            setBody("""{"message": "Test"}""")
        }

        assertEquals(HttpStatusCode.OK, response.status)
        val body = response.bodyAsText()
        assertContains(body, "START")
        assertContains(body, "CONTENT")
        assertContains(body, "DONE")
    }

    @Test
    fun `stream endpoint handles errors gracefully`() = testApplication {
        val mockAgent = createMockAgent()
        every {
            mockAgent.chatStream(any(), any(), any(), any(), any())
        } returns flowOf(
            StreamEvent(type = StreamEventType.START, conversationId = "conv-1", messageId = "msg-1"),
            StreamEvent(type = StreamEventType.ERROR, error = "Test error")
        )

        application { configureTestApp(mockAgent) }

        val response = client.post("/api/chat/stream") {
            contentType(ContentType.Application.Json)
            setBody("""{"message": "Test"}""")
        }

        assertEquals(HttpStatusCode.OK, response.status)
        assertContains(response.bodyAsText(), "ERROR")
    }
}
