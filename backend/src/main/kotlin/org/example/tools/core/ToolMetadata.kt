package org.example.tools.core

import org.example.model.FunctionDefinition
import org.example.model.FunctionParameters
import org.example.model.PropertyDefinition
import org.example.model.Tool
import org.example.tools.annotations.Param
import org.example.tools.annotations.Params
import kotlin.reflect.KClass
import org.example.tools.annotations.Tool as ToolAnnotation

/**
 * Метаданные инструмента, извлечённые из аннотаций.
 */
data class ToolMetadata(
    val name: String,
    val description: String,
    val parameters: List<ParamMetadata>
) {
    /**
     * Конвертирует метаданные в определение Tool для LLM API.
     */
    fun toToolDefinition(): Tool = Tool(
        type = "function",
        function = FunctionDefinition(
            name = name,
            description = description,
            parameters = FunctionParameters(
                type = "object",
                properties = parameters.associate { param ->
                    param.name to PropertyDefinition(
                        type = param.type,
                        description = param.description,
                        enum = param.enumValues.takeIf { it.isNotEmpty() }
                    )
                },
                required = parameters.filter { it.required }.map { it.name }
            )
        )
    )
}

/**
 * Метаданные параметра инструмента.
 */
data class ParamMetadata(
    val name: String,
    val description: String,
    val type: String,
    val required: Boolean,
    val enumValues: List<String>
)

/**
 * Читает метаданные инструмента из аннотаций класса.
 */
object ToolMetadataReader {

    /**
     * Извлекает метаданные из аннотаций класса.
     * Возвращает null если класс не аннотирован @Tool.
     */
    fun readMetadata(klass: KClass<*>): ToolMetadata? {
        val toolAnnotation = klass.java.getAnnotation(ToolAnnotation::class.java)
            ?: return null

        val params = readParams(klass)

        return ToolMetadata(
            name = toolAnnotation.name,
            description = toolAnnotation.description,
            parameters = params
        )
    }

    /**
     * Читает параметры из @Param аннотаций.
     * Поддерживает как одиночные, так и множественные @Param через @Params контейнер.
     */
    private fun readParams(klass: KClass<*>): List<ParamMetadata> {
        val javaClass = klass.java
        val params = mutableListOf<ParamMetadata>()

        // Читаем @Params контейнер (для @Repeatable)
        javaClass.getAnnotation(Params::class.java)?.value?.forEach { param ->
            params.add(param.toMetadata())
        }

        // Также проверяем getAnnotationsByType для множественных @Param
        if (params.isEmpty()) {
            javaClass.getAnnotationsByType(Param::class.java).forEach { param ->
                params.add(param.toMetadata())
            }
        }

        return params
    }

    private fun Param.toMetadata() = ParamMetadata(
        name = name,
        description = description,
        type = type,
        required = required,
        enumValues = enumValues.toList()
    )
}
