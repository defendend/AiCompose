package org.example.mcp

import io.modelcontextprotocol.kotlin.sdk.*
import io.modelcontextprotocol.kotlin.sdk.server.*
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.*
import kotlinx.io.asSource
import kotlinx.io.asSink
import kotlinx.io.buffered

/**
 * Простой MCP сервер для демонстрации возможностей протокола.
 *
 * Сервер предоставляет три инструмента:
 * - echo: возвращает переданный текст
 * - add: складывает два числа
 * - get_time: возвращает текущее время
 */
fun startMcpServer() = runBlocking {
    // Создаем сервер с информацией о реализации
    val server = Server(
        serverInfo = Implementation(
            name = "simple-mcp-server",
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

    // Регистрируем инструмент "echo"
    server.addTool(
        name = "echo",
        description = "Возвращает переданный текст обратно"
    ) { request ->
        val args = request.arguments?.jsonObject
        val text = args?.get("text")?.jsonPrimitive?.content ?: "Нет текста"

        CallToolResult(
            content = listOf(TextContent(text = "Результат: $text"))
        )
    }

    // Регистрируем инструмент "add"
    server.addTool(
        name = "add",
        description = "Складывает два числа"
    ) { request ->
        val args = request.arguments?.jsonObject
        val a = args?.get("a")?.jsonPrimitive?.intOrNull ?: 0
        val b = args?.get("b")?.jsonPrimitive?.intOrNull ?: 0
        val result = a + b

        CallToolResult(
            content = listOf(TextContent(text = "Результат: $a + $b = $result"))
        )
    }

    // Регистрируем инструмент "get_time"
    server.addTool(
        name = "get_time",
        description = "Возвращает текущее время в формате ISO"
    ) { request ->
        val currentTime = java.time.Instant.now().toString()

        CallToolResult(
            content = listOf(TextContent(text = "Текущее время: $currentTime"))
        )
    }

    // Запускаем сервер через STDIO транспорт
    println("🚀 MCP Server запущен. Доступные инструменты: echo, add, get_time")
    server.connect(StdioServerTransport(System.`in`.asSource().buffered(), System.out.asSink().buffered()))
}

/**
 * Точка входа для запуска сервера
 */
fun main() {
    startMcpServer()
}
