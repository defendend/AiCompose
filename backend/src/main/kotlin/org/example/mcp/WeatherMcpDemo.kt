package org.example.mcp

import kotlinx.coroutines.runBlocking
import org.example.integrations.WeatherMcpClient

/**
 * Демонстрация прямого вызова MCP сервера погоды.
 *
 * Запуск:
 * ./gradlew :backend:run -PmainClass=org.example.mcp.WeatherMcpDemoKt
 */
fun main() = runBlocking {
    val client = WeatherMcpClient()

    println("🌦️  === Демо MCP Weather Server ===")
    println()

    try {
        // Проверяем доступность
        if (!client.isAvailable()) {
            println("⚠️  MCP сервер погоды недоступен.")
            println("   Установите: pip install mcp_weather_server")
            return@runBlocking
        }

        println("✅ MCP сервер погоды доступен")
        println()

        // Подключаемся
        println("🔌 Подключение к MCP серверу...")
        client.connect()
        println("✅ Подключено!")
        println()

        // Тестируем инструменты
        println("=== Тест 1: Текущая погода в Москве ===")
        val weatherMoscow = client.getCurrentWeather("Moscow")
        println(weatherMoscow)
        println()

        println("=== Тест 2: Текущая погода в Лондоне ===")
        val weatherLondon = client.getCurrentWeather("London")
        println(weatherLondon)
        println()

        println("=== Тест 3: Качество воздуха в Москве ===")
        val airQuality = client.getAirQuality("Moscow")
        println(airQuality)
        println()

        println("=== Тест 4: Детальная погода в Токио ===")
        val weatherDetails = client.getWeatherDetails("Tokyo")
        println(weatherDetails)
        println()

        // Отключаемся
        println("🔌 Отключение от MCP сервера...")
        client.disconnect()
        println("✅ Демо завершено!")

    } catch (e: Exception) {
        println("❌ Ошибка: ${e.message}")
        e.printStackTrace()
        client.disconnect()
    }
}
