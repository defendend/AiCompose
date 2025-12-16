package org.example.mcp

import io.modelcontextprotocol.kotlin.sdk.*
import io.modelcontextprotocol.kotlin.sdk.server.*
import io.modelcontextprotocol.kotlin.sdk.types.ToolSchema
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.*

/**
 * Демонстрация MCP (Model Context Protocol).
 *
 * Показывает:
 * 1. Создание MCP сервера
 * 2. Регистрацию инструментов (tools)
 * 3. Вывод информации о сервере
 *
 * Запуск: ./gradlew :backend:run -PmainClass=org.example.mcp.McpDemoKt
 */
fun mcpDemo() = runBlocking {
    println("=" .repeat(70))
    println("🚀 MCP (Model Context Protocol) Демонстрация")
    println("=" .repeat(70))

    // Создаем сервер
    val server = createMcpServer()

    println("\n✅ MCP сервер успешно создан!")
    println("\n📋 Зарегистрировано 5 инструментов:")
    println("   1. echo - Возвращает текст обратно")
    println("   2. add - Складывает два числа")
    println("   3. multiply - Умножает два числа")
    println("   4. get_time - Возвращает текущее время")
    println("   5. reverse - Переворачивает строку")

    println("\n" + "=" .repeat(70))
    println("✨ Демонстрация завершена!")
    println("\nℹ️  Для запуска сервера используйте SimpleMcpServer")
    println("   ./gradlew :backend:run -PmainClass=org.example.mcp.SimpleMcpServerKt")
    println("=" .repeat(70))
}

/**
 * Создает MCP сервер с набором демонстрационных инструментов
 */
private fun createMcpServer(): Server {
    val server = Server(
        serverInfo = Implementation(
            name = "aicompose-mcp-demo",
            version = "1.0.0"
        ),
        options = ServerOptions(
            capabilities = ServerCapabilities(
                tools = ServerCapabilities.Tools(
                    listChanged = false
                )
            )
        )
    )

    // Инструмент 1: Echo
    server.addTool(
        name = "echo",
        description = "Возвращает переданный текст обратно"
    ) { request ->
        val args = request.arguments?.jsonObject
        val text = args?.get("text")?.jsonPrimitive?.content ?: ""
        CallToolResult(
            content = listOf(TextContent(text = "Echo: $text"))
        )
    }

    // Инструмент 2: Add
    server.addTool(
        name = "add",
        description = "Складывает два числа"
    ) { request ->
        val args = request.arguments?.jsonObject
        val a = args?.get("a")?.jsonPrimitive?.intOrNull ?: 0
        val b = args?.get("b")?.jsonPrimitive?.intOrNull ?: 0
        CallToolResult(
            content = listOf(TextContent(text = "Результат: ${a + b}"))
        )
    }

    // Инструмент 3: Multiply
    server.addTool(
        name = "multiply",
        description = "Умножает два числа"
    ) { request ->
        val args = request.arguments?.jsonObject
        val a = args?.get("a")?.jsonPrimitive?.intOrNull ?: 0
        val b = args?.get("b")?.jsonPrimitive?.intOrNull ?: 0
        CallToolResult(
            content = listOf(TextContent(text = "Результат: ${a * b}"))
        )
    }

    // Инструмент 4: GetTime
    server.addTool(
        name = "get_time",
        description = "Возвращает текущее время в формате ISO"
    ) { request ->
        val currentTime = java.time.Instant.now().toString()
        CallToolResult(
            content = listOf(TextContent(text = "Текущее время: $currentTime"))
        )
    }

    // Инструмент 5: Reverse
    server.addTool(
        name = "reverse",
        description = "Переворачивает строку задом наперед"
    ) { request ->
        val args = request.arguments?.jsonObject
        val text = args?.get("text")?.jsonPrimitive?.content ?: ""
        CallToolResult(
            content = listOf(TextContent(text = "Reversed: ${text.reversed()}"))
        )
    }

    return server
}

/**
 * Точка входа для запуска демо
 */
fun main() {
    mcpDemo()
}
