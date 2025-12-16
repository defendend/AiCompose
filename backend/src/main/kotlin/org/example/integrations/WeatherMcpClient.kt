package org.example.integrations

import io.modelcontextprotocol.kotlin.sdk.*
import io.modelcontextprotocol.kotlin.sdk.client.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.io.asSource
import kotlinx.io.asSink
import kotlinx.io.buffered
import kotlinx.serialization.json.*
import org.slf4j.LoggerFactory

/**
 * Клиент для подключения к MCP серверу погоды (Open-Meteo)
 *
 * Запускает Python процесс `python3 -m mcp_weather_server` и подключается через stdio транспорт.
 *
 * Доступные инструменты:
 * - get_current_weather - текущая погода
 * - get_weather_by_datetime_range - почасовая погода
 * - get_weather_details - детальные данные
 * - get_air_quality - качество воздуха
 */
class WeatherMcpClient {
    private val logger = LoggerFactory.getLogger(WeatherMcpClient::class.java)
    private var process: Process? = null
    private var client: Client? = null
    private var isConnected = false

    /**
     * Инициализация и подключение к MCP серверу
     */
    suspend fun connect() = withContext(Dispatchers.IO) {
        try {
            logger.info("🌦️  Запуск MCP сервера погоды...")

            // Запускаем процесс Python MCP сервера
            val processBuilder = ProcessBuilder(
                "python3", "-m", "mcp_weather_server", "--mode", "stdio"
            )
            processBuilder.redirectErrorStream(false) // stderr отдельно для логов

            process = processBuilder.start()
            logger.info("✅ Процесс MCP сервера запущен (PID: ${process?.pid()})")

            // Создаём MCP клиент
            val mcpClient = Client(
                clientInfo = Implementation(
                    name = "aicompose-weather-client",
                    version = "1.0.0"
                )
            )

            // Создаём stdio транспорт
            val transport = StdioClientTransport(
                input = process!!.inputStream.asSource().buffered(),
                output = process!!.outputStream.asSink().buffered()
            )

            // Подключаемся к серверу
            logger.info("🔌 Подключение к MCP серверу через stdio...")
            mcpClient.connect(transport)

            client = mcpClient
            isConnected = true

            logger.info("✅ Подключение к MCP серверу погоды успешно!")

        } catch (e: Exception) {
            logger.error("❌ Ошибка подключения к MCP серверу погоды", e)
            disconnect()
            throw e
        }
    }

    /**
     * Получить текущую погоду для города
     */
    suspend fun getCurrentWeather(location: String): String = withContext(Dispatchers.IO) {
        ensureConnected()

        try {
            logger.info("🌡️  Запрос текущей погоды для: $location")

            val result = client!!.callTool(
                name = "get_current_weather",
                arguments = buildJsonObject {
                    put("location", location)
                }
            )

            // Извлекаем текст из результата
            val content = result.content.firstOrNull()
            when (content) {
                is TextContent -> {
                    logger.info("✅ Получена погода для $location")
                    content.text
                }
                else -> {
                    logger.warn("⚠️  Неожиданный формат ответа от MCP сервера")
                    "Не удалось получить данные о погоде"
                }
            }
        } catch (e: Exception) {
            logger.error("❌ Ошибка при запросе погоды", e)
            "Ошибка: ${e.message}"
        }
    }

    /**
     * Получить детальную погоду (JSON формат)
     */
    suspend fun getWeatherDetails(location: String): String = withContext(Dispatchers.IO) {
        ensureConnected()

        try {
            logger.info("📊 Запрос детальной погоды для: $location")

            val result = client!!.callTool(
                name = "get_weather_details",
                arguments = buildJsonObject {
                    put("location", location)
                }
            )

            val content = result.content.firstOrNull()
            when (content) {
                is TextContent -> {
                    logger.info("✅ Получены детали погоды для $location")
                    content.text
                }
                else -> "Не удалось получить детальные данные о погоде"
            }
        } catch (e: Exception) {
            logger.error("❌ Ошибка при запросе деталей погоды", e)
            "Ошибка: ${e.message}"
        }
    }

    /**
     * Получить качество воздуха
     */
    suspend fun getAirQuality(location: String): String = withContext(Dispatchers.IO) {
        ensureConnected()

        try {
            logger.info("🌫️  Запрос качества воздуха для: $location")

            val result = client!!.callTool(
                name = "get_air_quality",
                arguments = buildJsonObject {
                    put("location", location)
                }
            )

            val content = result.content.firstOrNull()
            when (content) {
                is TextContent -> {
                    logger.info("✅ Получено качество воздуха для $location")
                    content.text
                }
                else -> "Не удалось получить данные о качестве воздуха"
            }
        } catch (e: Exception) {
            logger.error("❌ Ошибка при запросе качества воздуха", e)
            "Ошибка: ${e.message}"
        }
    }

    /**
     * Отключение от сервера и завершение процесса
     */
    suspend fun disconnect() = withContext(Dispatchers.IO) {
        try {
            if (isConnected) {
                logger.info("🔌 Отключение от MCP сервера погоды...")
                client?.close()
                isConnected = false
            }

            process?.let {
                if (it.isAlive) {
                    logger.info("⏹️  Остановка процесса MCP сервера...")
                    it.destroy()
                    it.waitFor(5, java.util.concurrent.TimeUnit.SECONDS)
                    if (it.isAlive) {
                        logger.warn("⚠️  Принудительная остановка процесса...")
                        it.destroyForcibly()
                    }
                }
            }
            process = null
            client = null

            logger.info("✅ MCP сервер погоды остановлен")
        } catch (e: Exception) {
            logger.error("❌ Ошибка при отключении от MCP сервера", e)
        }
    }

    private fun ensureConnected() {
        if (!isConnected || client == null) {
            throw IllegalStateException("MCP клиент погоды не подключен. Вызовите connect() сначала.")
        }
    }

    /**
     * Проверка доступности MCP сервера погоды
     */
    fun isAvailable(): Boolean {
        return try {
            // Проверяем, установлен ли Python пакет
            val process = ProcessBuilder("python3", "-c", "import mcp_weather_server")
                .redirectErrorStream(true)
                .start()
            val exitCode = process.waitFor()
            exitCode == 0
        } catch (e: Exception) {
            logger.warn("⚠️  MCP сервер погоды недоступен: ${e.message}")
            false
        }
    }
}
