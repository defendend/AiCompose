package org.example.model

/**
 * Модели для работы с MCP (Model Context Protocol) серверами
 */

/**
 * Информация о подключенном MCP сервере
 */
data class McpServerInfo(
    val id: String,
    val name: String,
    val version: String,
    val status: McpConnectionStatus,
    val tools: List<McpTool> = emptyList()
)

/**
 * Статус подключения к MCP серверу
 */
enum class McpConnectionStatus {
    CONNECTED,
    DISCONNECTED,
    CONNECTING,
    ERROR
}

/**
 * Информация об инструменте (tool) MCP сервера
 */
data class McpTool(
    val name: String,
    val description: String,
    val parameters: String = "{}" // JSON schema параметров
)
