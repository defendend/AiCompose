package org.example.agent

import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.engine.cio.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.client.request.*
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.*
import kotlinx.serialization.json.Json
import org.example.model.*
import org.example.tools.ToolRegistry
import org.slf4j.LoggerFactory

class Agent(
    private val apiKey: String,
    private val model: String = "deepseek-chat",
    private val baseUrl: String = "https://api.deepseek.com/v1"
) {
    private val logger = LoggerFactory.getLogger(Agent::class.java)

    private val client = HttpClient(CIO) {
        install(ContentNegotiation) {
            json(Json {
                prettyPrint = true
                isLenient = true
                ignoreUnknownKeys = true
            })
        }
    }

    private val conversations = mutableMapOf<String, MutableList<LLMMessage>>()

    suspend fun chat(userMessage: String, conversationId: String): ChatResponse {
        logger.info("Получен запрос: $userMessage (conversation: $conversationId)")

        // Получаем или создаём историю диалога
        val history = conversations.getOrPut(conversationId) {
            mutableListOf(
                LLMMessage(
                    role = "system",
                    content = """Ты — полезный AI-ассистент. Ты можешь использовать инструменты для выполнения задач.
                        |Доступные инструменты:
                        |- get_current_time: получить текущее время
                        |- calculator: выполнить математические вычисления (операции: add, subtract, multiply, divide, power, sqrt)
                        |- random_number: сгенерировать случайное число
                        |
                        |Если пользователь спрашивает что-то, что требует использования инструмента, используй его.
                        |Отвечай на русском языке.""".trimMargin()
                )
            )
        }

        // Добавляем сообщение пользователя
        history.add(LLMMessage(role = "user", content = userMessage))

        // Вызываем LLM
        val response = callLLM(history)

        // Обрабатываем ответ
        val assistantMessage = response.choices.firstOrNull()?.message
            ?: throw RuntimeException("Пустой ответ от LLM")

        // Проверяем, есть ли вызов инструмента
        if (assistantMessage.tool_calls != null && assistantMessage.tool_calls.isNotEmpty()) {
            logger.info("Агент вызывает инструменты: ${assistantMessage.tool_calls.map { it.function.name }}")

            // Добавляем ответ ассистента с tool_calls
            history.add(assistantMessage)

            // Выполняем каждый инструмент
            for (toolCall in assistantMessage.tool_calls) {
                val toolName = toolCall.function.name
                val toolArgs = toolCall.function.arguments

                logger.info("Выполняю инструмент: $toolName с аргументами: $toolArgs")

                val toolResult = ToolRegistry.executeTool(toolName, toolArgs)

                logger.info("Результат инструмента $toolName: $toolResult")

                // Добавляем результат инструмента в историю
                history.add(
                    LLMMessage(
                        role = "tool",
                        content = toolResult,
                        tool_call_id = toolCall.id
                    )
                )
            }

            // Вызываем LLM ещё раз для получения финального ответа
            val finalResponse = callLLM(history)
            val finalMessage = finalResponse.choices.firstOrNull()?.message
                ?: throw RuntimeException("Пустой финальный ответ от LLM")

            history.add(finalMessage)

            // Возвращаем ответ с информацией о вызове инструмента
            val firstToolCall = assistantMessage.tool_calls.first()
            return ChatResponse(
                message = ChatMessage(
                    role = MessageRole.ASSISTANT,
                    content = finalMessage.content ?: "",
                    toolCall = ToolCall(
                        id = firstToolCall.id,
                        name = firstToolCall.function.name,
                        arguments = firstToolCall.function.arguments
                    )
                ),
                conversationId = conversationId
            )
        } else {
            // Простой ответ без инструментов
            history.add(assistantMessage)

            return ChatResponse(
                message = ChatMessage(
                    role = MessageRole.ASSISTANT,
                    content = assistantMessage.content ?: ""
                ),
                conversationId = conversationId
            )
        }
    }

    private suspend fun callLLM(messages: List<LLMMessage>): LLMResponse {
        logger.debug("Вызов LLM API: ${messages.size} сообщений")

        val request = LLMRequest(
            model = model,
            messages = messages,
            tools = ToolRegistry.getAllTools()
        )

        val response = client.post("$baseUrl/chat/completions") {
            contentType(ContentType.Application.Json)
            header("Authorization", "Bearer $apiKey")
            setBody(request)
        }

        if (!response.status.isSuccess()) {
            val errorBody = response.body<String>()
            logger.error("Ошибка LLM API: ${response.status} - $errorBody")
            throw RuntimeException("Ошибка LLM API: ${response.status}")
        }

        return response.body()
    }

    fun close() {
        client.close()
    }
}
