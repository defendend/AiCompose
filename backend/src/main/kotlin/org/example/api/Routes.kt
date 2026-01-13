package org.example.api

import io.ktor.http.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import kotlinx.coroutines.flow.collect
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.example.agent.Agent
import org.example.data.ConversationRepository
import org.example.data.LLMClient
import org.example.data.RedisConversationRepository
import org.example.demo.TokenCounterDemo
import org.example.logging.ServerLogger
import org.example.model.LogCategory
import org.example.model.LogLevel
import org.example.model.LLMMessage
import org.example.shared.model.ChatMessage
import org.example.shared.model.ChatRequest
import org.example.shared.model.ChatStreamRequest
import org.example.shared.model.ConversationDetailResponse
import org.example.shared.model.ConversationListResponse
import org.example.shared.model.CreateConversationRequest
import org.example.shared.model.HealthCheckResponse
import org.example.shared.model.ImportConversationRequest
import org.example.shared.model.MessageRole
import org.example.shared.model.RenameConversationRequest
import org.example.shared.model.SearchResponse
import org.example.shared.model.ServiceHealth
import java.util.UUID

private val json = Json { prettyPrint = true }
private val jsonCompact = Json { prettyPrint = false }

fun Route.chatRoutes(
    agent: Agent,
    llmClient: LLMClient? = null,
    conversationRepository: ConversationRepository? = null,
    reminderRepository: org.example.data.ReminderRepository? = null
) {
    route("/api") {
        post("/chat") {
            val startTime = System.currentTimeMillis()
            var requestId = ""

            try {
                val bodyText = call.receiveText()
                val request = Json.decodeFromString<ChatRequest>(bodyText)

                val conversationId = request.conversationId ?: UUID.randomUUID().toString()

                requestId = ServerLogger.logRequest(
                    method = "POST",
                    path = "/api/chat",
                    body = bodyText,
                    conversationId = conversationId
                )

                val response = agent.chat(
                    userMessage = request.message,
                    conversationId = conversationId,
                    format = request.responseFormat,
                    collectionSettings = request.collectionSettings,
                    temperature = request.temperature,
                    compressionSettings = request.compressionSettings
                )
                val responseBody = json.encodeToString(response)
                val duration = System.currentTimeMillis() - startTime

                ServerLogger.logResponse(
                    requestId = requestId,
                    statusCode = 200,
                    body = responseBody,
                    durationMs = duration
                )

                call.respond(HttpStatusCode.OK, response)
            } catch (e: Exception) {
                val duration = System.currentTimeMillis() - startTime
                val errorResponse = mapOf("error" to (e.message ?: "Неизвестная ошибка"))

                ServerLogger.logError(
                    message = "Ошибка обработки запроса: ${e.message}",
                    error = e,
                    category = LogCategory.REQUEST
                )

                if (requestId.isNotEmpty()) {
                    ServerLogger.logResponse(
                        requestId = requestId,
                        statusCode = 500,
                        body = json.encodeToString(errorResponse),
                        durationMs = duration
                    )
                }

                call.respond(HttpStatusCode.InternalServerError, errorResponse)
            }
        }

        // Streaming endpoint
        post("/chat/stream") {
            val startTime = System.currentTimeMillis()

            try {
                val bodyText = call.receiveText()
                val request = Json.decodeFromString<ChatStreamRequest>(bodyText)
                val conversationId = request.conversationId ?: UUID.randomUUID().toString()

                ServerLogger.logRequest(
                    method = "POST",
                    path = "/api/chat/stream",
                    body = bodyText,
                    conversationId = conversationId
                )

                call.response.header(HttpHeaders.ContentType, ContentType.Text.EventStream.toString())
                call.response.header(HttpHeaders.CacheControl, "no-cache")
                call.response.header(HttpHeaders.Connection, "keep-alive")

                call.respondTextWriter(contentType = ContentType.Text.EventStream) {
                    agent.chatStream(
                        userMessage = request.message,
                        conversationId = conversationId,
                        format = request.responseFormat,
                        collectionSettings = request.collectionSettings,
                        temperature = request.temperature
                    ).collect { event ->
                        val eventJson = jsonCompact.encodeToString(event)
                        write("data: $eventJson\n\n")
                        flush()
                    }
                }

                val duration = System.currentTimeMillis() - startTime
                ServerLogger.log(
                    level = LogLevel.INFO,
                    message = "Stream completed in ${duration}ms",
                    category = LogCategory.RESPONSE,
                    conversationId = conversationId
                )

            } catch (e: Exception) {
                ServerLogger.logError(
                    message = "Ошибка streaming: ${e.message}",
                    error = e,
                    category = LogCategory.REQUEST
                )
                call.respond(HttpStatusCode.InternalServerError, mapOf("error" to (e.message ?: "Ошибка streaming")))
            }
        }

        // Простой health check (обратная совместимость)
        get("/health") {
            call.respond(HttpStatusCode.OK, mapOf("status" to "ok"))
        }

        // Расширенный health check с проверкой сервисов
        get("/health/detailed") {
            val services = mutableMapOf<String, ServiceHealth>()
            var overallHealthy = true

            // Проверка LLM API
            if (llmClient != null) {
                val startTime = System.currentTimeMillis()
                val llmHealthy = try {
                    llmClient.healthCheck()
                } catch (e: Exception) {
                    false
                }
                val latency = System.currentTimeMillis() - startTime

                services["llm"] = ServiceHealth(
                    status = if (llmHealthy) "healthy" else "unhealthy",
                    message = if (llmHealthy) "DeepSeek API доступен" else "DeepSeek API недоступен",
                    latencyMs = latency
                )
                if (!llmHealthy) overallHealthy = false
            }

            // Проверка Redis (если используется)
            if (conversationRepository is RedisConversationRepository) {
                val startTime = System.currentTimeMillis()
                val redisHealthy = try {
                    // Проверяем Redis через простую операцию
                    conversationRepository.hasConversation("health-check-ping")
                    true
                } catch (e: Exception) {
                    false
                }
                val latency = System.currentTimeMillis() - startTime

                services["redis"] = ServiceHealth(
                    status = if (redisHealthy) "healthy" else "unhealthy",
                    message = if (redisHealthy) "Redis доступен" else "Redis недоступен",
                    latencyMs = latency
                )
                if (!redisHealthy) overallHealthy = false
            } else {
                services["storage"] = ServiceHealth(
                    status = "healthy",
                    message = "In-Memory storage",
                    latencyMs = 0
                )
            }

            val response = HealthCheckResponse(
                status = if (overallHealthy) "healthy" else "degraded",
                services = services,
                timestamp = System.currentTimeMillis()
            )

            val statusCode = if (overallHealthy) HttpStatusCode.OK else HttpStatusCode.ServiceUnavailable
            call.respond(statusCode, response)
        }

        get("/logs") {
            try {
                val limit = call.request.queryParameters["limit"]?.toIntOrNull() ?: 100
                val offset = call.request.queryParameters["offset"]?.toIntOrNull() ?: 0
                val levelParam = call.request.queryParameters["level"]
                val categoryParam = call.request.queryParameters["category"]

                val level = levelParam?.let {
                    try { LogLevel.valueOf(it.uppercase()) } catch (e: Exception) { null }
                }
                val category = categoryParam?.let {
                    try { LogCategory.valueOf(it.uppercase()) } catch (e: Exception) { null }
                }

                val logsResponse = ServerLogger.getLogs(
                    limit = limit,
                    offset = offset,
                    level = level,
                    category = category
                )

                call.respond(HttpStatusCode.OK, logsResponse)
            } catch (e: Exception) {
                ServerLogger.logError("Ошибка получения логов", e)
                call.respond(
                    HttpStatusCode.InternalServerError,
                    mapOf("error" to (e.message ?: "Ошибка получения логов"))
                )
            }
        }

        delete("/logs") {
            ServerLogger.clear()
            call.respond(HttpStatusCode.OK, mapOf("status" to "cleared"))
        }

        // === Демонстрация подсчёта токенов ===

        /**
         * Запустить полное сравнение токенов (короткий, средний, длинный, превышающий лимит).
         * GET /api/tokens/demo
         */
        get("/tokens/demo") {
            if (llmClient == null) {
                call.respond(
                    HttpStatusCode.ServiceUnavailable,
                    mapOf("error" to "LLM клиент не настроен")
                )
                return@get
            }

            try {
                val demo = TokenCounterDemo(llmClient)
                val result = demo.runComparison()
                call.respond(HttpStatusCode.OK, result)
            } catch (e: Exception) {
                ServerLogger.logError("Ошибка запуска демо токенов", e)
                call.respond(
                    HttpStatusCode.InternalServerError,
                    mapOf("error" to (e.message ?: "Ошибка демо"))
                )
            }
        }

        /**
         * Подсчитать токены для произвольного текста.
         * POST /api/tokens/count
         * Body: { "text": "ваш текст", "sendToApi": false }
         */
        post("/tokens/count") {
            if (llmClient == null) {
                call.respond(
                    HttpStatusCode.ServiceUnavailable,
                    mapOf("error" to "LLM клиент не настроен")
                )
                return@post
            }

            try {
                val request = call.receive<TokenCountRequest>()
                val demo = TokenCounterDemo(llmClient)

                if (request.sendToApi) {
                    // Отправить в API для получения реального количества токенов
                    val result = demo.runTest(
                        testName = "custom",
                        prompt = request.text,
                        notes = "Пользовательский запрос"
                    )
                    call.respond(HttpStatusCode.OK, result)
                } else {
                    // Только оценка без отправки в API
                    val estimated = demo.estimateTokens(request.text)
                    call.respond(HttpStatusCode.OK, TokenEstimate(
                        text = request.text.take(100) + if (request.text.length > 100) "..." else "",
                        length = request.text.length,
                        estimatedTokens = estimated,
                        note = "Оценка: ~${TokenCounterDemo.AVG_CHARS_PER_TOKEN_RU} символа/токен для русского, ~${TokenCounterDemo.AVG_CHARS_PER_TOKEN_EN} для английского"
                    ))
                }
            } catch (e: Exception) {
                ServerLogger.logError("Ошибка подсчёта токенов", e)
                call.respond(
                    HttpStatusCode.InternalServerError,
                    mapOf("error" to (e.message ?: "Ошибка подсчёта"))
                )
            }
        }

        /**
         * Получить информацию о лимитах модели.
         * GET /api/tokens/limits
         */
        get("/tokens/limits") {
            call.respond(HttpStatusCode.OK, mapOf(
                "model" to "deepseek-chat",
                "maxContextTokens" to TokenCounterDemo.MAX_CONTEXT_TOKENS,
                "maxOutputTokens" to TokenCounterDemo.MAX_OUTPUT_TOKENS,
                "defaultMaxOutput" to TokenCounterDemo.DEFAULT_MAX_OUTPUT,
                "avgCharsPerTokenRu" to TokenCounterDemo.AVG_CHARS_PER_TOKEN_RU,
                "avgCharsPerTokenEn" to TokenCounterDemo.AVG_CHARS_PER_TOKEN_EN,
                "notes" to listOf(
                    "DeepSeek deepseek-chat поддерживает до 64K токенов контекста",
                    "Максимум выходных токенов: 8K (по умолчанию 4K)",
                    "Русский текст занимает больше токенов чем английский",
                    "Системный промпт и история диалога также считаются в контекст"
                )
            ))
        }

        // === Управление чатами (Conversations) ===

        /**
         * Получить список всех диалогов.
         * GET /api/conversations
         */
        get("/conversations") {
            if (conversationRepository == null) {
                call.respond(HttpStatusCode.ServiceUnavailable, mapOf("error" to "Storage не настроен"))
                return@get
            }

            try {
                val conversations = conversationRepository.listConversations()
                val response = ConversationListResponse(
                    conversations = conversations,
                    totalCount = conversations.size
                )
                call.respond(HttpStatusCode.OK, response)
            } catch (e: Exception) {
                ServerLogger.logError("Ошибка получения списка диалогов", e)
                call.respond(HttpStatusCode.InternalServerError, mapOf("error" to (e.message ?: "Ошибка")))
            }
        }

        /**
         * Получить детали диалога с историей сообщений.
         * GET /api/conversations/{id}
         */
        get("/conversations/{id}") {
            if (conversationRepository == null) {
                call.respond(HttpStatusCode.ServiceUnavailable, mapOf("error" to "Storage не настроен"))
                return@get
            }

            val conversationId = call.parameters["id"]
            if (conversationId == null) {
                call.respond(HttpStatusCode.BadRequest, mapOf("error" to "ID диалога не указан"))
                return@get
            }

            try {
                val info = conversationRepository.getConversationInfo(conversationId)
                if (info == null) {
                    call.respond(HttpStatusCode.NotFound, mapOf("error" to "Диалог не найден"))
                    return@get
                }

                val history = conversationRepository.getHistory(conversationId)
                val format = conversationRepository.getFormat(conversationId)

                val messages = history.mapIndexed { index, msg ->
                    ChatMessage(
                        id = "$conversationId-$index",
                        role = when (msg.role) {
                            "user" -> MessageRole.USER
                            "assistant" -> MessageRole.ASSISTANT
                            "system" -> MessageRole.SYSTEM
                            else -> MessageRole.USER
                        },
                        content = msg.content ?: "",
                        timestamp = info.updatedAt
                    )
                }

                val response = ConversationDetailResponse(
                    id = info.id,
                    title = info.title,
                    messages = messages,
                    responseFormat = format ?: org.example.shared.model.ResponseFormat.PLAIN,
                    createdAt = info.createdAt,
                    updatedAt = info.updatedAt
                )
                call.respond(HttpStatusCode.OK, response)
            } catch (e: Exception) {
                ServerLogger.logError("Ошибка получения диалога $conversationId", e)
                call.respond(HttpStatusCode.InternalServerError, mapOf("error" to (e.message ?: "Ошибка")))
            }
        }

        /**
         * Создать новый диалог.
         * POST /api/conversations
         * Body: { "title": "Название" } (опционально)
         */
        post("/conversations") {
            if (conversationRepository == null) {
                call.respond(HttpStatusCode.ServiceUnavailable, mapOf("error" to "Storage не настроен"))
                return@post
            }

            try {
                val request = try {
                    call.receive<CreateConversationRequest>()
                } catch (e: Exception) {
                    CreateConversationRequest()
                }

                val conversationId = conversationRepository.createConversation(request.title)
                val info = conversationRepository.getConversationInfo(conversationId)

                if (info != null) {
                    call.respond(HttpStatusCode.Created, info)
                } else {
                    call.respond(HttpStatusCode.Created, mapOf("id" to conversationId))
                }
            } catch (e: Exception) {
                ServerLogger.logError("Ошибка создания диалога", e)
                call.respond(HttpStatusCode.InternalServerError, mapOf("error" to (e.message ?: "Ошибка")))
            }
        }

        /**
         * Удалить диалог.
         * DELETE /api/conversations/{id}
         */
        delete("/conversations/{id}") {
            if (conversationRepository == null) {
                call.respond(HttpStatusCode.ServiceUnavailable, mapOf("error" to "Storage не настроен"))
                return@delete
            }

            val conversationId = call.parameters["id"]
            if (conversationId == null) {
                call.respond(HttpStatusCode.BadRequest, mapOf("error" to "ID диалога не указан"))
                return@delete
            }

            try {
                if (!conversationRepository.hasConversation(conversationId)) {
                    call.respond(HttpStatusCode.NotFound, mapOf("error" to "Диалог не найден"))
                    return@delete
                }

                conversationRepository.deleteConversation(conversationId)
                call.respond(HttpStatusCode.OK, mapOf("status" to "deleted", "id" to conversationId))
            } catch (e: Exception) {
                ServerLogger.logError("Ошибка удаления диалога $conversationId", e)
                call.respond(HttpStatusCode.InternalServerError, mapOf("error" to (e.message ?: "Ошибка")))
            }
        }

        /**
         * Переименовать диалог.
         * PATCH /api/conversations/{id}
         * Body: { "title": "Новое название" }
         */
        patch("/conversations/{id}") {
            if (conversationRepository == null) {
                call.respond(HttpStatusCode.ServiceUnavailable, mapOf("error" to "Storage не настроен"))
                return@patch
            }

            val conversationId = call.parameters["id"]
            if (conversationId == null) {
                call.respond(HttpStatusCode.BadRequest, mapOf("error" to "ID диалога не указан"))
                return@patch
            }

            try {
                val request = call.receive<RenameConversationRequest>()

                if (!conversationRepository.hasConversation(conversationId)) {
                    call.respond(HttpStatusCode.NotFound, mapOf("error" to "Диалог не найден"))
                    return@patch
                }

                conversationRepository.renameConversation(conversationId, request.title)
                val info = conversationRepository.getConversationInfo(conversationId)

                if (info != null) {
                    call.respond(HttpStatusCode.OK, info)
                } else {
                    call.respond(HttpStatusCode.OK, mapOf("status" to "renamed", "id" to conversationId))
                }
            } catch (e: Exception) {
                ServerLogger.logError("Ошибка переименования диалога $conversationId", e)
                call.respond(HttpStatusCode.InternalServerError, mapOf("error" to (e.message ?: "Ошибка")))
            }
        }

        /**
         * Поиск по сообщениям.
         * GET /api/conversations/search?q=запрос
         */
        get("/conversations/search") {
            if (conversationRepository == null) {
                call.respond(HttpStatusCode.ServiceUnavailable, mapOf("error" to "Storage не настроен"))
                return@get
            }

            val query = call.request.queryParameters["q"]
            if (query.isNullOrBlank()) {
                call.respond(HttpStatusCode.BadRequest, mapOf("error" to "Параметр q обязателен"))
                return@get
            }

            try {
                val results = conversationRepository.searchMessages(query)
                val response = SearchResponse(
                    results = results,
                    totalCount = results.size,
                    query = query
                )
                call.respond(HttpStatusCode.OK, response)
            } catch (e: Exception) {
                ServerLogger.logError("Ошибка поиска по сообщениям", e)
                call.respond(HttpStatusCode.InternalServerError, mapOf("error" to (e.message ?: "Ошибка")))
            }
        }

        /**
         * Экспорт диалога.
         * GET /api/conversations/{id}/export?format=json|markdown
         */
        get("/conversations/{id}/export") {
            if (conversationRepository == null) {
                call.respond(HttpStatusCode.ServiceUnavailable, mapOf("error" to "Storage не настроен"))
                return@get
            }

            val conversationId = call.parameters["id"]
            if (conversationId == null) {
                call.respond(HttpStatusCode.BadRequest, mapOf("error" to "ID диалога не указан"))
                return@get
            }

            val format = call.request.queryParameters["format"] ?: "json"

            try {
                val export = conversationRepository.exportConversation(conversationId)
                if (export == null) {
                    call.respond(HttpStatusCode.NotFound, mapOf("error" to "Диалог не найден"))
                    return@get
                }

                when (format.lowercase()) {
                    "markdown" -> {
                        val markdown = buildString {
                            appendLine("# ${export.title}")
                            appendLine()
                            appendLine("*Экспортировано: ${java.time.Instant.ofEpochMilli(export.exportedAt)}*")
                            appendLine()
                            appendLine("---")
                            appendLine()

                            for (msg in export.messages) {
                                val roleLabel = when (msg.role) {
                                    "user" -> "**Пользователь**"
                                    "assistant" -> "**Ассистент**"
                                    "system" -> "*Система*"
                                    else -> "**${msg.role}**"
                                }
                                appendLine("### $roleLabel")
                                appendLine()
                                appendLine(msg.content ?: "")
                                appendLine()
                            }
                        }

                        call.response.header(
                            HttpHeaders.ContentDisposition,
                            "attachment; filename=\"${export.title.replace(" ", "_")}.md\""
                        )
                        call.respondText(markdown, ContentType.Text.Plain)
                    }
                    else -> {
                        call.response.header(
                            HttpHeaders.ContentDisposition,
                            "attachment; filename=\"${export.title.replace(" ", "_")}.json\""
                        )
                        call.respond(HttpStatusCode.OK, export)
                    }
                }
            } catch (e: Exception) {
                ServerLogger.logError("Ошибка экспорта диалога $conversationId", e)
                call.respond(HttpStatusCode.InternalServerError, mapOf("error" to (e.message ?: "Ошибка")))
            }
        }

        /**
         * Импорт диалога.
         * POST /api/conversations/import
         * Body: { "export": {...} }
         */
        post("/conversations/import") {
            if (conversationRepository == null) {
                call.respond(HttpStatusCode.ServiceUnavailable, mapOf("error" to "Storage не настроен"))
                return@post
            }

            try {
                val request = call.receive<ImportConversationRequest>()
                val newId = conversationRepository.importConversation(request.export)
                val info = conversationRepository.getConversationInfo(newId)

                if (info != null) {
                    call.respond(HttpStatusCode.Created, info)
                } else {
                    call.respond(HttpStatusCode.Created, mapOf("id" to newId))
                }
            } catch (e: Exception) {
                ServerLogger.logError("Ошибка импорта диалога", e)
                call.respond(HttpStatusCode.InternalServerError, mapOf("error" to (e.message ?: "Ошибка")))
            }
        }

        /**
         * Получить уведомления о напоминаниях (для Desktop polling).
         * GET /api/reminders/notifications?limit=10
         */
        get("/reminders/notifications") {
            if (reminderRepository == null) {
                call.respond(HttpStatusCode.ServiceUnavailable, mapOf("error" to "ReminderRepository не настроен"))
                return@get
            }

            try {
                val limit = call.request.queryParameters["limit"]?.toIntOrNull() ?: 10
                val reminders = reminderRepository.getNotifications(limit)
                val dtos = reminders.map { reminder ->
                    org.example.model.ReminderNotificationDto(
                        id = reminder.id,
                        title = reminder.title,
                        description = reminder.description,
                        reminderTime = reminder.reminderTime.toString(),
                        notified = reminder.notified
                    )
                }

                val response = org.example.model.ReminderNotificationsResponse(
                    notifications = dtos,
                    count = dtos.size
                )

                call.respond(HttpStatusCode.OK, response)
            } catch (e: Exception) {
                ServerLogger.logError("Ошибка получения уведомлений", e)
                call.respond(HttpStatusCode.InternalServerError, mapOf("error" to (e.message ?: "Ошибка")))
            }
        }

        /**
         * Автоматическое ревью Pull Request.
         * POST /api/review
         */
        post("/review") {
            val startTime = System.currentTimeMillis()

            try {
                val request = call.receive<CodeReviewRequest>()

                ServerLogger.log(
                    level = LogLevel.INFO,
                    message = "Code Review запрос: ${request.owner}/${request.repo}#${request.prNumber}",
                    category = LogCategory.REQUEST
                )

                // Формируем промпт для ревью
                val reviewPrompt = buildString {
                    appendLine("Проведи code review для Pull Request #${request.prNumber} в репозитории ${request.owner}/${request.repo}.")
                    appendLine()
                    appendLine("Используй инструменты для анализа:")
                    appendLine("1. github_get_pr_info - получи информацию о PR")
                    appendLine("2. github_get_pr_diff - получи diff изменений")
                    appendLine("3. github_get_pr_files - получи список файлов")
                    appendLine("4. docs_search - найди релевантную документацию")
                    appendLine()
                    appendLine("Параметры для GitHub инструментов:")
                    appendLine("- owner: ${request.owner}")
                    appendLine("- repo: ${request.repo}")
                    appendLine("- pr_number: ${request.prNumber}")
                    appendLine("- token: ${request.githubToken}")
                    appendLine()
                    appendLine("После анализа:")
                    appendLine("1. Сформируй структурированное ревью")
                    appendLine("2. Укажи найденные проблемы с указанием файла и строки")
                    appendLine("3. Дай рекомендации по улучшению")
                    appendLine("4. Определи итоговый статус: APPROVE, REQUEST_CHANGES или COMMENT")
                    if (request.postReview) {
                        appendLine()
                        appendLine("5. Опубликуй ревью с помощью github_post_review")
                    }
                }

                // Используем агента для анализа
                val conversationId = UUID.randomUUID().toString()
                val response = agent.chat(
                    userMessage = reviewPrompt,
                    conversationId = conversationId,
                    temperature = 0.3f  // Низкая температура для точности
                )

                val duration = System.currentTimeMillis() - startTime

                ServerLogger.log(
                    level = LogLevel.INFO,
                    message = "Code Review завершён за ${duration}ms",
                    category = LogCategory.RESPONSE
                )

                val reviewResponse = CodeReviewResponse(
                    status = "completed",
                    owner = request.owner,
                    repo = request.repo,
                    prNumber = request.prNumber,
                    review = response.message.content,
                    durationMs = duration
                )

                call.respond(HttpStatusCode.OK, reviewResponse)

            } catch (e: Exception) {
                ServerLogger.logError("Ошибка code review", e)
                call.respond(
                    HttpStatusCode.InternalServerError,
                    mapOf("error" to (e.message ?: "Ошибка code review"))
                )
            }
        }
    }
}

@Serializable
data class TokenCountRequest(
    val text: String,
    val sendToApi: Boolean = false
)

@Serializable
data class TokenEstimate(
    val text: String,
    val length: Int,
    val estimatedTokens: Int,
    val note: String
)

/**
 * Запрос на автоматическое ревью Pull Request.
 */
@Serializable
data class CodeReviewRequest(
    /** Владелец репозитория (organization или username) */
    val owner: String,
    /** Название репозитория */
    val repo: String,
    /** Номер Pull Request */
    val prNumber: Int,
    /** GitHub Personal Access Token с правами на repo */
    val githubToken: String,
    /** Опубликовать ревью в PR (по умолчанию false - только анализ) */
    val postReview: Boolean = false
)

/**
 * Ответ с результатами code review.
 */
@Serializable
data class CodeReviewResponse(
    /** Статус выполнения: completed, error */
    val status: String,
    /** Владелец репозитория */
    val owner: String,
    /** Название репозитория */
    val repo: String,
    /** Номер Pull Request */
    val prNumber: Int,
    /** Текст ревью с анализом и рекомендациями */
    val review: String,
    /** Время выполнения в миллисекундах */
    val durationMs: Long
)
