package org.example.api

import io.ktor.http.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.example.agent.Agent
import org.example.logging.ServerLogger
import org.example.model.ChatRequest
import org.example.model.LogCategory
import org.example.model.LogLevel
import java.util.UUID

private val json = Json { prettyPrint = true }

fun Route.chatRoutes(agent: Agent) {
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

                val response = agent.chat(request.message, conversationId)
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

        get("/health") {
            call.respond(HttpStatusCode.OK, mapOf("status" to "ok"))
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
    }
}
