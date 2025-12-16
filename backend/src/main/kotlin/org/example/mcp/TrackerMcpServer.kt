package org.example.mcp

import io.ktor.client.*
import io.ktor.client.engine.cio.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.serialization.kotlinx.json.*
import io.modelcontextprotocol.kotlin.sdk.*
import io.modelcontextprotocol.kotlin.sdk.server.*
import kotlinx.coroutines.runBlocking
import kotlinx.io.asSource
import kotlinx.io.asSink
import kotlinx.io.buffered
import org.example.mcp.tools.YandexTrackerMcpTool

/**
 * MCP сервер с инструментами Яндекс.Трекера
 *
 * Переменные окружения:
 * - YANDEX_TRACKER_TOKEN - OAuth токен для API
 * - YANDEX_TRACKER_ORG_ID - ID организации
 *
 * Запуск:
 * YANDEX_TRACKER_TOKEN=xxx YANDEX_TRACKER_ORG_ID=yyy ./gradlew :backend:run -PmainClass=org.example.mcp.TrackerMcpServerKt
 */
fun startTrackerMcpServer() = runBlocking {
    // Читаем конфигурацию из переменных окружения
    val oauthToken = System.getenv("YANDEX_TRACKER_TOKEN")
    val orgId = System.getenv("YANDEX_TRACKER_ORG_ID")

    if (oauthToken == null || orgId == null) {
        println("⚠️  ПРЕДУПРЕЖДЕНИЕ: Яндекс.Трекер не настроен!")
        println("   Установите переменные окружения:")
        println("   - YANDEX_TRACKER_TOKEN - OAuth токен")
        println("   - YANDEX_TRACKER_ORG_ID - ID организации")
        println("")
        println("   Сервер запустится, но инструменты будут недоступны.")
        println("")
    }

    // Создаём HTTP клиент для запросов к API
    val httpClient = HttpClient(CIO) {
        install(ContentNegotiation) {
            json()
        }
    }

    // Создаём MCP сервер
    val server = Server(
        serverInfo = Implementation(
            name = "yandex-tracker-mcp-server",
            version = "1.0.0"
        ),
        options = ServerOptions(
            capabilities = ServerCapabilities(
                tools = ServerCapabilities.Tools(listChanged = false)
            )
        )
    )

    // Регистрируем инструменты Яндекс.Трекера
    val trackerTool = YandexTrackerMcpTool(httpClient, oauthToken, orgId)
    trackerTool.register(server)

    println("🚀 Яндекс.Трекер MCP сервер запущен")
    println("📋 Доступные инструменты:")
    println("   • yandex_tracker_get_open_issues_count - Получить количество открытых задач")
    println("   • yandex_tracker_search_issues - Поиск задач по фильтрам")
    println("   • yandex_tracker_get_issue - Получить детали задачи")
    println("")

    // Запускаем сервер через STDIO транспорт
    server.connect(
        StdioServerTransport(
            System.`in`.asSource().buffered(),
            System.out.asSink().buffered()
        )
    )
}

fun main() {
    startTrackerMcpServer()
}
