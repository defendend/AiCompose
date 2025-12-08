package org.example.tools.historical

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.example.tools.annotations.Param
import org.example.tools.annotations.Tool
import org.example.tools.core.AnnotatedAgentTool

/**
 * Инструмент для сравнения исторических эпох.
 */
@Tool(
    name = "compare_eras",
    description = "Сравнивает две исторические эпохи по ключевым характеристикам"
)
@Param(
    name = "era1",
    description = "Первая эпоха (античность, средневековье, возрождение, просвещение)",
    type = "string",
    required = true
)
@Param(
    name = "era2",
    description = "Вторая эпоха для сравнения",
    type = "string",
    required = true
)
object CompareErasTool : AnnotatedAgentTool() {

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

    override suspend fun execute(arguments: String): String {
        return try {
            val json = Json.parseToJsonElement(arguments).jsonObject
            val era1Name = json["era1"]?.jsonPrimitive?.contentOrNull?.lowercase()
                ?: return "Ошибка: не указана первая эпоха"
            val era2Name = json["era2"]?.jsonPrimitive?.contentOrNull?.lowercase()
                ?: return "Ошибка: не указана вторая эпоха"

            val era1 = eras[era1Name]
                ?: return "Эпоха '$era1Name' не найдена. Доступны: ${eras.keys.joinToString(", ")}"
            val era2 = eras[era2Name]
                ?: return "Эпоха '$era2Name' не найдена. Доступны: ${eras.keys.joinToString(", ")}"

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
