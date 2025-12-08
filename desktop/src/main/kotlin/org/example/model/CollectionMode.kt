package org.example.model

import org.example.shared.model.CollectionMode

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
