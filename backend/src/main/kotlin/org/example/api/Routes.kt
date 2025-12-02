package org.example.api

import io.ktor.http.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import org.example.agent.Agent
import org.example.model.ChatRequest
import org.slf4j.LoggerFactory
import java.util.UUID

fun Route.chatRoutes(agent: Agent) {
    val logger = LoggerFactory.getLogger("ChatRoutes")

    route("/api") {
        post("/chat") {
            try {
                val request = call.receive<ChatRequest>()
                logger.info("Получен запрос: ${request.message}")

                val conversationId = request.conversationId ?: UUID.randomUUID().toString()

                val response = agent.chat(request.message, conversationId)

                call.respond(HttpStatusCode.OK, response)
            } catch (e: Exception) {
                logger.error("Ошибка обработки запроса", e)
                call.respond(
                    HttpStatusCode.InternalServerError,
                    mapOf("error" to (e.message ?: "Неизвестная ошибка"))
                )
            }
        }

        get("/health") {
            call.respond(HttpStatusCode.OK, mapOf("status" to "ok"))
        }
    }
}
