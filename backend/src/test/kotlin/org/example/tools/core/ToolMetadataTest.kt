package org.example.tools.core

import kotlinx.coroutines.test.runTest
import org.example.tools.annotations.Param
import org.example.tools.annotations.Tool
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ToolMetadataTest {

    // === Тестовые инструменты ===

    @Tool(
        name = "test_single_param",
        description = "Тестовый инструмент с одним параметром"
    )
    @Param(
        name = "input",
        description = "Входной параметр",
        type = "string",
        required = true
    )
    object SingleParamTool : AnnotatedAgentTool() {
        override suspend fun execute(arguments: String): String = "ok"
    }

    @Tool(
        name = "test_multiple_params",
        description = "Тестовый инструмент с несколькими параметрами"
    )
    @Param(name = "param1", description = "Первый параметр", type = "string", required = true)
    @Param(name = "param2", description = "Второй параметр", type = "integer", required = false)
    @Param(name = "param3", description = "Третий параметр", type = "boolean", required = true)
    object MultipleParamsTool : AnnotatedAgentTool() {
        override suspend fun execute(arguments: String): String = "ok"
    }

    @Tool(
        name = "test_enum_param",
        description = "Инструмент с enum параметром"
    )
    @Param(
        name = "color",
        description = "Выбор цвета",
        type = "string",
        required = true,
        enumValues = ["red", "green", "blue"]
    )
    object EnumParamTool : AnnotatedAgentTool() {
        override suspend fun execute(arguments: String): String = "ok"
    }

    @Tool(
        name = "test_no_params",
        description = "Инструмент без параметров"
    )
    object NoParamsTool : AnnotatedAgentTool() {
        override suspend fun execute(arguments: String): String = "ok"
    }

    class NotAnnotatedTool : AgentTool {
        override val name = "not_annotated"
        override val description = "Not annotated"
        override fun getDefinition() = throw UnsupportedOperationException()
        override suspend fun execute(arguments: String) = "ok"
    }

    // === Тесты ToolMetadataReader ===

    @Test
    fun `readMetadata returns metadata for annotated class`() {
        val metadata = ToolMetadataReader.readMetadata(SingleParamTool::class)

        assertNotNull(metadata)
        assertEquals("test_single_param", metadata.name)
        assertEquals("Тестовый инструмент с одним параметром", metadata.description)
    }

    @Test
    fun `readMetadata returns null for non-annotated class`() {
        val metadata = ToolMetadataReader.readMetadata(NotAnnotatedTool::class)

        assertNull(metadata)
    }

    @Test
    fun `readMetadata reads single param correctly`() {
        val metadata = ToolMetadataReader.readMetadata(SingleParamTool::class)

        assertNotNull(metadata)
        assertEquals(1, metadata.parameters.size)

        val param = metadata.parameters[0]
        assertEquals("input", param.name)
        assertEquals("Входной параметр", param.description)
        assertEquals("string", param.type)
        assertTrue(param.required)
    }

    @Test
    fun `readMetadata reads multiple params correctly`() {
        val metadata = ToolMetadataReader.readMetadata(MultipleParamsTool::class)

        assertNotNull(metadata)
        assertEquals(3, metadata.parameters.size)

        // Проверяем имена всех параметров
        val paramNames = metadata.parameters.map { it.name }
        assertTrue("param1" in paramNames)
        assertTrue("param2" in paramNames)
        assertTrue("param3" in paramNames)
    }

    @Test
    fun `readMetadata reads param types correctly`() {
        val metadata = ToolMetadataReader.readMetadata(MultipleParamsTool::class)!!
        val paramsByName = metadata.parameters.associateBy { it.name }

        assertEquals("string", paramsByName["param1"]?.type)
        assertEquals("integer", paramsByName["param2"]?.type)
        assertEquals("boolean", paramsByName["param3"]?.type)
    }

    @Test
    fun `readMetadata reads required flags correctly`() {
        val metadata = ToolMetadataReader.readMetadata(MultipleParamsTool::class)!!
        val paramsByName = metadata.parameters.associateBy { it.name }

        assertTrue(paramsByName["param1"]?.required == true)
        assertTrue(paramsByName["param2"]?.required == false)
        assertTrue(paramsByName["param3"]?.required == true)
    }

    @Test
    fun `readMetadata reads enum values correctly`() {
        val metadata = ToolMetadataReader.readMetadata(EnumParamTool::class)!!
        val param = metadata.parameters[0]

        assertEquals(listOf("red", "green", "blue"), param.enumValues)
    }

    @Test
    fun `readMetadata handles no params`() {
        val metadata = ToolMetadataReader.readMetadata(NoParamsTool::class)

        assertNotNull(metadata)
        assertTrue(metadata.parameters.isEmpty())
    }

    // === Тесты ToolMetadata.toToolDefinition ===

    @Test
    fun `toToolDefinition creates correct tool structure`() {
        val metadata = ToolMetadataReader.readMetadata(SingleParamTool::class)!!
        val definition = metadata.toToolDefinition()

        assertEquals("function", definition.type)
        assertEquals("test_single_param", definition.function.name)
        assertEquals("Тестовый инструмент с одним параметром", definition.function.description)
        assertEquals("object", definition.function.parameters.type)
    }

    @Test
    fun `toToolDefinition includes required params in required list`() {
        val metadata = ToolMetadataReader.readMetadata(MultipleParamsTool::class)!!
        val definition = metadata.toToolDefinition()

        val required = definition.function.parameters.required
        assertTrue("param1" in required)
        assertTrue("param2" !in required)
        assertTrue("param3" in required)
    }

    @Test
    fun `toToolDefinition creates property definitions for all params`() {
        val metadata = ToolMetadataReader.readMetadata(MultipleParamsTool::class)!!
        val definition = metadata.toToolDefinition()

        val properties = definition.function.parameters.properties
        assertEquals(3, properties.size)
        assertTrue("param1" in properties)
        assertTrue("param2" in properties)
        assertTrue("param3" in properties)
    }

    @Test
    fun `toToolDefinition includes enum values in property`() {
        val metadata = ToolMetadataReader.readMetadata(EnumParamTool::class)!!
        val definition = metadata.toToolDefinition()

        val colorProp = definition.function.parameters.properties["color"]
        assertNotNull(colorProp)
        assertEquals(listOf("red", "green", "blue"), colorProp.enum)
    }

    // === Тесты AnnotatedAgentTool ===

    @Test
    fun `AnnotatedAgentTool name comes from annotation`() {
        assertEquals("test_single_param", SingleParamTool.name)
        assertEquals("test_multiple_params", MultipleParamsTool.name)
    }

    @Test
    fun `AnnotatedAgentTool description comes from annotation`() {
        assertEquals("Тестовый инструмент с одним параметром", SingleParamTool.description)
    }

    @Test
    fun `AnnotatedAgentTool getDefinition works correctly`() {
        val definition = SingleParamTool.getDefinition()

        assertEquals("function", definition.type)
        assertEquals("test_single_param", definition.function.name)
    }

    @Test
    fun `AnnotatedAgentTool execute works`() = runTest {
        val result = SingleParamTool.execute("{}")
        assertEquals("ok", result)
    }
}
