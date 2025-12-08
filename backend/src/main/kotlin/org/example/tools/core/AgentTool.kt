package org.example.tools.core

import org.example.model.Tool

/**
 * Интерфейс для инструментов агента.
 * Каждый инструмент должен предоставить определение для LLM и метод выполнения.
 */
interface AgentTool {
    /**
     * Уникальное имя инструмента (snake_case).
     * Используется для идентификации при вызове.
     */
    val name: String

    /**
     * Описание инструмента для LLM.
     * Должно чётко объяснять, что делает инструмент.
     */
    val description: String

    /**
     * Возвращает определение инструмента в формате OpenAI/DeepSeek.
     * Включает схему параметров.
     */
    fun getDefinition(): Tool

    /**
     * Выполняет инструмент с заданными аргументами.
     *
     * @param arguments JSON-строка с аргументами
     * @return Результат выполнения в виде строки
     */
    suspend fun execute(arguments: String): String
}

/**
 * Базовый класс для инструментов с автоматической генерацией определения из аннотаций.
 * Наследуйте от этого класса и добавьте аннотации @Tool и @Param.
 */
abstract class AnnotatedAgentTool : AgentTool {

    private val toolMetadata: ToolMetadata by lazy {
        ToolMetadataReader.readMetadata(this::class)
            ?: throw IllegalStateException(
                "Class ${this::class.simpleName} must be annotated with @Tool"
            )
    }

    override val name: String
        get() = toolMetadata.name

    override val description: String
        get() = toolMetadata.description

    override fun getDefinition(): Tool = toolMetadata.toToolDefinition()
}
