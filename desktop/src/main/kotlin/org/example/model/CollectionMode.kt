package org.example.model

import kotlinx.serialization.Serializable

/**
 * Режим сбора данных — определяет какую информацию модель должна собрать
 * и когда она должна выдать финальный результат
 */
@Serializable
enum class CollectionMode {
    /** Обычный режим — без ограничений */
    NONE,
    /** Сбор требований для технического задания */
    TECHNICAL_SPEC,
    /** Сбор информации для брифа дизайна */
    DESIGN_BRIEF,
    /** Сбор информации для резюме проекта */
    PROJECT_SUMMARY,
    /** Пользовательский режим */
    CUSTOM,

    // === Режимы решения задач ===
    /** Прямой ответ — модель отвечает сразу без рассуждений */
    SOLVE_DIRECT,
    /** Пошаговое решение — модель решает задачу шаг за шагом */
    SOLVE_STEP_BY_STEP,
    /** Группа экспертов — несколько экспертов дают свои решения */
    SOLVE_EXPERT_PANEL
}

/**
 * Настройки для режима сбора данных
 */
@Serializable
data class CollectionSettings(
    val mode: CollectionMode = CollectionMode.NONE,
    /** Пользовательское описание для режима CUSTOM */
    val customPrompt: String = "",
    /** Название результата (например, "Техническое задание") */
    val resultTitle: String = "",
    /** Включён ли режим сбора */
    val enabled: Boolean = false
) {
    companion object {
        val DISABLED = CollectionSettings()

        fun forMode(mode: CollectionMode): CollectionSettings {
            return when (mode) {
                CollectionMode.NONE -> DISABLED
                CollectionMode.TECHNICAL_SPEC -> CollectionSettings(
                    mode = mode,
                    resultTitle = "Техническое задание",
                    enabled = true
                )
                CollectionMode.DESIGN_BRIEF -> CollectionSettings(
                    mode = mode,
                    resultTitle = "Бриф для дизайна",
                    enabled = true
                )
                CollectionMode.PROJECT_SUMMARY -> CollectionSettings(
                    mode = mode,
                    resultTitle = "Резюме проекта",
                    enabled = true
                )
                CollectionMode.CUSTOM -> CollectionSettings(
                    mode = mode,
                    resultTitle = "Результат",
                    enabled = true
                )
                CollectionMode.SOLVE_DIRECT -> CollectionSettings(
                    mode = mode,
                    resultTitle = "Прямой ответ",
                    enabled = true
                )
                CollectionMode.SOLVE_STEP_BY_STEP -> CollectionSettings(
                    mode = mode,
                    resultTitle = "Пошаговое решение",
                    enabled = true
                )
                CollectionMode.SOLVE_EXPERT_PANEL -> CollectionSettings(
                    mode = mode,
                    resultTitle = "Мнения экспертов",
                    enabled = true
                )
            }
        }
    }
}

/**
 * Шаблон режима сбора для отображения в UI
 */
data class CollectionModeTemplate(
    val mode: CollectionMode,
    val title: String,
    val description: String,
    val icon: String,
    val requiredFields: List<String>
)

/**
 * Доступные шаблоны режимов сбора
 */
object CollectionModeTemplates {
    val templates = listOf(
        CollectionModeTemplate(
            mode = CollectionMode.NONE,
            title = "Обычный чат",
            description = "Стандартный режим общения без сбора данных",
            icon = "💬",
            requiredFields = emptyList()
        ),
        CollectionModeTemplate(
            mode = CollectionMode.TECHNICAL_SPEC,
            title = "Техническое задание",
            description = "Соберу требования для ТЗ: цель, функционал, технологии, ограничения",
            icon = "📋",
            requiredFields = listOf(
                "Цель проекта",
                "Целевая аудитория",
                "Функциональные требования",
                "Нефункциональные требования",
                "Технологический стек",
                "Ограничения и зависимости",
                "Критерии приёмки"
            )
        ),
        CollectionModeTemplate(
            mode = CollectionMode.DESIGN_BRIEF,
            title = "Бриф для дизайна",
            description = "Соберу информацию для дизайн-брифа: стиль, аудитория, предпочтения",
            icon = "🎨",
            requiredFields = listOf(
                "Название проекта",
                "Описание бренда/продукта",
                "Целевая аудитория",
                "Стилевые предпочтения",
                "Референсы",
                "Цветовая палитра",
                "Ограничения"
            )
        ),
        CollectionModeTemplate(
            mode = CollectionMode.PROJECT_SUMMARY,
            title = "Резюме проекта",
            description = "Соберу ключевую информацию о проекте для презентации",
            icon = "📊",
            requiredFields = listOf(
                "Название проекта",
                "Проблема",
                "Решение",
                "Ключевые преимущества",
                "Целевой рынок",
                "Этапы реализации"
            )
        ),
        CollectionModeTemplate(
            mode = CollectionMode.CUSTOM,
            title = "Свой режим",
            description = "Настройте свой режим сбора данных",
            icon = "⚙️",
            requiredFields = emptyList()
        ),
        // === Режимы решения задач ===
        CollectionModeTemplate(
            mode = CollectionMode.SOLVE_DIRECT,
            title = "Прямой ответ",
            description = "Модель даёт ответ напрямую, без объяснений и рассуждений",
            icon = "⚡",
            requiredFields = listOf("Быстрый ответ", "Без рассуждений")
        ),
        CollectionModeTemplate(
            mode = CollectionMode.SOLVE_STEP_BY_STEP,
            title = "Пошаговое решение",
            description = "Модель решает задачу шаг за шагом, объясняя каждый этап",
            icon = "🔢",
            requiredFields = listOf("Анализ задачи", "Шаги решения", "Промежуточные выводы", "Итоговый ответ")
        ),
        CollectionModeTemplate(
            mode = CollectionMode.SOLVE_EXPERT_PANEL,
            title = "Группа экспертов",
            description = "Три эксперта анализируют задачу и дают свои решения, затем сравнение",
            icon = "👥",
            requiredFields = listOf("Мнение логика", "Мнение практика", "Мнение критика", "Сравнение и вывод")
        )
    )

    fun getTemplate(mode: CollectionMode): CollectionModeTemplate {
        return templates.find { it.mode == mode } ?: templates.first()
    }
}
