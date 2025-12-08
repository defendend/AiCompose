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
import kotlinx.serialization.json.Json
import org.example.agent.Agent
import org.example.shared.model.ChatResponse
import org.example.shared.model.MessageRole
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

    private fun Application.configureTestApp(agent: Agent) {
        install(ContentNegotiation) { json(json) }
        routing { chatRoutes(agent) }
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
}
