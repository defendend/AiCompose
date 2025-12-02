package org.example

import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.*
import io.ktor.server.application.*
import io.ktor.server.engine.*
import io.ktor.server.netty.*
import io.ktor.server.plugins.calllogging.*
import io.ktor.server.plugins.contentnegotiation.*
import io.ktor.server.plugins.cors.routing.*
import io.ktor.server.plugins.statuspages.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import kotlinx.serialization.json.Json
import org.example.agent.Agent
import org.example.api.chatRoutes
import org.slf4j.LoggerFactory
import org.slf4j.event.Level

fun main() {
    val logger = LoggerFactory.getLogger("Application")

    val apiKey = System.getenv("DEEPSEEK_API_KEY")
        ?: throw RuntimeException("DEEPSEEK_API_KEY environment variable is not set")

    logger.info("Запуск сервера AiCompose Backend...")

    val agent = Agent(apiKey = apiKey)

    embeddedServer(Netty, port = 8080, host = "0.0.0.0") {
        install(ContentNegotiation) {
            json(Json {
                prettyPrint = true
                isLenient = true
                ignoreUnknownKeys = true
            })
        }

        install(CORS) {
            anyHost()
            allowHeader(HttpHeaders.ContentType)
            allowHeader(HttpHeaders.Authorization)
            allowMethod(HttpMethod.Get)
            allowMethod(HttpMethod.Post)
            allowMethod(HttpMethod.Options)
        }

        install(CallLogging) {
            level = Level.INFO
        }

        install(StatusPages) {
            exception<Throwable> { call, cause ->
                logger.error("Unhandled exception", cause)
                call.respond(
                    HttpStatusCode.InternalServerError,
                    mapOf("error" to (cause.message ?: "Internal server error"))
                )
            }
        }

        routing {
            chatRoutes(agent)
        }
    }.start(wait = true)
}
