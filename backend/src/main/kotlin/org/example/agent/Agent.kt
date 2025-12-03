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
    private val conversationCollectionSettings = mutableMapOf<String, CollectionSettings>()

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

    /**
     * Генерирует инструкции для режима сбора данных
     */
    private fun getCollectionModeInstruction(settings: CollectionSettings?): String {
        if (settings == null || !settings.enabled || settings.mode == CollectionMode.NONE) {
            return ""
        }

        val baseInstruction = """
            |
            |=== РЕЖИМ СБОРА ДАННЫХ ===
            |Ты работаешь в режиме сбора информации для документа: "${settings.resultTitle}".
            |
            |ТВОЯ ЗАДАЧА:
            |1. Задавай уточняющие вопросы пользователю, чтобы собрать всю необходимую информацию
            |2. Отслеживай, какие данные уже собраны, а какие ещё нужны
            |3. После каждого ответа пользователя анализируй, достаточно ли информации
            |4. Когда ВСЯ необходимая информация собрана — АВТОМАТИЧЕСКИ сформируй финальный документ
            |
            |ФОРМАТ РАБОТЫ:
            |• В начале диалога представься и объясни, что будешь собирать информацию
            |• После каждого ответа пользователя кратко подтверждай, что понял, и задавай следующий вопрос
            |• В конце каждого сообщения показывай прогресс: "Собрано: X из Y пунктов"
            |• Когда всё собрано, напиши "=== ГОТОВЫЙ ДОКУМЕНТ ===" и выведи структурированный результат
            |""".trimMargin()

        val modeSpecificInstruction = when (settings.mode) {
            CollectionMode.TECHNICAL_SPEC -> """
                |
                |СОБЕРИ СЛЕДУЮЩУЮ ИНФОРМАЦИЮ ДЛЯ ТЗ:
                |1. Цель проекта — какую проблему решает?
                |2. Целевая аудитория — кто будет использовать?
                |3. Функциональные требования — что система должна делать?
                |4. Нефункциональные требования — производительность, безопасность, масштабируемость
                |5. Технологический стек — какие технологии предпочтительны?
                |6. Ограничения — бюджет, сроки, технические ограничения
                |7. Критерии приёмки — как определить, что проект готов?
                |
                |ФОРМАТ ИТОГОВОГО ТЗ:
                |# Техническое задание: [Название проекта]
                |
                |## 1. Введение
                |### 1.1 Цель проекта
                |### 1.2 Целевая аудитория
                |
                |## 2. Функциональные требования
                |[Список требований]
                |
                |## 3. Нефункциональные требования
                |[Производительность, безопасность, etc.]
                |
                |## 4. Технологии
                |[Стек технологий]
                |
                |## 5. Ограничения
                |[Сроки, бюджет, зависимости]
                |
                |## 6. Критерии приёмки
                |[Checklist]
                |""".trimMargin()

            CollectionMode.DESIGN_BRIEF -> """
                |
                |СОБЕРИ СЛЕДУЮЩУЮ ИНФОРМАЦИЮ ДЛЯ ДИЗАЙН-БРИФА:
                |1. Название проекта/бренда
                |2. Описание продукта или услуги
                |3. Целевая аудитория — демография, интересы
                |4. Ключевое сообщение — что должен чувствовать пользователь?
                |5. Стилевые предпочтения — современный/классический, минимализм/максимализм
                |6. Цветовые предпочтения — есть ли брендбук?
                |7. Референсы — примеры дизайна, которые нравятся
                |8. Ограничения — что точно НЕ должно быть?
                |
                |ФОРМАТ ИТОГОВОГО БРИФА:
                |# Дизайн-бриф: [Название]
                |
                |## О проекте
                |[Описание]
                |
                |## Целевая аудитория
                |[Портрет пользователя]
                |
                |## Стиль и настроение
                |[Описание желаемого стиля]
                |
                |## Цветовая палитра
                |[Предпочтения по цветам]
                |
                |## Референсы
                |[Ссылки или описания]
                |
                |## Ограничения
                |[Что избегать]
                |""".trimMargin()

            CollectionMode.PROJECT_SUMMARY -> """
                |
                |СОБЕРИ СЛЕДУЮЩУЮ ИНФОРМАЦИЮ ДЛЯ РЕЗЮМЕ ПРОЕКТА:
                |1. Название проекта
                |2. Проблема — какую боль решает?
                |3. Решение — как именно решает?
                |4. Уникальность — чем отличается от конкурентов?
                |5. Целевой рынок — кто платит?
                |6. Бизнес-модель — как зарабатывает?
                |7. Текущий статус — что уже сделано?
                |8. Планы — что дальше?
                |
                |ФОРМАТ ИТОГОВОГО РЕЗЮМЕ:
                |# [Название проекта]
                |
                |## Проблема
                |[Описание проблемы]
                |
                |## Решение
                |[Как решаем]
                |
                |## Преимущества
                |• [Преимущество 1]
                |• [Преимущество 2]
                |
                |## Рынок
                |[Целевая аудитория и размер рынка]
                |
                |## Статус
                |[Текущее состояние]
                |
                |## Дорожная карта
                |[Планы развития]
                |""".trimMargin()

            CollectionMode.CUSTOM -> """
                |
                |ПОЛЬЗОВАТЕЛЬСКИЕ ИНСТРУКЦИИ:
                |${settings.customPrompt}
                |
                |Собери всю необходимую информацию согласно инструкциям выше,
                |и сформируй структурированный документ "${settings.resultTitle}".
                |""".trimMargin()

            CollectionMode.NONE -> ""
        }

        return baseInstruction + modeSpecificInstruction
    }

    private fun getSystemPrompt(format: ResponseFormat, collectionSettings: CollectionSettings? = null): String {
        return getBaseSystemPrompt() + getFormatInstruction(format) + getCollectionModeInstruction(collectionSettings)
    }

    suspend fun chat(
        userMessage: String,
        conversationId: String,
        format: ResponseFormat = ResponseFormat.PLAIN,
        collectionSettings: CollectionSettings? = null
    ): ChatResponse {
        // Проверяем, изменился ли формат или настройки сбора для этого диалога
        val previousFormat = conversationFormats[conversationId]
        val previousCollectionSettings = conversationCollectionSettings[conversationId]
        val formatChanged = previousFormat != null && previousFormat != format
        val collectionSettingsChanged = previousCollectionSettings != collectionSettings

        conversationFormats[conversationId] = format
        if (collectionSettings != null) {
            conversationCollectionSettings[conversationId] = collectionSettings
        }

        // Получаем или создаём историю диалога
        val history = conversations.getOrPut(conversationId) {
            mutableListOf(
                LLMMessage(
                    role = "system",
                    content = getSystemPrompt(format, collectionSettings)
                )
            )
        }

        // Если формат или настройки изменились, обновляем системный промпт
        if ((formatChanged || collectionSettingsChanged) && history.isNotEmpty() && history[0].role == "system") {
            history[0] = LLMMessage(
                role = "system",
                content = getSystemPrompt(format, collectionSettings)
            )
        }

        // Добавляем сообщение пользователя
        history.add(LLMMessage(role = "user", content = userMessage))

        // Цикл обработки tool calls (максимум 5 итераций для защиты от бесконечного цикла)
        var currentResponse = callLLM(history, conversationId)
        var currentMessage = currentResponse.choices.firstOrNull()?.message
            ?: throw RuntimeException("Пустой ответ от LLM")

        var firstToolCall: LLMToolCall? = null
        var iterations = 0
        val maxIterations = 5

        while (currentMessage.tool_calls != null && currentMessage.tool_calls.isNotEmpty() && iterations < maxIterations) {
            iterations++

            // Сохраняем первый tool call для отображения в UI
            if (firstToolCall == null) {
                firstToolCall = currentMessage.tool_calls.first()
            }

            // Добавляем ответ ассистента с tool_calls (убеждаемся что type заполнен)
            val fixedToolCalls = currentMessage.tool_calls.map { tc ->
                LLMToolCall(
                    id = tc.id,
                    type = tc.type ?: "function",
                    function = tc.function
                )
            }
            history.add(currentMessage.copy(tool_calls = fixedToolCalls))

            // Выполняем каждый инструмент
            for (toolCall in currentMessage.tool_calls) {
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

            // Вызываем LLM ещё раз
            currentResponse = callLLM(history, conversationId)
            currentMessage = currentResponse.choices.firstOrNull()?.message
                ?: throw RuntimeException("Пустой ответ от LLM")
        }

        // Добавляем финальное сообщение в историю
        history.add(currentMessage)

        // Возвращаем ответ
        return ChatResponse(
            message = ChatMessage(
                role = MessageRole.ASSISTANT,
                content = currentMessage.content ?: "",
                toolCall = firstToolCall?.let { tc ->
                    ToolCall(
                        id = tc.id,
                        name = tc.function.name,
                        arguments = tc.function.arguments
                    )
                }
            ),
            conversationId = conversationId
        )
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
