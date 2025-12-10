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
import org.example.shared.model.ChatRequest
import org.example.shared.model.ChatStreamRequest
import org.example.shared.model.HealthCheckResponse
import org.example.shared.model.ServiceHealth
import java.util.UUID

private val json = Json { prettyPrint = true }
private val jsonCompact = Json { prettyPrint = false }

fun Route.chatRoutes(
    agent: Agent,
    llmClient: LLMClient? = null,
    conversationRepository: ConversationRepository? = null
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

                val response = agent.chat(request.message, conversationId, request.responseFormat, request.collectionSettings, request.temperature)
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
