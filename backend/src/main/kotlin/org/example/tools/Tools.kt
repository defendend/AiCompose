package org.example.tools

import kotlinx.serialization.json.*
import org.example.model.FunctionDefinition
import org.example.model.FunctionParameters
import org.example.model.PropertyDefinition
import org.example.model.Tool
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import kotlin.math.pow

/**
 * Интерфейс для инструментов агента
 */
interface AgentTool {
    val name: String
    val description: String
    fun getDefinition(): Tool
    suspend fun execute(arguments: String): String
}

/**
 * Инструмент для получения текущего времени
 */
object GetCurrentTimeTool : AgentTool {
    override val name = "get_current_time"
    override val description = "Получает текущее время и дату"

    override fun getDefinition(): Tool = Tool(
        function = FunctionDefinition(
            name = name,
            description = description,
            parameters = FunctionParameters(
                properties = mapOf(
                    "timezone" to PropertyDefinition(
                        type = "string",
                        description = "Часовой пояс (например, 'Europe/Moscow'). По умолчанию UTC."
                    )
                )
            )
        )
    )

    override suspend fun execute(arguments: String): String {
        val now = LocalDateTime.now()
        val formatter = DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm:ss")
        return "Текущее время: ${now.format(formatter)}"
    }
}

/**
 * Калькулятор
 */
object CalculatorTool : AgentTool {
    override val name = "calculator"
    override val description = "Выполняет математические вычисления"

    override fun getDefinition(): Tool = Tool(
        function = FunctionDefinition(
            name = name,
            description = description,
            parameters = FunctionParameters(
                properties = mapOf(
                    "operation" to PropertyDefinition(
                        type = "string",
                        description = "Операция: add, subtract, multiply, divide, power, sqrt"
                    ),
                    "a" to PropertyDefinition(
                        type = "number",
                        description = "Первое число"
                    ),
                    "b" to PropertyDefinition(
                        type = "number",
                        description = "Второе число (не требуется для sqrt)"
                    )
                ),
                required = listOf("operation", "a")
            )
        )
    )

    override suspend fun execute(arguments: String): String {
        return try {
            val json = Json.parseToJsonElement(arguments).jsonObject
            val operation = json["operation"]?.jsonPrimitive?.content ?: return "Ошибка: не указана операция"
            val a = json["a"]?.jsonPrimitive?.double ?: return "Ошибка: не указано число a"
            val b = json["b"]?.jsonPrimitive?.doubleOrNull

            val result = when (operation) {
                "add" -> a + (b ?: 0.0)
                "subtract" -> a - (b ?: 0.0)
                "multiply" -> a * (b ?: 1.0)
                "divide" -> {
                    if (b == null || b == 0.0) return "Ошибка: деление на ноль"
                    a / b
                }
                "power" -> a.pow(b ?: 2.0)
                "sqrt" -> kotlin.math.sqrt(a)
                else -> return "Ошибка: неизвестная операция '$operation'"
            }

            "Результат: $result"
        } catch (e: Exception) {
            "Ошибка вычисления: ${e.message}"
        }
    }
}

/**
 * Генератор случайных чисел
 */
object RandomNumberTool : AgentTool {
    override val name = "random_number"
    override val description = "Генерирует случайное число в заданном диапазоне"

    override fun getDefinition(): Tool = Tool(
        function = FunctionDefinition(
            name = name,
            description = description,
            parameters = FunctionParameters(
                properties = mapOf(
                    "min" to PropertyDefinition(
                        type = "integer",
                        description = "Минимальное значение (по умолчанию 1)"
                    ),
                    "max" to PropertyDefinition(
                        type = "integer",
                        description = "Максимальное значение (по умолчанию 100)"
                    )
                )
            )
        )
    )

    override suspend fun execute(arguments: String): String {
        return try {
            val json = Json.parseToJsonElement(arguments).jsonObject
            val min = json["min"]?.jsonPrimitive?.intOrNull ?: 1
            val max = json["max"]?.jsonPrimitive?.intOrNull ?: 100

            if (min > max) return "Ошибка: min не может быть больше max"

            val result = (min..max).random()
            "Случайное число от $min до $max: $result"
        } catch (e: Exception) {
            "Ошибка: ${e.message}"
        }
    }
}

/**
 * Реестр всех доступных инструментов
 */
object ToolRegistry {
    private val tools: Map<String, AgentTool> = listOf(
        GetCurrentTimeTool,
        CalculatorTool,
        RandomNumberTool
    ).associateBy { it.name }

    fun getAllTools(): List<Tool> = tools.values.map { it.getDefinition() }

    fun getTool(name: String): AgentTool? = tools[name]

    suspend fun executeTool(name: String, arguments: String): String {
        val tool = getTool(name) ?: return "Ошибка: инструмент '$name' не найден"
        return tool.execute(arguments)
    }
}
