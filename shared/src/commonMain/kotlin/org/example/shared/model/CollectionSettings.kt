package org.example.shared.model

import kotlinx.serialization.Serializable

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
    val enabled: Boolean = false,
    /** Пользовательский системный промпт (персонаж агента) */
    val customSystemPrompt: String = ""
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
