package org.example.tools

import kotlinx.coroutines.test.runTest
import org.example.tools.core.ToolRegistry
import org.example.tools.historical.CompareErasTool
import org.example.tools.historical.HistoricalEventsTool
import org.example.tools.historical.HistoricalFigureTool
import org.example.tools.historical.HistoricalQuoteTool
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ToolsTest {

    // === Тесты HistoricalEventsTool ===

    @Test
    fun `HistoricalEventsTool returns events for known year`() = runTest {
        val result = HistoricalEventsTool.execute("""{"year": 1945}""")

        assertContains(result, "1945")
        assertContains(result, "Второй мировой войны")
        assertContains(result, "Капитуляция")
    }

    @Test
    fun `HistoricalEventsTool returns message for unknown year`() = runTest {
        val result = HistoricalEventsTool.execute("""{"year": 2000}""")

        assertContains(result, "нет записей")
    }

    @Test
    fun `HistoricalEventsTool returns error for missing year`() = runTest {
        val result = HistoricalEventsTool.execute("""{}""")

        assertContains(result, "Ошибка")
    }

    @Test
    fun `HistoricalEventsTool has correct definition`() {
        val definition = HistoricalEventsTool.getDefinition()

        assertEquals("function", definition.type)
        assertEquals("get_historical_events", definition.function.name)
        assertContains(definition.function.parameters.required, "year")
    }

    // === Тесты HistoricalFigureTool ===

    @Test
    fun `HistoricalFigureTool returns info for Napoleon`() = runTest {
        val result = HistoricalFigureTool.execute("""{"name": "Наполеон"}""")

        assertContains(result, "Наполеон Бонапарт")
        assertContains(result, "1769-1821")
        assertContains(result, "Французский император")
    }

    @Test
    fun `HistoricalFigureTool is case insensitive`() = runTest {
        val result = HistoricalFigureTool.execute("""{"name": "ЦЕЗАРЬ"}""")

        assertContains(result, "Юлий Цезарь")
    }

    @Test
    fun `HistoricalFigureTool finds by partial name`() = runTest {
        val result = HistoricalFigureTool.execute("""{"name": "александр великий"}""")

        assertContains(result, "Александр Македонский")
    }

    @Test
    fun `HistoricalFigureTool returns message for unknown figure`() = runTest {
        val result = HistoricalFigureTool.execute("""{"name": "неизвестный"}""")

        assertContains(result, "не найдена")
        assertContains(result, "Доступны")
    }

    @Test
    fun `HistoricalFigureTool returns error for missing name`() = runTest {
        val result = HistoricalFigureTool.execute("""{}""")

        assertContains(result, "Ошибка")
    }

    // === Тесты CompareErasTool ===

    @Test
    fun `CompareErasTool compares two eras`() = runTest {
        val result = CompareErasTool.execute("""{"era1": "античность", "era2": "средневековье"}""")

        assertContains(result, "АНТИЧНОСТЬ")
        assertContains(result, "СРЕДНЕВЕКОВЬЕ")
        assertContains(result, "период")
    }

    @Test
    fun `CompareErasTool returns error for unknown era`() = runTest {
        val result = CompareErasTool.execute("""{"era1": "античность", "era2": "будущее"}""")

        assertContains(result, "не найдена")
        assertContains(result, "Доступны")
    }

    @Test
    fun `CompareErasTool returns error for missing era`() = runTest {
        val result = CompareErasTool.execute("""{"era1": "античность"}""")

        assertContains(result, "Ошибка")
    }

    @Test
    fun `CompareErasTool has two required parameters`() {
        val definition = CompareErasTool.getDefinition()

        assertEquals(2, definition.function.parameters.required.size)
        assertContains(definition.function.parameters.required, "era1")
        assertContains(definition.function.parameters.required, "era2")
    }

    // === Тесты HistoricalQuoteTool ===

    @Test
    fun `HistoricalQuoteTool returns quote for topic`() = runTest {
        val result = HistoricalQuoteTool.execute("""{"topic": "цезарь"}""")

        assertContains(result, "Цезарь")
        assertContains(result, "«")
    }

    @Test
    fun `HistoricalQuoteTool returns random quote for empty topic`() = runTest {
        val result = HistoricalQuoteTool.execute("""{}""")

        assertContains(result, "«")
        assertContains(result, "»")
        assertContains(result, "—")
    }

    @Test
    fun `HistoricalQuoteTool returns random quote for blank topic`() = runTest {
        val result = HistoricalQuoteTool.execute("""{"topic": ""}""")

        assertContains(result, "«")
    }

    @Test
    fun `HistoricalQuoteTool topic is not required`() {
        val definition = HistoricalQuoteTool.getDefinition()

        assertTrue(definition.function.parameters.required.isEmpty())
    }

    // === Тесты ToolRegistry ===

    @Test
    fun `ToolRegistry returns all tools`() {
        val tools = ToolRegistry.getAllTools()

        assertEquals(46, tools.size) // 4 исторических + 3 pipeline + 5 docker + 6 rag + 1 системный + 5 git + 3 docs + 4 code + 4 ide + 4 github + 7 support
        // Исторические инструменты
        assertTrue(tools.any { it.function.name == "get_historical_events" })
        assertTrue(tools.any { it.function.name == "get_historical_figure" })
        assertTrue(tools.any { it.function.name == "compare_eras" })
        assertTrue(tools.any { it.function.name == "get_historical_quote" })
        // Pipeline инструменты
        assertTrue(tools.any { it.function.name == "pipeline_search_docs" })
        assertTrue(tools.any { it.function.name == "pipeline_summarize" })
        assertTrue(tools.any { it.function.name == "pipeline_save_to_file" })
        // Docker инструменты
        assertTrue(tools.any { it.function.name == "docker_run" })
        assertTrue(tools.any { it.function.name == "docker_exec" })
        assertTrue(tools.any { it.function.name == "docker_logs" })
        assertTrue(tools.any { it.function.name == "docker_stop" })
        assertTrue(tools.any { it.function.name == "docker_ps" })
        // Системные инструменты
        assertTrue(tools.any { it.function.name == "get_current_time" })
    }

    @Test
    fun `ToolRegistry getTool returns existing tool`() {
        val tool = ToolRegistry.getTool("get_historical_events")

        assertNotNull(tool)
        assertEquals("get_historical_events", tool.name)
    }

    @Test
    fun `ToolRegistry getTool returns null for unknown tool`() {
        val tool = ToolRegistry.getTool("unknown_tool")

        assertNull(tool)
    }

    @Test
    fun `ToolRegistry executeTool executes existing tool`() = runTest {
        val result = ToolRegistry.executeTool("get_historical_events", """{"year": 1961}""")

        assertContains(result, "Гагарин")
    }

    @Test
    fun `ToolRegistry executeTool returns error for unknown tool`() = runTest {
        val result = ToolRegistry.executeTool("unknown_tool", "{}")

        assertContains(result, "не найден")
    }

    // === Тесты корректности всех определений ===

    @Test
    fun `all tools have type function`() {
        val tools = ToolRegistry.getAllTools()

        tools.forEach { tool ->
            assertEquals("function", tool.type, "Tool ${tool.function.name} should have type 'function'")
        }
    }

    @Test
    fun `all tools have type object in parameters`() {
        val tools = ToolRegistry.getAllTools()

        tools.forEach { tool ->
            assertEquals(
                "object",
                tool.function.parameters.type,
                "Tool ${tool.function.name} parameters should have type 'object'"
            )
        }
    }

    @Test
    fun `all tools have non-empty description`() {
        val tools = ToolRegistry.getAllTools()

        tools.forEach { tool ->
            assertTrue(
                tool.function.description.isNotBlank(),
                "Tool ${tool.function.name} should have non-empty description"
            )
        }
    }
}
