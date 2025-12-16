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
import kotlinx.coroutines.runBlocking
import org.example.agent.Agent
import org.example.api.chatRoutes
import org.example.data.ConversationRepository
import org.example.data.LLMClient
import org.example.di.appModule
import org.example.integrations.WeatherMcpClient
import org.example.logging.ServerLogger
import org.example.model.LogLevel
import org.example.tools.McpToolsAdapter
import org.example.tools.core.ToolRegistry
import org.koin.ktor.ext.inject
import org.koin.ktor.ext.getKoin
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
        configureMcpTools()
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
 * Инициализация MCP инструментов.
 * Подключается к MCP серверам и регистрирует инструменты в ToolRegistry.
 */
fun Application.configureMcpTools() {
    val logger = LoggerFactory.getLogger("Application")

    runBlocking {
        try {
            // Получаем зависимости через Koin
            val koin = getKoin()
            val weatherMcpClient = koin.getOrNull<WeatherMcpClient>()
            val mcpToolsAdapter = koin.get<McpToolsAdapter>()

            // Подключаемся к MCP серверу погоды, если доступен
            if (weatherMcpClient != null) {
                logger.info("🌦️  Подключение к MCP серверу погоды...")
                weatherMcpClient.connect()
                logger.info("✅ MCP сервер погоды подключен")
            } else {
                logger.info("ℹ️  MCP сервер погоды недоступен")
            }

            // Регистрируем MCP инструменты в ToolRegistry
            val mcpTools = mcpToolsAdapter.getTools()
            logger.info("📋 Регистрация ${mcpTools.size} MCP инструментов...")
            mcpTools.forEach { tool ->
                ToolRegistry.register(tool)
                logger.debug("  ✓ ${tool.name}")
            }

            logger.info("✅ Зарегистрировано инструментов: ${ToolRegistry.size()}")
            logger.info("📝 Доступные инструменты: ${ToolRegistry.getToolNames()}")

        } catch (e: Exception) {
            logger.error("❌ Ошибка при инициализации MCP инструментов", e)
            // Не бросаем исключение, чтобы сервер мог запуститься без MCP
        }
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
    val llmClient by inject<LLMClient>()
    val conversationRepository by inject<ConversationRepository>()

    routing {
        chatRoutes(agent, llmClient, conversationRepository)
    }
}
