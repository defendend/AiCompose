package org.example.tools.historical

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.example.tools.annotations.Param
import org.example.tools.annotations.Tool
import org.example.tools.core.AnnotatedAgentTool

/**
 * Инструмент для получения исторических цитат.
 */
@Tool(
    name = "get_historical_quote",
    description = "Возвращает известную историческую цитату по теме или автору"
)
@Param(
    name = "topic",
    description = "Тема или имя автора для поиска цитаты (опционально, если не указано — случайная цитата)",
    type = "string",
    required = false
)
object HistoricalQuoteTool : AnnotatedAgentTool() {

    private val quotes = listOf(
        Triple("Юлий Цезарь", "Пришёл, увидел, победил.", "после победы над Фарнаком"),
        Triple("Архимед", "Дайте мне точку опоры, и я переверну Землю.", "о силе рычага"),
        Triple("Людовик XIV", "Государство — это я.", "об абсолютной власти"),
        Triple("Вольтер", "Я не согласен с тем, что вы говорите, но готов умереть за ваше право это говорить.", "о свободе слова"),
        Triple("Наполеон", "От великого до смешного — один шаг.", "после отступления из России"),
        Triple("Александр Невский", "Кто с мечом к нам придёт, от меча и погибнет.", "о защите Руси"),
        Triple("Пётр I", "Промедление смерти подобно.", "о необходимости действовать"),
        Triple("Черчилль", "Я могу предложить лишь кровь, тяжёлый труд, слёзы и пот.", "в начале Второй мировой"),
        Triple("Мартин Лютер Кинг", "У меня есть мечта.", "о расовом равенстве"),
        Triple("Нил Армстронг", "Это один маленький шаг для человека, но гигантский скачок для человечества.", "на Луне")
    )

    override suspend fun execute(arguments: String): String {
        return try {
            val json = Json.parseToJsonElement(arguments).jsonObject
            val topic = json["topic"]?.jsonPrimitive?.contentOrNull?.lowercase()

            val quote = if (topic.isNullOrBlank()) {
                quotes.random()
            } else {
                quotes.find {
                    it.first.lowercase().contains(topic) ||
                        it.second.lowercase().contains(topic) ||
                        it.third.lowercase().contains(topic)
                } ?: quotes.random()
            }

            "«${quote.second}»\n— ${quote.first} (${quote.third})"
        } catch (e: Exception) {
            "Ошибка: ${e.message}"
        }
    }
}
