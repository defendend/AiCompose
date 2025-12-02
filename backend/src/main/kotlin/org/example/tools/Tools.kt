package org.example.tools

import kotlinx.serialization.json.*
import org.example.model.FunctionDefinition
import org.example.model.FunctionParameters
import org.example.model.PropertyDefinition
import org.example.model.Tool

/**
 * Интерфейс для инструментов агента
 */
interface AgentTool {
    val name: String
    val description: String
    fun getDefinition(): Tool
    suspend fun execute(arguments: String): String
}

/**
 * Инструмент для поиска исторических событий по году
 */
object HistoricalEventsTool : AgentTool {
    override val name = "get_historical_events"
    override val description = "Получает важные исторические события по указанному году"

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

    override fun getDefinition(): Tool = Tool(
        type = "function",
        function = FunctionDefinition(
            name = name,
            description = description,
            parameters = FunctionParameters(
                type = "object",
                properties = mapOf(
                    "year" to PropertyDefinition(
                        type = "integer",
                        description = "Год для поиска исторических событий"
                    )
                ),
                required = listOf("year")
            )
        )
    )

    override suspend fun execute(arguments: String): String {
        return try {
            val json = Json.parseToJsonElement(arguments).jsonObject
            val year = json["year"]?.jsonPrimitive?.intOrNull ?: return "Ошибка: не указан год"

            val events = historicalEvents[year]
            if (events != null) {
                "События $year года:\n" + events.mapIndexed { i, e -> "${i + 1}. $e" }.joinToString("\n")
            } else {
                "В базе данных нет записей о ключевых событиях $year года. Попробуйте другой год или задайте вопрос напрямую."
            }
        } catch (e: Exception) {
            "Ошибка: ${e.message}"
        }
    }
}

/**
 * Инструмент для получения информации об исторических личностях
 */
object HistoricalFigureTool : AgentTool {
    override val name = "get_historical_figure"
    override val description = "Получает краткую биографию известной исторической личности"

    private val figures = mapOf(
        "наполеон" to """
            Наполеон Бонапарт (1769-1821)
            Французский император и полководец.
            • Родился на Корсике
            • Пришёл к власти после Французской революции
            • Провёл успешные военные кампании по всей Европе
            • Ввёл Кодекс Наполеона — основу современного гражданского права
            • Потерпел поражение в России (1812) и при Ватерлоо (1815)
            • Умер в ссылке на острове Святой Елены
        """.trimIndent(),
        "цезарь" to """
            Гай Юлий Цезарь (100-44 до н.э.)
            Римский полководец, политик и диктатор.
            • Завоевал Галлию (современная Франция)
            • Перешёл Рубикон, начав гражданскую войну
            • Стал пожизненным диктатором Рима
            • Провёл календарную реформу (юлианский календарь)
            • Убит заговорщиками в мартовские иды
            • Его имя стало титулом — «цезарь», «кайзер», «царь»
        """.trimIndent(),
        "пётр" to """
            Пётр I Великий (1672-1725)
            Русский царь и первый император России.
            • Провёл масштабные реформы, модернизировав Россию
            • Основал Санкт-Петербург (1703)
            • Создал русский флот
            • Победил шведов в Северной войне (Полтавская битва, 1709)
            • Ввёл новое летоисчисление и европейскую одежду
            • Прорубил «окно в Европу»
        """.trimIndent(),
        "клеопатра" to """
            Клеопатра VII (69-30 до н.э.)
            Последняя царица Древнего Египта.
            • Последняя из династии Птолемеев
            • Была возлюбленной Юлия Цезаря и Марка Антония
            • Владела несколькими языками, включая египетский
            • Умная правительница и искусный дипломат
            • После поражения от Октавиана покончила с собой
            • С её смертью Египет стал римской провинцией
        """.trimIndent(),
        "александр" to """
            Александр Македонский (356-323 до н.э.)
            Царь Македонии, величайший полководец древности.
            • Ученик философа Аристотеля
            • Создал огромную империю от Греции до Индии
            • Разбил Персидскую империю Дария III
            • Основал более 70 городов, включая Александрию
            • Не проиграл ни одного сражения
            • Умер в 32 года, возможно от лихорадки
        """.trimIndent(),
        "леонардо" to """
            Леонардо да Винчи (1452-1519)
            Итальянский художник, учёный и изобретатель эпохи Возрождения.
            • Автор «Моны Лизы» и «Тайной вечери»
            • Анатом, инженер, архитектор, музыкант
            • Проектировал летательные аппараты и танки
            • Вёл знаменитые зеркальные записи
            • Идеальный «человек Возрождения» (homo universalis)
            • Работал при дворах Милана, Рима и Франции
        """.trimIndent()
    )

    override fun getDefinition(): Tool = Tool(
        type = "function",
        function = FunctionDefinition(
            name = name,
            description = description,
            parameters = FunctionParameters(
                type = "object",
                properties = mapOf(
                    "name" to PropertyDefinition(
                        type = "string",
                        description = "Имя исторической личности (например: Наполеон, Цезарь, Пётр, Клеопатра, Александр, Леонардо)"
                    )
                ),
                required = listOf("name")
            )
        )
    )

