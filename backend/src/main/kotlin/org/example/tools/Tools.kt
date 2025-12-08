package org.example.tools

import org.example.model.Tool

/**
 * Старый интерфейс AgentTool для обратной совместимости.
 * @deprecated Используйте org.example.tools.core.AgentTool
 */
@Deprecated(
    message = "Use org.example.tools.core.AgentTool instead",
    replaceWith = ReplaceWith("org.example.tools.core.AgentTool")
)
typealias AgentTool = org.example.tools.core.AgentTool

/**
 * Реестр инструментов для обратной совместимости.
 * Делегирует вызовы новому ToolRegistry.
 *
 * @deprecated Используйте org.example.tools.core.ToolRegistry
 */
@Deprecated(
    message = "Use org.example.tools.core.ToolRegistry instead",
    replaceWith = ReplaceWith("org.example.tools.core.ToolRegistry")
)
object ToolRegistry {
    private val delegate = org.example.tools.core.ToolRegistry

    fun getAllTools(): List<Tool> = delegate.getAllTools()

    fun getTool(name: String): org.example.tools.core.AgentTool? = delegate.getTool(name)

    suspend fun executeTool(name: String, arguments: String): String =
        delegate.executeTool(name, arguments)
}
