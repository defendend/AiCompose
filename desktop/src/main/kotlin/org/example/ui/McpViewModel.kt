package org.example.ui

import androidx.compose.runtime.mutableStateListOf
import io.modelcontextprotocol.kotlin.sdk.*
import io.modelcontextprotocol.kotlin.sdk.server.*
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.serialization.json.*
import org.example.model.McpConnectionStatus
import org.example.model.McpServerInfo
import org.example.model.McpTool

/**
 * ViewModel для управления MCP серверами в desktop приложении
 */
class McpViewModel {
    private val scope = CoroutineScope(Dispatchers.Default)

    // Список подключенных MCP серверов
    private val _servers = mutableStateListOf<McpServerInfo>()
    val servers: List<McpServerInfo> get() = _servers

    init {
        // Создаём демонстрационный локальный MCP сервер при инициализации
        createDemoServer()
    }

    /**
     * Создаёт демонстрационный MCP сервер с тестовыми инструментами
     */
    private fun createDemoServer() {
        scope.launch {
            try {
                val server = Server(
                    serverInfo = Implementation(
                        name = "aicompose-demo-server",
                        version = "1.0.0"
                    ),
                    options = ServerOptions(
                        capabilities = ServerCapabilities(
                            tools = ServerCapabilities.Tools(listChanged = false)
                        )
                    )
                )

                // Регистрируем инструменты
                server.addTool(
                    name = "echo",
                    description = "Возвращает переданный текст обратно"
                ) { request ->
                    val args = request.arguments?.jsonObject
                    val text = args?.get("text")?.jsonPrimitive?.content ?: ""
                    CallToolResult(content = listOf(TextContent(text = "Echo: $text")))
                }

                server.addTool(
                    name = "add",
                    description = "Складывает два числа"
                ) { request ->
                    val args = request.arguments?.jsonObject
                    val a = args?.get("a")?.jsonPrimitive?.intOrNull ?: 0
                    val b = args?.get("b")?.jsonPrimitive?.intOrNull ?: 0
                    CallToolResult(content = listOf(TextContent(text = "Результат: ${a + b}")))
                }

                server.addTool(
                    name = "multiply",
                    description = "Умножает два числа"
                ) { request ->
                    val args = request.arguments?.jsonObject
                    val a = args?.get("a")?.jsonPrimitive?.intOrNull ?: 0
                    val b = args?.get("b")?.jsonPrimitive?.intOrNull ?: 0
                    CallToolResult(content = listOf(TextContent(text = "Результат: ${a * b}")))
                }

                server.addTool(
                    name = "get_time",
                    description = "Возвращает текущее время в формате ISO"
                ) { _ ->
                    val currentTime = java.time.Instant.now().toString()
                    CallToolResult(content = listOf(TextContent(text = "Текущее время: $currentTime")))
                }

                server.addTool(
                    name = "reverse",
                    description = "Переворачивает строку задом наперед"
                ) { request ->
                    val args = request.arguments?.jsonObject
                    val text = args?.get("text")?.jsonPrimitive?.content ?: ""
                    CallToolResult(content = listOf(TextContent(text = "Reversed: ${text.reversed()}")))
                }

                // Создаём информацию о сервере для UI
                val serverInfo = McpServerInfo(
                    id = "demo-server-1",
                    name = "AiCompose Demo Server",
                    version = "1.0.0",
                    status = McpConnectionStatus.CONNECTED,
                    tools = listOf(
                        McpTool("echo", "Возвращает переданный текст обратно", """{"text": "string"}"""),
                        McpTool("add", "Складывает два числа", """{"a": "integer", "b": "integer"}"""),
                        McpTool("multiply", "Умножает два числа", """{"a": "integer", "b": "integer"}"""),
                        McpTool("get_time", "Возвращает текущее время", "{}"),
                        McpTool("reverse", "Переворачивает строку", """{"text": "string"}""")
                    )
                )

                _servers.add(serverInfo)

            } catch (e: Exception) {
                println("Ошибка создания демо сервера: ${e.message}")
                e.printStackTrace()
            }
        }
    }

    /**
     * Добавляет новый MCP сервер (заглушка для будущей реализации)
     */
    fun addServer(url: String) {
        // TODO: реализовать подключение к внешнему MCP серверу
    }

    /**
     * Отключает MCP сервер
     */
    fun disconnectServer(serverId: String) {
        val index = _servers.indexOfFirst { it.id == serverId }
        if (index != -1) {
            _servers[index] = _servers[index].copy(status = McpConnectionStatus.DISCONNECTED)
        }
    }

    /**
     * Переподключает MCP сервер
     */
    fun reconnectServer(serverId: String) {
        val index = _servers.indexOfFirst { it.id == serverId }
        if (index != -1) {
            _servers[index] = _servers[index].copy(status = McpConnectionStatus.CONNECTING)
            // Имитация переподключения
            scope.launch {
                kotlinx.coroutines.delay(1000)
                _servers[index] = _servers[index].copy(status = McpConnectionStatus.CONNECTED)
            }
        }
    }
}
