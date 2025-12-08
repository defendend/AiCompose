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
import org.example.di.appModule
import org.example.logging.ServerLogger
import org.example.model.LogLevel
import org.koin.ktor.ext.inject
import org.koin.ktor.plugin.Koin
import org.koin.logger.slf4jLogger
import org.slf4j.LoggerFactory
import org.slf4j.event.Level

fun main() {
    val logger = LoggerFactory.getLogger("Application")

    val apiKey = System.getenv("DEEPSEEK_API_KEY")
        ?: throw RuntimeException("DEEPSEEK_API_KEY environment variable is not set")

    logger.info("Запуск сервера AiCompose Backend...")
    ServerLogger.logSystem("Запуск сервера AiCompose Backend...", LogLevel.INFO)

    embeddedServer(Netty, port = 8080, host = "0.0.0.0") {
        configureKoin(apiKey)
        configurePlugins()
        configureRouting()
    }.start(wait = true)
}

/**
 * Конфигурация Koin DI.
 */
fun Application.configureKoin(apiKey: String) {
    install(Koin) {
        slf4jLogger()
        modules(appModule(apiKey))
    }
}

/**
 * Конфигурация Ktor плагинов.
 */
fun Application.configurePlugins() {
    val logger = LoggerFactory.getLogger("Application")

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
        allowMethod(HttpMethod.Delete)
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
}

/**
 * Конфигурация роутинга с инжектированными зависимостями.
 */
fun Application.configureRouting() {
    val agent by inject<Agent>()

    routing {
        chatRoutes(agent)
    }
}
