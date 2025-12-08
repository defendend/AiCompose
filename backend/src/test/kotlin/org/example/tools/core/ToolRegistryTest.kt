package org.example.tools.core

import kotlinx.coroutines.test.runTest
import org.example.tools.annotations.Param
import org.example.tools.annotations.Tool
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ToolRegistryTest {

    @BeforeTest
    fun setUp() {
        ToolRegistry.reset()
    }

    @AfterTest
    fun tearDown() {
        ToolRegistry.reset()
    }

    // Тестовый инструмент для регистрации
    @Tool(name = "custom_test_tool", description = "Кастомный тестовый инструмент")
    @Param(name = "value", description = "Значение", type = "string", required = true)
    object CustomTestTool : AnnotatedAgentTool() {
        override suspend fun execute(arguments: String): String = "custom result"
    }

    // === Тесты инициализации ===

    @Test
    fun `initialize registers built-in tools`() {
        ToolRegistry.initialize()

        assertTrue(ToolRegistry.hasTool("get_historical_events"))
        assertTrue(ToolRegistry.hasTool("get_historical_figure"))
        assertTrue(ToolRegistry.hasTool("compare_eras"))
        assertTrue(ToolRegistry.hasTool("get_historical_quote"))
    }

    @Test
    fun `getAllTools returns 4 built-in tools after initialization`() {
        val tools = ToolRegistry.getAllTools()

        assertEquals(4, tools.size)
    }

    @Test
    fun `initialize is idempotent`() {
        ToolRegistry.initialize()
        val count1 = ToolRegistry.size()

        ToolRegistry.initialize()
        val count2 = ToolRegistry.size()

        assertEquals(count1, count2)
    }

    // === Тесты ручной регистрации ===

    @Test
    fun `register adds new tool`() {
        ToolRegistry.initialize()
        val initialSize = ToolRegistry.size()

        val success = ToolRegistry.register(CustomTestTool)

        assertTrue(success)
        assertEquals(initialSize + 1, ToolRegistry.size())
        assertTrue(ToolRegistry.hasTool("custom_test_tool"))
    }

    @Test
    fun `register returns false for duplicate tool`() {
        ToolRegistry.initialize()
        ToolRegistry.register(CustomTestTool)

        val success = ToolRegistry.register(CustomTestTool)

        assertFalse(success)
    }

    @Test
    fun `registerAll adds multiple tools`() {
        ToolRegistry.reset()

        ToolRegistry.registerAll(ToolA, ToolB)

        assertTrue(ToolRegistry.hasTool("tool_a"))
        assertTrue(ToolRegistry.hasTool("tool_b"))
    }

    @Tool(name = "tool_a", description = "Tool A")
    object ToolA : AnnotatedAgentTool() {
        override suspend fun execute(arguments: String) = "a"
    }

    @Tool(name = "tool_b", description = "Tool B")
    object ToolB : AnnotatedAgentTool() {
        override suspend fun execute(arguments: String) = "b"
    }

    // === Тесты получения инструментов ===

    @Test
    fun `getTool returns tool by name`() {
        ToolRegistry.initialize()

        val tool = ToolRegistry.getTool("get_historical_events")

        assertNotNull(tool)
        assertEquals("get_historical_events", tool.name)
    }

    @Test
    fun `getTool returns null for unknown tool`() {
        ToolRegistry.initialize()

        val tool = ToolRegistry.getTool("unknown_tool")

        assertNull(tool)
    }

    @Test
    fun `hasTool returns true for existing tool`() {
        ToolRegistry.initialize()

        assertTrue(ToolRegistry.hasTool("get_historical_events"))
    }

    @Test
    fun `hasTool returns false for unknown tool`() {
        ToolRegistry.initialize()

        assertFalse(ToolRegistry.hasTool("unknown_tool"))
    }

    @Test
    fun `getToolNames returns all tool names`() {
        ToolRegistry.initialize()

        val names = ToolRegistry.getToolNames()

        assertTrue("get_historical_events" in names)
        assertTrue("get_historical_figure" in names)
        assertTrue("compare_eras" in names)
        assertTrue("get_historical_quote" in names)
    }

    // === Тесты выполнения ===

    @Test
    fun `executeTool executes existing tool`() = runTest {
        ToolRegistry.initialize()

        val result = ToolRegistry.executeTool("get_historical_events", """{"year": 1945}""")

        assertTrue(result.contains("1945"))
    }

    @Test
    fun `executeTool returns error for unknown tool`() = runTest {
        ToolRegistry.initialize()

        val result = ToolRegistry.executeTool("unknown_tool", "{}")

        assertTrue(result.contains("не найден"))
    }

    @Test
    fun `executeTool handles tool exceptions gracefully`() = runTest {
        ToolRegistry.register(ThrowingTool)

        val result = ToolRegistry.executeTool("throwing_tool", "{}")

        assertTrue(result.contains("Ошибка"))
        assertTrue(result.contains("Test exception"))
    }

    @Tool(name = "throwing_tool", description = "Throws exception")
    object ThrowingTool : AnnotatedAgentTool() {
        override suspend fun execute(arguments: String): String {
            throw RuntimeException("Test exception")
        }
    }

    // === Тесты отмены регистрации ===

    @Test
    fun `unregister removes tool`() {
        ToolRegistry.initialize()
        ToolRegistry.register(CustomTestTool)
        assertTrue(ToolRegistry.hasTool("custom_test_tool"))

        val removed = ToolRegistry.unregister("custom_test_tool")

        assertNotNull(removed)
        assertFalse(ToolRegistry.hasTool("custom_test_tool"))
    }

    @Test
    fun `unregister returns null for unknown tool`() {
        ToolRegistry.initialize()

        val removed = ToolRegistry.unregister("unknown_tool")

        assertNull(removed)
    }

    // === Тесты сброса ===

    @Test
    fun `reset clears all tools`() {
        ToolRegistry.initialize()
        assertTrue(ToolRegistry.size() > 0)

        ToolRegistry.reset()

        // После reset нужна реинициализация
        ToolRegistry.initialize()
        assertEquals(4, ToolRegistry.size()) // Только built-in
    }

    // === Тесты автоматической инициализации ===

    @Test
    fun `getAllTools triggers auto-initialization`() {
        // Reset очищает initialized flag
        ToolRegistry.reset()

        // getAllTools должен вызвать initialize автоматически
        val tools = ToolRegistry.getAllTools()

        assertTrue(tools.isNotEmpty())
    }

    @Test
    fun `getTool triggers auto-initialization`() {
        ToolRegistry.reset()

        val tool = ToolRegistry.getTool("get_historical_events")

        assertNotNull(tool)
    }
}