    override suspend fun execute(arguments: String): String {
        return try {
            val json = Json.parseToJsonElement(arguments).jsonObject
            val name = json["name"]?.jsonPrimitive?.content?.lowercase() ?: return "Ошибка: не указано имя"

            val figure = figures.entries.find { name.contains(it.key) }?.value
            figure ?: "Информация о '$name' не найдена в базе. Доступны: Наполеон, Цезарь, Пётр I, Клеопатра, Александр Македонский, Леонардо да Винчи."
        } catch (e: Exception) {
            "Ошибка: ${e.message}"
        }
    }
}

/**
 * Инструмент для сравнения исторических эпох
 */
object CompareErasTool : AgentTool {
    override val name = "compare_eras"
    override val description = "Сравнивает две исторические эпохи по ключевым характеристикам"

    private val eras = mapOf(
        "античность" to mapOf(
            "период" to "VIII в. до н.э. — V в. н.э.",
            "регион" to "Средиземноморье (Греция, Рим)",
            "достижения" to "Философия, демократия, право, архитектура (Парфенон, Колизей)",
            "общество" to "Рабовладельческий строй, полисы и империи",
            "культура" to "Олимпийские игры, театр, мифология"
        ),
        "средневековье" to mapOf(
            "период" to "V — XV века",
            "регион" to "Европа, Ближний Восток",
            "достижения" to "Готические соборы, университеты, рыцарство",
            "общество" to "Феодализм, сословия, власть церкви",
            "культура" to "Религиозное искусство, крестовые походы, схоластика"
        ),
        "возрождение" to mapOf(
            "период" to "XIV — XVII века",
            "регион" to "Италия, затем вся Европа",
            "достижения" to "Живопись (Микеланджело, Рафаэль), книгопечатание",
            "общество" to "Гуманизм, подъём городов, меценатство",
            "культура" to "Возврат к античным идеалам, научный метод"
        ),
        "просвещение" to mapOf(
            "период" to "XVII — XVIII века",
            "регион" to "Франция, Англия, Германия",
            "достижения" to "Энциклопедия, научные открытия, конституции",
            "общество" to "Критика абсолютизма, права человека",
            "культура" to "Разум, прогресс, образование для всех"
        )
    )

    override fun getDefinition(): Tool = Tool(
        type = "function",
        function = FunctionDefinition(
            name = name,
            description = description,
            parameters = FunctionParameters(
                type = "object",
                properties = mapOf(
                    "era1" to PropertyDefinition(
                        type = "string",
                        description = "Первая эпоха (античность, средневековье, возрождение, просвещение)"
                    ),
                    "era2" to PropertyDefinition(
                        type = "string",
                        description = "Вторая эпоха для сравнения"
                    )
                ),
                required = listOf("era1", "era2")
            )
        )
    )

    override suspend fun execute(arguments: String): String {
        return try {
            val json = Json.parseToJsonElement(arguments).jsonObject
            val era1Name = json["era1"]?.jsonPrimitive?.content?.lowercase() ?: return "Ошибка: не указана первая эпоха"
            val era2Name = json["era2"]?.jsonPrimitive?.content?.lowercase() ?: return "Ошибка: не указана вторая эпоха"

            val era1 = eras[era1Name] ?: return "Эпоха '$era1Name' не найдена. Доступны: ${eras.keys.joinToString(", ")}"
            val era2 = eras[era2Name] ?: return "Эпоха '$era2Name' не найдена. Доступны: ${eras.keys.joinToString(", ")}"

            buildString {
                appendLine("=== Сравнение эпох ===\n")
                appendLine("【${era1Name.uppercase()}】")
                era1.forEach { (k, v) -> appendLine("• $k: $v") }
                appendLine()
                appendLine("【${era2Name.uppercase()}】")
                era2.forEach { (k, v) -> appendLine("• $k: $v") }
            }
        } catch (e: Exception) {
            "Ошибка: ${e.message}"
        }
    }
}

/**
 * Инструмент для получения исторических цитат
 */
object HistoricalQuoteTool : AgentTool {
    override val name = "get_historical_quote"
    override val description = "Возвращает известную историческую цитату по теме или автору"

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

    override fun getDefinition(): Tool = Tool(
        type = "function",
        function = FunctionDefinition(
            name = name,
            description = description,
            parameters = FunctionParameters(
                type = "object",
                properties = mapOf(
                    "topic" to PropertyDefinition(
                        type = "string",
                        description = "Тема или имя автора для поиска цитаты (опционально, если не указано — случайная цитата)"
                    )
                )
            )
        )
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

/**
 * Реестр всех доступных инструментов
 */
object ToolRegistry {
    private val tools: Map<String, AgentTool> = listOf(
        HistoricalEventsTool,
        HistoricalFigureTool,
        CompareErasTool,
        HistoricalQuoteTool
    ).associateBy { it.name }

    fun getAllTools(): List<Tool> = tools.values.map { it.getDefinition() }

    fun getTool(name: String): AgentTool? = tools[name]

    suspend fun executeTool(name: String, arguments: String): String {
        val tool = getTool(name) ?: return "Ошибка: инструмент '$name' не найден"
        return tool.execute(arguments)
    }
}
