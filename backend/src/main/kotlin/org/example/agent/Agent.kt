package org.example.agent

import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.engine.cio.*
import io.ktor.client.plugins.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.client.request.*
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.*
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.example.logging.ServerLogger
import org.example.model.*
import org.example.model.ResponseFormat
import org.example.tools.ToolRegistry

class Agent(
    private val apiKey: String,
    private val model: String = "deepseek-chat",
    private val baseUrl: String = "https://api.deepseek.com/v1"
) {
    private val client = HttpClient(CIO) {
        install(ContentNegotiation) {
            json(Json {
                prettyPrint = true
                isLenient = true
                ignoreUnknownKeys = true
            })
        }
        install(HttpTimeout) {
            requestTimeoutMillis = 120000
            connectTimeoutMillis = 30000
            socketTimeoutMillis = 120000
        }
    }

    private val conversations = mutableMapOf<String, MutableList<LLMMessage>>()
    private val conversationFormats = mutableMapOf<String, ResponseFormat>()

    private fun getBaseSystemPrompt(): String = """Ты — профессор Архивариус, увлечённый историк и рассказчик с энциклопедическими знаниями.
        |
        |Твой характер:
        |• Ты обожаешь историю и можешь часами рассказывать увлекательные истории о прошлом
        |• Говоришь живо, с интересными деталями и анекдотами
        |• Любишь проводить параллели между историческими событиями и современностью
        |• Иногда вставляешь латинские выражения или цитаты великих людей
        |
        |Доступные инструменты:
        |- get_historical_events: узнать важные события конкретного года
        |- get_historical_figure: получить биографию исторической личности
        |- compare_eras: сравнить две исторические эпохи
        |- get_historical_quote: найти известную историческую цитату
        |
        |Всегда используй инструменты, когда пользователь спрашивает о конкретных датах, личностях или эпохах.
        |После получения данных от инструмента — дополни их своими интересными комментариями и историями.
        |
        |Отвечай на русском языке, увлекательно и познавательно!""".trimMargin()

    private fun getFormatInstruction(format: ResponseFormat): String = when (format) {
        ResponseFormat.PLAIN -> """
            |
            |Формат ответа: обычный текст. Отвечай простым понятным текстом без специального форматирования.""".trimMargin()

        ResponseFormat.JSON -> """
            |
            |ВАЖНО: Всегда возвращай ответ ТОЛЬКО в следующем JSON формате (без markdown блоков):
            |{
            |  "topic": "краткая тема ответа",
            |  "period": "исторический период или год (если применимо)",
            |  "summary": "краткое резюме в 1-2 предложения",
            |  "main_content": "основной текст ответа с деталями и историями",
            |  "interesting_facts": ["интересный факт 1", "интересный факт 2"],
            |  "related_topics": ["связанная тема 1", "связанная тема 2"],
            |  "quote": "цитата по теме (если есть)"
            |}""".trimMargin()

        ResponseFormat.MARKDOWN -> """
            |
            |Формат ответа: Markdown. Используй заголовки (##), списки (- или 1.), **жирный текст**, *курсив*, > цитаты.
            |Структурируй ответ с заголовками для разных разделов.""".trimMargin()
    }

    private fun getSystemPrompt(format: ResponseFormat): String {
        return getBaseSystemPrompt() + getFormatInstruction(format)
    }

    suspend fun chat(userMessage: String, conversationId: String, format: ResponseFormat = ResponseFormat.PLAIN): ChatResponse {
        // Проверяем, изменился ли формат для этого диалога
        val previousFormat = conversationFormats[conversationId]
        val formatChanged = previousFormat != null && previousFormat != format
        conversationFormats[conversationId] = format

        // Получаем или создаём историю диалога
        val history = conversations.getOrPut(conversationId) {
            mutableListOf(
                LLMMessage(
                    role = "system",
                    content = getSystemPrompt(format)
                )
            )
        }

        // Если формат изменился, обновляем системный промпт
        if (formatChanged && history.isNotEmpty() && history[0].role == "system") {
            history[0] = LLMMessage(
                role = "system",
                content = getSystemPrompt(format)
            )
        }

        // Добавляем сообщение пользователя
        history.add(LLMMessage(role = "user", content = userMessage))

        // Вызываем LLM
        val response = callLLM(history, conversationId)

        // Обрабатываем ответ
        val assistantMessage = response.choices.firstOrNull()?.message
            ?: throw RuntimeException("Пустой ответ от LLM")

        // Проверяем, есть ли вызов инструмента
        if (assistantMessage.tool_calls != null && assistantMessage.tool_calls.isNotEmpty()) {
            // Добавляем ответ ассистента с tool_calls (убеждаемся что type заполнен)
            val fixedToolCalls = assistantMessage.tool_calls.map { tc ->
                LLMToolCall(
                    id = tc.id,
                    type = tc.type ?: "function",
                    function = tc.function
                )
            }
            history.add(assistantMessage.copy(tool_calls = fixedToolCalls))

            // Выполняем каждый инструмент
            for (toolCall in assistantMessage.tool_calls) {
                val toolName = toolCall.function.name
                val toolArgs = toolCall.function.arguments

                ServerLogger.logToolCall(toolName, toolArgs, conversationId)

                val toolStartTime = System.currentTimeMillis()
                val toolResult = ToolRegistry.executeTool(toolName, toolArgs)
                val toolDuration = System.currentTimeMillis() - toolStartTime

                ServerLogger.logToolResult(toolName, toolResult, toolDuration, conversationId)

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
            val finalResponse = callLLM(history, conversationId)
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

    private val jsonPretty = Json {
        prettyPrint = true
        ignoreUnknownKeys = true
    }

    private suspend fun callLLM(messages: List<LLMMessage>, conversationId: String): LLMResponse {
        val tools = ToolRegistry.getAllTools()

        // Формируем превью сообщений для логов
        val messagesPreview = messages.joinToString("\n") { msg ->
            val content = msg.content?.take(200) ?: (msg.tool_calls?.firstOrNull()?.let { "tool_call: ${it.function.name}" } ?: "")
            "[${msg.role}] $content"
        }

        ServerLogger.logLLMRequest(model, messages.size, tools.size, conversationId, messagesPreview)

        val request = LLMRequest(
            model = model,
            messages = messages,
            tools = tools
        )

        // Логируем полный JSON запрос
        val requestJson = jsonPretty.encodeToString(LLMRequest.serializer(), request)
        ServerLogger.logLLMRawRequest(requestJson, conversationId)

        val startTime = System.currentTimeMillis()

        val response = client.post("$baseUrl/chat/completions") {
            contentType(ContentType.Application.Json)
            header("Authorization", "Bearer $apiKey")
            setBody(request)
        }

        val duration = System.currentTimeMillis() - startTime

        // Получаем сырой ответ как строку
        val rawResponseBody = response.body<String>()

        if (!response.status.isSuccess()) {
            ServerLogger.logError("LLM API error: ${response.status} - $rawResponseBody", null, LogCategory.LLM_RESPONSE)
            throw RuntimeException("Ошибка LLM API: ${response.status}")
        }

        // Логируем полный JSON ответ
        ServerLogger.logLLMRawResponse(rawResponseBody, duration, conversationId)

        // Парсим ответ
        val llmResponse: LLMResponse = Json { ignoreUnknownKeys = true }.decodeFromString(rawResponseBody)
        val hasToolCalls = llmResponse.choices.firstOrNull()?.message?.tool_calls?.isNotEmpty() == true
        val content = llmResponse.choices.firstOrNull()?.message?.content

        ServerLogger.logLLMResponse(model, hasToolCalls, content, duration, conversationId)

        return llmResponse
    }

    fun close() {
        client.close()
    }
}
