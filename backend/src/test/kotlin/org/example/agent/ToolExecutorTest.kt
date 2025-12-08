package org.example.agent

import kotlinx.coroutines.test.runTest
import org.example.model.FunctionCall
import org.example.model.LLMToolCall
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertContains

class ToolExecutorTest {
    private val executor = ToolExecutor()

    // === Тесты fixToolCalls ===

    @Test
    fun `fixToolCalls sets type to function when null`() {
        val toolCalls = listOf(
            LLMToolCall(
                id = "call_1",
                type = null,
                function = FunctionCall(name = "test", arguments = "{}")
            )
        )

        val fixed = executor.fixToolCalls(toolCalls)

        assertEquals("function", fixed[0].type)
    }

    @Test
    fun `fixToolCalls preserves existing type`() {
        val toolCalls = listOf(
            LLMToolCall(
                id = "call_1",
                type = "function",
                function = FunctionCall(name = "test", arguments = "{}")
            )
        )

        val fixed = executor.fixToolCalls(toolCalls)

        assertEquals("function", fixed[0].type)
    }

    @Test
    fun `fixToolCalls handles multiple tool calls`() {
        val toolCalls = listOf(
            LLMToolCall(
                id = "call_1",
                type = null,
                function = FunctionCall(name = "tool1", arguments = "{}")
            ),
            LLMToolCall(
                id = "call_2",
                type = "function",
                function = FunctionCall(name = "tool2", arguments = "{}")
            )
        )

        val fixed = executor.fixToolCalls(toolCalls)

        assertEquals(2, fixed.size)
        assertEquals("function", fixed[0].type)
        assertEquals("function", fixed[1].type)
    }

    @Test
    fun `fixToolCalls preserves id and function`() {
        val toolCalls = listOf(
            LLMToolCall(
                id = "unique_id",
                type = null,
                function = FunctionCall(name = "my_tool", arguments = """{"key": "value"}""")
            )
        )

        val fixed = executor.fixToolCalls(toolCalls)

        assertEquals("unique_id", fixed[0].id)
        assertEquals("my_tool", fixed[0].function.name)
        assertEquals("""{"key": "value"}""", fixed[0].function.arguments)
    }

    // === Тесты executeToolCall ===

    @Test
    fun `executeToolCall returns tool message with correct role`() = runTest {
        val toolCall = LLMToolCall(
            id = "call_123",
            type = "function",
            function = FunctionCall(
                name = "get_historical_events",
                arguments = """{"year": 1945}"""
            )
        )

        val result = executor.executeToolCall(toolCall, "conv-1")

        assertEquals("tool", result.role)
        assertEquals("call_123", result.tool_call_id)
    }

    @Test
    fun `executeToolCall returns events for known year`() = runTest {
        val toolCall = LLMToolCall(
            id = "call_1",
            type = "function",
            function = FunctionCall(
                name = "get_historical_events",
                arguments = """{"year": 1945}"""
            )
        )

        val result = executor.executeToolCall(toolCall, "conv-1")

        assertContains(result.content ?: "", "1945")
        assertContains(result.content ?: "", "Второй мировой войны")
    }

    @Test
    fun `executeToolCall returns error for unknown tool`() = runTest {
        val toolCall = LLMToolCall(
            id = "call_1",
            type = "function",
            function = FunctionCall(
                name = "unknown_tool",
                arguments = "{}"
            )
        )

        val result = executor.executeToolCall(toolCall, "conv-1")

        assertContains(result.content ?: "", "не найден")
    }

    @Test
    fun `executeToolCalls executes multiple tools`() = runTest {
        val toolCalls = listOf(
            LLMToolCall(
                id = "call_1",
                type = "function",
                function = FunctionCall(
                    name = "get_historical_events",
                    arguments = """{"year": 1961}"""
                )
            ),
            LLMToolCall(
                id = "call_2",
                type = "function",
                function = FunctionCall(
                    name = "get_historical_figure",
                    arguments = """{"name": "наполеон"}"""
                )
            )
        )

        val results = executor.executeToolCalls(toolCalls, "conv-1")

        assertEquals(2, results.size)
        assertEquals("call_1", results[0].tool_call_id)
        assertEquals("call_2", results[1].tool_call_id)
        assertContains(results[0].content ?: "", "Гагарин")
        assertContains(results[1].content ?: "", "Наполеон")
    }
}
