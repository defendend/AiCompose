package org.example.tools.historical

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.example.tools.annotations.Param
import org.example.tools.annotations.Tool
import org.example.tools.core.AnnotatedAgentTool

/**
 * Инструмент для поиска исторических событий по году.
 */
@Tool(
    name = "get_historical_events",
    description = "Получает важные исторические события по указанному году"
)
@Param(
    name = "year",
    description = "Год для поиска исторических событий",
    type = "integer",
    required = true
)
object HistoricalEventsTool : AnnotatedAgentTool() {

    private val historicalEvents = mapOf(
        1066 to listOf(
            "Битва при Гастингсе — Вильгельм Завоеватель разбил англосаксонское войско короля Гарольда II",
            "Начало нормандского завоевания Англии"
        ),
        1453 to listOf(
            "Падение Константинополя — конец Византийской империи",
            "Окончание Столетней войны между Англией и Францией"
        ),
        1492 to listOf(
            "Христофор Колумб открыл Америку",
            "Завершение Реконкисты в Испании — падение Гранады"
        ),
        1789 to listOf(
            "Начало Великой французской революции",
            "Взятие Бастилии 14 июля",
            "Принятие Декларации прав человека и гражданина"
        ),
        1812 to listOf(
            "Отечественная война — вторжение Наполеона в Россию",
            "Бородинское сражение",
            "Пожар Москвы и отступление Великой армии"
        ),
        1861 to listOf(
            "Отмена крепостного права в России Александром II",
            "Начало Гражданской войны в США"
        ),
        1917 to listOf(
            "Февральская революция в России — отречение Николая II",
            "Октябрьская революция — большевики пришли к власти",
            "США вступили в Первую мировую войну"
        ),
        1945 to listOf(
            "Окончание Второй мировой войны",
            "Капитуляция нацистской Германии 9 мая",
            "Атомные бомбардировки Хиросимы и Нагасаки",
            "Создание ООН"
        ),
        1961 to listOf(
            "Юрий Гагарин — первый человек в космосе",
            "Строительство Берлинской стены"
        ),
        1969 to listOf(
            "Нил Армстронг — первый человек на Луне",
            "Миссия Аполлон-11"
        ),
        1991 to listOf(
            "Распад СССР",
            "Августовский путч в Москве",
            "Беловежские соглашения"
        )
    )

    override suspend fun execute(arguments: String): String {
        return try {
            val json = Json.parseToJsonElement(arguments).jsonObject
            val year = json["year"]?.jsonPrimitive?.intOrNull
                ?: return "Ошибка: не указан год"

            val events = historicalEvents[year]
            if (events != null) {
                "События $year года:\n" + events.mapIndexed { i, e -> "${i + 1}. $e" }.joinToString("\n")
            } else {
                "В базе данных нет записей о ключевых событиях $year года. " +
                    "Попробуйте другой год или задайте вопрос напрямую."
            }
        } catch (e: Exception) {
            "Ошибка: ${e.message}"
        }
    }
}
